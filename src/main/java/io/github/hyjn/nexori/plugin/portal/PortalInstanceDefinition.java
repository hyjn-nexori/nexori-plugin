package io.github.hyjn.nexori.plugin.portal;

import com.hypixel.hytale.math.vector.Vector3i;

import javax.annotation.Nonnull;
import java.util.Locale;

public record PortalInstanceDefinition(
    String portalId,
    String displayName,
    String worldName,
    int blockX,
    int blockY,
    int blockZ,
    String autoDestinationTargetId,
    boolean enabled,
    long createdAtEpochMillis,
    long updatedAtEpochMillis
) {

    @Nonnull
    public PortalInstanceDefinition normalized() {
        long now = System.currentTimeMillis();
        long normalizedCreatedAt = createdAtEpochMillis <= 0 ? now : createdAtEpochMillis;
        long normalizedUpdatedAt = updatedAtEpochMillis <= 0 ? normalizedCreatedAt : updatedAtEpochMillis;
        String normalizedWorldName = normalizeOptional(worldName, "default");
        String normalizedPortalId = normalizeOptional(portalId, locationKey(normalizedWorldName, blockX, blockY, blockZ));
        String normalizedDisplayName = normalizeOptional(
            displayName,
            "Portal @ " + normalizedWorldName + " (" + blockX + ", " + blockY + ", " + blockZ + ")"
        );
        return new PortalInstanceDefinition(
            normalizedPortalId,
            normalizedDisplayName,
            normalizedWorldName,
            blockX,
            blockY,
            blockZ,
            normalizeOptional(autoDestinationTargetId, ""),
            enabled,
            normalizedCreatedAt,
            normalizedUpdatedAt
        );
    }

    @Nonnull
    public Vector3i blockPosition() {
        return new Vector3i(blockX, blockY, blockZ);
    }

    @Nonnull
    public String locationKey() {
        return locationKey(worldName, blockX, blockY, blockZ);
    }

    @Nonnull
    public static String locationKey(@Nonnull String worldName, int blockX, int blockY, int blockZ) {
        return normalizeOptional(worldName, "default") + ":" + blockX + ":" + blockY + ":" + blockZ;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? defaultValue : normalized;
    }
}
