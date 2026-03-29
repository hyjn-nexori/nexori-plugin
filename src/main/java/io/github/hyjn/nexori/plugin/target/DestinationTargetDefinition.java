package io.github.hyjn.nexori.plugin.target;

import javax.annotation.Nonnull;
import java.util.Locale;

public record DestinationTargetDefinition(
    String id,
    String displayName,
    DestinationTargetKind kind,
    String worldName,
    String arrivalPointId,
    String arrivalMessage,
    String metadataJson
) {

    @Nonnull
    public DestinationTargetDefinition normalized() {
        String normalizedId = normalizeId(id);
        DestinationTargetKind normalizedKind = kind == null ? DestinationTargetKind.NATURAL_SPAWN : kind;
        String normalizedDisplayName = normalizeOptional(displayName, normalizedId);
        String normalizedWorldName = normalizeOptional(worldName, "default");
        String normalizedArrivalPointId = normalizeOptional(arrivalPointId, "");
        String normalizedArrivalMessage = normalizeOptional(arrivalMessage, "");
        String normalizedMetadataJson = normalizeOptional(metadataJson, "{}");
        return new DestinationTargetDefinition(
            normalizedId,
            normalizedDisplayName,
            normalizedKind,
            normalizedWorldName,
            normalizedArrivalPointId,
            normalizedArrivalMessage,
            normalizedMetadataJson
        );
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        String normalized = rawId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Destination target id cannot be blank.");
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }
}
