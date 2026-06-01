package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;

/**
 * Resolves the value shown in the Games form's initial placement window input.
 *
 * <p>An in-progress per-player draft (the raw text the player last typed) takes precedence so the
 * value survives re-renders triggered by editing other fields, toggles, or validation errors. When
 * there is no draft the value falls back to the saved arena (normalised) or the default for a brand
 * new game.</p>
 */
public final class InitialPlacementWindowDraftResolver {

    private InitialPlacementWindowDraftResolver() {
    }

    public static String resolveDisplayValue(String draftValue, ArenaDefinition editing) {
        if (draftValue != null) {
            return draftValue;
        }
        return Integer.toString(editing == null
            ? ArenaDefinition.DEFAULT_INITIAL_PLACEMENT_WINDOW_SECONDS
            : ArenaDefinition.normalizeInitialPlacementWindowSeconds(editing.initialPlacementWindowSeconds()));
    }
}
