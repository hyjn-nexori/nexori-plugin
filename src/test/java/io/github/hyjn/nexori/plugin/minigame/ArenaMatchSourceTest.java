package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArenaMatchSourceTest {

    // ── enum values ───────────────────────────────────────────────────────────

    @Test
    void enumHasTwoValues() {
        assertEquals(2, ArenaMatchSource.values().length);
    }

    @Test
    void idsAreStable() {
        assertEquals("LOCAL_FIFO", ArenaMatchSource.LOCAL_FIFO.id());
        assertEquals("BACKEND_DRIVEN", ArenaMatchSource.BACKEND_DRIVEN.id());
    }

    // ── defaultSource ─────────────────────────────────────────────────────────

    @Test
    void defaultSourceIsLocalFifo() {
        assertEquals(ArenaMatchSource.LOCAL_FIFO, ArenaMatchSource.defaultSource());
    }

    // ── tryParse ──────────────────────────────────────────────────────────────

    @Test
    void tryParseFindsLocalFifoExact() {
        assertTrue(ArenaMatchSource.tryParse("LOCAL_FIFO").isPresent());
        assertEquals(ArenaMatchSource.LOCAL_FIFO, ArenaMatchSource.tryParse("LOCAL_FIFO").get());
    }

    @Test
    void tryParseFindsBackendDrivenExact() {
        assertEquals(ArenaMatchSource.BACKEND_DRIVEN, ArenaMatchSource.tryParse("BACKEND_DRIVEN").get());
    }

    @Test
    void tryParseIsCaseInsensitive() {
        assertEquals(ArenaMatchSource.LOCAL_FIFO, ArenaMatchSource.tryParse("local_fifo").get());
        assertEquals(ArenaMatchSource.BACKEND_DRIVEN, ArenaMatchSource.tryParse("backend_driven").get());
    }

    @Test
    void tryParseTrimsWhitespace() {
        assertEquals(ArenaMatchSource.LOCAL_FIFO, ArenaMatchSource.tryParse("  LOCAL_FIFO  ").get());
    }

    @Test
    void tryParseNullReturnsEmpty() {
        assertTrue(ArenaMatchSource.tryParse(null).isEmpty());
    }

    @Test
    void tryParseBlankReturnsEmpty() {
        assertTrue(ArenaMatchSource.tryParse("  ").isEmpty());
    }

    @Test
    void tryParseUnknownReturnsEmpty() {
        assertFalse(ArenaMatchSource.tryParse("UNKNOWN_SOURCE").isPresent());
    }
}
