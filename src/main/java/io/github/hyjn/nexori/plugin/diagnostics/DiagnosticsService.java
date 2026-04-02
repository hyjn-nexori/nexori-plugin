package io.github.hyjn.nexori.plugin.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;

import javax.annotation.Nonnull;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class DiagnosticsService {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter FILE_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final long MAX_SEGMENT_BYTES = 1024L * 1024L;
    private static final long MAX_SEGMENT_LINES = 5000L;
    private static final long RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1000L;
    private static final int MAX_EVENT_BYTES = 2048;

    private final HytaleLogger logger;
    private final Path journalDir;
    private final DiagnosticsSequenceStore sequenceStore;
    private final String localServerId;
    private final Supplier<String> localConnectionAddressSupplier;
    private final String pluginVersion;
    private ActiveSegment activeSegment;

    public DiagnosticsService(
        @Nonnull HytaleLogger logger,
        @Nonnull Path pluginDataDirectory,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull Supplier<String> localConnectionAddressSupplier,
        @Nonnull String pluginVersion
    ) throws IOException {
        this.logger = logger;
        Path diagnosticsDir = pluginDataDirectory.resolve("state").resolve("diagnostics");
        this.journalDir = diagnosticsDir.resolve("journal");
        Files.createDirectories(this.journalDir);
        this.sequenceStore = new DiagnosticsSequenceStore(diagnosticsDir);
        this.localServerId = localIdentity.serverId().toString();
        this.localConnectionAddressSupplier = localConnectionAddressSupplier;
        this.pluginVersion = pluginVersion == null || pluginVersion.isBlank() ? "unknown" : pluginVersion;
        pruneRetention();
    }

    @Nonnull
    public String newOperationId(@Nonnull String prefix) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "operation" : prefix.trim().toLowerCase();
        return normalizedPrefix + ":" + UUID.randomUUID();
    }

    public synchronized void record(
        @Nonnull DiagnosticsCategory category,
        @Nonnull String action,
        @Nonnull DiagnosticsOutcome outcome,
        @Nonnull DiagnosticsReasonClass reasonClass,
        @Nonnull String reasonCode,
        @Nonnull String message,
        @Nonnull String operationId,
        Consumer<DiagnosticsEvent.Builder> customizer
    ) {
        try {
            long now = System.currentTimeMillis();
            DiagnosticsEvent.Builder builder = new DiagnosticsEvent.Builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAtEpochMs(now)
                .sourceSequence(sequenceStore.next())
                .sourceServerId(localServerId)
                .sourceConnectionAddress(safeAddress(localConnectionAddressSupplier.get()))
                .pluginVersion(pluginVersion)
                .category(category)
                .action(action)
                .outcome(outcome)
                .reasonClass(reasonClass)
                .reasonCode(reasonCode)
                .message(message == null ? "" : message.trim())
                .operationId(operationId);
            if (customizer != null) {
                customizer.accept(builder);
            }

            DiagnosticsEvent event = truncate(builder.build());
            append(event);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist a Nexori diagnostics event.");
        } catch (RuntimeException exception) {
            logger.atWarning().withCause(exception).log("Failed to build a Nexori diagnostics event.");
        }
    }

    @Nonnull
    public synchronized LocalDiagnosticsView loadLocalView() {
        List<DiagnosticsEvent> events = loadAllEvents();
        return new LocalDiagnosticsView(
            buildLocalOverview(events),
            buildTrendBuckets(events, 7),
            buildRecentFailedOperations(events, 10)
        );
    }

    @Nonnull
    public synchronized List<DiagnosticsEvent> operationTimeline(@Nonnull String operationId) {
        if (operationId == null || operationId.isBlank()) {
            return List.of();
        }
        return buildOperationTimeline(loadAllEvents(), operationId);
    }

    private DiagnosticsEvent truncate(@Nonnull DiagnosticsEvent event) {
        DiagnosticsEvent current = event;
        if (serializedSize(current) <= MAX_EVENT_BYTES) {
            return current;
        }

        LinkedHashSet<String> truncatedFields = new LinkedHashSet<>(current.truncatedFieldsOrEmpty());
        if (current.payloadPreview() != null && !current.payloadPreview().isEmpty()) {
            truncatedFields.add("payloadPreview");
            current = current.toBuilder()
                .payloadPreview(null)
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        while (serializedSize(current) > MAX_EVENT_BYTES && !current.message().isEmpty()) {
            truncatedFields.add("message");
            current = current.toBuilder()
                .message(shrinkMessage(current.message()))
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        if (serializedSize(current) > MAX_EVENT_BYTES && !current.tagsOrEmpty().isEmpty()) {
            truncatedFields.add("tags");
            current = current.toBuilder()
                .tags(null)
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        if (serializedSize(current) > MAX_EVENT_BYTES) {
            current = current.toBuilder()
                .message("Diagnostics event truncated.")
                .truncated(true)
                .truncatedFields(new ArrayList<>(truncatedFields))
                .build();
        }

        return current;
    }

    @Nonnull
    private String shrinkMessage(@Nonnull String message) {
        if (message.length() > 512) {
            return message.substring(0, 512);
        }
        if (message.length() > 256) {
            return message.substring(0, 256);
        }
        if (message.length() > 128) {
            return message.substring(0, 128);
        }
        if (message.length() > 64) {
            return message.substring(0, 64);
        }
        return "";
    }

    private int serializedSize(@Nonnull DiagnosticsEvent event) {
        return GSON.toJson(event).getBytes(StandardCharsets.UTF_8).length;
    }

    private void append(@Nonnull DiagnosticsEvent event) throws IOException {
        ensureActiveSegment(event.occurredAtEpochMs(), serializedSize(event) + 1);
        try (BufferedWriter writer = Files.newBufferedWriter(
            activeSegment.file,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            writer.write(GSON.toJson(event));
            writer.newLine();
        }
        activeSegment.lineCount += 1L;
        activeSegment.byteSize = Files.size(activeSegment.file);
        activeSegment.startedAtEpochMs = activeSegment.lineCount == 1L
            ? event.occurredAtEpochMs()
            : Math.min(activeSegment.startedAtEpochMs, event.occurredAtEpochMs());
        activeSegment.endedAtEpochMs = Math.max(activeSegment.endedAtEpochMs, event.occurredAtEpochMs());
        activeSegment.firstSequence = activeSegment.firstSequence == 0L ? event.sourceSequence() : Math.min(activeSegment.firstSequence, event.sourceSequence());
        activeSegment.lastSequence = Math.max(activeSegment.lastSequence, event.sourceSequence());
        activeSegment.categoryCounts.merge(event.category().name(), 1L, Long::sum);
        activeSegment.outcomeCounts.merge(event.outcome().name(), 1L, Long::sum);
        writeSidecar(activeSegment);
    }

    private void ensureActiveSegment(long eventEpochMs, int incomingLineBytes) throws IOException {
        LocalDate eventDayUtc = Instant.ofEpochMilli(eventEpochMs).atZone(ZoneOffset.UTC).toLocalDate();
        if (activeSegment == null) {
            activeSegment = createSegment(eventDayUtc, 1);
            return;
        }

        boolean dayChanged = !activeSegment.utcDate.equals(eventDayUtc);
        boolean byteLimitReached = activeSegment.byteSize > 0L && activeSegment.byteSize + incomingLineBytes > MAX_SEGMENT_BYTES;
        boolean lineLimitReached = activeSegment.lineCount > 0L && activeSegment.lineCount + 1L > MAX_SEGMENT_LINES;
        if (!dayChanged && !byteLimitReached && !lineLimitReached) {
            return;
        }

        int nextIndex = dayChanged ? 1 : activeSegment.segmentIndex + 1;
        activeSegment = createSegment(eventDayUtc, nextIndex);
        pruneRetention();
    }

    @Nonnull
    private ActiveSegment createSegment(@Nonnull LocalDate utcDate, int segmentIndex) throws IOException {
        String dayToken = FILE_DAY.format(utcDate);
        String indexToken = String.format("%04d", segmentIndex);
        String fileId = dayToken + "-" + indexToken;
        Path file = journalDir.resolve("events-" + dayToken + "-" + indexToken + ".jsonl");
        Path sidecar = journalDir.resolve("events-" + dayToken + "-" + indexToken + ".meta.json");
        if (!Files.exists(file)) {
            Files.createFile(file);
        }
        ActiveSegment segment = new ActiveSegment(utcDate, segmentIndex, fileId, file, sidecar);
        writeSidecar(segment);
        return segment;
    }

    private void writeSidecar(@Nonnull ActiveSegment segment) throws IOException {
        DiagnosticsSegmentMetadata metadata = new DiagnosticsSegmentMetadata(
            DiagnosticsSegmentMetadata.SCHEMA_VERSION,
            segment.fileId,
            segment.file.getFileName().toString(),
            segment.startedAtEpochMs,
            segment.endedAtEpochMs,
            segment.lineCount,
            segment.byteSize,
            sha256(segment.file),
            segment.firstSequence,
            segment.lastSequence,
            Map.copyOf(segment.categoryCounts),
            Map.copyOf(segment.outcomeCounts)
        );
        Files.writeString(segment.sidecar, GSON.toJson(metadata), StandardCharsets.UTF_8);
    }

    @Nonnull
    private LocalOverview buildLocalOverview(@Nonnull List<DiagnosticsEvent> events) {
        long cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L;
        int failedTravels = 0;
        int securityDenials = 0;
        int bootstrapFailures = 0;
        int recoveriesActivated = 0;
        for (DiagnosticsEvent event : events) {
            if (event.occurredAtEpochMs() < cutoff) {
                continue;
            }
            if (event.category() == DiagnosticsCategory.TRAVEL
                && (event.outcome() == DiagnosticsOutcome.FAILED || event.outcome() == DiagnosticsOutcome.DENIED)) {
                failedTravels++;
            }
            if (event.category() == DiagnosticsCategory.SECURITY
                && (event.outcome() == DiagnosticsOutcome.FAILED || event.outcome() == DiagnosticsOutcome.DENIED)) {
                securityDenials++;
            }
            if (event.category() == DiagnosticsCategory.BOOTSTRAP && event.outcome() == DiagnosticsOutcome.FAILED) {
                bootstrapFailures++;
            }
            if (event.category() == DiagnosticsCategory.RECOVERY
                && (DiagnosticsAction.RECOVERY_QUERY_START.equals(event.action())
                || DiagnosticsAction.RECOVERY_CLAIM_LOCAL.equals(event.action()))) {
                recoveriesActivated++;
            }
        }
        return new LocalOverview(failedTravels, securityDenials, bootstrapFailures, recoveriesActivated);
    }

    @Nonnull
    private List<TrendBucket> buildTrendBuckets(@Nonnull List<DiagnosticsEvent> events, int days) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LinkedHashMap<LocalDate, MutableTrendBucket> buckets = new LinkedHashMap<>();
        for (int index = Math.max(1, days) - 1; index >= 0; index--) {
            LocalDate date = today.minusDays(index);
            buckets.put(date, new MutableTrendBucket(date));
        }

        for (DiagnosticsEvent event : events) {
            LocalDate date = Instant.ofEpochMilli(event.occurredAtEpochMs()).atZone(zone).toLocalDate();
            MutableTrendBucket bucket = buckets.get(date);
            if (bucket == null) {
                continue;
            }
            if (event.category() == DiagnosticsCategory.TRAVEL
                && (event.outcome() == DiagnosticsOutcome.FAILED || event.outcome() == DiagnosticsOutcome.DENIED)) {
                bucket.failedTravels += 1;
            }
            if (event.category() == DiagnosticsCategory.SECURITY
                && (event.outcome() == DiagnosticsOutcome.FAILED || event.outcome() == DiagnosticsOutcome.DENIED)) {
                bucket.securityDenials += 1;
            }
            if (event.category() == DiagnosticsCategory.BOOTSTRAP && event.outcome() == DiagnosticsOutcome.FAILED) {
                bucket.bootstrapFailures += 1;
            }
            if (event.category() == DiagnosticsCategory.RECOVERY
                && (DiagnosticsAction.RECOVERY_QUERY_START.equals(event.action())
                || DiagnosticsAction.RECOVERY_CLAIM_LOCAL.equals(event.action()))) {
                bucket.recoveriesActivated += 1;
            }
        }

        return buckets.values().stream()
            .map(MutableTrendBucket::toImmutable)
            .toList();
    }

    @Nonnull
    private List<DiagnosticsEvent> buildRecentFailedOperations(@Nonnull List<DiagnosticsEvent> events, int limit) {
        List<DiagnosticsEvent> descending = new ArrayList<>(events);
        descending.sort(Comparator
            .comparingLong(DiagnosticsEvent::occurredAtEpochMs)
            .thenComparingLong(DiagnosticsEvent::sourceSequence)
            .reversed());
        LinkedHashMap<String, DiagnosticsEvent> latestByOperation = new LinkedHashMap<>();
        for (DiagnosticsEvent event : descending) {
            if (event.outcome() != DiagnosticsOutcome.FAILED && event.outcome() != DiagnosticsOutcome.DENIED) {
                continue;
            }
            String key = event.operationId().isBlank() ? event.eventId() : event.operationId();
            latestByOperation.putIfAbsent(key, event);
            if (latestByOperation.size() >= Math.max(1, limit)) {
                break;
            }
        }
        return List.copyOf(latestByOperation.values());
    }

    @Nonnull
    private List<DiagnosticsEvent> buildOperationTimeline(@Nonnull List<DiagnosticsEvent> events, @Nonnull String operationId) {
        return events.stream()
            .filter(event -> operationId.equals(event.operationId()) || operationId.equals(event.correlationId()))
            .sorted(Comparator
                .comparingLong(DiagnosticsEvent::occurredAtEpochMs)
                .thenComparingLong(DiagnosticsEvent::sourceSequence))
            .toList();
    }

    @Nonnull
    public synchronized List<DiagnosticsEvent> loadAllEvents() {
        pruneRetentionQuietly();
        List<DiagnosticsEvent> events = new ArrayList<>();
        for (SegmentRef ref : listSegments()) {
            try {
                for (String line : Files.readAllLines(ref.file, StandardCharsets.UTF_8)) {
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    DiagnosticsEvent event = GSON.fromJson(line, DiagnosticsEvent.class);
                    if (event != null) {
                        events.add(event);
                    }
                }
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to read Nexori diagnostics segment " + ref.file.getFileName() + ".");
            } catch (RuntimeException exception) {
                logger.atWarning().withCause(exception).log("Failed to parse a Nexori diagnostics event line.");
            }
        }
        events.sort(Comparator
            .comparingLong(DiagnosticsEvent::occurredAtEpochMs)
            .thenComparingLong(DiagnosticsEvent::sourceSequence)
            .thenComparing(DiagnosticsEvent::eventId));
        return events;
    }

    @Nonnull
    private List<SegmentRef> listSegments() {
        List<SegmentRef> refs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(journalDir, "*.meta.json")) {
            for (Path sidecar : stream) {
                DiagnosticsSegmentMetadata metadata = tryReadMetadata(sidecar).orElse(null);
                if (metadata == null) {
                    continue;
                }
                Path file = journalDir.resolve(metadata.fileName());
                if (Files.exists(file)) {
                    refs.add(new SegmentRef(file, metadata));
                }
            }
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to list Nexori diagnostics segments.");
        }
        refs.sort(Comparator
            .comparingLong((SegmentRef ref) -> ref.metadata.startedAtEpochMs())
            .thenComparing(ref -> ref.file.getFileName().toString()));
        return refs;
    }

    private void pruneRetentionQuietly() {
        try {
            pruneRetention();
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to prune old Nexori diagnostics segments.");
        }
    }

    private void pruneRetention() throws IOException {
        long cutoff = System.currentTimeMillis() - RETENTION_MILLIS;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(journalDir, "*.meta.json")) {
            for (Path sidecar : stream) {
                DiagnosticsSegmentMetadata metadata = tryReadMetadata(sidecar).orElse(null);
                if (metadata == null) {
                    continue;
                }
                if (metadata.endedAtEpochMs() >= cutoff) {
                    continue;
                }
                Files.deleteIfExists(journalDir.resolve(metadata.fileName()));
                Files.deleteIfExists(sidecar);
            }
        }
    }

    @Nonnull
    private Optional<DiagnosticsSegmentMetadata> tryReadMetadata(@Nonnull Path sidecar) {
        try {
            DiagnosticsSegmentMetadata metadata = GSON.fromJson(Files.readString(sidecar, StandardCharsets.UTF_8), DiagnosticsSegmentMetadata.class);
            return Optional.ofNullable(metadata);
        } catch (IOException | RuntimeException exception) {
            logger.atWarning().withCause(exception).log("Failed to read Nexori diagnostics sidecar " + sidecar.getFileName() + ".");
            return Optional.empty();
        }
    }

    @Nonnull
    private String sha256(@Nonnull Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = Files.readAllBytes(file);
            byte[] hash = digest.digest(data);
            StringBuilder out = new StringBuilder("sha256:");
            for (byte value : hash) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    @Nonnull
    private String safeAddress(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record LocalOverview(
        int failedTravelsLast24h,
        int securityDenialsLast24h,
        int bootstrapFailuresLast24h,
        int recoveriesActivatedLast24h
    ) {
    }

    public record TrendBucket(
        @Nonnull String label,
        long failedTravels,
        long securityDenials,
        long bootstrapFailures,
        long recoveriesActivated
    ) {
    }

    public record LocalDiagnosticsView(
        @Nonnull LocalOverview overview,
        @Nonnull List<TrendBucket> trends,
        @Nonnull List<DiagnosticsEvent> recentFailedOperations
    ) {
    }

    private static final class MutableTrendBucket {
        private final LocalDate date;
        private long failedTravels;
        private long securityDenials;
        private long bootstrapFailures;
        private long recoveriesActivated;

        private MutableTrendBucket(@Nonnull LocalDate date) {
            this.date = date;
        }

        @Nonnull
        private TrendBucket toImmutable() {
            return new TrendBucket(
                date.toString(),
                failedTravels,
                securityDenials,
                bootstrapFailures,
                recoveriesActivated
            );
        }
    }

    private record SegmentRef(@Nonnull Path file, @Nonnull DiagnosticsSegmentMetadata metadata) {
    }

    private static final class ActiveSegment {
        private final LocalDate utcDate;
        private final int segmentIndex;
        private final String fileId;
        private final Path file;
        private final Path sidecar;
        private long startedAtEpochMs;
        private long endedAtEpochMs;
        private long lineCount;
        private long byteSize;
        private long firstSequence;
        private long lastSequence;
        private final Map<String, Long> categoryCounts = new HashMap<>();
        private final Map<String, Long> outcomeCounts = new HashMap<>();

        private ActiveSegment(
            @Nonnull LocalDate utcDate,
            int segmentIndex,
            @Nonnull String fileId,
            @Nonnull Path file,
            @Nonnull Path sidecar
        ) throws IOException {
            this.utcDate = utcDate;
            this.segmentIndex = segmentIndex;
            this.fileId = fileId;
            this.file = file;
            this.sidecar = sidecar;
            this.byteSize = Files.size(file);
        }
    }
}
