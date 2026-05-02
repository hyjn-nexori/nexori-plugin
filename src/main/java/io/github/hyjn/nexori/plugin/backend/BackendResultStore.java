package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class BackendResultStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final int SCHEMA_VERSION = 1;
    private static final int MAX_ACKNOWLEDGED_HISTORY = 256;

    private final Path file;
    private final Map<String, BackendResultRecord> resultsById = new LinkedHashMap<>();

    public BackendResultStore(@Nonnull Path file) throws IOException {
        this.file = file;
        loadOrCreate();
    }

    @Nonnull
    public synchronized StorePutResult putPending(@Nonnull BackendResultRecord result) throws IOException {
        BackendResultRecord normalized = result.normalized();
        BackendResultRecord existing = findByLocalOrExternal(normalized.localMatchId(), normalized.externalMatchId()).orElse(null);
        if (existing != null) {
            boolean sameHash = existing.payloadHash().equals(normalized.payloadHash());
            return new StorePutResult(
                sameHash ? StorePutOutcome.ALREADY_SUBMITTED : StorePutOutcome.DUPLICATE_CONFLICT,
                existing.resultId(),
                sameHash ? "Result already exists." : "Result conflicts with an existing local or external match result."
            );
        }
        resultsById.put(normalized.resultId(), normalized);
        persist();
        return new StorePutResult(StorePutOutcome.QUEUED, normalized.resultId(), "Result queued.");
    }

    @Nonnull
    public synchronized Optional<BackendResultRecord> findNextDuePending(long nowEpochMs) {
        return resultsById.values().stream()
            .filter(result -> BackendResultStatus.PENDING.name().equals(result.status()))
            .filter(result -> result.nextAttemptAtEpochMs() <= nowEpochMs)
            .min(Comparator.comparingLong(BackendResultRecord::nextAttemptAtEpochMs));
    }

    @Nonnull
    public synchronized Optional<BackendResultRecord> find(@Nonnull String resultId) {
        return Optional.ofNullable(resultsById.get(normalize(resultId)));
    }

    public synchronized void markAttempt(@Nonnull String resultId, long nowEpochMs) throws IOException {
        BackendResultRecord current = resultsById.get(normalize(resultId));
        if (current == null) {
            return;
        }
        resultsById.put(current.resultId(), current.withAttempt(nowEpochMs));
        persist();
    }

    public synchronized void markAcknowledged(
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String backendStatus,
        long nowEpochMs
    ) throws IOException {
        BackendResultRecord current = resultsById.get(normalize(resultId));
        if (current == null) {
            return;
        }
        resultsById.put(current.resultId(), current.withTerminal(
            BackendResultStatus.ACKNOWLEDGED.name(),
            statusCode,
            backendStatus,
            "",
            "",
            nowEpochMs,
            0L,
            nowEpochMs
        ));
        persist();
    }

    public synchronized void markRetry(
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message,
        long nowEpochMs,
        long nextAttemptAtEpochMs
    ) throws IOException {
        BackendResultRecord current = resultsById.get(normalize(resultId));
        if (current == null) {
            return;
        }
        resultsById.put(current.resultId(), current.withTerminal(
            BackendResultStatus.PENDING.name(),
            statusCode,
            "",
            errorClass,
            message,
            nowEpochMs,
            nextAttemptAtEpochMs,
            0L
        ));
        persist();
    }

    public synchronized void markNeedsAttention(
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message,
        long nowEpochMs
    ) throws IOException {
        BackendResultRecord current = resultsById.get(normalize(resultId));
        if (current == null) {
            return;
        }
        resultsById.put(current.resultId(), current.withTerminal(
            BackendResultStatus.NEEDS_ATTENTION.name(),
            statusCode,
            "",
            errorClass,
            message,
            nowEpochMs,
            0L,
            0L
        ));
        persist();
    }

    public synchronized void markPermanentFailure(
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message,
        long nowEpochMs
    ) throws IOException {
        BackendResultRecord current = resultsById.get(normalize(resultId));
        if (current == null) {
            return;
        }
        resultsById.put(current.resultId(), current.withTerminal(
            BackendResultStatus.FAILED_PERMANENT.name(),
            statusCode,
            "",
            errorClass,
            message,
            nowEpochMs,
            0L,
            0L
        ));
        persist();
    }

    private Optional<BackendResultRecord> findByLocalOrExternal(@Nonnull String localMatchId, @Nonnull String externalMatchId) {
        for (BackendResultRecord result : resultsById.values()) {
            if (!localMatchId.isBlank() && localMatchId.equals(result.localMatchId())) {
                return Optional.of(result);
            }
            if (!externalMatchId.isBlank() && externalMatchId.equals(result.externalMatchId())) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    private void loadOrCreate() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            persist();
            return;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            persist();
            return;
        }
        BackendResultDocument document = GSON.fromJson(json, BackendResultDocument.class);
        if (document == null) {
            persist();
            return;
        }
        resultsById.clear();
        for (BackendResultRecord result : document.results()) {
            if (result != null && result.resultId() != null && !result.resultId().isBlank()) {
                BackendResultRecord normalized = result.normalized();
                resultsById.put(normalized.resultId(), normalized);
            }
        }
        pruneAcknowledgedHistory();
    }

    private void persist() throws IOException {
        ensureParent();
        pruneAcknowledgedHistory();
        BackendResultDocument document = new BackendResultDocument(
            SCHEMA_VERSION,
            new ArrayList<>(resultsById.values())
        );
        String json = GSON.toJson(document);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
    }

    private void pruneAcknowledgedHistory() {
        List<BackendResultRecord> acknowledged = resultsById.values().stream()
            .filter(result -> BackendResultStatus.ACKNOWLEDGED.name().equals(result.status()))
            .sorted(Comparator
                .comparingLong(BackendResultRecord::acknowledgedAtEpochMs)
                .thenComparingLong(BackendResultRecord::updatedAtEpochMs))
            .toList();
        int toRemove = acknowledged.size() - MAX_ACKNOWLEDGED_HISTORY;
        for (int index = 0; index < toRemove; index++) {
            resultsById.remove(acknowledged.get(index).resultId());
        }
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }

    @Nonnull
    private static List<BackendResultPlayerRecord> normalizePlayers(List<BackendResultPlayerRecord> rawPlayers) {
        if (rawPlayers == null || rawPlayers.isEmpty()) {
            return List.of();
        }
        List<BackendResultPlayerRecord> normalized = new ArrayList<>();
        for (BackendResultPlayerRecord player : rawPlayers) {
            if (player != null && player.playerUuid() != null && !player.playerUuid().isBlank()) {
                normalized.add(player.normalized());
            }
        }
        return List.copyOf(normalized);
    }

    @Nonnull
    private static Map<String, String> normalizeMetadata(Map<String, String> rawMetadata) {
        if (rawMetadata == null || rawMetadata.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        rawMetadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> normalized.put(normalize(entry.getKey()), normalize(entry.getValue())));
        return Map.copyOf(normalized);
    }

    public enum BackendResultStatus {
        PENDING,
        ACKNOWLEDGED,
        FAILED_PERMANENT,
        NEEDS_ATTENTION
    }

    public enum StorePutOutcome {
        QUEUED,
        ALREADY_SUBMITTED,
        DUPLICATE_CONFLICT
    }

    public record StorePutResult(
        StorePutOutcome outcome,
        String resultId,
        String message
    ) {
    }

    private record BackendResultDocument(
        int schemaVersion,
        List<BackendResultRecord> results
    ) {
        private BackendResultDocument {
            results = results == null ? List.of() : List.copyOf(results);
        }
    }

    public record BackendResultPlayerRecord(
        String playerUuid,
        String outcome,
        String reason
    ) {
        @Nonnull
        private BackendResultPlayerRecord normalized() {
            return new BackendResultPlayerRecord(
                normalize(playerUuid),
                normalize(outcome),
                normalize(reason)
            );
        }
    }

    public record BackendResultRecord(
        String resultId,
        String localMatchId,
        String externalMatchId,
        String assignmentId,
        String queueId,
        String arenaId,
        List<BackendResultPlayerRecord> players,
        String reason,
        Map<String, String> metadata,
        String payloadHash,
        String status,
        int attemptCount,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        long endedAtEpochMs,
        long nextAttemptAtEpochMs,
        long acknowledgedAtEpochMs,
        String lastErrorClass,
        String lastErrorMessage,
        int lastStatusCode,
        String lastBackendStatus
    ) {
        @Nonnull
        private BackendResultRecord normalized() {
            long now = System.currentTimeMillis();
            String normalizedStatus = normalize(status).isBlank() ? BackendResultStatus.PENDING.name() : normalize(status);
            return new BackendResultRecord(
                normalize(resultId),
                normalize(localMatchId),
                normalize(externalMatchId),
                normalize(assignmentId),
                normalize(queueId),
                normalize(arenaId),
                normalizePlayers(players),
                normalize(reason),
                normalizeMetadata(metadata),
                normalize(payloadHash),
                normalizedStatus,
                Math.max(0, attemptCount),
                createdAtEpochMs <= 0L ? now : createdAtEpochMs,
                updatedAtEpochMs <= 0L ? now : updatedAtEpochMs,
                Math.max(0L, endedAtEpochMs),
                Math.max(0L, nextAttemptAtEpochMs),
                Math.max(0L, acknowledgedAtEpochMs),
                normalize(lastErrorClass),
                normalize(lastErrorMessage),
                Math.max(0, lastStatusCode),
                normalize(lastBackendStatus)
            );
        }

        @Nonnull
        private BackendResultRecord withAttempt(long nowEpochMs) {
            return new BackendResultRecord(
                resultId,
                localMatchId,
                externalMatchId,
                assignmentId,
                queueId,
                arenaId,
                players,
                reason,
                metadata,
                payloadHash,
                status,
                attemptCount + 1,
                createdAtEpochMs,
                nowEpochMs,
                endedAtEpochMs,
                nextAttemptAtEpochMs,
                acknowledgedAtEpochMs,
                lastErrorClass,
                lastErrorMessage,
                lastStatusCode,
                lastBackendStatus
            ).normalized();
        }

        @Nonnull
        private BackendResultRecord withTerminal(
            @Nonnull String nextStatus,
            int statusCode,
            @Nonnull String backendStatus,
            @Nonnull String errorClass,
            @Nonnull String errorMessage,
            long nowEpochMs,
            long nextAttemptAtEpochMs,
            long acknowledgedAtEpochMs
        ) {
            return new BackendResultRecord(
                resultId,
                localMatchId,
                externalMatchId,
                assignmentId,
                queueId,
                arenaId,
                players,
                reason,
                metadata,
                payloadHash,
                nextStatus,
                attemptCount,
                createdAtEpochMs,
                nowEpochMs,
                endedAtEpochMs,
                nextAttemptAtEpochMs,
                acknowledgedAtEpochMs,
                errorClass,
                errorMessage,
                statusCode,
                backendStatus
            ).normalized();
        }
    }
}
