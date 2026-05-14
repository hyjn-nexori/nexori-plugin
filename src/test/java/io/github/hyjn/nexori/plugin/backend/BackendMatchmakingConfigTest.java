package io.github.hyjn.nexori.plugin.backend;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendMatchmakingConfigTest {

    // ── defaults() ────────────────────────────────────────────────────────────

    @Test
    void defaultsReturnsNonNull() {
        assertNotNull(BackendMatchmakingConfig.defaults());
    }

    @Test
    void defaultsHasCurrentSchemaVersion() {
        assertEquals(BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION, BackendMatchmakingConfig.defaults().schemaVersion());
    }

    @Test
    void defaultsSyncIntervalIs1000ms() {
        assertEquals(1_000L, BackendMatchmakingConfig.defaults().syncIntervalMs());
    }

    @Test
    void defaultsRequestTimeoutIs3000ms() {
        assertEquals(3_000L, BackendMatchmakingConfig.defaults().requestTimeoutMs());
    }

    @Test
    void defaultsResultRetryIntervalIs5000ms() {
        assertEquals(5_000L, BackendMatchmakingConfig.defaults().resultRetryIntervalMs());
    }

    @Test
    void defaultsMatchStateDebounceIs1000ms() {
        assertEquals(1_000L, BackendMatchmakingConfig.defaults().matchStateDebounceMs());
    }

    @Test
    void defaultsMatchStateRetryIntervalIs3000ms() {
        assertEquals(3_000L, BackendMatchmakingConfig.defaults().matchStateRetryIntervalMs());
    }

    @Test
    void defaultsMatchStateStaleAfterIs30000ms() {
        assertEquals(30_000L, BackendMatchmakingConfig.defaults().matchStateStaleAfterMs());
    }

    // ── normalized() ─────────────────────────────────────────────────────────

    @Test
    void normalizedSetsSchemaVersionToCurrent() {
        BackendMatchmakingConfig old = new BackendMatchmakingConfig(
            0, false, "http://example.com", "token", 1000L, "eu", 3000L, false, 5000L
        );
        assertEquals(BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION, old.normalized().schemaVersion());
    }

    @Test
    void normalizedPreservesBaseUrl() {
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            1, true, "http://example.com", "token", 1000L, "eu", 3000L, false, 5000L
        );
        assertTrue(config.normalized().baseUrl().contains("example.com"));
    }

    // ── maskedToken() ─────────────────────────────────────────────────────────

    @Test
    void maskedTokenForBlankServerTokenIsBlankAccordingToCurrentBehavior() {
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            1, false, "", "", 1000L, "", 3000L, false, 5000L
        );
        String masked = config.maskedToken();
        assertNotNull(masked);
        assertTrue(masked.isBlank(), "Blank serverToken should produce blank masked token: '" + masked + "'");
    }

    @Test
    void maskedTokenForLongTokenShowsLastFourChars() {
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            1, false, "", "abcdefgh1234", 1000L, "", 3000L, false, 5000L
        );
        String masked = config.maskedToken();
        assertTrue(masked.endsWith("1234"), "Expected masked token to end with last 4 chars: " + masked);
    }

    // ── URL methods ───────────────────────────────────────────────────────────

    @Test
    void syncUrlContainsBaseUrl() {
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            1, true, "http://example.com", "token", 1000L, "eu", 3000L, false, 5000L
        );
        assertTrue(config.syncUrl().contains("example.com"), "syncUrl should contain base URL: " + config.syncUrl());
    }

    @Test
    void resultsUrlContainsBaseUrl() {
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            1, true, "http://example.com", "token", 1000L, "eu", 3000L, true, 5000L
        );
        assertTrue(config.resultsUrl().contains("example.com"), "resultsUrl should contain base URL: " + config.resultsUrl());
    }
}
