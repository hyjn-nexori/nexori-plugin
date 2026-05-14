package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.BackendSyncHttpResult;
import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncResponsePayload;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendSyncResponsePolicyTest {

    private static final String SYNC_ID = "sync-1";
    private static final long SEQUENCE = 42L;
    private static final long NOW = 10_000L;
    private static final long AUTH_BACKOFF_MS = 30_000L;
    private static final long ERROR_BACKOFF_MS = 5_000L;

    private final BackendSyncResponsePolicy policy = new BackendSyncResponsePolicy();

    @Test
    void authFailure401ProducesAuthFailedDecision() {
        BackendSyncResponseDecision decision = decide(failure(401, "HTTP", "Unauthorized"));

        assertEquals(BackendSyncResponseDecision.Action.AUTH_FAILED, decision.action());
        assertEquals("AUTH_FAILED", decision.healthState().status());
        assertEquals(401, decision.healthState().lastStatusCode());
        assertEquals("HTTP", decision.healthState().lastErrorClass());
        assertEquals("Backend sync auth failed.", decision.healthState().lastMessage());
        assertEquals(NOW + AUTH_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertFalse(decision.shouldProcessResponse());
    }

    @Test
    void forbidden403ProducesForbiddenDecision() {
        BackendSyncResponseDecision decision = decide(failure(403, "HTTP", "Forbidden"));

        assertEquals(BackendSyncResponseDecision.Action.FORBIDDEN, decision.action());
        assertEquals("AUTH_FORBIDDEN", decision.healthState().status());
        assertEquals(403, decision.healthState().lastStatusCode());
        assertEquals("HTTP", decision.healthState().lastErrorClass());
        assertEquals("Backend sync forbidden.", decision.healthState().lastMessage());
        assertEquals(NOW + AUTH_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertFalse(decision.shouldProcessResponse());
    }

    @Test
    void failureWithoutResponseUsesSyncFailedHealth() {
        BackendSyncResponseDecision decision = decide(failure(502, "HTTP", "Backend sync returned non-success status."));

        assertEquals(BackendSyncResponseDecision.Action.FAILED_NO_RESPONSE, decision.action());
        assertEquals("SYNC_FAILED", decision.healthState().status());
        assertEquals(502, decision.healthState().lastStatusCode());
        assertEquals("HTTP", decision.healthState().lastErrorClass());
        assertEquals("Backend sync returned non-success status.", decision.healthState().lastMessage());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertFalse(decision.shouldProcessResponse());
    }

    @Test
    void transportFailureStatusZeroUsesErrorBackoff() {
        BackendSyncResponseDecision decision = decide(failure(0, "ConnectException", "Backend sync request failed."));

        assertEquals("SYNC_FAILED", decision.healthState().status());
        assertEquals(0, decision.healthState().lastStatusCode());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
    }

    @Test
    void http500WithoutParsedResponseUsesErrorBackoff() {
        BackendSyncResponseDecision decision = decide(failure(500, "HTTP", "Backend sync returned non-success status."));

        assertEquals(BackendSyncResponseDecision.Action.FAILED_NO_RESPONSE, decision.action());
        assertEquals("SYNC_FAILED", decision.healthState().status());
        assertEquals(500, decision.healthState().lastStatusCode());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
    }

    @Test
    void parseFailureWith2xxStatusUsesSyncFailedHealthAndErrorBackoff() {
        BackendSyncResponseDecision decision = decide(failure(
            200,
            "JsonSyntaxException",
            "Backend sync response could not be parsed."
        ));

        assertEquals(BackendSyncResponseDecision.Action.FAILED_NO_RESPONSE, decision.action());
        assertEquals("SYNC_FAILED", decision.healthState().status());
        assertEquals(200, decision.healthState().lastStatusCode());
        assertEquals("JsonSyntaxException", decision.healthState().lastErrorClass());
        assertEquals("Backend sync response could not be parsed.", decision.healthState().lastMessage());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertFalse(decision.shouldProcessResponse());
    }

    @Test
    void successfulResponseProducesHealthyDecision() {
        BackendSyncResponseDecision decision = decide(success());

        assertEquals(BackendSyncResponseDecision.Action.PROCESS_RESPONSE, decision.action());
        assertEquals("HEALTHY", decision.healthState().status());
        assertEquals(0, decision.healthState().lastStatusCode());
        assertEquals(NOW, decision.healthState().lastAttemptAtEpochMs());
        assertEquals(0L, decision.nextAttemptAtEpochMs());
    }

    @Test
    void successfulResponseShouldProcessAssignments() {
        BackendSyncResponseDecision decision = decide(success());

        assertTrue(decision.shouldProcessResponse());
    }

    @Test
    void failureWithoutResponseDoesNotProcessAssignments() {
        BackendSyncResponseDecision decision = decide(failure(503, "HTTP", "Backend sync returned non-success status."));

        assertFalse(decision.shouldProcessResponse());
    }

    @Test
    void preservesErrorClassAndMessageForFailure() {
        BackendSyncResponseDecision decision = decide(failure(0, "SocketTimeoutException", "Backend sync request failed."));

        assertEquals("SocketTimeoutException", decision.healthState().lastErrorClass());
        assertEquals("Backend sync request failed.", decision.healthState().lastMessage());
    }

    @Test
    void clampsNextAttemptUsingProvidedBackoff() {
        BackendSyncResponseDecision authDecision = policy.decide(failure(401, "HTTP", "Unauthorized"), 0L, -5L, ERROR_BACKOFF_MS);
        BackendSyncResponseDecision errorDecision = policy.decide(failure(0, "NO_RESPONSE", "No response"), 0L, AUTH_BACKOFF_MS, -5L);

        assertEquals(0L, authDecision.healthState().nextAttemptAtEpochMs());
        assertEquals(-5L, authDecision.nextAttemptAtEpochMs());
        assertEquals(0L, errorDecision.healthState().nextAttemptAtEpochMs());
        assertEquals(-5L, errorDecision.nextAttemptAtEpochMs());
    }

    @Test
    void doesNotUseHttpStoreOrLoggerDependencies() {
        assertEquals(0, BackendSyncResponsePolicy.class.getDeclaredFields().length);
    }

    private BackendSyncResponseDecision decide(BackendSyncHttpResult result) {
        return policy.decide(result, NOW, AUTH_BACKOFF_MS, ERROR_BACKOFF_MS);
    }

    private static BackendSyncHttpResult success() {
        return BackendSyncHttpResult.success(
            SYNC_ID,
            SEQUENCE,
            200,
            new BackendSyncResponsePayload(1, SEQUENCE, List.of("ack-1"), List.of())
        );
    }

    private static BackendSyncHttpResult failure(int statusCode, String errorClass, String message) {
        return BackendSyncHttpResult.failure(SYNC_ID, SEQUENCE, statusCode, errorClass, message);
    }
}
