package io.github.hyjn.nexori.plugin.backend.logic;

/**
 * Pure decision for applying a backend result-reporting HTTP response.
 */
public record BackendResultResponseDecision(
    Action action,
    int statusCode,
    String backendStatus,
    String errorClass,
    String errorMessage,
    String healthStatus,
    long nextAttemptAtEpochMs,
    boolean authWarning
) {

    public enum Action {
        ACKNOWLEDGE,
        RETRY,
        PERMANENT_FAILURE
    }
}
