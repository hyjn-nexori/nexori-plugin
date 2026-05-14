package io.github.hyjn.nexori.plugin.backend.logic;

import javax.annotation.Nonnull;

/**
 * Decides how result reporting should apply backend HTTP responses.
 */
public final class BackendResultResponsePolicy {

    public static final String HEALTH_AUTH_FAILED = "AUTH_FAILED";
    public static final String HEALTH_AUTH_FORBIDDEN = "AUTH_FORBIDDEN";
    public static final String HEALTH_FAILED_PERMANENT = "FAILED_PERMANENT";
    public static final String HEALTH_RESULT_REPORT_FAILED = "RESULT_REPORT_FAILED";

    @Nonnull
    public BackendResultResponseDecision decide(
        @Nonnull String resultId,
        int statusCode,
        String receivedResultId,
        String backendStatus,
        String errorClass,
        String errorMessage,
        long nowEpochMs,
        long retryIntervalMs,
        long authBackoffMs,
        long errorBackoffMs
    ) {
        String normalizedBackendStatus = normalizeOptional(backendStatus);
        if (isAcceptedOrDuplicate(resultId, receivedResultId, normalizedBackendStatus)) {
            return new BackendResultResponseDecision(
                BackendResultResponseDecision.Action.ACKNOWLEDGE,
                statusCode,
                normalizedBackendStatus,
                "",
                "",
                "",
                0L,
                false
            );
        }

        String normalizedErrorClass = normalizeOptional(errorClass);
        String normalizedErrorMessage = normalizeOptional(errorMessage);
        if (statusCode == 400 || statusCode == 422) {
            return new BackendResultResponseDecision(
                BackendResultResponseDecision.Action.PERMANENT_FAILURE,
                statusCode,
                normalizedBackendStatus,
                normalizedErrorClass,
                normalizedErrorMessage,
                HEALTH_FAILED_PERMANENT,
                0L,
                false
            );
        }

        boolean authFailure = statusCode == 401;
        boolean forbidden = statusCode == 403;
        long backoffMs = authFailure || forbidden
            ? authBackoffMs
            : retryBackoffMs(statusCode, retryIntervalMs, errorBackoffMs);
        return new BackendResultResponseDecision(
            BackendResultResponseDecision.Action.RETRY,
            statusCode,
            normalizedBackendStatus,
            normalizedErrorClass,
            normalizedErrorMessage,
            authFailure ? HEALTH_AUTH_FAILED : forbidden ? HEALTH_AUTH_FORBIDDEN : HEALTH_RESULT_REPORT_FAILED,
            nowEpochMs + backoffMs,
            authFailure || forbidden
        );
    }

    private static boolean isAcceptedOrDuplicate(
        @Nonnull String resultId,
        String receivedResultId,
        @Nonnull String backendStatus
    ) {
        return resultId.equals(receivedResultId)
            && ("ACCEPTED".equalsIgnoreCase(backendStatus) || "DUPLICATE".equalsIgnoreCase(backendStatus));
    }

    private static long retryBackoffMs(int statusCode, long retryIntervalMs, long errorBackoffMs) {
        if (statusCode == 429 || statusCode >= 500 || statusCode <= 0) {
            return retryIntervalMs;
        }
        return errorBackoffMs;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
