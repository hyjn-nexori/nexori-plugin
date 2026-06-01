package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies the Games form keeps the {@code initialPlacementWindowSeconds} value across re-renders
 * triggered by editing other fields. The in-progress per-player draft must win over the saved
 * arena value so toggles and validation errors do not reset it.
 */
final class InitialPlacementWindowDraftResolverTest {

    private static ArenaDefinition arenaWithWindow(int windowSeconds) {
        return new ArenaDefinition(
            "arena-1",
            "Arena One",
            "host:25565",
            "",
            "none",
            "skywars",
            windowSeconds,
            9999,
            true,
            AfkDetectionPolicy.defaults()
        ).normalized();
    }

    @Test
    void defaultWhenNoDraftAndCreatingNewGame() {
        assertEquals(
            Integer.toString(ArenaDefinition.DEFAULT_INITIAL_PLACEMENT_WINDOW_SECONDS),
            InitialPlacementWindowDraftResolver.resolveDisplayValue(null, null)
        );
    }

    @Test
    void usesEditingArenaValueWhenNoDraft() {
        assertEquals("45", InitialPlacementWindowDraftResolver.resolveDisplayValue(null, arenaWithWindow(45)));
    }

    @Test
    void draftValueSurvivesEditingOtherFields() {
        // The player typed 120 and then edited another field; the saved arena still reports 45 but
        // the in-progress draft must take precedence.
        assertEquals("120", InitialPlacementWindowDraftResolver.resolveDisplayValue("120", arenaWithWindow(45)));
    }

    @Test
    void draftValueSurvivesForNewGameToo() {
        assertEquals("90", InitialPlacementWindowDraftResolver.resolveDisplayValue("90", null));
    }
}
