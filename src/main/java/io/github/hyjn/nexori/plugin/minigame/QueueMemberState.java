package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

public record QueueMemberState(
    UUID playerUuid,
    String playerNameSnapshot,
    String sourceLobbyId,
    String sourcePortalId,
    long joinedAtEpochMs
) {

    @Nonnull
    public QueueMemberState normalized() {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Queue member player UUID cannot be null.");
        }
        return new QueueMemberState(
            playerUuid,
            normalizePlayerName(playerNameSnapshot, playerUuid.toString()),
            normalizeRequiredLower(sourceLobbyId, "Queue member source context id cannot be blank."),
            normalizeOptionalLower(sourcePortalId),
            joinedAtEpochMs <= 0 ? System.currentTimeMillis() : joinedAtEpochMs
        );
    }

    @Nonnull
    private static String normalizeRequiredLower(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptionalLower(rawValue);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizePlayerName(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static String normalizeOptionalLower(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "" : normalized;
    }
}
