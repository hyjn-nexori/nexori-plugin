package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

public record NetworkLobbyDefinition(
    String lobbyConnectionAddress,
    String lobbyWorldName,
    String entryTargetId,
    String returnTargetId,
    long updatedAtEpochMillis
) {

    @Nonnull
    public NetworkLobbyDefinition normalized() {
        String normalizedConnectionAddress = normalizeRequired(
            lobbyConnectionAddress,
            "Lobby connection address cannot be blank."
        );
        String normalizedWorldName = normalizeRequired(
            lobbyWorldName,
            "Lobby world name cannot be blank."
        );
        String normalizedEntryTargetId = normalizeRequired(
            entryTargetId,
            "Lobby entry target id cannot be blank."
        );
        String normalizedReturnTargetId = normalizeOptional(returnTargetId, normalizedEntryTargetId);
        return new NetworkLobbyDefinition(
            normalizedConnectionAddress,
            normalizedWorldName,
            normalizedEntryTargetId,
            normalizedReturnTargetId,
            updatedAtEpochMillis <= 0L ? System.currentTimeMillis() : updatedAtEpochMillis
        );
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
}
