package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SourceContextIdTest {

    // ── normalizeId ───────────────────────────────────────────────────────────

    @Test
    void normalizeIdTrimsAndLowercases() {
        assertEquals("lobby-1", SourceContextId.normalizeId("  Lobby-1  "));
    }

    @Test
    void normalizeIdAlreadyNormalizedPassesThrough() {
        assertEquals("lobby-server", SourceContextId.normalizeId("lobby-server"));
    }

    @Test
    void normalizeIdBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> SourceContextId.normalizeId("   "));
    }

    @Test
    void normalizeIdNullThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class, () -> SourceContextId.normalizeId(null),
            "null rawId → normalized = '' → isBlank → throws IllegalArgumentException");
    }

    @Test
    void normalizeIdUppercaseIsLowercased() {
        assertEquals("lobby-alpha", SourceContextId.normalizeId("LOBBY-ALPHA"));
    }
}
