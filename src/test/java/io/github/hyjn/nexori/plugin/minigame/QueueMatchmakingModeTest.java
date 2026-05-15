package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueueMatchmakingModeTest {

    // ── enum values ───────────────────────────────────────────────────────────

    @Test
    void enumHasTwoValues() {
        assertEquals(2, QueueMatchmakingMode.values().length);
    }

    @Test
    void idReturnsName() {
        assertEquals("LOCAL_FIFO", QueueMatchmakingMode.LOCAL_FIFO.id());
        assertEquals("BACKEND_DRIVEN", QueueMatchmakingMode.BACKEND_DRIVEN.id());
    }

    // ── defaultMode ───────────────────────────────────────────────────────────

    @Test
    void defaultModeIsLocalFifo() {
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, QueueMatchmakingMode.defaultMode());
    }

    // ── tryParse ──────────────────────────────────────────────────────────────

    @Test
    void tryParseFindsLocalFifoExact() {
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, QueueMatchmakingMode.tryParse("LOCAL_FIFO").get());
    }

    @Test
    void tryParseFindsBackendDriven() {
        assertEquals(QueueMatchmakingMode.BACKEND_DRIVEN, QueueMatchmakingMode.tryParse("BACKEND_DRIVEN").get());
    }

    @Test
    void tryParseIsCaseInsensitive() {
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, QueueMatchmakingMode.tryParse("local_fifo").get());
        assertEquals(QueueMatchmakingMode.BACKEND_DRIVEN, QueueMatchmakingMode.tryParse("backend_driven").get());
    }

    @Test
    void tryParseReplacesHyphensWithUnderscores() {
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, QueueMatchmakingMode.tryParse("local-fifo").get());
        assertEquals(QueueMatchmakingMode.BACKEND_DRIVEN, QueueMatchmakingMode.tryParse("backend-driven").get());
    }

    @Test
    void tryParseReplacesSpacesWithUnderscores() {
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, QueueMatchmakingMode.tryParse("local fifo").get());
    }

    @Test
    void tryParseTrimsWhitespace() {
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, QueueMatchmakingMode.tryParse("  LOCAL_FIFO  ").get());
    }

    @Test
    void tryParseNullReturnsEmpty() {
        assertTrue(QueueMatchmakingMode.tryParse(null).isEmpty());
    }

    @Test
    void tryParseBlankReturnsEmpty() {
        assertTrue(QueueMatchmakingMode.tryParse("  ").isEmpty());
    }

    @Test
    void tryParseUnknownReturnsEmpty() {
        assertFalse(QueueMatchmakingMode.tryParse("UNKNOWN_MODE").isPresent());
    }
}
