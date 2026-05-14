package io.github.hyjn.nexori.plugin.backend.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendResultResponsePolicyTest {

    private static final String RESULT_ID = "result-1";
    private static final long NOW = 1_000L;
    private static final long RETRY_INTERVAL_MS = 30_000L;
    private static final long AUTH_BACKOFF_MS = 60_000L;
    private static final long ERROR_BACKOFF_MS = 5_000L;

    private final BackendResultResponsePolicy policy = new BackendResultResponsePolicy();

    @Test
    void accepts2xxAcceptedResponse() {
        BackendResultResponseDecision decision = decide(200, RESULT_ID, "ACCEPTED", "", "");

        assertEquals(BackendResultResponseDecision.Action.ACKNOWLEDGE, decision.action());
        assertEquals("ACCEPTED", decision.backendStatus());
        assertEquals(0L, decision.nextAttemptAtEpochMs());
    }

    @Test
    void accepts2xxDuplicateResponseIfCurrentBehaviorDoesThat() {
        BackendResultResponseDecision decision = decide(200, RESULT_ID, "DUPLICATE", "", "");

        assertEquals(BackendResultResponseDecision.Action.ACKNOWLEDGE, decision.action());
        assertEquals("DUPLICATE", decision.backendStatus());
    }

    @Test
    void handles2xxRejectedResponseAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(200, RESULT_ID, "REJECTED", "", "");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertEquals(BackendResultResponsePolicy.HEALTH_RESULT_REPORT_FAILED, decision.healthStatus());
    }

    @Test
    void retries429Response() {
        BackendResultResponseDecision decision = decide(429, "", "", "RATE_LIMITED", "retry later");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(NOW + RETRY_INTERVAL_MS, decision.nextAttemptAtEpochMs());
    }

    @Test
    void retries5xxResponse() {
        BackendResultResponseDecision decision = decide(503, "", "", "HTTP_503", "backend unavailable");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(NOW + RETRY_INTERVAL_MS, decision.nextAttemptAtEpochMs());
    }

    @Test
    void marks400AsPermanentFailureAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(400, "", "", "BAD_REQUEST", "invalid");

        assertEquals(BackendResultResponseDecision.Action.PERMANENT_FAILURE, decision.action());
        assertEquals(BackendResultResponsePolicy.HEALTH_FAILED_PERMANENT, decision.healthStatus());
        assertEquals(0L, decision.nextAttemptAtEpochMs());
    }

    @Test
    void acknowledgesAcceptedResponseEvenWhenHttpStatusIs400AccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(400, RESULT_ID, "ACCEPTED", "", "");

        assertEquals(BackendResultResponseDecision.Action.ACKNOWLEDGE, decision.action());
    }

    @Test
    void marks422AsPermanentFailureAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(422, "", "", "UNPROCESSABLE", "invalid");

        assertEquals(BackendResultResponseDecision.Action.PERMANENT_FAILURE, decision.action());
        assertEquals(BackendResultResponsePolicy.HEALTH_FAILED_PERMANENT, decision.healthStatus());
    }

    @Test
    void acknowledgesDuplicateResponseEvenWhenHttpStatusIs422AccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(422, RESULT_ID, "DUPLICATE", "", "");

        assertEquals(BackendResultResponseDecision.Action.ACKNOWLEDGE, decision.action());
    }

    @Test
    void retries401ResponseAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(401, "", "", "UNAUTHORIZED", "bad token");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(BackendResultResponsePolicy.HEALTH_AUTH_FAILED, decision.healthStatus());
        assertEquals(NOW + AUTH_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertTrue(decision.authWarning());
    }

    @Test
    void retries403ResponseAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(403, "", "", "FORBIDDEN", "forbidden");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(BackendResultResponsePolicy.HEALTH_AUTH_FORBIDDEN, decision.healthStatus());
        assertEquals(NOW + AUTH_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertTrue(decision.authWarning());
    }

    @Test
    void retries404ResponseAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(404, "", "", "NOT_FOUND", "missing");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(BackendResultResponsePolicy.HEALTH_RESULT_REPORT_FAILED, decision.healthStatus());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
    }

    @Test
    void handlesMalformedBackendResponseAccordingToCurrentBehavior() {
        BackendResultResponseDecision decision = decide(200, "", "", "JsonSyntaxException", "Backend result response body could not be parsed.");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
        assertEquals("JsonSyntaxException", decision.errorClass());
    }

    @Test
    void preservesBackendStatusMessage() {
        BackendResultResponseDecision decision = decide(200, RESULT_ID, " rejected ", "", "");

        assertEquals("rejected", decision.backendStatus());
    }

    @Test
    void calculatesNextAttemptAtEpochMs() {
        BackendResultResponseDecision decision = decide(0, "", "", "IOException", "network failed");

        assertEquals(NOW + RETRY_INTERVAL_MS, decision.nextAttemptAtEpochMs());
    }

    @Test
    void normalizesBlankErrorClassAndMessage() {
        BackendResultResponseDecision decision = decide(500, "", "", " ", null);

        assertEquals("", decision.errorClass());
        assertEquals("", decision.errorMessage());
    }

    @Test
    void doesNotUseHttpOrStoreDependencies() {
        BackendResultResponseDecision decision = decide(202, RESULT_ID, "duplicate", "", "");

        assertEquals(BackendResultResponseDecision.Action.ACKNOWLEDGE, decision.action());
        assertFalse(decision.authWarning());
    }

    @Test
    void retriesWhenReceivedResultIdDoesNotMatch() {
        BackendResultResponseDecision decision = decide(200, "different-result", "ACCEPTED", "", "");

        assertEquals(BackendResultResponseDecision.Action.RETRY, decision.action());
        assertEquals(NOW + ERROR_BACKOFF_MS, decision.nextAttemptAtEpochMs());
    }

    private BackendResultResponseDecision decide(
        int statusCode,
        String receivedResultId,
        String backendStatus,
        String errorClass,
        String errorMessage
    ) {
        return policy.decide(
            RESULT_ID,
            statusCode,
            receivedResultId,
            backendStatus,
            errorClass,
            errorMessage,
            NOW,
            RETRY_INTERVAL_MS,
            AUTH_BACKOFF_MS,
            ERROR_BACKOFF_MS
        );
    }
}
