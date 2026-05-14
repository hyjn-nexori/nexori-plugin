package io.github.hyjn.nexori.plugin.portal.logic;

import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Optional;

public final class PortalLocationMatcher {

    @Nonnull
    public static Optional<PortalInstanceDefinition> findNearest(
        @Nonnull Collection<PortalInstanceDefinition> portals,
        @Nonnull String worldName,
        int x,
        int y,
        int z,
        int horizontalRadius,
        int verticalRadius
    ) {
        String normalizedWorldName = worldName.trim().toLowerCase();
        PortalInstanceDefinition bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;

        for (PortalInstanceDefinition portal : portals) {
            if (!portal.worldName().equals(normalizedWorldName)) {
                continue;
            }

            int dx = Math.abs(portal.blockX() - x);
            int dy = Math.abs(portal.blockY() - y);
            int dz = Math.abs(portal.blockZ() - z);
            if (dx > horizontalRadius || dy > verticalRadius || dz > horizontalRadius) {
                continue;
            }

            int distance = dx + dy + dz;
            if (bestMatch == null || distance < bestDistance) {
                bestMatch = portal;
                bestDistance = distance;
            }
        }

        return Optional.ofNullable(bestMatch);
    }
}
