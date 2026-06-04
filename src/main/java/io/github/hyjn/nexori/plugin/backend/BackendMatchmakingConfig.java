package io.github.hyjn.nexori.plugin.backend;

import javax.annotation.Nonnull;

public record BackendMatchmakingConfig(
    int schemaVersion,
    boolean syncEnabled,
    String baseUrl,
    String serverToken,
    long syncIntervalMs,
    String region,
    long requestTimeoutMs,
    boolean resultReportingEnabled,
    long resultRetryIntervalMs,
    boolean matchStateReportingEnabled,
    long matchStateDebounceMs,
    long matchStateMaxCoalesceWindowMs,
    long matchStateRetryIntervalMs,
    long matchStateStaleAfterMs
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final long DEFAULT_SYNC_INTERVAL_MS = 1000L;
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 3000L;
    private static final long DEFAULT_RESULT_RETRY_INTERVAL_MS = 5000L;
    public static final long DEFAULT_MATCH_STATE_DEBOUNCE_MS = 1000L;
    public static final long DEFAULT_MATCH_STATE_MAX_COALESCE_WINDOW_MS = 5000L;
    public static final long DEFAULT_MATCH_STATE_RETRY_INTERVAL_MS = 3000L;
    public static final long DEFAULT_MATCH_STATE_STALE_AFTER_MS = 30000L;

    public BackendMatchmakingConfig(
        int schemaVersion,
        boolean syncEnabled,
        String baseUrl,
        String serverToken,
        long syncIntervalMs,
        String region,
        long requestTimeoutMs,
        boolean resultReportingEnabled,
        long resultRetryIntervalMs
    ) {
        this(
            schemaVersion,
            syncEnabled,
            baseUrl,
            serverToken,
            syncIntervalMs,
            region,
            requestTimeoutMs,
            resultReportingEnabled,
            resultRetryIntervalMs,
            false,
            DEFAULT_MATCH_STATE_DEBOUNCE_MS,
            DEFAULT_MATCH_STATE_MAX_COALESCE_WINDOW_MS,
            DEFAULT_MATCH_STATE_RETRY_INTERVAL_MS,
            DEFAULT_MATCH_STATE_STALE_AFTER_MS
        );
    }

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
            DEFAULT_RESULT_RETRY_INTERVAL_MS,
            false,
            DEFAULT_MATCH_STATE_DEBOUNCE_MS,
            DEFAULT_MATCH_STATE_MAX_COALESCE_WINDOW_MS,
            DEFAULT_MATCH_STATE_RETRY_INTERVAL_MS,
            DEFAULT_MATCH_STATE_STALE_AFTER_MS
        );
    }

    @Nonnull
    public BackendMatchmakingConfig normalized() {
        long normalizedDebounceMs = Math.max(0L, matchStateDebounceMs);
        long normalizedMaxCoalesceWindowMs = matchStateMaxCoalesceWindowMs < normalizedDebounceMs
            ? DEFAULT_MATCH_STATE_MAX_COALESCE_WINDOW_MS
            : matchStateMaxCoalesceWindowMs;
        if (normalizedMaxCoalesceWindowMs < normalizedDebounceMs) {
            normalizedMaxCoalesceWindowMs = normalizedDebounceMs;
        }
        if (normalizedMaxCoalesceWindowMs <= 0L) {
            normalizedMaxCoalesceWindowMs = DEFAULT_MATCH_STATE_MAX_COALESCE_WINDOW_MS;
        }
        long normalizedRetryIntervalMs = matchStateRetryIntervalMs > 0L
            ? matchStateRetryIntervalMs
            : DEFAULT_MATCH_STATE_RETRY_INTERVAL_MS;
        long normalizedStaleAfterMs = matchStateStaleAfterMs > normalizedMaxCoalesceWindowMs
            ? matchStateStaleAfterMs
            : DEFAULT_MATCH_STATE_STALE_AFTER_MS;
        if (normalizedStaleAfterMs <= normalizedMaxCoalesceWindowMs) {
            normalizedStaleAfterMs = normalizedMaxCoalesceWindowMs + DEFAULT_MATCH_STATE_STALE_AFTER_MS;
        }
        return new BackendMatchmakingConfig(
            CURRENT_SCHEMA_VERSION,
            syncEnabled,
            normalizeOptional(baseUrl),
            normalizeOptional(serverToken),
            syncIntervalMs <= 0L ? DEFAULT_SYNC_INTERVAL_MS : syncIntervalMs,
            normalizeOptional(region),
            requestTimeoutMs <= 0L ? DEFAULT_REQUEST_TIMEOUT_MS : requestTimeoutMs,
            resultReportingEnabled,
            resultRetryIntervalMs <= 0L ? DEFAULT_RESULT_RETRY_INTERVAL_MS : resultRetryIntervalMs,
            matchStateReportingEnabled,
            normalizedDebounceMs,
            normalizedMaxCoalesceWindowMs,
            normalizedRetryIntervalMs,
            normalizedStaleAfterMs
        );
    }

    public boolean isUsable() {
        return isSyncUsable();
    }

    public boolean isSyncUsable() {
        BackendMatchmakingConfig normalized = normalized();
        return !normalized.syncEnabled() || (!normalized.baseUrl().isBlank() && !normalized.serverToken().isBlank());
    }

    public boolean isResultReportingUsable() {
        BackendMatchmakingConfig normalized = normalized();
        return !normalized.resultReportingEnabled()
            || (!normalized.baseUrl().isBlank() && !normalized.serverToken().isBlank());
    }

    public boolean isMatchStateReportingUsable() {
        BackendMatchmakingConfig normalized = normalized();
        return !normalized.matchStateReportingEnabled()
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
    public String matchStateUrl() {
        String normalizedBaseUrl = normalizeOptional(baseUrl);
        if (normalizedBaseUrl.endsWith("/")) {
            normalizedBaseUrl = normalizedBaseUrl.substring(0, normalizedBaseUrl.length() - 1);
        }
        return normalizedBaseUrl + "/nexori/matches/state";
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
