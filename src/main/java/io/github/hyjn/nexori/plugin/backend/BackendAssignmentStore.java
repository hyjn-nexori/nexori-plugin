package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentAckPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class BackendAssignmentStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;
    private final Map<String, BackendAssignmentRecord> assignmentsById = new LinkedHashMap<>();
    private final Map<String, BackendAssignmentAckPayload> pendingAcksById = new LinkedHashMap<>();
    private long lastSequence;

    public BackendAssignmentStore(@Nonnull Path file) throws IOException {
        this.file = file;
        loadOrCreate();
    }

    public synchronized long nextSequence() throws IOException {
        lastSequence++;
        persist();
        return lastSequence;
    }

    @Nonnull
    public synchronized List<BackendAssignmentAckPayload> listPendingAcks() {
        return List.copyOf(pendingAcksById.values());
    }

    public synchronized void acknowledge(@Nonnull List<String> ackIds) throws IOException {
        if (ackIds == null || ackIds.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String ackId : ackIds) {
            if (ackId != null && pendingAcksById.remove(ackId) != null) {
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    @Nonnull
    public synchronized Optional<BackendAssignmentRecord> findAssignment(@Nonnull String assignmentId) {
        if (assignmentId == null || assignmentId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(assignmentsById.get(assignmentId.trim()));
    }

    @Nonnull
    public synchronized BackendAssignmentAckPayload recordProcessed(
        @Nonnull BackendAssignmentPayload assignment,
        @Nonnull String assignmentHash,
        @Nonnull String status,
        @Nonnull String localMatchId,
        @Nonnull String reason,
        long nowEpochMs
    ) throws IOException {
        String assignmentId = normalizeRequired(assignment.assignmentId(), "Assignment id cannot be blank.");
        BackendAssignmentRecord record = new BackendAssignmentRecord(
            assignmentId,
            assignmentHash,
            normalize(assignment.externalMatchId()),
            normalize(assignment.queueId()),
            normalize(assignment.arenaId()),
            normalizePlayers(assignment.playerUuids()),
            normalize(status),
            normalize(localMatchId),
            nowEpochMs,
            nowEpochMs,
            normalize(reason)
        );
        assignmentsById.put(assignmentId, record);
        BackendAssignmentAckPayload ack = createAck(assignmentId, assignment.externalMatchId(), status, localMatchId, reason, nowEpochMs);
        pendingAcksById.put(ack.ackId(), ack);
        persist();
        return ack;
    }

    @Nonnull
    public synchronized BackendAssignmentAckPayload recordRejectedDuplicate(
        @Nonnull BackendAssignmentPayload assignment,
        @Nonnull String reason,
        long nowEpochMs
    ) throws IOException {
        String assignmentId = normalizeRequired(assignment.assignmentId(), "Assignment id cannot be blank.");
        for (BackendAssignmentAckPayload ack : pendingAcksById.values()) {
            if (assignmentId.equals(ack.assignmentId()) && "REJECTED".equals(ack.status())) {
                return ack;
            }
        }
        BackendAssignmentAckPayload ack = createAck(assignmentId, assignment.externalMatchId(), "REJECTED", "", reason, nowEpochMs);
        pendingAcksById.put(ack.ackId(), ack);
        persist();
        return ack;
    }

    @Nonnull
    public synchronized Optional<BackendAssignmentAckPayload> findPendingAckForAssignment(@Nonnull String assignmentId) {
        for (BackendAssignmentAckPayload ack : pendingAcksById.values()) {
            if (assignmentId.equals(ack.assignmentId())) {
                return Optional.of(ack);
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
        BackendAssignmentDocument document = GSON.fromJson(json, BackendAssignmentDocument.class);
        if (document == null) {
            persist();
            return;
        }
        lastSequence = Math.max(0L, document.lastSequence());
        assignmentsById.clear();
        for (BackendAssignmentRecord assignment : document.assignments()) {
            if (assignment != null && assignment.assignmentId() != null && !assignment.assignmentId().isBlank()) {
                assignmentsById.put(assignment.assignmentId(), assignment.normalized());
            }
        }
        pendingAcksById.clear();
        for (BackendAssignmentAckPayload ack : document.pendingAcks()) {
            if (ack != null && ack.ackId() != null && !ack.ackId().isBlank()) {
                pendingAcksById.put(ack.ackId(), ack);
            }
        }
    }

    private void persist() throws IOException {
        ensureParent();
        BackendAssignmentDocument document = new BackendAssignmentDocument(
            1,
            lastSequence,
            new ArrayList<>(assignmentsById.values()),
            new ArrayList<>(pendingAcksById.values())
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

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    @Nonnull
    private static BackendAssignmentAckPayload createAck(
        @Nonnull String assignmentId,
        String externalMatchId,
        @Nonnull String status,
        @Nonnull String localMatchId,
        @Nonnull String reason,
        long nowEpochMs
    ) {
        return new BackendAssignmentAckPayload(
            UUID.randomUUID().toString().toLowerCase(),
            assignmentId,
            normalize(externalMatchId),
            normalize(status),
            normalize(localMatchId),
            normalize(reason),
            nowEpochMs
        );
    }

    @Nonnull
    private static List<String> normalizePlayers(List<String> rawPlayers) {
        if (rawPlayers == null || rawPlayers.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String rawPlayer : rawPlayers) {
            String value = normalize(rawPlayer);
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalize(rawValue);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }

    private record BackendAssignmentDocument(
        int schemaVersion,
        long lastSequence,
        List<BackendAssignmentRecord> assignments,
        List<BackendAssignmentAckPayload> pendingAcks
    ) {
        private BackendAssignmentDocument {
            assignments = assignments == null ? List.of() : List.copyOf(assignments);
            pendingAcks = pendingAcks == null ? List.of() : List.copyOf(pendingAcks);
        }
    }

    public record BackendAssignmentRecord(
        String assignmentId,
        String assignmentHash,
        String externalMatchId,
        String queueId,
        String arenaId,
        List<String> playerUuids,
        String status,
        String localMatchId,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        String message
    ) {
        @Nonnull
        private BackendAssignmentRecord normalized() {
            return new BackendAssignmentRecord(
                normalize(assignmentId),
                normalize(assignmentHash),
                normalize(externalMatchId),
                normalize(queueId),
                normalize(arenaId),
                normalizePlayers(playerUuids),
                normalize(status),
                normalize(localMatchId),
                Math.max(0L, createdAtEpochMs),
                Math.max(0L, updatedAtEpochMs),
                normalize(message)
            );
        }
    }
}
