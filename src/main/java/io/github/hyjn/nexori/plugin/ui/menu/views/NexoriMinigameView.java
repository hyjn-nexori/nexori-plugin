package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuSetupState;
import io.github.hyjn.nexori.plugin.ui.menu.views.minigame.NexoriMinigameSections;

import javax.annotation.Nonnull;

/**
 * Renders the minigame workspace for arenas, queues, spawn slots, backfill
 * policy controls, and catalog sync UI.
 * <p>
 * Minigame-specific UI belongs in this view instead of {@code NexoriMenuV2Page}.
 * Reusable controls should be promoted to {@code ui.menu.components}.
 */
public final class NexoriMinigameView {

    private NexoriMinigameView() {
    }

    @Nonnull
    public static GroupBuilder tabs(@Nonnull NexoriMenuRenderContext context) {
        return NexoriMinigameSections.minigameTabs(
            context.ref(),
            context.store(),
            context.playerRef(),
            context.player(),
            context.plugin(),
            context.state()
        );
    }

    @Nonnull
    public static ReorderableListBuilder render(
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        return NexoriMinigameSections.buildMinigameWorkspaceScroll(
            context.ref(),
            context.store(),
            context.playerRef(),
            context.player(),
            context.plugin(),
            context.state(),
            context.peers(),
            setup,
            viewportHeight,
            scrollId
        );
    }
}
