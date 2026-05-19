package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Immutable public snapshot for a player-scoped Nexori match lifecycle event.
 */
public record NexoriPlayerMatchLifecycleEvent(
    NexoriMatchLifecycleEvent match,
    UUID playerUuid,
    String playerName,
    String playerAssignmentId,
    String reason,
    long eventAtEpochMs
) {

    public NexoriPlayerMatchLifecycleEvent {
        if (match == null) {
            throw new IllegalArgumentException("Match lifecycle event cannot be null.");
        }
        if (playerUuid == null) {
            throw new IllegalArgumentException("Player UUID cannot be null.");
        }
        playerName = normalize(playerName);
        playerAssignmentId = normalize(playerAssignmentId);
        reason = normalize(reason);
        eventAtEpochMs = Math.max(0L, eventAtEpochMs);
    }

    @Nonnull
    private static String normalize(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
