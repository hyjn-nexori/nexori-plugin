package io.github.hyjn.nexori.plugin.backend;

public record BackendResultReportingHealthState(
    String status,
    int lastStatusCode,
    String lastErrorClass,
    String lastMessage,
    long lastAttemptAtEpochMs,
    long nextAttemptAtEpochMs
) {

    public static BackendResultReportingHealthState healthy(long nowEpochMs) {
        return new BackendResultReportingHealthState("HEALTHY", 0, "", "", nowEpochMs, 0L);
    }

    public static BackendResultReportingHealthState failed(
        String status,
        int statusCode,
        String errorClass,
        String message,
        long nowEpochMs,
        long nextAttemptAtEpochMs
    ) {
        return new BackendResultReportingHealthState(
            normalize(status, "FAILED"),
            statusCode,
            normalize(errorClass, ""),
            normalize(message, ""),
            nowEpochMs,
            Math.max(0L, nextAttemptAtEpochMs)
        );
    }

    private static String normalize(String rawValue, String defaultValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }
        return rawValue.trim();
    }
}
