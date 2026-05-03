package io.github.hyjn.nexori.plugin.api.minigame;

import java.util.List;
import java.util.UUID;

/**
 * Public runtime snapshot for one active Nexori match.
 */
public record NexoriActiveMatchInfo(
    String matchId,
    String queueId,
    String arenaId,
    String assignmentId,
    String externalMatchId,
    String rulesEngineId,
    String matchResolutionTriggerId,
    List<UUID> expectedPlayerUuids,
    List<UUID> arrivedPlayerUuids,
    List<UUID> activePlayerUuids,
    List<UUID> eliminatedPlayerUuids,
    List<UUID> spectatorPlayerUuids,
    List<UUID> requiredResultPlayerUuids,
    List<NexoriPlayerOutcomeState> playerOutcomes,
    int expectedPlayerCount,
    long completedAtEpochMs,
    long resultSubmittedAtEpochMs
) {
}
