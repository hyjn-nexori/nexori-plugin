package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentAckPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendAssignmentStoreTest {

    private static BackendAssignmentPayload makePayload(String assignmentId) {
        return new BackendAssignmentPayload(
            "MATCH", assignmentId, "match-" + assignmentId, "ext-" + assignmentId, "MATCH",
            "queue-1", List.of("player-1"), List.of("player-1"), "arena-1",
            List.of(), "server-1", "target:5520", "", "", false, new JsonObject()
        );
    }

    @Test
    void nextSequenceOnFreshStoreReturnsOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);

        long seq = store.nextSequence();

        assertEquals(1L, seq);
    }

    @Test
    void nextSequenceIncrementsOnEachCall(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);

        long first = store.nextSequence();
        long second = store.nextSequence();

        assertEquals(first + 1, second);
    }

    @Test
    void listPendingAcksOnEmptyStoreReturnsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);

        List<BackendAssignmentAckPayload> acks = store.listPendingAcks();

        assertNotNull(acks);
        assertTrue(acks.isEmpty());
    }

    @Test
    void recordProcessedAddsToAssignmentMap(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);
        BackendAssignmentPayload payload = makePayload("assign-1");

        store.recordProcessed(payload, "hash-abc", "PROCESSED", "local-1", "ok", 1_000L);

        assertTrue(store.findAssignment("assign-1").isPresent());
    }

    @Test
    void findAssignmentByIdAfterRecordProcessedReturnsRecord(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);
        BackendAssignmentPayload payload = makePayload("assign-2");

        store.recordProcessed(payload, "hash-def", "PROCESSED", "local-2", "ok", 2_000L);
        BackendAssignmentStore.BackendAssignmentRecord record = store.findAssignment("assign-2").orElseThrow();

        assertEquals("assign-2", record.assignmentId());
        assertEquals("local-2", record.localMatchId());
    }

    @Test
    void findAssignmentForUnknownIdReturnsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);

        assertTrue(store.findAssignment("nonexistent").isEmpty());
    }

    @Test
    void recordProcessedAddsToPendingAcks(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);
        BackendAssignmentPayload payload = makePayload("assign-3");

        store.recordProcessed(payload, "hash-ghi", "PROCESSED", "local-3", "ok", 3_000L);

        assertFalse(store.listPendingAcks().isEmpty(), "Pending acks should not be empty after recordProcessed");
    }

    @Test
    void acknowledgeRemovesFromPendingAcks(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);
        BackendAssignmentPayload payload = makePayload("assign-4");
        BackendAssignmentAckPayload ack = store.recordProcessed(payload, "hash-jkl", "PROCESSED", "local-4", "ok", 4_000L);

        store.acknowledge(List.of(ack.ackId()));

        assertTrue(store.listPendingAcks().isEmpty(), "Pending acks should be empty after acknowledge");
    }

    @Test
    void nextSequenceRoundTripsAcrossStoreInstances(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store1 = new BackendAssignmentStore(file);
        store1.nextSequence();
        store1.nextSequence();

        BackendAssignmentStore store2 = new BackendAssignmentStore(file);
        long next = store2.nextSequence();

        assertEquals(3L, next, "Sequence should persist across store instances");
    }

    @Test
    void recordRejectedDuplicateAddsToPendingAcksAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);
        BackendAssignmentPayload payload = makePayload("assign-5");

        store.recordRejectedDuplicate(payload, "duplicate assignment", 5_000L);

        assertFalse(store.listPendingAcks().isEmpty(), "recordRejectedDuplicate should add a pending ack");
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void pendingAcksPersistAcrossStoreInstances(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store1 = new BackendAssignmentStore(file);
        BackendAssignmentAckPayload ack = store1.recordProcessed(
            makePayload("assign-6"), "hash-pqr", "PROCESSED", "local-6", "ok", 6_000L);

        BackendAssignmentStore store2 = new BackendAssignmentStore(file);
        List<BackendAssignmentAckPayload> acks = store2.listPendingAcks();

        assertTrue(acks.stream().anyMatch(a -> a.ackId().equals(ack.ackId())),
            "Pending ack should persist across store instances: " + ack.ackId());
    }

    @Test
    void acknowledgeMissingAckPreservesExistingAcks(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        BackendAssignmentStore store = new BackendAssignmentStore(file);
        BackendAssignmentAckPayload ack = store.recordProcessed(
            makePayload("assign-7"), "hash-stu", "PROCESSED", "local-7", "ok", 7_000L);

        store.acknowledge(List.of("missing-ack-id-that-does-not-exist"));

        assertTrue(store.listPendingAcks().stream().anyMatch(a -> a.ackId().equals(ack.ackId())),
            "Original ack should still be present after acknowledging unknown ack id");
    }

    @Test
    void blankFileIsRewrittenAsEmptyDocument(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        BackendAssignmentStore store = new BackendAssignmentStore(file);

        assertTrue(store.listPendingAcks().isEmpty());
        assertEquals(1L, store.nextSequence());
        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertFalse(written.isBlank(), "Blank file should be rewritten to a valid empty document");
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("assignments.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);

        assertThrows(RuntimeException.class, () -> new BackendAssignmentStore(file),
            "Corrupt JSON should throw RuntimeException (JsonSyntaxException)");
    }
}
