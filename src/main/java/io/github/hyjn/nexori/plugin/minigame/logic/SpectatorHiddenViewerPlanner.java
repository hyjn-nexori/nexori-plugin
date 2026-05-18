package io.github.hyjn.nexori.plugin.minigame.logic;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public final class SpectatorHiddenViewerPlanner {

    @Nonnull
    public SpectatorHiddenViewerPlan plan(
        @Nonnull UUID spectatorUuid,
        @Nonnull Collection<UUID> currentHiddenViewers,
        @Nonnull Collection<UUID> requestedViewerUuids
    ) {
        Set<UUID> desiredHiddenViewers = normalizeDesiredViewers(spectatorUuid, requestedViewerUuids);

        Set<UUID> viewersToHide = new LinkedHashSet<>(desiredHiddenViewers);
        viewersToHide.removeAll(currentHiddenViewers);

        Set<UUID> viewersToShow = new LinkedHashSet<>(currentHiddenViewers);
        viewersToShow.removeAll(desiredHiddenViewers);

        return new SpectatorHiddenViewerPlan(desiredHiddenViewers, viewersToHide, viewersToShow);
    }

    @Nonnull
    public Set<UUID> normalizeDesiredViewers(@Nonnull UUID spectatorUuid, @Nonnull Collection<UUID> requestedViewerUuids) {
        Set<UUID> desiredHiddenViewers = new LinkedHashSet<>();
        for (UUID viewerUuid : requestedViewerUuids) {
            if (viewerUuid != null && !viewerUuid.equals(spectatorUuid)) {
                desiredHiddenViewers.add(viewerUuid);
            }
        }
        return desiredHiddenViewers;
    }
}
