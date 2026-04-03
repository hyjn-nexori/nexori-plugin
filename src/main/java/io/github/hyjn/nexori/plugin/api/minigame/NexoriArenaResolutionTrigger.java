package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;

/**
 * Public extension point for external mods that want Nexori to keep handling launch/return while custom code decides
 * when a player wins or loses inside an arena.
 */
public interface NexoriArenaResolutionTrigger {

    /**
     * Stable identifier stored in {@code ArenaDefinition.matchResolutionTriggerId}.
     */
    @Nonnull
    String id();

    /**
     * Called from Nexori's arena tick flow on the world thread.
     * Implementations may inspect the match view and optionally mark one or more players as winners or losers.
     */
    void evaluate(@Nonnull NexoriArenaResolutionContext context);
}
