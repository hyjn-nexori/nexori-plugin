package io.github.hyjn.nexori.plugin.backend.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdmissionStateResponsePolicyTest {

    private static final long NOW = 1_000L;
    private static final long AUTH_BACKOFF_MS = 30_000L;
    private static final long RETRY_INTERVAL_MS = 5_000L;
    private static final long EXPIRES_AT = 10_000L;

    private final AdmissionStateResponsePolicy policy = new AdmissionStateResponsePolicy();

    @Test
    void acceptsBlank2xxBodyAsOk() {
        AdmissionStateResponseDecision decision = decide(204, "", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decision.outcome());
        assertEquals("OK", decision.backendStatus());
        assertTrue(decision.shouldAckConsumedReservations());
    }

    @Test
    void accepts2xxOkAcceptedDuplicateAndDuplicateAccepted() {
        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decide(200, "{\"status\":\"OK\"}", false, EXPIRES_AT, false, true).outcome());
        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decide(200, "{\"status\":\"ACCEPTED\"}", false, EXPIRES_AT, false, true).outcome());
        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decide(200, "{\"status\":\"DUPLICATE\"}", false, EXPIRES_AT, false, true).outcome());
        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decide(200, "{\"status\":\"DUPLICATE_ACCEPTED\"}", false, EXPIRES_AT, false, true).outcome());
    }

    @Test
    void accepts2xxStatusWithWhitespaceAndLowercase() {
        AdmissionStateResponseDecision decision = decide(200, "{\"status\":\" accepted \"}", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decision.outcome());
        assertEquals("accepted", decision.backendStatus());
    }

    @Test
    void rejects2xxRejectedAsNotSemanticallyAcknowledged() {
        AdmissionStateResponseDecision decision = decide(200, "{\"status\":\"REJECTED\"}", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.NOT_SEMANTICALLY_ACKNOWLEDGED, decision.outcome());
        assertTrue(decision.shouldMarkDirtyFromSnapshot());
        assertTrue(decision.shouldScheduleImmediateFlush());
        assertFalse(decision.shouldAckConsumedReservations());
    }

    @Test
    void malformed2xxBodyDefaultsToOkAccordingToCurrentBehavior() {
        AdmissionStateResponseDecision decision = decide(200, "{not-json", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decision.outcome());
        assertEquals("OK", decision.backendStatus());
    }

    @Test
    void permanentFailure400RemovesClosedSnapshot() {
        AdmissionStateResponseDecision decision = decide(400, "", true, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.PERMANENT_FAILURE, decision.outcome());
        assertTrue(decision.shouldRememberClosedMatch());
        assertTrue(decision.shouldRemovePublicationState());
    }

    @Test
    void permanentFailure400KeepsOpenReportableState() {
        AdmissionStateResponseDecision decision = decide(400, "", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.PERMANENT_FAILURE, decision.outcome());
        assertFalse(decision.shouldRemovePublicationState());
        assertFalse(decision.shouldRememberClosedMatch());
    }

    @Test
    void permanentFailure422RemovesWhenMatchNoLongerReportable() {
        AdmissionStateResponseDecision decision = decide(422, "", false, EXPIRES_AT, false, false);

        assertEquals(AdmissionStateResponseDecision.Outcome.PERMANENT_FAILURE, decision.outcome());
        assertFalse(decision.shouldRememberClosedMatch());
        assertTrue(decision.shouldRemovePublicationState());
    }

    @Test
    void auth401SchedulesGlobalBackoffAndMarksDirty() {
        AdmissionStateResponseDecision decision = decide(401, "", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.AUTH_FAILURE, decision.outcome());
        assertEquals(AdmissionStateResponsePolicy.AUTH_FAILED, decision.lastHealthStatus());
        assertEquals(NOW + AUTH_BACKOFF_MS, decision.nextGlobalAttemptAtEpochMs());
        assertTrue(decision.shouldMarkDirtyFromSnapshot());
    }

    @Test
    void auth403SchedulesGlobalBackoffAndMarksDirty() {
        AdmissionStateResponseDecision decision = decide(403, "", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.AUTH_FAILURE, decision.outcome());
        assertEquals(NOW + AUTH_BACKOFF_MS, decision.nextGlobalAttemptAtEpochMs());
        assertTrue(decision.shouldMarkDirtyFromSnapshot());
    }

    @Test
    void retryable408MarksDirty() {
        AdmissionStateResponseDecision decision = decide(408, "", false, EXPIRES_AT, true, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE, decision.outcome());
        assertTrue(decision.shouldClearPendingRetrySnapshot());
        assertEquals(NOW + RETRY_INTERVAL_MS, decision.retryNotBeforeEpochMs());
    }

    @Test
    void retryable429MarksDirty() {
        AdmissionStateResponseDecision decision = decide(429, "", false, EXPIRES_AT, true, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE, decision.outcome());
        assertTrue(decision.shouldClearPendingRetrySnapshot());
    }

    @Test
    void retryable5xxMarksDirty() {
        AdmissionStateResponseDecision decision = decide(503, "", false, EXPIRES_AT, true, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE, decision.outcome());
        assertTrue(decision.shouldClearPendingRetrySnapshot());
    }

    @Test
    void retryableZeroStatusMarksDirty() {
        AdmissionStateResponseDecision decision = decide(0, "", false, EXPIRES_AT, true, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE, decision.outcome());
        assertTrue(decision.shouldClearPendingRetrySnapshot());
    }

    @Test
    void unexpectedFailureDoesNotMarkDirtyButSchedulesIfStateAlreadyDirty() {
        AdmissionStateResponseDecision decision = decide(418, "", false, EXPIRES_AT, true, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.UNEXPECTED_FAILURE, decision.outcome());
        assertFalse(decision.shouldMarkDirtyFromSnapshot());
        assertTrue(decision.shouldScheduleImmediateFlush());
    }

    @Test
    void acceptedClosedSnapshotRemembersClosedMatchAndRemovesState() {
        AdmissionStateResponseDecision decision = decide(200, "{\"status\":\"OK\"}", true, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decision.outcome());
        assertTrue(decision.shouldRememberClosedMatch());
        assertTrue(decision.shouldRemovePublicationState());
    }

    @Test
    void acceptedOpenSnapshotWithDirtyStateSchedulesImmediateFlush() {
        AdmissionStateResponseDecision decision = decide(200, "{\"status\":\"OK\"}", false, EXPIRES_AT, true, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED, decision.outcome());
        assertTrue(decision.shouldScheduleImmediateFlush());
        assertFalse(decision.shouldRemovePublicationState());
    }

    @Test
    void acceptedOpenSnapshotAcksConsumedReservationIds() {
        AdmissionStateResponseDecision decision = decide(200, "{\"status\":\"ACCEPTED\"}", false, EXPIRES_AT, false, true);

        assertTrue(decision.shouldAckConsumedReservations());
    }

    @Test
    void retryableUnexpiredSnapshotStoresPendingRetryWhenStateClean() {
        AdmissionStateResponseDecision decision = decide(500, "", false, EXPIRES_AT, false, true);

        assertTrue(decision.shouldStorePendingRetrySnapshot());
        assertEquals(NOW + RETRY_INTERVAL_MS, decision.retryNotBeforeEpochMs());
    }

    @Test
    void retryableCleanUnexpiredSnapshotSetsGlobalAndPendingRetry() {
        AdmissionStateResponseDecision decision = decide(500, "", false, EXPIRES_AT, false, true);

        assertEquals(AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE, decision.outcome());
        assertTrue(decision.shouldStorePendingRetrySnapshot());
        assertEquals(NOW + RETRY_INTERVAL_MS, decision.retryNotBeforeEpochMs());
        assertEquals(NOW + RETRY_INTERVAL_MS, decision.nextGlobalAttemptAtEpochMs());
    }

    @Test
    void retryableExpiredSnapshotMarksStartedDirtyWhenMatchStillReportable() {
        AdmissionStateResponseDecision decision = decide(500, "", false, NOW - 1, false, true);

        assertTrue(decision.shouldMarkMatchStartedDirty());
        assertFalse(decision.shouldRemovePublicationState());
    }

    @Test
    void retryableExpiredSnapshotRemovesStateWhenMatchNoLongerReportable() {
        AdmissionStateResponseDecision decision = decide(500, "", false, NOW - 1, false, false);

        assertFalse(decision.shouldMarkMatchStartedDirty());
        assertTrue(decision.shouldRemovePublicationState());
    }

    private AdmissionStateResponseDecision decide(
        int statusCode,
        String responseBody,
        boolean snapshotClosed,
        long expiresAt,
        boolean stateDirty,
        boolean reportableMatchStillExists
    ) {
        return policy.decide(
            statusCode,
            responseBody,
            NOW,
            AUTH_BACKOFF_MS,
            RETRY_INTERVAL_MS,
            snapshotClosed,
            expiresAt,
            stateDirty,
            reportableMatchStillExists
        );
    }
}
