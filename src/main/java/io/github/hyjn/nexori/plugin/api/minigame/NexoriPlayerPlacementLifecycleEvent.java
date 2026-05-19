package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;

/**
 * Immutable public snapshot for an individual player placement lifecycle event.
 */
public record NexoriPlayerPlacementLifecycleEvent(
    NexoriPlayerMatchLifecycleEvent player,
    NexoriPlayerPlacementOutcome placementOutcome,
    NexoriMatchPlacementState placementState,
    String worldName,
    String instanceTemplateId,
    long eventAtEpochMs
) {

    public NexoriPlayerPlacementLifecycleEvent {
        if (player == null) {
            throw new IllegalArgumentException("Player lifecycle event cannot be null.");
        }
        if (placementOutcome == null) {
            throw new IllegalArgumentException("Placement outcome cannot be null.");
        }
        if (placementState == null) {
            throw new IllegalArgumentException("Placement state cannot be null.");
        }
        worldName = normalize(worldName);
        instanceTemplateId = normalize(instanceTemplateId);
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
