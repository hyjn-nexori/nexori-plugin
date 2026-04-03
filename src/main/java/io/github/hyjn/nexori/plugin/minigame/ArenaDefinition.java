package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

public record ArenaDefinition(
    String arenaId,
    String displayName,
    String destinationConnectionAddress,
    String destinationTargetId,
    String matchResolutionTriggerId,
    int maxSupportedPlayers,
    boolean enabled
) {

    public static final String NO_MATCH_RESOLUTION_TRIGGER_ID = "none";

    @Nonnull
    public ArenaDefinition normalized() {
        String normalizedArenaId = normalizeRequired(arenaId, "Arena id cannot be blank.");
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedArenaId);
        String normalizedDestinationAddress = normalizeOptional(destinationConnectionAddress, "");
        String normalizedTargetId = normalizeOptional(destinationTargetId, "");
        return new ArenaDefinition(
            normalizedArenaId,
            normalizedDisplayName,
            normalizedDestinationAddress,
            normalizedTargetId,
            normalizeTriggerId(matchResolutionTriggerId),
            maxSupportedPlayers,
            enabled
        );
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        return normalizeRequired(rawId, "Arena id cannot be blank.");
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static String normalizeDisplayName(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static String normalizeTriggerId(String rawValue) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            return NO_MATCH_RESOLUTION_TRIGGER_ID;
        }
        return normalized;
    }
}
