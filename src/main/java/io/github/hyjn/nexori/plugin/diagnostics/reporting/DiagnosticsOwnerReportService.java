package io.github.hyjn.nexori.plugin.diagnostics.reporting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.diagnostics.collect.DiagnosticsCollectSession;
import io.github.hyjn.nexori.plugin.diagnostics.collect.DiagnosticsCollectSessionStore;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class DiagnosticsOwnerReportService {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final long GROUP_WINDOW_MILLIS = Duration.ofMinutes(30).toMillis();
    private static final int MAX_VISIBLE_LABELS = 3;
    private static final int MAX_ATTENTION_ITEMS = 5;
    private static final int MAX_ALERTS = 18;

    private final HytaleLogger logger;
    private final DiagnosticsService diagnosticsService;
    private final DiagnosticsCollectSessionStore collectSessionStore;
    private final PortalInstanceService portalInstanceService;
    private final DestinationTargetService destinationTargetService;

    public DiagnosticsOwnerReportService(
        @Nonnull HytaleLogger logger,
        @Nonnull Path pluginDataDirectory,
        @Nonnull DiagnosticsService diagnosticsService,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull DestinationTargetService destinationTargetService
    ) throws IOException {
        this.logger = logger;
        this.diagnosticsService = diagnosticsService;
        this.collectSessionStore = new DiagnosticsCollectSessionStore(pluginDataDirectory);
        this.portalInstanceService = portalInstanceService;
        this.destinationTargetService = destinationTargetService;
    }

    @Nonnull
    public Report load() {
        EventSource source = loadPreferredEvents();
        List<Alert> alerts = buildAlerts(source.events());
        Summary summary = buildSummary(source.description(), alerts);
        return new Report(source.description(), summary, alerts);
    }

    @Nonnull
    private EventSource loadPreferredEvents() {
        Optional<DiagnosticsCollectSession> completed = collectSessionStore.loadLatestCompletedSession();
        if (completed.isPresent()) {
            Path consolidated = collectSessionStore.outputFile(completed.get().sessionId(), "consolidated-events.jsonl");
            if (Files.exists(consolidated)) {
                try {
                    return new EventSource(
                        "Based on the latest completed collect.",
                        readEvents(consolidated)
                    );
                } catch (IOException | RuntimeException exception) {
                    logger.atWarning().withCause(exception).log("Failed to read consolidated diagnostics output. Falling back to local diagnostics journal.");
                }
            }
        }
        return new EventSource(
            "Based on local events from this server.",
            diagnosticsService.loadAllEvents()
        );
    }

    @Nonnull
    private List<DiagnosticsEvent> readEvents(@Nonnull Path file) throws IOException {
        List<DiagnosticsEvent> events = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line == null || line.isBlank()) {
                continue;
            }
            DiagnosticsEvent event = GSON.fromJson(line, DiagnosticsEvent.class);
            if (event != null) {
                events.add(event);
            }
        }
        events.sort(Comparator
            .comparingLong(DiagnosticsEvent::occurredAtEpochMs)
            .thenComparingLong(DiagnosticsEvent::sourceSequence)
            .thenComparing(DiagnosticsEvent::sourceServerId)
            .thenComparing(DiagnosticsEvent::eventId));
        return events;
    }

    @Nonnull
    private List<Alert> buildAlerts(@Nonnull List<DiagnosticsEvent> events) {
        LinkedHashMap<String, MutableAlert> grouped = new LinkedHashMap<>();
        for (DiagnosticsEvent event : events) {
            if (!isAlertCandidate(event)) {
                continue;
            }

            HumanAlertType type = classify(event);
            String serverContext = contextKeyForServer(event, type.suspicious());
            String portalContext = resolvePortalLabel(event);
            long timeBucket = event.occurredAtEpochMs() / GROUP_WINDOW_MILLIS;
            String groupingContext = type.suspicious()
                ? normalizeKey(serverContext) + "|" + normalizeKey(event.payloadType())
                : normalizeKey(portalContext) + "|" + normalizeKey(primaryServerLabel(event));
            String key = type.key() + "|" + groupingContext + "|" + timeBucket;

            grouped.computeIfAbsent(key, ignored -> new MutableAlert(type))
                .add(event, primaryServerLabel(event), secondaryServerLabel(event), portalContext);
        }

        return grouped.values().stream()
            .map(this::toAlert)
            .sorted(Comparator
                .comparingInt(Alert::priorityRank)
                .thenComparing(Alert::lastSeenEpochMs, Comparator.reverseOrder()))
            .limit(MAX_ALERTS)
            .toList();
    }

    private boolean isAlertCandidate(@Nonnull DiagnosticsEvent event) {
        return event.outcome() == DiagnosticsOutcome.DENIED
            || event.outcome() == DiagnosticsOutcome.FAILED
            || event.outcome() == DiagnosticsOutcome.TIMED_OUT
            || event.outcome() == DiagnosticsOutcome.EXPIRED;
    }

    @Nonnull
    private HumanAlertType classify(@Nonnull DiagnosticsEvent event) {
        String reasonCode = event.reasonCode();
        if (isSuspicious(event)) {
            if (DiagnosticsReasonCode.ISSUER_NOT_TRUSTED.equals(reasonCode)
                || DiagnosticsReasonCode.BOOTSTRAP_ORIGIN_NOT_VERIFIED.equals(reasonCode)) {
                return new HumanAlertType("suspicious.untrusted-source", "Message rejected from an untrusted source", true);
            }
            if (DiagnosticsReasonCode.SIGNATURE_INVALID.equals(reasonCode)
                || DiagnosticsReasonCode.SIGNATURE_VERIFY_EXCEPTION.equals(reasonCode)) {
                return new HumanAlertType("suspicious.invalid-signature", "Invalid signature detected in a server-to-server message", true);
            }
            if (DiagnosticsReasonCode.PAYLOAD_TYPE_UNSUPPORTED.equals(reasonCode)) {
                return new HumanAlertType("suspicious.unsupported-payload", "Suspicious attempt to use the protocol without authorization", true);
            }
            if (DiagnosticsReasonCode.REFERRAL_DECODE_FAILED.equals(reasonCode)) {
                return new HumanAlertType("suspicious.decode-failure", "Suspicious message that could not be read", true);
            }
            if (DiagnosticsReasonCode.REFERRAL_EXPIRED.equals(reasonCode)) {
                return new HumanAlertType("suspicious.expired-message", "Message rejected for arriving too late", true);
            }
            return new HumanAlertType("suspicious.generic", "Suspicious server-to-server communication attempt", true);
        }

        if (event.category() == DiagnosticsCategory.BOOTSTRAP) {
            if (DiagnosticsReasonCode.BOOTSTRAP_PEER_ERROR.equals(reasonCode)
                || DiagnosticsReasonCode.BUNDLE_DISTRIBUTION_FAILED.equals(reasonCode)
                || DiagnosticsReasonCode.BUNDLE_ACK_HASH_MISMATCH.equals(reasonCode)) {
                return new HumanAlertType("failure.bootstrap-peer", "Peer communication error during bootstrap", false);
            }
            return new HumanAlertType("failure.bootstrap", "Bootstrap failure", false);
        }
        if (event.category() == DiagnosticsCategory.TRAVEL) {
            return new HumanAlertType("failure.travel", "Transfer failure", false);
        }
        if (event.category() == DiagnosticsCategory.RECOVERY) {
            return new HumanAlertType("failure.recovery", "Recovery problem", false);
        }
        if (event.category() == DiagnosticsCategory.RULES) {
            return new HumanAlertType("failure.rules", "Rules sync problem", false);
        }
        if (event.category() == DiagnosticsCategory.DISCOVERY) {
            return new HumanAlertType("failure.discovery", "Remote destination discovery problem", false);
        }
        return new HumanAlertType("failure.generic", "Operational network failure", false);
    }

    private boolean isSuspicious(@Nonnull DiagnosticsEvent event) {
        if (event.category() == DiagnosticsCategory.SECURITY || event.reasonClass() == DiagnosticsReasonClass.SECURITY) {
            return true;
        }
        return DiagnosticsReasonCode.BOOTSTRAP_ORIGIN_NOT_VERIFIED.equals(event.reasonCode())
            || DiagnosticsReasonCode.DESTINATION_NOT_TRUSTED.equals(event.reasonCode())
            || DiagnosticsReasonCode.DISCOVERY_DESTINATION_NOT_TRUSTED.equals(event.reasonCode())
            || DiagnosticsReasonCode.RULES_DESTINATION_NOT_TRUSTED.equals(event.reasonCode());
    }

    @Nonnull
    private Alert toAlert(@Nonnull MutableAlert aggregate) {
        int occurrences = aggregate.occurrenceKeys.size();
        boolean repeated = occurrences > 1;
        boolean suspicious = aggregate.type.suspicious();
        int priorityRank = suspicious ? (repeated ? 0 : 1) : (repeated ? 2 : 3);
        String severity = switch (priorityRank) {
            case 0 -> "Critical";
            case 1 -> "High";
            case 2 -> "Medium";
            default -> "Low";
        };
        List<String> serverLabels = List.copyOf(aggregate.serverLabels);
        List<String> portalLabels = List.copyOf(aggregate.portalLabels);
        return new Alert(
            severity,
            aggregate.type.title(),
            humanWhen(aggregate.lastSeenEpochMs),
            suspicious,
            repeated,
            occurrences,
            serverLabels,
            portalLabels,
            buildAlertSummary(aggregate, repeated, joinLabels(serverLabels, "No visible server"), joinLabels(portalLabels, "No visible portal")),
            aggregate.lastSeenEpochMs,
            priorityRank
        );
    }

    @Nonnull
    private String buildAlertSummary(
        @Nonnull MutableAlert aggregate,
        boolean repeated,
        @Nonnull String serverText,
        @Nonnull String portalText
    ) {
        String base = switch (aggregate.type.key()) {
            case "suspicious.untrusted-source" -> repeated
                ? "Messages were rejected because they did not match a trusted source."
                : "A message was rejected because it did not match a trusted source.";
            case "suspicious.invalid-signature" -> repeated
                ? "Messages were rejected because signature validation failed."
                : "A message was rejected due to signature validation.";
            case "suspicious.unsupported-payload" -> repeated
                ? "Messages attempted to use the protocol without authorization."
                : "A message attempted to use the protocol without authorization.";
            case "suspicious.decode-failure" -> repeated
                ? "Suspicious messages were detected and could not be read."
                : "A suspicious message was detected and could not be read.";
            case "suspicious.expired-message" -> repeated
                ? "Messages were rejected because they arrived too late."
                : "A message was rejected because it arrived too late.";
            case "failure.bootstrap-peer" -> repeated
                ? "Repeated peer communication errors were detected during bootstrap."
                : "A peer communication error was detected during bootstrap.";
            case "failure.bootstrap" -> repeated
                ? "Repeated bootstrap failures were detected."
                : "A bootstrap failure was detected.";
            case "failure.travel" -> repeated
                ? "Repeated transfer failures were detected."
                : "A transfer failure was detected.";
            case "failure.recovery" -> repeated
                ? "Repeated recovery problems were detected."
                : "A recovery problem was detected.";
            case "failure.rules" -> repeated
                ? "Repeated rules sync problems were detected."
                : "A rules sync problem was detected.";
            case "failure.discovery" -> repeated
                ? "Repeated remote destination discovery problems were detected."
                : "A remote destination discovery problem was detected.";
            default -> repeated
                ? "Repeated events were detected that require attention."
                : "An event was detected that requires attention.";
        };

        if (!aggregate.portalLabels.isEmpty()) {
            return base + " Involved portal: " + portalText + ".";
        }
        return base + " Involved server: " + serverText + ".";
    }

    @Nonnull
    private Summary buildSummary(@Nonnull String dataSourceText, @Nonnull List<Alert> alerts) {
        boolean hasSuspicious = alerts.stream().anyMatch(Alert::suspicious);
        boolean hasRepeatedSuspicious = alerts.stream().anyMatch(alert -> alert.suspicious() && alert.repeated());
        boolean hasFailures = alerts.stream().anyMatch(alert -> !alert.suspicious());

        LinkedHashSet<String> allServers = new LinkedHashSet<>();
        LinkedHashSet<String> allPortals = new LinkedHashSet<>();
        for (Alert alert : alerts) {
            allServers.addAll(alert.serverLabels());
            allPortals.addAll(alert.portalLabels());
        }

        List<String> attention = alerts.stream()
            .limit(MAX_ATTENTION_ITEMS)
            .map(Alert::summary)
            .toList();
        if (attention.isEmpty()) {
            attention = List.of("No recent alerts were detected.");
        }

        return new Summary(
            dataSourceText,
            hasRepeatedSuspicious ? "Critical" : (hasSuspicious || hasFailures ? "Attention" : "OK"),
            hasRepeatedSuspicious ? "Repeated" : (hasSuspicious ? "Detected" : "None"),
            hasFailures ? "Detected" : "None",
            joinLabels(allServers.stream().toList(), "None"),
            joinLabels(allPortals.stream().toList(), "None"),
            attention
        );
    }

    @Nonnull
    private String primaryServerLabel(@Nonnull DiagnosticsEvent event) {
        String source = humanServerLabel(event.sourceConnectionAddress(), event.sourceServerId());
        String remote = humanServerLabel(event.remoteConnectionAddress(), event.remoteServerId());
        return !source.isBlank() ? source : remote;
    }

    @Nonnull
    private String secondaryServerLabel(@Nonnull DiagnosticsEvent event) {
        String source = humanServerLabel(event.sourceConnectionAddress(), event.sourceServerId());
        String remote = humanServerLabel(event.remoteConnectionAddress(), event.remoteServerId());
        if (!source.isBlank() && !remote.isBlank() && !source.equals(remote)) {
            return remote;
        }
        return "";
    }

    @Nonnull
    private String contextKeyForServer(@Nonnull DiagnosticsEvent event, boolean suspicious) {
        if (suspicious) {
            String remote = humanServerLabel(event.remoteConnectionAddress(), event.remoteServerId());
            if (!remote.isBlank()) {
                return remote;
            }
        }
        return primaryServerLabel(event);
    }

    @Nonnull
    private String resolvePortalLabel(@Nonnull DiagnosticsEvent event) {
        if (event.portalDisplayName() != null && !event.portalDisplayName().isBlank()) {
            return event.portalDisplayName();
        }
        if (event.portalId() != null && !event.portalId().isBlank()) {
            Optional<PortalInstanceDefinition> portal = portalInstanceService.findById(event.portalId());
            if (portal.isPresent()) {
                return portal.get().displayName();
            }
        }
        if (event.targetDisplayName() != null && !event.targetDisplayName().isBlank()) {
            return event.targetDisplayName();
        }
        if (event.targetId() != null && !event.targetId().isBlank()) {
            Optional<PortalInstanceDefinition> portal = portalInstanceService.findByAutoDestinationTargetId(event.targetId());
            if (portal.isPresent()) {
                return portal.get().displayName();
            }
            Optional<DestinationTargetDefinition> target = destinationTargetService.find(event.targetId());
            if (target.isPresent()) {
                return target.get().displayName();
            }
            return event.targetId();
        }
        if (event.portalId() != null && !event.portalId().isBlank()) {
            return event.portalId();
        }
        return "";
    }

    @Nonnull
    private String humanServerLabel(@Nullable String connectionAddress, @Nullable String serverId) {
        if (connectionAddress != null && !connectionAddress.isBlank()) {
            return connectionAddress;
        }
        if (serverId != null && !serverId.isBlank()) {
            return abbreviate(serverId);
        }
        return "";
    }

    @Nonnull
    private String humanWhen(long epochMs) {
        long elapsedSeconds = Math.max(0L, Duration.between(Instant.ofEpochMilli(epochMs), Instant.now()).getSeconds());
        if (elapsedSeconds < 60L) {
            return "Seconds ago";
        }
        long elapsedMinutes = elapsedSeconds / 60L;
        if (elapsedMinutes < 60L) {
            return elapsedMinutes + " min ago";
        }
        long elapsedHours = elapsedMinutes / 60L;
        if (elapsedHours < 48L) {
            return elapsedHours + " h ago";
        }
        return TIME_FORMAT.format(Instant.ofEpochMilli(epochMs));
    }

    @Nonnull
    private String joinLabels(@Nonnull List<String> labels, @Nonnull String fallback) {
        List<String> visible = labels.stream()
            .filter(label -> label != null && !label.isBlank())
            .distinct()
            .limit(MAX_VISIBLE_LABELS)
            .toList();
        if (visible.isEmpty()) {
            return fallback;
        }
        return String.join(", ", visible);
    }

    @Nonnull
    private String normalizeKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "none";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private String abbreviate(@Nonnull String raw) {
        if (raw.length() <= 18) {
            return raw;
        }
        return raw.substring(0, 8) + "..." + raw.substring(raw.length() - 6);
    }

    public record Report(
        @Nonnull String dataSourceText,
        @Nonnull Summary summary,
        @Nonnull List<Alert> alerts
    ) {
    }

    public record Summary(
        @Nonnull String dataSourceText,
        @Nonnull String generalStatus,
        @Nonnull String suspiciousStatus,
        @Nonnull String failuresStatus,
        @Nonnull String serversText,
        @Nonnull String portalsText,
        @Nonnull List<String> attentionItems
    ) {
    }

    public record Alert(
        @Nonnull String severity,
        @Nonnull String title,
        @Nonnull String whenText,
        boolean suspicious,
        boolean repeated,
        int occurrences,
        @Nonnull List<String> serverLabels,
        @Nonnull List<String> portalLabels,
        @Nonnull String summary,
        long lastSeenEpochMs,
        int priorityRank
    ) {
        @Nonnull
        public String alertTypeText() {
            return suspicious ? "Suspicious" : "Failure";
        }

        @Nonnull
        public String serversText() {
            List<String> visible = serverLabels.stream()
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .limit(MAX_VISIBLE_LABELS)
                .toList();
            return visible.isEmpty() ? "No visible server" : String.join(", ", visible);
        }

        @Nonnull
        public String portalsText() {
            List<String> visible = portalLabels.stream()
                .filter(label -> label != null && !label.isBlank())
                .distinct()
                .limit(MAX_VISIBLE_LABELS)
                .toList();
            return visible.isEmpty() ? "No visible portal" : String.join(", ", visible);
        }

        @Nonnull
        public String occurrencesText() {
            return repeated ? "Repeated " + occurrences + " times" : "Occurred once";
        }
    }

    private record EventSource(
        @Nonnull String description,
        @Nonnull List<DiagnosticsEvent> events
    ) {
    }

    private record HumanAlertType(
        @Nonnull String key,
        @Nonnull String title,
        boolean suspicious
    ) {
    }

    private static final class MutableAlert {
        private final HumanAlertType type;
        private final LinkedHashSet<String> serverLabels = new LinkedHashSet<>();
        private final LinkedHashSet<String> portalLabels = new LinkedHashSet<>();
        private final LinkedHashSet<String> occurrenceKeys = new LinkedHashSet<>();
        private long lastSeenEpochMs;

        private MutableAlert(@Nonnull HumanAlertType type) {
            this.type = type;
        }

        private MutableAlert add(
            @Nonnull DiagnosticsEvent event,
            @Nonnull String primaryServer,
            @Nonnull String secondaryServer,
            @Nonnull String portalLabel
        ) {
            if (!primaryServer.isBlank()) {
                serverLabels.add(primaryServer);
            }
            if (!secondaryServer.isBlank()) {
                serverLabels.add(secondaryServer);
            }
            if (!portalLabel.isBlank()) {
                portalLabels.add(portalLabel);
            }
            String occurrenceKey = event.operationId() == null || event.operationId().isBlank()
                ? event.eventId()
                : event.operationId();
            occurrenceKeys.add(occurrenceKey);
            lastSeenEpochMs = Math.max(lastSeenEpochMs, event.occurredAtEpochMs());
            return this;
        }
    }
}
