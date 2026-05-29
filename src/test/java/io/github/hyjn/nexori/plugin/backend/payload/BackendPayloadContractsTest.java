package io.github.hyjn.nexori.plugin.backend.payload;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendPayloadContractsTest {

    // ── BackendAssignmentAckPayload ────────────────────────────────────────────

    @Test
    void assignmentAckPreservesFields() {
        BackendAssignmentAckPayload ack = new BackendAssignmentAckPayload(
            "ack-1", "assign-1", "ext-match-1", "ACCEPTED", "local-match-1", "all players arrived", 1_000_000L
        );
        assertEquals("ack-1", ack.ackId());
        assertEquals("assign-1", ack.assignmentId());
        assertEquals("ext-match-1", ack.externalMatchId());
        assertEquals("ACCEPTED", ack.status());
        assertEquals("local-match-1", ack.localMatchId());
        assertEquals("all players arrived", ack.reason());
        assertEquals(1_000_000L, ack.createdAtEpochMs());
    }

    // ── BackendAssignmentPlayerPayload ────────────────────────────────────────

    @Test
    void assignmentPlayerPreservesFields() {
        BackendAssignmentPlayerPayload player = new BackendAssignmentPlayerPayload(
            "player-uuid-1", "reservation-abc", 2_000_000L
        );
        assertEquals("player-uuid-1", player.playerUuid());
        assertEquals("reservation-abc", player.admissionReservationId());
        assertEquals(2_000_000L, player.admissionExpiresAtEpochMs());
    }

    // ── BackendAssignmentPayload ──────────────────────────────────────────────

    @Test
    void assignmentPayloadPreservesAllFields() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("key", "value");
        BackendAssignmentPayload payload = new BackendAssignmentPayload(
            "MATCH", "assign-1", "match-1", "ext-match-1", "TRAVEL",
            "queue-1", List.of("player-1"), List.of("player-1"),
            "arena-1", List.of(), "srv-1", "remote.srv:25565",
            "mode-1", "kit-1", false, metadata
        );
        assertEquals("MATCH", payload.assignmentType());
        assertEquals("assign-1", payload.assignmentId());
        assertEquals("match-1", payload.matchId());
        assertEquals("remote.srv:25565", payload.targetConnectionAddress());
        assertEquals("mode-1", payload.modeId());
        assertEquals(metadata, payload.metadata());
    }

    // ── BackendSyncRequestPayload ─────────────────────────────────────────────

    @Test
    void syncRequestPayloadPreservesSchemaSyncSequenceServerAndCollections() {
        BackendSyncRequestPayload.ServerSnapshot server =
            new BackendSyncRequestPayload.ServerSnapshot("fp-1", "srv.host:25565", "LOBBY", "us-east");
        BackendSyncRequestPayload payload = new BackendSyncRequestPayload(
            3, "sync-1", 42L, 1_000_000L, "srv-1",
            server, List.of(), List.of(), List.of(), List.of()
        );
        assertEquals(3, payload.schemaVersion());
        assertEquals("sync-1", payload.syncId());
        assertEquals(42L, payload.sequence());
        assertEquals("srv-1", payload.serverId());
        assertEquals(server, payload.server());
        assertTrue(payload.queues().isEmpty());
    }

    @Test
    void syncRequestNestedSnapshotsPreserveFields() {
        BackendSyncRequestPayload.ServerSnapshot server =
            new BackendSyncRequestPayload.ServerSnapshot("fp-abc", "srv.host:25565", "GAME", "eu-west");
        assertEquals("fp-abc", server.fingerprint());
        assertEquals("srv.host:25565", server.connectionAddress());
        assertEquals("GAME", server.role());
        assertEquals("eu-west", server.region());

        BackendSyncRequestPayload.ArenaSnapshot arena =
            new BackendSyncRequestPayload.ArenaSnapshot(
                "arena-1", "Arena One", "remote.srv:25565", "target-1",
                "template-1", "trigger-1", 8, true
            );
        assertEquals("arena-1", arena.arenaId());
        assertEquals(8, arena.maxSupportedPlayers());
        assertTrue(arena.enabled());
    }

    // ── BackendSyncResponsePayload ────────────────────────────────────────────

    @Test
    void syncResponsePayloadPreservesSequenceAcksAndAssignments() {
        BackendSyncResponsePayload payload = new BackendSyncResponsePayload(
            1, 15L, List.of("ack-1", "ack-2"), List.of()
        );
        assertEquals(1, payload.schemaVersion());
        assertEquals(15L, payload.receivedSequence());
        assertEquals(2, payload.acknowledgedAssignmentAckIds().size());
        assertTrue(payload.assignments().isEmpty());
    }

    // ── BackendResultPlayerPayload ────────────────────────────────────────────

    @Test
    void resultPlayerPayloadPreservesFields() {
        BackendResultPlayerPayload player = new BackendResultPlayerPayload("player-uuid-1", "WIN", "team-a won");
        assertEquals("player-uuid-1", player.playerUuid());
        assertEquals("WIN", player.outcome());
        assertEquals("team-a won", player.reason());
    }

    // ── BackendResultPayload ──────────────────────────────────────────────────

    @Test
    void resultPayloadPreservesSchemaIdsMetadataPlayersAndCustomData() {
        JsonObject customData = new JsonObject();
        customData.addProperty("winner", "team-a");
        BackendResultPayload payload = new BackendResultPayload(
            2, "result-1", 1_000_000L, "srv-1",
            "local-match-1", "ext-match-1", "assign-1",
            Map.of("player-uuid-1", "assign-1"),
            "queue-1", "arena-1", "rules-1",
            List.of(new BackendResultPlayerPayload("player-uuid-1", "WIN", "")),
            "match completed",
            Map.of("region", "us-east"),
            customData,
            2_000_000L
        );
        assertEquals(2, payload.schemaVersion());
        assertEquals("result-1", payload.resultId());
        assertEquals("local-match-1", payload.localMatchId());
        assertEquals(1, payload.players().size());
        assertEquals("WIN", payload.players().get(0).outcome());
        assertEquals(customData, payload.customData());
        assertEquals("us-east", payload.metadata().get("region"));
    }

    // ── BackendResultResponsePayload ──────────────────────────────────────────

    @Test
    void resultResponsePayloadPreservesSchemaReceivedIdAndStatus() {
        BackendResultResponsePayload payload = new BackendResultResponsePayload(1, "result-1", "ok");
        assertEquals(1, payload.schemaVersion());
        assertEquals("result-1", payload.receivedResultId());
        assertEquals("ok", payload.status());
    }

    // ── BackendMatchAdmissionStatePayload ────────────────────────────────────

    @Test
    void admissionStatePayloadPreservesAllContractFields() {
        BackendMatchAdmissionStatePayload payload = new BackendMatchAdmissionStatePayload(
            1, "state-update-1", 7L, "hash-abc", 1_000_000L, 2_000_000L,
            "srv-1", "game-server-1.example.com:7777", "match-1", "ext-match-1", "queue-1", "arena-1",
            true, "OPPORTUNISTIC", 60, "ACTIVE",
            true, 3_000_000L, 8, 5, 3,
            10, 5, 5,
            List.of("reservation-1"), false, "", "PLAYER_JOINED", List.of()
        );
        assertEquals("state-update-1", payload.stateUpdateId());
        assertEquals(7L, payload.admissionStateSequence());
        assertEquals("hash-abc", payload.payloadHash());
        assertEquals("match-1", payload.matchId());
        assertTrue(payload.backfillEnabled());
        assertTrue(payload.admissionOpen());
        assertEquals(8, payload.admissionCapacity());
        assertEquals(5, payload.admittedSlotCount());
        assertEquals(1, payload.consumedAdmissionReservationIds().size());
    }

    // ── BackendMatchAdmissionStateResponsePayload ─────────────────────────────

    @Test
    void admissionStateResponsePayloadPreservesSchemaStatusAndConsumedIds() {
        BackendMatchAdmissionStateResponsePayload payload = new BackendMatchAdmissionStateResponsePayload(
            1, "state-update-1", 7L, "ok"
        );
        assertEquals(1, payload.schemaVersion());
        assertEquals("state-update-1", payload.receivedStateUpdateId());
        assertEquals(7L, payload.receivedAdmissionStateSequence());
        assertEquals("ok", payload.status());
    }

    // ── null collection contracts ─────────────────────────────────────────────

    @Test
    void nullCollectionsArePreservedAccordingToCurrentBehavior() {
        BackendSyncResponsePayload payload = new BackendSyncResponsePayload(1, 0L, null, null);
        assertNull(payload.acknowledgedAssignmentAckIds(),
            "Record constructor does not normalize collections; null is preserved as-is");
        assertNull(payload.assignments());
    }

    // ── JsonObject reference contracts ────────────────────────────────────────

    @Test
    void jsonObjectReferencesArePreservedAccordingToCurrentBehavior() {
        JsonObject metadata = new JsonObject();
        metadata.addProperty("rank", "gold");
        BackendAssignmentPayload payload = new BackendAssignmentPayload(
            "MATCH", "a-1", "m-1", "e-1", "TRAVEL",
            "q-1", List.of(), List.of(),
            "arena-1", List.of(), "srv-1", "", "", "", false, metadata
        );
        assertNotNull(payload.metadata());
        assertTrue(payload.metadata().has("rank"),
            "JsonObject reference is stored as-is; mutations to the original object are visible");
        metadata.addProperty("newKey", "newVal");
        assertTrue(payload.metadata().has("newKey"),
            "Record does not deep-copy JsonObject; same reference is stored");
    }
}
