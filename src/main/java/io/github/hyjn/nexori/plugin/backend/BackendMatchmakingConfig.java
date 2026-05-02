package io.github.hyjn.nexori.plugin.backend;

import javax.annotation.Nonnull;

public record BackendMatchmakingConfig(
    int schemaVersion,
    boolean enabled,
    String baseUrl,
    String serverToken,
    long syncIntervalMs,
    String region,
    long requestTimeoutMs,
    boolean resultReportingEnabled,
    long resultRetryIntervalMs
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final long DEFAULT_SYNC_INTERVAL_MS = 1000L;
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 3000L;
    private static final long DEFAULT_RESULT_RETRY_INTERVAL_MS = 5000L;

    @Nonnull
    public static BackendMatchmakingConfig defaults() {
        return new BackendMatchmakingConfig(
            CURRENT_SCHEMA_VERSION,
            false,
            "",
            "",
            DEFAULT_SYNC_INTERVAL_MS,
            "",
            DEFAULT_REQUEST_TIMEOUT_MS,
            false,
            DEFAULT_RESULT_RETRY_INTERVAL_MS
        );
    }

    @Nonnull
    public BackendMatchmakingConfig normalized() {
        return new BackendMatchmakingConfig(
            CURRENT_SCHEMA_VERSION,
            enabled,
            normalizeOptional(baseUrl),
            normalizeOptional(serverToken),
            syncIntervalMs <= 0L ? DEFAULT_SYNC_INTERVAL_MS : syncIntervalMs,
            normalizeOptional(region),
            requestTimeoutMs <= 0L ? DEFAULT_REQUEST_TIMEOUT_MS : requestTimeoutMs,
            resultReportingEnabled,
            resultRetryIntervalMs <= 0L ? DEFAULT_RESULT_RETRY_INTERVAL_MS : resultRetryIntervalMs
        );
    }

    public boolean isUsable() {
        return isSyncUsable();
    }

    public boolean isSyncUsable() {
        BackendMatchmakingConfig normalized = normalized();
        return !normalized.enabled() || (!normalized.baseUrl().isBlank() && !normalized.serverToken().isBlank());
    }

    public boolean isResultReportingUsable() {
        BackendMatchmakingConfig normalized = normalized();
        return !normalized.resultReportingEnabled()
            || (!normalized.baseUrl().isBlank() && !normalized.serverToken().isBlank());
    }

    @Nonnull
    public String syncUrl() {
        String normalizedBaseUrl = normalizeOptional(baseUrl);
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + "/nexori/sync";
    }

    @Nonnull
    public String resultsUrl() {
        String normalizedBaseUrl = normalizeOptional(baseUrl);
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + "/nexori/results";
    }

    @Nonnull
    public String maskedToken() {
        String token = normalizeOptional(serverToken);
        if (token.isBlank()) {
            return "";
        }
        if (token.length() <= 4) {
            return "****";
        }
        return "********" + token.substring(token.length() - 4);
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
