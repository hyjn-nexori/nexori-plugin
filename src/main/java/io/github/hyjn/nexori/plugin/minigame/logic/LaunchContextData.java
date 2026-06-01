package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerReturnTarget;
import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;

import java.util.List;
import java.util.UUID;

/**
 * Normalized data parsed from a minigame launch travel context.
 */
public record LaunchContextData(
    String matchId,
    String queueId,
    String arenaId,
    String originLobbyId,
    String returnConnectionAddress,
    String returnFallbackTargetId,
    String launchTravelProfileId,
    String instanceTemplateId,
    String rulesEngineId,
    String assignmentId,
    String assignmentType,
    String externalMatchId,
    String matchSource,
    int admissionPolicySchemaVersion,
    int admissionCapacity,
    boolean backfillEnabled,
    String backfillMode,
    int backfillWindowSeconds,
    int initialPlacementWindowSeconds,
    int minimumInitialPlayers,
    AfkDetectionPolicy afkDetectionPolicy,
    List<UUID> expectedPlayerUuids,
    int expectedPlayerCount,
    UUID playerUuid,
    String admissionReservationId,
    long admissionExpiresAtEpochMs,
    String reportingServerId,
    ArenaPlayerReturnTarget playerReturnTarget
) {

    public boolean usesInstanceTemplate() {
        return !instanceTemplateId.isBlank()
            && !ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId);
    }
}
