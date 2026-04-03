package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;

public interface ArenaMatchResolutionTrigger {

    @Nonnull
    String id();

    @Nonnull
    ArenaActiveMatch evaluate(@Nonnull ArenaMatchService arenaMatchService, @Nonnull ArenaActiveMatch match, long nowEpochMs);
}
