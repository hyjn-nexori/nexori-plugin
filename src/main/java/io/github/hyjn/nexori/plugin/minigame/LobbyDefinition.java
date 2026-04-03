package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

public record LobbyDefinition(
    String lobbyId,
    String displayName,
    String worldName,
    String entryTargetId,
    String returnTargetId,
    boolean enabled
) {

    @Nonnull
    public LobbyDefinition normalized() {
        String normalizedLobbyId = normalizeRequired(lobbyId, "Lobby id cannot be blank.");
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedLobbyId);
        String normalizedWorldName = normalizeRequired(worldName, "Lobby world name cannot be blank.");
        String normalizedEntryTargetId = normalizeRequired(entryTargetId, "Lobby entry target id cannot be blank.");
        String normalizedReturnTargetId = normalizeOptional(returnTargetId, normalizedEntryTargetId);
        return new LobbyDefinition(
            normalizedLobbyId,
            normalizedDisplayName,
            normalizedWorldName,
            normalizedEntryTargetId,
            normalizedReturnTargetId,
            enabled
        );
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        return normalizeRequired(rawId, "Lobby id cannot be blank.");
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
}
