package io.github.hyjn.nexori.plugin.portal;

import io.github.hyjn.nexori.plugin.worldlabel.WorldLabelDefinition;
import io.github.hyjn.nexori.plugin.worldlabel.WorldLabelSource;

import javax.annotation.Nonnull;
import java.util.List;

public final class PortalWorldLabelSource implements WorldLabelSource {

    private static final double LABEL_Y_OFFSET = 2.25;

    private final PortalInstanceService portalInstanceService;

    public PortalWorldLabelSource(@Nonnull PortalInstanceService portalInstanceService) {
        this.portalInstanceService = portalInstanceService;
    }

    @Override
    @Nonnull
    public List<WorldLabelDefinition> listForWorld(@Nonnull String worldName) {
        String normalizedWorldName = worldName == null ? "" : worldName.trim().toLowerCase();
        return portalInstanceService.list().stream()
            .filter(portal -> portal.worldName().equals(normalizedWorldName))
            .filter(portal -> !portal.displayName().isBlank())
            .map(portal -> new WorldLabelDefinition(
                "portal:" + portal.portalId(),
                portal.worldName(),
                portal.blockX() + 0.5,
                portal.blockY() + LABEL_Y_OFFSET,
                portal.blockZ() + 0.5,
                portal.displayName()
            ))
            .toList();
    }
}
