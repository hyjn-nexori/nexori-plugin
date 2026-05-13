package io.github.hyjn.nexori.plugin.ui.menu.state;

import javax.annotation.Nonnull;

/**
 * Temporary backend configuration draft preserved across menu rerenders for one player.
 */
public record BackendConfigDraft(
    Boolean enabled,
    Boolean resultReportingEnabled,
    Boolean matchStateReportingEnabled,
    String baseUrl,
    String serverToken,
    String syncIntervalMs,
    String region,
    String requestTimeoutMs,
    String resultRetryIntervalMs,
    String matchStateDebounceMs,
    String matchStateMaxCoalesceWindowMs,
    String matchStateRetryIntervalMs,
    String matchStateStaleAfterMs
) {

    public BackendConfigDraft {
        baseUrl = normalizeOptional(baseUrl);
        serverToken = normalizeOptional(serverToken);
        syncIntervalMs = normalizeOptional(syncIntervalMs);
        region = normalizeOptional(region);
        requestTimeoutMs = normalizeOptional(requestTimeoutMs);
        resultRetryIntervalMs = normalizeOptional(resultRetryIntervalMs);
        matchStateDebounceMs = normalizeOptional(matchStateDebounceMs);
        matchStateMaxCoalesceWindowMs = normalizeOptional(matchStateMaxCoalesceWindowMs);
        matchStateRetryIntervalMs = normalizeOptional(matchStateRetryIntervalMs);
        matchStateStaleAfterMs = normalizeOptional(matchStateStaleAfterMs);
    }

    public BackendConfigDraft(
        Boolean enabled,
        String baseUrl,
        String serverToken,
        String syncIntervalMs,
        String region,
        String requestTimeoutMs
    ) {
        this(enabled, null, null, baseUrl, serverToken, syncIntervalMs, region, requestTimeoutMs, null, null, null, null, null);
    }

    @Nonnull
    private static String normalizeOptional(String value) {
        return value == null ? "" : value.trim();
    }
}
