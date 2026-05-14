package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerReturnTarget;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionState;

import java.util.Map;
import java.util.UUID;

/**
 * Pure result for a prepared minigame launch context.
 */
public record MinigameLaunchContextBuildResult(
    String matchId,
    String contextJson,
    MatchSessionState matchSessionState,
    Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
    String assignmentType,
    Map<UUID, MinigameLaunchContextFactory.AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
    String reportingServerId
) {
}
