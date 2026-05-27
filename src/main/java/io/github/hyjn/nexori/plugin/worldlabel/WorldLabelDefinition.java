package io.github.hyjn.nexori.plugin.worldlabel;

import org.joml.Vector3d;

import javax.annotation.Nonnull;

public record WorldLabelDefinition(
    @Nonnull String labelId,
    @Nonnull String worldName,
    double x,
    double y,
    double z,
    @Nonnull String text
) {

    @Nonnull
    public WorldLabelDefinition normalized() {
        String normalizedId = labelId == null ? "" : labelId.trim().toLowerCase();
        String normalizedWorldName = worldName == null ? "" : worldName.trim().toLowerCase();
        String normalizedText = text == null ? "" : text.trim();
        return new WorldLabelDefinition(
            normalizedId,
            normalizedWorldName,
            x,
            y,
            z,
            normalizedText
        );
    }

    @Nonnull
    public Vector3d position() {
        return new Vector3d(x, y, z);
    }
}
