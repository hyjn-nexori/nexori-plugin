package io.github.hyjn.nexori.plugin.backend;

import io.github.hyjn.nexori.plugin.backend.payload.BackendResultResponsePayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncResponsePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendHealthAndHttpResultTest {

    // ── BackendSyncHealthState ────────────────────────────────────────────────

    @Test
    void syncHealthyUsesHealthyStatusAndNow() {
        BackendSyncHealthState state = BackendSyncHealthState.healthy(1_000_000L);
        assertEquals("HEALTHY", state.status());
        assertEquals(0, state.lastStatusCode());
        assertEquals("", state.lastErrorClass());
        assertEquals("", state.lastMessage());
        assertEquals(1_000_000L, state.lastAttemptAtEpochMs());
        assertEquals(0L, state.nextAttemptAtEpochMs());
    }

    @Test
    void syncFailedDefaultsBlankStatusToFailed() {
        BackendSyncHealthState state = BackendSyncHealthState.failed(
            "   ", 503, "IOException", "timeout", 1_000_000L, 2_000_000L
        );
        assertEquals("FAILED", state.status(),
            "normalize(blank, 'FAILED') returns 'FAILED' default");
    }

    @Test
    void syncFailedTrimsStatusErrorClassAndMessage() {
        BackendSyncHealthState state = BackendSyncHealthState.failed(
            "  AUTH_FAILED  ", 401, "  IOException  ", "  timeout  ", 1_000_000L, 2_000_000L
        );
        assertEquals("AUTH_FAILED", state.status());
        assertEquals("IOException", state.lastErrorClass());
        assertEquals("timeout", state.lastMessage());
        assertEquals(401, state.lastStatusCode());
    }

    @Test
    void syncFailedClampsNegativeNextAttemptToZero() {
        BackendSyncHealthState state = BackendSyncHealthState.failed(
            "FAILED", 0, "", "", 1_000_000L, -500L
        );
        assertEquals(0L, state.nextAttemptAtEpochMs(),
            "Math.max(0L, nextAttemptAtEpochMs) clamps negative values to zero");
    }

    // ── BackendResultReportingHealthState ────────────────────────────────────

    @Test
    void resultHealthyUsesHealthyStatusAndNow() {
        BackendResultReportingHealthState state = BackendResultReportingHealthState.healthy(2_000_000L);
        assertEquals("HEALTHY", state.status());
        assertEquals(0, state.lastStatusCode());
        assertEquals("", state.lastErrorClass());
        assertEquals("", state.lastMessage());
        assertEquals(2_000_000L, state.lastAttemptAtEpochMs());
        assertEquals(0L, state.nextAttemptAtEpochMs());
    }

    @Test
    void resultFailedDefaultsBlankStatusToFailed() {
        BackendResultReportingHealthState state = BackendResultReportingHealthState.failed(
            null, 500, "RuntimeException", "error", 1_000_000L, 2_000_000L
        );
        assertEquals("FAILED", state.status(),
            "normalize(null, 'FAILED') returns 'FAILED' default");
    }

    @Test
    void resultFailedTrimsStatusErrorClassAndMessage() {
        BackendResultReportingHealthState state = BackendResultReportingHealthState.failed(
            "  RETRY_LIMIT  ", 429, "  ThrottleException  ", "  too many  ", 1_000_000L, 3_000_000L
        );
        assertEquals("RETRY_LIMIT", state.status());
        assertEquals("ThrottleException", state.lastErrorClass());
        assertEquals("too many", state.lastMessage());
    }

    @Test
    void resultFailedClampsNegativeNextAttemptToZero() {
        BackendResultReportingHealthState state = BackendResultReportingHealthState.failed(
            "FAILED", 0, "", "", 1_000_000L, -1_000L
        );
        assertEquals(0L, state.nextAttemptAtEpochMs());
    }

    // ── BackendSyncHttpResult ─────────────────────────────────────────────────

    @Test
    void syncSuccessPreservesSyncIdSequenceStatusAndResponse() {
        BackendSyncResponsePayload response = new BackendSyncResponsePayload(1, 42L, List.of(), List.of());
        BackendSyncHttpResult result = BackendSyncHttpResult.success("sync-1", 42L, 200, response);

        assertEquals("sync-1", result.syncId());
        assertEquals(42L, result.sequence());
        assertEquals(200, result.statusCode());
        assertEquals("", result.errorClass());
        assertEquals("", result.message());
        assertEquals(response, result.response());
    }

    @Test
    void syncSuccessHasResponse() {
        BackendSyncResponsePayload response = new BackendSyncResponsePayload(1, 1L, List.of(), List.of());
        BackendSyncHttpResult result = BackendSyncHttpResult.success("sync-1", 1L, 200, response);
        assertTrue(result.hasResponse());
    }

    @Test
    void syncFailureTrimsErrorClassAndMessage() {
        BackendSyncHttpResult result = BackendSyncHttpResult.failure(
            "sync-1", 1L, 503, "  IOException  ", "  connection timeout  "
        );
        assertEquals("IOException", result.errorClass());
        assertEquals("connection timeout", result.message());
    }

    @Test
    void syncFailureHasNoResponse() {
        BackendSyncHttpResult result = BackendSyncHttpResult.failure("sync-1", 1L, 503, "Err", "msg");
        assertFalse(result.hasResponse());
        assertNull(result.response());
    }

    @Test
    void syncAuthFailureOnlyFor401() {
        assertTrue(BackendSyncHttpResult.failure("s", 1L, 401, "e", "m").isAuthFailure());
        assertFalse(BackendSyncHttpResult.failure("s", 1L, 403, "e", "m").isAuthFailure());
        assertFalse(BackendSyncHttpResult.failure("s", 1L, 200, "e", "m").isAuthFailure());
    }

    @Test
    void syncForbiddenOnlyFor403() {
        assertTrue(BackendSyncHttpResult.failure("s", 1L, 403, "e", "m").isForbidden());
        assertFalse(BackendSyncHttpResult.failure("s", 1L, 401, "e", "m").isForbidden());
        assertFalse(BackendSyncHttpResult.failure("s", 1L, 500, "e", "m").isForbidden());
    }

    // ── BackendResultHttpResult ───────────────────────────────────────────────

    @Test
    void resultSuccessPreservesRequestResultStatusAndResponse() {
        BackendResultResponsePayload response = new BackendResultResponsePayload(1, "result-1", "ok");
        BackendResultHttpResult result = BackendResultHttpResult.success("req-1", "result-1", 200, response);

        assertEquals("req-1", result.requestId());
        assertEquals("result-1", result.resultId());
        assertEquals(200, result.statusCode());
        assertEquals("", result.errorClass());
        assertEquals("", result.message());
        assertEquals(response, result.response());
    }

    @Test
    void resultSuccessHasResponse() {
        BackendResultResponsePayload response = new BackendResultResponsePayload(1, "r-1", "ok");
        assertTrue(BackendResultHttpResult.success("req-1", "r-1", 200, response).hasResponse());
    }

    @Test
    void resultFailureTrimsErrorClassAndMessage() {
        BackendResultHttpResult result = BackendResultHttpResult.failure(
            "req-1", "r-1", 500, "  RuntimeException  ", "  unexpected error  "
        );
        assertEquals("RuntimeException", result.errorClass());
        assertEquals("unexpected error", result.message());
    }

    @Test
    void resultFailureHasNoResponse() {
        BackendResultHttpResult result = BackendResultHttpResult.failure("req-1", "r-1", 500, "Err", "msg");
        assertFalse(result.hasResponse());
        assertNull(result.response());
    }

    @Test
    void resultAuthFailureOnlyFor401() {
        assertTrue(BackendResultHttpResult.failure("r", "i", 401, "e", "m").isAuthFailure());
        assertFalse(BackendResultHttpResult.failure("r", "i", 403, "e", "m").isAuthFailure());
    }

    @Test
    void resultForbiddenOnlyFor403() {
        assertTrue(BackendResultHttpResult.failure("r", "i", 403, "e", "m").isForbidden());
        assertFalse(BackendResultHttpResult.failure("r", "i", 401, "e", "m").isForbidden());
    }
}
