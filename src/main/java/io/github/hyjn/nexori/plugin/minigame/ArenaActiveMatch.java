package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record ArenaActiveMatch(
    String matchId,
    String queueId,
    String arenaId,
    String originLobbyId,
    String returnConnectionAddress,
    String returnFallbackTargetId,
    String launchTravelProfileId,
    String instanceTemplateId,
    String instanceWorldName,
    String rulesEngineId,
    String assignmentId,
    String createdByAssignmentType,
    String externalMatchId,
    String matchSource,
    int admissionPolicySchemaVersion,
    int admissionCapacity,
    boolean backfillEnabled,
    String backfillMode,
    int backfillWindowSeconds,
    AfkDetectionPolicy afkDetectionPolicy,
    List<UUID> expectedPlayerUuids,
    int expectedPlayerCount,
    List<UUID> arrivedPlayerUuids,
    List<UUID> activePlayerUuids,
    List<UUID> eliminatedPlayerUuids,
    List<UUID> spectatorPlayerUuids,
    Map<UUID, String> assignmentIdsByPlayerUuid,
    Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
    Map<UUID, ArenaPlayerOutcomeState> playerOutcomeByUuid,
    Map<UUID, Long> pendingReturnAtEpochMsByPlayerUuid,
    int consumedBackfillAdmissionCount,
    Set<String> acceptedBackfillReservationIds,
    boolean explicitAdmissionClosed,
    String explicitAdmissionCloseReason,
    String explicitAdmissionCloseMessage,
    long explicitAdmissionClosedAtEpochMs,
    String winnerPlayerUuid,
    int minimumInitialPlayers,
    long initialPlacementWindowStartedAtEpochMs,
    long initialPlacementWindowExpiresAtEpochMs,
    long initialPlacementWindowClosedAtEpochMs,
    String initialPlacementWindowCloseReason,
    long startGateOpenedAtEpochMs,
    String startGateOpenReason,
    long placementCompletedAtEpochMs,
    long matchStartedAtEpochMs,
    long completedAtEpochMs,
    long resultSubmittedAtEpochMs,
    String resultPayloadHash,
    long createdAtEpochMs,
    long lastUpdatedAtEpochMs,
    String lastError
) {

    public ArenaActiveMatch(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId,
        String instanceTemplateId,
        String instanceWorldName,
        String rulesEngineId,
        String assignmentId,
        String createdByAssignmentType,
        String externalMatchId,
        String matchSource,
        int admissionPolicySchemaVersion,
        int admissionCapacity,
        boolean backfillEnabled,
        String backfillMode,
        int backfillWindowSeconds,
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids,
        List<UUID> spectatorPlayerUuids,
        Map<UUID, String> assignmentIdsByPlayerUuid,
        Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        Map<UUID, ArenaPlayerOutcomeState> playerOutcomeByUuid,
        Map<UUID, Long> pendingReturnAtEpochMsByPlayerUuid,
        int consumedBackfillAdmissionCount,
        Set<String> acceptedBackfillReservationIds,
        boolean explicitAdmissionClosed,
        String explicitAdmissionCloseReason,
        String explicitAdmissionCloseMessage,
        long explicitAdmissionClosedAtEpochMs,
        String winnerPlayerUuid,
        long placementCompletedAtEpochMs,
        long matchStartedAtEpochMs,
        long completedAtEpochMs,
        long resultSubmittedAtEpochMs,
        String resultPayloadHash,
        long createdAtEpochMs,
        long lastUpdatedAtEpochMs,
        String lastError
    ) {
        this(
            matchId,
            queueId,
            arenaId,
            originLobbyId,
            returnConnectionAddress,
            returnFallbackTargetId,
            launchTravelProfileId,
            instanceTemplateId,
            instanceWorldName,
            rulesEngineId,
            assignmentId,
            createdByAssignmentType,
            externalMatchId,
            matchSource,
            admissionPolicySchemaVersion,
            admissionCapacity,
            backfillEnabled,
            backfillMode,
            backfillWindowSeconds,
            AfkDetectionPolicy.defaults(),
            expectedPlayerUuids,
            expectedPlayerCount,
            arrivedPlayerUuids,
            activePlayerUuids,
            eliminatedPlayerUuids,
            spectatorPlayerUuids,
            assignmentIdsByPlayerUuid,
            playerReturnTargetsByUuid,
            playerOutcomeByUuid,
            pendingReturnAtEpochMsByPlayerUuid,
            consumedBackfillAdmissionCount,
            acceptedBackfillReservationIds,
            explicitAdmissionClosed,
            explicitAdmissionCloseReason,
            explicitAdmissionCloseMessage,
            explicitAdmissionClosedAtEpochMs,
            winnerPlayerUuid,
            placementCompletedAtEpochMs,
            matchStartedAtEpochMs,
            completedAtEpochMs,
            resultSubmittedAtEpochMs,
            resultPayloadHash,
            createdAtEpochMs,
            lastUpdatedAtEpochMs,
            lastError
        );
    }

    public ArenaActiveMatch(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId,
        String instanceTemplateId,
        String instanceWorldName,
        String assignmentId,
        String externalMatchId,
        String matchSource,
        int admissionPolicySchemaVersion,
        int admissionCapacity,
        boolean backfillEnabled,
        String backfillMode,
        int backfillWindowSeconds,
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids,
        Map<UUID, Long> pendingReturnAtEpochMsByPlayerUuid,
        String winnerPlayerUuid,
        long placementCompletedAtEpochMs,
        long matchStartedAtEpochMs,
        long completedAtEpochMs,
        long resultSubmittedAtEpochMs,
        String resultPayloadHash,
        long createdAtEpochMs,
        long lastUpdatedAtEpochMs,
        String lastError
    ) {
        this(
            matchId,
            queueId,
            arenaId,
            originLobbyId,
            returnConnectionAddress,
            returnFallbackTargetId,
            launchTravelProfileId,
            instanceTemplateId,
            instanceWorldName,
            "",
            assignmentId,
            "",
            externalMatchId,
            matchSource,
            admissionPolicySchemaVersion,
            admissionCapacity,
            backfillEnabled,
            backfillMode,
            backfillWindowSeconds,
            AfkDetectionPolicy.defaults(),
            expectedPlayerUuids,
            expectedPlayerCount,
            arrivedPlayerUuids,
            activePlayerUuids,
            eliminatedPlayerUuids,
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            pendingReturnAtEpochMsByPlayerUuid,
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            winnerPlayerUuid,
            placementCompletedAtEpochMs,
            matchStartedAtEpochMs,
            completedAtEpochMs,
            resultSubmittedAtEpochMs,
            resultPayloadHash,
            createdAtEpochMs,
            lastUpdatedAtEpochMs,
            lastError
        );
    }

    public ArenaActiveMatch(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId,
        String instanceTemplateId,
        String instanceWorldName,
        String assignmentId,
        String externalMatchId,
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids,
        Map<UUID, Long> pendingReturnAtEpochMsByPlayerUuid,
        String winnerPlayerUuid,
        long completedAtEpochMs,
        long resultSubmittedAtEpochMs,
        String resultPayloadHash,
        long createdAtEpochMs,
        long lastUpdatedAtEpochMs,
        String lastError
    ) {
        this(
            matchId,
            queueId,
            arenaId,
            originLobbyId,
            returnConnectionAddress,
            returnFallbackTargetId,
            launchTravelProfileId,
            instanceTemplateId,
            instanceWorldName,
            "",
            assignmentId,
            "",
            externalMatchId,
            ArenaMatchSource.defaultSource().id(),
            0,
            0,
            false,
            QueueBackfillMode.defaultMode().id(),
            0,
            AfkDetectionPolicy.defaults(),
            expectedPlayerUuids,
            expectedPlayerCount,
            arrivedPlayerUuids,
            activePlayerUuids,
            eliminatedPlayerUuids,
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            pendingReturnAtEpochMsByPlayerUuid,
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            winnerPlayerUuid,
            0L,
            0L,
            completedAtEpochMs,
            resultSubmittedAtEpochMs,
            resultPayloadHash,
            createdAtEpochMs,
            lastUpdatedAtEpochMs,
            lastError
        );
    }

    public ArenaActiveMatch(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId,
        String instanceTemplateId,
        String instanceWorldName,
        String rulesEngineId,
        String assignmentId,
        String createdByAssignmentType,
        String externalMatchId,
        String matchSource,
        int admissionPolicySchemaVersion,
        int admissionCapacity,
        boolean backfillEnabled,
        String backfillMode,
        int backfillWindowSeconds,
        AfkDetectionPolicy afkDetectionPolicy,
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids,
        List<UUID> spectatorPlayerUuids,
        Map<UUID, String> assignmentIdsByPlayerUuid,
        Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        Map<UUID, ArenaPlayerOutcomeState> playerOutcomeByUuid,
        Map<UUID, Long> pendingReturnAtEpochMsByPlayerUuid,
        int consumedBackfillAdmissionCount,
        Set<String> acceptedBackfillReservationIds,
        boolean explicitAdmissionClosed,
        String explicitAdmissionCloseReason,
        String explicitAdmissionCloseMessage,
        long explicitAdmissionClosedAtEpochMs,
        String winnerPlayerUuid,
        long placementCompletedAtEpochMs,
        long matchStartedAtEpochMs,
        long completedAtEpochMs,
        long resultSubmittedAtEpochMs,
        String resultPayloadHash,
        long createdAtEpochMs,
        long lastUpdatedAtEpochMs,
        String lastError
    ) {
        this(
            matchId,
            queueId,
            arenaId,
            originLobbyId,
            returnConnectionAddress,
            returnFallbackTargetId,
            launchTravelProfileId,
            instanceTemplateId,
            instanceWorldName,
            rulesEngineId,
            assignmentId,
            createdByAssignmentType,
            externalMatchId,
            matchSource,
            admissionPolicySchemaVersion,
            admissionCapacity,
            backfillEnabled,
            backfillMode,
            backfillWindowSeconds,
            afkDetectionPolicy,
            expectedPlayerUuids,
            expectedPlayerCount,
            arrivedPlayerUuids,
            activePlayerUuids,
            eliminatedPlayerUuids,
            spectatorPlayerUuids,
            assignmentIdsByPlayerUuid,
            playerReturnTargetsByUuid,
            playerOutcomeByUuid,
            pendingReturnAtEpochMsByPlayerUuid,
            consumedBackfillAdmissionCount,
            acceptedBackfillReservationIds,
            explicitAdmissionClosed,
            explicitAdmissionCloseReason,
            explicitAdmissionCloseMessage,
            explicitAdmissionClosedAtEpochMs,
            winnerPlayerUuid,
            Math.min(Math.max(expectedPlayerCount, 0), PlayerUuidLists.canonicalize(expectedPlayerUuids).size()),
            0L,
            0L,
            0L,
            "",
            0L,
            "",
            placementCompletedAtEpochMs,
            matchStartedAtEpochMs,
            completedAtEpochMs,
            resultSubmittedAtEpochMs,
            resultPayloadHash,
            createdAtEpochMs,
            lastUpdatedAtEpochMs,
            lastError
        );
    }

    @Nonnull
    public ArenaActiveMatch normalized() {
        long now = System.currentTimeMillis();
        return new ArenaActiveMatch(
            NexoriMatchIds.normalizeRequiredMatchId(matchId, "Arena match id cannot be blank."),
            QueueDefinition.normalizeId(queueId),
            ArenaDefinition.normalizeId(arenaId),
            SourceContextId.normalizeId(originLobbyId),
            normalizeRequired(returnConnectionAddress, "Arena match return connection address cannot be blank."),
            normalizeRequired(returnFallbackTargetId, "Arena match return fallback target id cannot be blank."),
            normalizeRequired(launchTravelProfileId, "Arena match travel profile id cannot be blank."),
            normalizeInstanceTemplateId(instanceTemplateId),
            normalizeOptional(instanceWorldName),
            ArenaDefinition.normalizeRulesEngineId(rulesEngineId),
            normalizeOptional(assignmentId),
            normalizeOptional(createdByAssignmentType),
            normalizeOptional(externalMatchId),
            effectiveMatchSource().id(),
            Math.max(0, admissionPolicySchemaVersion),
            Math.max(0, admissionCapacity),
            backfillEnabled,
            effectiveBackfillMode().id(),
            Math.max(0, backfillWindowSeconds),
            AfkDetectionPolicy.normalize(afkDetectionPolicy),
            PlayerUuidLists.canonicalize(expectedPlayerUuids),
            Math.max(expectedPlayerCount, 0),
            PlayerUuidLists.canonicalize(arrivedPlayerUuids),
            PlayerUuidLists.canonicalize(activePlayerUuids),
            PlayerUuidLists.canonicalize(eliminatedPlayerUuids),
            PlayerUuidLists.canonicalize(spectatorPlayerUuids),
            normalizeAssignmentIdsByPlayerUuid(assignmentIdsByPlayerUuid),
            normalizePlayerReturnTargets(playerReturnTargetsByUuid),
            normalizePlayerOutcomes(playerOutcomeByUuid),
            normalizePendingReturns(pendingReturnAtEpochMsByPlayerUuid),
            Math.max(0, consumedBackfillAdmissionCount),
            normalizeReservationIds(acceptedBackfillReservationIds),
            explicitAdmissionClosed,
            normalizeOptional(explicitAdmissionCloseReason),
            normalizeOptional(explicitAdmissionCloseMessage),
            Math.max(0L, explicitAdmissionClosedAtEpochMs),
            normalizeOptional(winnerPlayerUuid),
            normalizeMinimumInitialPlayers(
                minimumInitialPlayers,
                Math.max(expectedPlayerCount, 0),
                PlayerUuidLists.canonicalize(expectedPlayerUuids).size()
            ),
            Math.max(0L, initialPlacementWindowStartedAtEpochMs),
            Math.max(0L, initialPlacementWindowExpiresAtEpochMs),
            Math.max(0L, initialPlacementWindowClosedAtEpochMs),
            normalizeOptional(initialPlacementWindowCloseReason),
            Math.max(0L, startGateOpenedAtEpochMs),
            normalizeOptional(startGateOpenReason),
            Math.max(0L, placementCompletedAtEpochMs),
            Math.max(0L, matchStartedAtEpochMs),
            Math.max(0L, completedAtEpochMs),
            Math.max(0L, resultSubmittedAtEpochMs),
            normalizeOptional(resultPayloadHash),
            createdAtEpochMs <= 0L ? now : createdAtEpochMs,
            lastUpdatedAtEpochMs <= 0L ? now : lastUpdatedAtEpochMs,
            normalizeOptional(lastError)
        );
    }

    /**
     * Adds the player to arrivedPlayerUuids AND activePlayerUuids simultaneously.
     * Use this for arenas without an instance template where the player is immediately active.
     * For instance-template arenas prefer {@link #withPlayerArrivedOnly} until placement
     * is confirmed, then call {@link #withPlayerPlacementConfirmed}.
     */
    @Nonnull
    public ArenaActiveMatch withPlayerArrival(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> arrived = new LinkedHashSet<>(arrivedPlayerUuids());
        arrived.add(playerUuid);
        LinkedHashSet<UUID> active = new LinkedHashSet<>(activePlayerUuids());
        active.add(playerUuid);
        return copy(
            PlayerUuidLists.canonicalize(arrived),
            PlayerUuidLists.canonicalize(active),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    /**
     * Records that the player has arrived but is not yet active (placement is still in progress).
     * Only adds to arrivedPlayerUuids; activePlayerUuids is untouched until
     * {@link #withPlayerPlacementConfirmed} is called.
     */
    @Nonnull
    public ArenaActiveMatch withPlayerArrivedOnly(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> arrived = new LinkedHashSet<>(arrivedPlayerUuids());
        arrived.add(playerUuid);
        return copy(
            PlayerUuidLists.canonicalize(arrived),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    /**
     * Moves the player from arrived-only to fully active once placement is confirmed in the
     * gameplay world.  No-ops if the player is already in activePlayerUuids.
     */
    @Nonnull
    public ArenaActiveMatch withPlayerPlacementConfirmed(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> active = new LinkedHashSet<>(activePlayerUuids());
        active.add(playerUuid);
        return copy(
            arrivedPlayerUuids(),
            PlayerUuidLists.canonicalize(active),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withPlayerReturnTarget(
        @Nonnull UUID playerUuid,
        @Nonnull ArenaPlayerReturnTarget returnTarget,
        long nowEpochMs
    ) {
        LinkedHashMap<UUID, ArenaPlayerReturnTarget> updatedTargets = new LinkedHashMap<>(playerReturnTargetsByUuid());
        updatedTargets.put(playerUuid, returnTarget.normalized());
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            updatedTargets,
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withPlayerAssignmentId(@Nonnull UUID playerUuid, @Nonnull String rawAssignmentId, long nowEpochMs) {
        String normalizedAssignmentId = normalizeOptional(rawAssignmentId);
        if (normalizedAssignmentId.isBlank()) {
            return this;
        }
        LinkedHashMap<UUID, String> updatedAssignmentIds = new LinkedHashMap<>(assignmentIdsByPlayerUuid());
        updatedAssignmentIds.put(playerUuid, normalizedAssignmentId);
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            updatedAssignmentIds,
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withInstanceWorldName(@Nonnull String rawInstanceWorldName, long nowEpochMs) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            rawInstanceWorldName,
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withEliminatedPlayer(@Nonnull UUID playerUuid, long returnAtEpochMs, long nowEpochMs) {
        LinkedHashSet<UUID> eliminated = new LinkedHashSet<>(eliminatedPlayerUuids());
        eliminated.add(playerUuid);
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.put(playerUuid, returnAtEpochMs);
        LinkedHashMap<UUID, ArenaPlayerOutcomeState> outcomes = new LinkedHashMap<>(playerOutcomeByUuid());
        outcomes.put(playerUuid, new ArenaPlayerOutcomeState(
            playerUuid,
            ArenaPlayerResolutionOutcome.LOSS,
            ArenaPlayerResolutionOutcome.LOSS.name(),
            "eliminated",
            nowEpochMs
        ));
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            List.copyOf(eliminated),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            outcomes,
            pendingReturns,
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withWinner(@Nonnull UUID playerUuid, long returnAtEpochMs, long nowEpochMs) {
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.put(playerUuid, returnAtEpochMs);
        LinkedHashSet<UUID> eliminated = new LinkedHashSet<>(eliminatedPlayerUuids());
        eliminated.remove(playerUuid);
        LinkedHashMap<UUID, ArenaPlayerOutcomeState> outcomes = new LinkedHashMap<>(playerOutcomeByUuid());
        outcomes.put(playerUuid, new ArenaPlayerOutcomeState(
            playerUuid,
            ArenaPlayerResolutionOutcome.WIN,
            ArenaPlayerResolutionOutcome.WIN.name(),
            "winner",
            nowEpochMs
        ));
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            List.copyOf(eliminated),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            outcomes,
            pendingReturns,
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            playerUuid.toString(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withPendingReturn(@Nonnull UUID playerUuid, long returnAtEpochMs, long nowEpochMs) {
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.put(playerUuid, returnAtEpochMs);
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturns,
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withPlayerOutcome(
        @Nonnull UUID playerUuid,
        @Nonnull ArenaPlayerResolutionOutcome outcome,
        @Nonnull String backendOutcome,
        @Nonnull String reason,
        long nowEpochMs
    ) {
        LinkedHashSet<UUID> eliminated = new LinkedHashSet<>(eliminatedPlayerUuids());
        String winner = winnerPlayerUuid();
        if (outcome == ArenaPlayerResolutionOutcome.WIN) {
            eliminated.remove(playerUuid);
            winner = playerUuid.toString();
        } else if (outcome != ArenaPlayerResolutionOutcome.NO_CONTEST) {
            eliminated.add(playerUuid);
            if (winner.equalsIgnoreCase(playerUuid.toString())) {
                winner = "";
            }
        } else if (winner.equalsIgnoreCase(playerUuid.toString())) {
            winner = "";
        }
        LinkedHashMap<UUID, ArenaPlayerOutcomeState> outcomes = new LinkedHashMap<>(playerOutcomeByUuid());
        outcomes.put(playerUuid, new ArenaPlayerOutcomeState(playerUuid, outcome, backendOutcome, reason, nowEpochMs));
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            List.copyOf(eliminated),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            outcomes,
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winner,
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withSpectatorPlayer(@Nonnull UUID playerUuid, boolean spectator, long nowEpochMs) {
        LinkedHashSet<UUID> spectators = new LinkedHashSet<>(spectatorPlayerUuids());
        if (spectator) {
            spectators.add(playerUuid);
        } else {
            spectators.remove(playerUuid);
        }
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            List.copyOf(spectators),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withExpectedPlayerCount(int rawExpectedPlayerCount, long nowEpochMs) {
        int normalizedExpected = Math.max(rawExpectedPlayerCount, arrivedInitialPlayerCount());
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            normalizedExpected,
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withLastError(@Nonnull String rawLastError, long nowEpochMs) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            rawLastError
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withSubmittedResult(long completedAtEpochMs, long submittedAtEpochMs, @Nonnull String payloadHash) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs,
            submittedAtEpochMs,
            payloadHash,
            createdAtEpochMs(),
            submittedAtEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withMatchSource(@Nonnull String rawMatchSource, long nowEpochMs) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            rawMatchSource,
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withInitialPlacementWindowRuntime(
        int rawMinimumInitialPlayers,
        long startedAtEpochMs,
        long expiresAtEpochMs,
        long nowEpochMs
    ) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            rawMinimumInitialPlayers,
            Math.max(0L, startedAtEpochMs),
            Math.max(0L, expiresAtEpochMs),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withInitialPlacementWindowClosed(@Nonnull String rawReason, long closedAtEpochMs, long nowEpochMs) {
        long normalizedClosedAtEpochMs = Math.max(0L, closedAtEpochMs);
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            normalizedClosedAtEpochMs,
            rawReason,
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withStartGateOpened(@Nonnull String rawReason, long openedAtEpochMs, long nowEpochMs) {
        long normalizedOpenedAtEpochMs = Math.max(0L, openedAtEpochMs);
        long normalizedMatchStartedAtEpochMs = matchStartedAtEpochMs() > 0L
            ? matchStartedAtEpochMs()
            : normalizedOpenedAtEpochMs;
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            normalizedOpenedAtEpochMs,
            rawReason,
            placementCompletedAtEpochMs(),
            normalizedMatchStartedAtEpochMs,
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withPlacementCompleted(long placementCompletedAtEpochMs, long nowEpochMs) {
        long normalizedPlacementCompletedAtEpochMs = Math.max(placementCompletedAtEpochMs, 0L);
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            normalizedPlacementCompletedAtEpochMs,
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withCompleted(long completedAtEpochMs, long nowEpochMs) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs,
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withoutReturnedPlayer(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> active = new LinkedHashSet<>(activePlayerUuids());
        active.remove(playerUuid);
        LinkedHashSet<UUID> eliminated = new LinkedHashSet<>(eliminatedPlayerUuids());
        eliminated.remove(playerUuid);
        LinkedHashSet<UUID> spectators = new LinkedHashSet<>(spectatorPlayerUuids());
        spectators.remove(playerUuid);
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.remove(playerUuid);
        LinkedHashMap<UUID, ArenaPlayerReturnTarget> returnTargets = new LinkedHashMap<>(playerReturnTargetsByUuid());
        returnTargets.remove(playerUuid);
        String winner = winnerPlayerUuid();
        if (winner.equalsIgnoreCase(playerUuid.toString())) {
            winner = "";
        }
        return copy(
            arrivedPlayerUuids(),
            List.copyOf(active),
            List.copyOf(eliminated),
            List.copyOf(spectators),
            assignmentIdsByPlayerUuid(),
            returnTargets,
            playerOutcomeByUuid(),
            pendingReturns,
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winner,
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    public boolean hasPlayer(@Nonnull UUID playerUuid) {
        return activePlayerUuids().contains(playerUuid)
            || eliminatedPlayerUuids().contains(playerUuid)
            || spectatorPlayerUuids().contains(playerUuid)
            || arrivedPlayerUuids().contains(playerUuid)
            || expectedPlayerUuids().contains(playerUuid);
    }

    public boolean hasExpectedPlayerList() {
        return !expectedPlayerUuids().isEmpty();
    }

    public boolean expectsPlayer(@Nonnull UUID playerUuid) {
        return !hasExpectedPlayerList() || expectedPlayerUuids().contains(playerUuid);
    }

    public boolean isPlayerEliminated(@Nonnull UUID playerUuid) {
        return eliminatedPlayerUuids().contains(playerUuid);
    }

    public boolean hasPendingReturn(@Nonnull UUID playerUuid) {
        return pendingReturnAtEpochMsByPlayerUuid().containsKey(playerUuid);
    }

    public boolean hasWinner() {
        return !winnerPlayerUuid().isBlank();
    }

    @Nonnull
    public ArenaMatchSource effectiveMatchSource() {
        return ArenaMatchSource.tryParse(matchSource)
            .orElse(ArenaMatchSource.defaultSource());
    }

    @Nonnull
    public QueueBackfillMode effectiveBackfillMode() {
        if (!backfillEnabled) {
            return QueueBackfillMode.NONE;
        }
        return QueueBackfillMode.tryParse(backfillMode)
            .orElse(QueueBackfillMode.NONE);
    }

    public boolean hasCompleted() {
        return completedAtEpochMs() > 0L;
    }

    public boolean hasSubmittedResult() {
        return resultSubmittedAtEpochMs() > 0L;
    }

    public boolean initialPlacementWindowClosed() {
        return initialPlacementWindowClosedAtEpochMs() > 0L;
    }

    public boolean initialPlacementWindowOpen(long nowEpochMs) {
        return initialPlacementWindowExpiresAtEpochMs() > 0L
            && initialPlacementWindowClosedAtEpochMs() <= 0L
            && startGateOpenedAtEpochMs() <= 0L
            && completedAtEpochMs() <= 0L
            && nowEpochMs < initialPlacementWindowExpiresAtEpochMs();
    }

    public boolean startGateOpen() {
        return startGateOpenedAtEpochMs() > 0L;
    }

    public boolean usesInstanceTemplate() {
        return !instanceTemplateId().isBlank() && !ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId());
    }

    public boolean allExpectedPlayersArrived() {
        return expectedPlayerCount() > 0 && arrivedInitialPlayerCount() >= expectedPlayerCount();
    }

    public int arrivedInitialPlayerCount() {
        if (expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(expectedPlayerUuids());
        int arrivedInitialPlayers = 0;
        for (UUID playerUuid : arrivedPlayerUuids()) {
            if (expected.contains(playerUuid)) {
                arrivedInitialPlayers++;
            }
        }
        return arrivedInitialPlayers;
    }

    @Nonnull
    public List<UUID> alivePlayerUuids() {
        LinkedHashSet<UUID> alive = new LinkedHashSet<>(activePlayerUuids());
        alive.removeAll(eliminatedPlayerUuids());
        alive.removeAll(spectatorPlayerUuids());
        return PlayerUuidLists.canonicalize(alive);
    }

    @Nonnull
    public List<ArenaPlayerOutcomeState> canonicalPlayerOutcomes() {
        return playerOutcomeByUuid().values().stream()
            .sorted(Comparator.comparing(state -> state.playerUuid().toString()))
            .toList();
    }

    @Nonnull
    public List<UUID> dueReturnPlayerUuids(long nowEpochMs) {
        List<UUID> duePlayers = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : pendingReturnAtEpochMsByPlayerUuid().entrySet()) {
            if (entry.getValue() <= nowEpochMs) {
                duePlayers.add(entry.getKey());
            }
        }
        return List.copyOf(duePlayers);
    }

    public boolean isEmpty() {
        return activePlayerUuids().isEmpty()
            && pendingReturnAtEpochMsByPlayerUuid().isEmpty()
            && spectatorPlayerUuids().isEmpty();
    }

    public ArenaPlayerReturnTarget findPlayerReturnTarget(@Nonnull UUID playerUuid) {
        return playerReturnTargetsByUuid().get(playerUuid);
    }

    public boolean hasAcceptedBackfillReservation(@Nonnull String rawReservationId) {
        String reservationId = normalizeOptional(rawReservationId);
        return !reservationId.isBlank() && acceptedBackfillReservationIds().contains(reservationId);
    }

    @Nonnull
    public ArenaActiveMatch withAcceptedBackfillReservation(@Nonnull String rawReservationId, long nowEpochMs) {
        String reservationId = normalizeOptional(rawReservationId);
        if (reservationId.isBlank() || acceptedBackfillReservationIds().contains(reservationId)) {
            return this;
        }
        LinkedHashSet<String> updatedAcceptedReservations = new LinkedHashSet<>(acceptedBackfillReservationIds());
        updatedAcceptedReservations.add(reservationId);
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            updatedAcceptedReservations,
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withConsumedBackfillAdmissionIncrement(long nowEpochMs) {
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount() + 1,
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    public ArenaActiveMatch withCreatedByAssignmentType(@Nonnull String rawCreatedByAssignmentType, long nowEpochMs) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            rawCreatedByAssignmentType,
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            explicitAdmissionClosed(),
            explicitAdmissionCloseReason(),
            explicitAdmissionCloseMessage(),
            explicitAdmissionClosedAtEpochMs(),
            winnerPlayerUuid(),
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withExplicitAdmissionClosed(
        @Nonnull String rawReason,
        @Nonnull String rawMessage,
        long closedAtEpochMs,
        long nowEpochMs
    ) {
        return copy(
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            spectatorPlayerUuids(),
            assignmentIdsByPlayerUuid(),
            playerReturnTargetsByUuid(),
            playerOutcomeByUuid(),
            pendingReturnAtEpochMsByPlayerUuid(),
            consumedBackfillAdmissionCount(),
            acceptedBackfillReservationIds(),
            true,
            rawReason,
            rawMessage,
            Math.max(0L, closedAtEpochMs),
            winnerPlayerUuid(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            nowEpochMs,
            lastError()
        );
    }

    @Nonnull
    private ArenaActiveMatch copy(
        @Nonnull List<UUID> arrived,
        @Nonnull List<UUID> active,
        @Nonnull List<UUID> eliminated,
        @Nonnull List<UUID> spectators,
        @Nonnull Map<UUID, String> assignmentIds,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> returnTargets,
        @Nonnull Map<UUID, ArenaPlayerOutcomeState> outcomes,
        @Nonnull Map<UUID, Long> pendingReturns,
        int consumedBackfillAdmissions,
        @Nonnull Set<String> acceptedReservations,
        boolean admissionClosed,
        @Nonnull String admissionCloseReason,
        @Nonnull String admissionCloseMessage,
        long admissionClosedAtEpochMs,
        @Nonnull String winner,
        long completedAt,
        long resultSubmittedAt,
        @Nonnull String payloadHash,
        long nowEpochMs,
        @Nonnull String error
    ) {
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            instanceTemplateId(),
            instanceWorldName(),
            rulesEngineId(),
            assignmentId(),
            createdByAssignmentType(),
            externalMatchId(),
            matchSource(),
            admissionPolicySchemaVersion(),
            admissionCapacity(),
            backfillEnabled(),
            backfillMode(),
            backfillWindowSeconds(),
            afkDetectionPolicy(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrived,
            active,
            eliminated,
            spectators,
            assignmentIds,
            returnTargets,
            outcomes,
            pendingReturns,
            consumedBackfillAdmissions,
            acceptedReservations,
            admissionClosed,
            admissionCloseReason,
            admissionCloseMessage,
            admissionClosedAtEpochMs,
            winner,
            minimumInitialPlayers(),
            initialPlacementWindowStartedAtEpochMs(),
            initialPlacementWindowExpiresAtEpochMs(),
            initialPlacementWindowClosedAtEpochMs(),
            initialPlacementWindowCloseReason(),
            startGateOpenedAtEpochMs(),
            startGateOpenReason(),
            placementCompletedAtEpochMs(),
            matchStartedAtEpochMs(),
            completedAt,
            resultSubmittedAt,
            payloadHash,
            createdAtEpochMs(),
            nowEpochMs,
            error
        ).normalized();
    }

    @Nonnull
    private static Map<UUID, ArenaPlayerOutcomeState> normalizePlayerOutcomes(Map<UUID, ArenaPlayerOutcomeState> rawOutcomes) {
        if (rawOutcomes == null || rawOutcomes.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<UUID, ArenaPlayerOutcomeState> normalized = new LinkedHashMap<>();
        rawOutcomes.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                ArenaPlayerOutcomeState state = entry.getValue().normalized(entry.getKey());
                if (state.playerUuid() != null && state.outcome() != null) {
                    normalized.put(state.playerUuid(), state);
                }
            });
        return Map.copyOf(normalized);
    }

    @Nonnull
    private static Map<UUID, String> normalizeAssignmentIdsByPlayerUuid(Map<UUID, String> rawAssignmentIdsByPlayerUuid) {
        if (rawAssignmentIdsByPlayerUuid == null || rawAssignmentIdsByPlayerUuid.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<UUID, String> normalized = new LinkedHashMap<>();
        rawAssignmentIdsByPlayerUuid.entrySet().stream()
            .filter(entry -> entry.getKey() != null)
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> {
                String assignmentId = normalizeOptional(entry.getValue());
                if (!assignmentId.isBlank()) {
                    normalized.put(entry.getKey(), assignmentId);
                }
            });
        return Map.copyOf(normalized);
    }

    @Nonnull
    private static Map<UUID, Long> normalizePendingReturns(Map<UUID, Long> rawPendingReturns) {
        if (rawPendingReturns == null || rawPendingReturns.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<UUID, Long> normalized = new LinkedHashMap<>();
        for (Map.Entry<UUID, Long> entry : rawPendingReturns.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null && entry.getValue() > 0L) {
                normalized.put(entry.getKey(), entry.getValue());
            }
        }
        return Map.copyOf(normalized);
    }

    @Nonnull
    private static Map<UUID, ArenaPlayerReturnTarget> normalizePlayerReturnTargets(Map<UUID, ArenaPlayerReturnTarget> rawTargets) {
        if (rawTargets == null || rawTargets.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<UUID, ArenaPlayerReturnTarget> normalized = new LinkedHashMap<>();
        rawTargets.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() != null)
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
            .forEach(entry -> normalized.put(entry.getKey(), entry.getValue().normalized()));
        return Map.copyOf(normalized);
    }

    @Nonnull
    private static Set<String> normalizeReservationIds(Set<String> rawReservationIds) {
        if (rawReservationIds == null || rawReservationIds.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawReservationId : rawReservationIds) {
            String reservationId = normalizeOptional(rawReservationId);
            if (!reservationId.isBlank()) {
                normalized.add(reservationId);
            }
        }
        return Set.copyOf(normalized);
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    private static int normalizeMinimumInitialPlayers(
        int rawMinimumInitialPlayers,
        int rawExpectedPlayerCount,
        int rawExpectedPlayerUuidCount
    ) {
        int expectedCount = Math.max(rawExpectedPlayerCount, 0);
        int expectedUuidCount = Math.max(rawExpectedPlayerUuidCount, 0);
        int maximum = expectedUuidCount > 0 ? Math.min(expectedCount, expectedUuidCount) : expectedCount;
        if (maximum <= 0) {
            return 0;
        }
        if (rawMinimumInitialPlayers <= 0) {
            return maximum;
        }
        return Math.min(rawMinimumInitialPlayers, maximum);
    }

    @Nonnull
    private static String normalizeInstanceTemplateId(String rawValue) {
        String normalized = normalizeOptional(rawValue);
        if (normalized.isBlank() || ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(normalized)) {
            return ArenaDefinition.NO_INSTANCE_TEMPLATE_ID;
        }
        return normalized;
    }

    public record ArenaPlayerOutcomeState(
        UUID playerUuid,
        ArenaPlayerResolutionOutcome outcome,
        String backendOutcome,
        String reason,
        long updatedAtEpochMs
    ) {

        @Nonnull
        private ArenaPlayerOutcomeState normalized(@Nonnull UUID fallbackPlayerUuid) {
            UUID normalizedPlayerUuid = playerUuid == null ? fallbackPlayerUuid : playerUuid;
            return new ArenaPlayerOutcomeState(
                normalizedPlayerUuid,
                outcome,
                normalizeOptional(backendOutcome, outcome == null ? "" : outcome.name()),
                normalizeOptional(reason),
                Math.max(0L, updatedAtEpochMs)
            );
        }
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }
}
