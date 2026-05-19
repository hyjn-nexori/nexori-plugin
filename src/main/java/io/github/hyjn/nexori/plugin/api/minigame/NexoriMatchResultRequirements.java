package io.github.hyjn.nexori.plugin.api.minigame;

import java.util.List;
import java.util.UUID;

/**
 * Player sets a rules mod can use to store outcomes before submitting a final match result.
 */
public record NexoriMatchResultRequirements(
    String matchId,
    String queueId,
    String arenaId,
    List<UUID> requiredPlayerUuids,
    List<UUID> expectedPlayerUuids,
    List<UUID> arrivedPlayerUuids,
    List<UUID> activePlayerUuids,
    List<UUID> eliminatedPlayerUuids
) {
}
