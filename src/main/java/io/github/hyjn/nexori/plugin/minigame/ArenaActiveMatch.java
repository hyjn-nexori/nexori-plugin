package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    String matchResolutionTriggerId,
    String rulesEngineId,
    String assignmentId,
    String externalMatchId,
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
    String winnerPlayerUuid,
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
        String matchResolutionTriggerId,
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
            matchResolutionTriggerId,
            "",
            assignmentId,
            externalMatchId,
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
            winnerPlayerUuid,
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
            normalizeOptional(matchResolutionTriggerId),
            ArenaDefinition.normalizeRulesEngineId(rulesEngineId),
            normalizeOptional(assignmentId),
            normalizeOptional(externalMatchId),
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
            normalizeOptional(winnerPlayerUuid),
            Math.max(0L, completedAtEpochMs),
            Math.max(0L, resultSubmittedAtEpochMs),
            normalizeOptional(resultPayloadHash),
            createdAtEpochMs <= 0L ? now : createdAtEpochMs,
            lastUpdatedAtEpochMs <= 0L ? now : lastUpdatedAtEpochMs,
            normalizeOptional(lastError)
        );
    }

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
            matchResolutionTriggerId(),
            rulesEngineId(),
            assignmentId(),
            externalMatchId(),
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
            winnerPlayerUuid(),
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
        } else {
            eliminated.add(playerUuid);
            if (winner.equalsIgnoreCase(playerUuid.toString())) {
                winner = "";
            }
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
        int normalizedExpected = Math.max(rawExpectedPlayerCount, arrivedPlayerUuids().size());
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
            matchResolutionTriggerId(),
            rulesEngineId(),
            assignmentId(),
            externalMatchId(),
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
            winnerPlayerUuid(),
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
            matchResolutionTriggerId(),
            rulesEngineId(),
            assignmentId(),
            externalMatchId(),
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
            winnerPlayerUuid(),
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
            matchResolutionTriggerId(),
            rulesEngineId(),
            assignmentId(),
            externalMatchId(),
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
            winnerPlayerUuid(),
            completedAtEpochMs,
            submittedAtEpochMs,
            payloadHash,
            createdAtEpochMs(),
            submittedAtEpochMs,
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

    public boolean hasCompleted() {
        return completedAtEpochMs() > 0L;
    }

    public boolean hasSubmittedResult() {
        return resultSubmittedAtEpochMs() > 0L;
    }

    public boolean usesInstanceTemplate() {
        return !instanceTemplateId().isBlank() && !ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId());
    }

    public boolean allExpectedPlayersArrived() {
        return expectedPlayerCount() > 0 && arrivedPlayerUuids().size() >= expectedPlayerCount();
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
            matchResolutionTriggerId(),
            rulesEngineId(),
            assignmentId(),
            externalMatchId(),
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
            winner,
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
