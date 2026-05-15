package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueueBackfillModeTest {

    // ── enum values ───────────────────────────────────────────────────────────

    @Test
    void enumHasThreeValues() {
        assertEquals(3, QueueBackfillMode.values().length);
    }

    @Test
    void idsAreStable() {
        assertEquals("NONE", QueueBackfillMode.NONE.id());
        assertEquals("PLACEMENT_ONLY", QueueBackfillMode.PLACEMENT_ONLY.id());
        assertEquals("ACTIVE_WINDOW", QueueBackfillMode.ACTIVE_WINDOW.id());
    }

    // ── defaultMode ───────────────────────────────────────────────────────────

    @Test
    void defaultModeIsNone() {
        assertEquals(QueueBackfillMode.NONE, QueueBackfillMode.defaultMode());
    }

    // ── tryParse ──────────────────────────────────────────────────────────────

    @Test
    void tryParseFindsNoneExact() {
        assertEquals(QueueBackfillMode.NONE, QueueBackfillMode.tryParse("NONE").get());
    }

    @Test
    void tryParseFindsPlacementOnly() {
        assertEquals(QueueBackfillMode.PLACEMENT_ONLY, QueueBackfillMode.tryParse("PLACEMENT_ONLY").get());
    }

    @Test
    void tryParseFindsActiveWindow() {
        assertEquals(QueueBackfillMode.ACTIVE_WINDOW, QueueBackfillMode.tryParse("ACTIVE_WINDOW").get());
    }

    @Test
    void tryParseIsCaseInsensitive() {
        assertEquals(QueueBackfillMode.NONE, QueueBackfillMode.tryParse("none").get());
        assertEquals(QueueBackfillMode.PLACEMENT_ONLY, QueueBackfillMode.tryParse("placement_only").get());
    }

    @Test
    void tryParseTrimsWhitespace() {
        assertEquals(QueueBackfillMode.ACTIVE_WINDOW, QueueBackfillMode.tryParse("  ACTIVE_WINDOW  ").get());
    }

    @Test
    void tryParseNullReturnsEmpty() {
        assertTrue(QueueBackfillMode.tryParse(null).isEmpty());
    }

    @Test
    void tryParseBlankReturnsEmpty() {
        assertTrue(QueueBackfillMode.tryParse("  ").isEmpty());
    }

    @Test
    void tryParseUnknownReturnsEmpty() {
        assertFalse(QueueBackfillMode.tryParse("UNKNOWN_MODE").isPresent());
    }
}
