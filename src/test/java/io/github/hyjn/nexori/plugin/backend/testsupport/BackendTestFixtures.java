package io.github.hyjn.nexori.plugin.backend.testsupport;

import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.BackendMatchmakingConfig;
import io.github.hyjn.nexori.plugin.backend.BackendResultStore;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared test data factories for backend service tests.
 *
 * <p>All configs are already normalized (positive timeouts, valid URLs, non-blank tokens)
 * so that {@code config.isUsable()} / {@code isResultReportingUsable()} / etc. return
 * {@code true} and the service under test will actually attempt to send HTTP requests.</p>
 */
public final class BackendTestFixtures {

    private BackendTestFixtures() {}

    // ── Configs ───────────────────────────────────────────────────────────────

    /** Sync enabled, result reporting disabled, match-state reporting disabled. */
    public static BackendMatchmakingConfig enabledSyncConfig() {
        return new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            true,
            "http://backend.test",
            "test-server-token",
            1_000L,
            "us-east",
            3_000L,
            false,
            5_000L
        );
    }

    /** Sync disabled, result reporting enabled, match-state reporting disabled. */
    public static BackendMatchmakingConfig enabledResultConfig() {
        return new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            "http://backend.test",
            "test-server-token",
            1_000L,
            "us-east",
            3_000L,
            true,
            5_000L
        );
    }

    /**
     * Sync disabled, result reporting disabled, match-state reporting disabled, AFK check enabled.
     */
    public static BackendMatchmakingConfig enabledAfkCheckConfig() {
        return new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            "http://backend.test",
            "test-server-token",
            1_000L,
            "us-east",
            3_000L,
            false,
            5_000L,
            false,
            BackendMatchmakingConfig.DEFAULT_MATCH_STATE_DEBOUNCE_MS,
            BackendMatchmakingConfig.DEFAULT_MATCH_STATE_MAX_COALESCE_WINDOW_MS,
            BackendMatchmakingConfig.DEFAULT_MATCH_STATE_RETRY_INTERVAL_MS,
            BackendMatchmakingConfig.DEFAULT_MATCH_STATE_STALE_AFTER_MS,
            true
        );
    }

    /**
     * Sync disabled, result reporting disabled, match-state reporting enabled.
     * Debounce is 0 ms so dirty matches flush immediately on the next tick.
     */
    public static BackendMatchmakingConfig enabledMatchStateConfig() {
        return new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            "http://backend.test",
            "test-server-token",
            1_000L,
            "us-east",
            3_000L,
            false,
            5_000L,
            true,
            0L,
            5_000L,
            3_000L,
            30_000L
        );
    }

    // ── Server identity ───────────────────────────────────────────────────────

    /** Minimal server identity for tests — keys are null (not needed by backend sync). */
    public static ServerIdentity testServerIdentity() {
        return new ServerIdentity(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            "RSA",
            "test-fingerprint-abc",
            Instant.ofEpochMilli(1_000_000L),
            "dGVzdA==",
            null,
            null
        );
    }

    // ── BackendResultRecord factory ───────────────────────────────────────────

    /**
     * Builds a minimal PENDING {@link BackendResultStore.BackendResultRecord} suitable for
     * seeding a {@link BackendResultStore} in tests.
     *
     * <p>{@code nextAttemptAtEpochMs = 0} so the record is immediately picked up by
     * {@link BackendResultStore#findNextDuePending(long)} on the first tick.</p>
     *
     * @param resultId       known result id (must be non-blank and unique in the store)
     * @param nowEpochMs     timestamp to use for created/updated/ended fields
     */
    public static BackendResultStore.BackendResultRecord pendingResultRecord(
        String resultId,
        long nowEpochMs
    ) {
        return new BackendResultStore.BackendResultRecord(
            resultId,
            "local-match-" + resultId,
            "ext-match-" + resultId,
            "assign-" + resultId,
            Map.of(),
            "queue-test",
            "arena-test",
            "",
            List.of(),
            "match ended normally",
            Map.of(),
            new JsonObject(),
            "payload-hash-" + resultId,
            BackendResultStore.BackendResultStatus.PENDING.name(),
            0,
            nowEpochMs,
            nowEpochMs,
            nowEpochMs,
            0L,
            0L,
            "",
            "",
            0,
            ""
        );
    }

    // ── Result reporting JSON helpers ─────────────────────────────────────────

    /** A 200 "ACCEPTED" result response for the given resultId. */
    public static String acceptedResultResponse(String resultId) {
        return "{\"schemaVersion\":1,\"receivedResultId\":\"" + resultId + "\",\"status\":\"ACCEPTED\"}";
    }

    /** A 200 "DUPLICATE" result response for the given resultId. */
    public static String duplicateResultResponse(String resultId) {
        return "{\"schemaVersion\":1,\"receivedResultId\":\"" + resultId + "\",\"status\":\"DUPLICATE\"}";
    }

    // ── Payload JSON helpers ──────────────────────────────────────────────────

    /** A valid 200 sync response with no assignments and no acknowledged acks. */
    public static String emptySyncResponse(long receivedSequence) {
        return "{\"schemaVersion\":1,\"receivedSequence\":" + receivedSequence
            + ",\"acknowledgedAssignmentAckIds\":[],\"assignments\":[]}";
    }

    /**
     * A 200 sync response containing one INITIAL_MATCH assignment.
     *
     * <p>The assignment is intentionally minimal — only the fields the validator
     * requires are populated.</p>
     *
     * @param assignmentId  unique assignment identifier
     * @param playerUuid    UUID string for the single player in the assignment
     * @param externalMatchId external match reference (can vary across test cycles)
     */
    public static String syncResponseWithAssignment(
        long receivedSequence,
        String assignmentId,
        String playerUuid,
        String externalMatchId
    ) {
        String assignment = "{"
            + "\"assignmentType\":\"INITIAL_MATCH\","
            + "\"assignmentId\":\"" + assignmentId + "\","
            + "\"matchId\":\"nexori-match-test-1\","
            + "\"externalMatchId\":\"" + externalMatchId + "\","
            + "\"type\":\"CREATE_MATCH\","
            + "\"queueId\":\"queue-test\","
            + "\"playerUuids\":[\"" + playerUuid + "\"],"
            + "\"expectedPlayerUuids\":[],"
            + "\"arenaId\":\"arena-test\","
            + "\"players\":[],"
            + "\"reportingServerId\":\"srv-1\","
            + "\"targetConnectionAddress\":\"\","
            + "\"modeId\":\"\","
            + "\"kitId\":\"\","
            + "\"ranked\":false,"
            + "\"metadata\":{}"
            + "}";
        return "{\"schemaVersion\":1,\"receivedSequence\":" + receivedSequence
            + ",\"acknowledgedAssignmentAckIds\":[],\"assignments\":[" + assignment + "]}";
    }

    /**
     * A 200 sync response containing one BACKFILL assignment with player tickets.
     *
     * @param assignmentId     unique assignment identifier
     * @param playerUuid       UUID string for the backfill player
     * @param reservationId    admission reservation id for the player
     * @param expiresAtEpochMs admission expiry epoch ms (must be > 0)
     * @param targetAddress    target server connection address for BACKFILL routing
     */
    public static String syncResponseWithBackfillAssignment(
        long receivedSequence,
        String assignmentId,
        String playerUuid,
        String reservationId,
        long expiresAtEpochMs,
        String targetAddress
    ) {
        String player = "{"
            + "\"playerUuid\":\"" + playerUuid + "\","
            + "\"admissionReservationId\":\"" + reservationId + "\","
            + "\"admissionExpiresAtEpochMs\":" + expiresAtEpochMs
            + "}";
        String assignment = "{"
            + "\"assignmentType\":\"BACKFILL\","
            + "\"assignmentId\":\"" + assignmentId + "\","
            + "\"matchId\":\"nexori-match-test-2\","
            + "\"externalMatchId\":\"ext-match-backfill\","
            + "\"type\":\"JOIN_MATCH\","
            + "\"queueId\":\"queue-test\","
            + "\"playerUuids\":[],"
            + "\"expectedPlayerUuids\":[],"
            + "\"arenaId\":\"arena-test\","
            + "\"players\":[" + player + "],"
            + "\"reportingServerId\":\"srv-1\","
            + "\"targetConnectionAddress\":\"" + targetAddress + "\","
            + "\"modeId\":\"\","
            + "\"kitId\":\"\","
            + "\"ranked\":false,"
            + "\"metadata\":{}"
            + "}";
        return "{\"schemaVersion\":1,\"receivedSequence\":" + receivedSequence
            + ",\"acknowledgedAssignmentAckIds\":[],\"assignments\":[" + assignment + "]}";
    }
}
