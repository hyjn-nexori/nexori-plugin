package io.github.hyjn.nexori.plugin.backend.logic;

/**
 * Pure decision for applying backend admission state reporting responses.
 */
public record AdmissionStateResponseDecision(
    Outcome outcome,
    String backendStatus,
    String lastHealthStatus,
    long nextGlobalAttemptAtEpochMs,
    long retryNotBeforeEpochMs,
    boolean shouldAckConsumedReservations,
    boolean shouldMarkDirtyFromSnapshot,
    boolean shouldScheduleImmediateFlush,
    boolean shouldRememberClosedMatch,
    boolean shouldRemovePublicationState,
    boolean shouldClearPendingRetrySnapshot,
    boolean shouldStorePendingRetrySnapshot,
    boolean shouldMarkMatchStartedDirty
) {

    public enum Outcome {
        ACKNOWLEDGED,
        NOT_SEMANTICALLY_ACKNOWLEDGED,
        PERMANENT_FAILURE,
        AUTH_FAILURE,
        RETRYABLE_FAILURE,
        UNEXPECTED_FAILURE
    }
}
