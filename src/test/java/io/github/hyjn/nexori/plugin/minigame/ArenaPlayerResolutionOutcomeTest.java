package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ArenaPlayerResolutionOutcomeTest {

    // ── enum values ───────────────────────────────────────────────────────────

    @Test
    void enumHasThreeValues() {
        assertEquals(3, ArenaPlayerResolutionOutcome.values().length);
    }

    // ── parse ─────────────────────────────────────────────────────────────────

    @Test
    void parseFindsWinExact() {
        assertEquals(ArenaPlayerResolutionOutcome.WIN, ArenaPlayerResolutionOutcome.parse("WIN"));
    }

    @Test
    void parseFindsLossExact() {
        assertEquals(ArenaPlayerResolutionOutcome.LOSS, ArenaPlayerResolutionOutcome.parse("LOSS"));
    }

    @Test
    void parseFindsDisconnectedExact() {
        assertEquals(ArenaPlayerResolutionOutcome.DISCONNECTED, ArenaPlayerResolutionOutcome.parse("DISCONNECTED"));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(ArenaPlayerResolutionOutcome.WIN, ArenaPlayerResolutionOutcome.parse("win"));
        assertEquals(ArenaPlayerResolutionOutcome.LOSS, ArenaPlayerResolutionOutcome.parse("Loss"));
        assertEquals(ArenaPlayerResolutionOutcome.DISCONNECTED, ArenaPlayerResolutionOutcome.parse("disconnected"));
    }

    @Test
    void parseTrimsWhitespace() {
        assertEquals(ArenaPlayerResolutionOutcome.WIN, ArenaPlayerResolutionOutcome.parse("  WIN  "));
    }

    @Test
    void parseBlankThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class, () -> ArenaPlayerResolutionOutcome.parse("  "),
            "Blank input: normalized is blank, throws IllegalArgumentException before valueOf");
    }

    @Test
    void parseNullThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class, () -> ArenaPlayerResolutionOutcome.parse(null),
            "null rawValue → normalized = '' (isBlank check) → throws IllegalArgumentException");
    }

    @Test
    void parseUnknownValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> ArenaPlayerResolutionOutcome.parse("UNKNOWN"),
            "Unknown value propagates as IllegalArgumentException from valueOf");
    }
}
