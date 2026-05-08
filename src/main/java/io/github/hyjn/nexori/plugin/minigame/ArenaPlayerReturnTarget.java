package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;

public record ArenaPlayerReturnTarget(
    String originLobbyId,
    String returnConnectionAddress,
    String returnFallbackTargetId,
    String launchTravelProfileId
) {

    @Nonnull
    public ArenaPlayerReturnTarget normalized() {
        return new ArenaPlayerReturnTarget(
            SourceContextId.normalizeId(originLobbyId),
            normalizeRequired(returnConnectionAddress, "Arena player return connection address cannot be blank."),
            normalizeRequired(returnFallbackTargetId, "Arena player return fallback target id cannot be blank."),
            normalizeRequired(launchTravelProfileId, "Arena player return travel profile id cannot be blank.")
        );
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = rawValue == null ? "" : rawValue.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
