package io.github.hyjn.nexori.plugin.minigame.logic;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record SpectatorHiddenViewerPlan(
    @Nonnull Set<UUID> desiredHiddenViewers,
    @Nonnull Set<UUID> viewersToHide,
    @Nonnull Set<UUID> viewersToShow
) {
    public SpectatorHiddenViewerPlan {
        desiredHiddenViewers = immutableOrderedSet(desiredHiddenViewers);
        viewersToHide = immutableOrderedSet(viewersToHide);
        viewersToShow = immutableOrderedSet(viewersToShow);
    }

    @Nonnull
    private static Set<UUID> immutableOrderedSet(@Nonnull Set<UUID> uuids) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(uuids));
    }
}
