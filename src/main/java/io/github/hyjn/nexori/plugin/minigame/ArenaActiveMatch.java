package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
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

    @Nonnull
    public ArenaActiveMatch normalized() {
        long now = System.currentTimeMillis();
        return new ArenaActiveMatch(
            normalizeRequired(matchId, "Arena match id cannot be blank."),
            QueueDefinition.normalizeId(queueId),
            ArenaDefinition.normalizeId(arenaId),
            LobbyDefinition.normalizeId(originLobbyId),
            normalizeRequired(returnConnectionAddress, "Arena match return connection address cannot be blank."),
            normalizeRequired(returnFallbackTargetId, "Arena match return fallback target id cannot be blank."),
            normalizeRequired(launchTravelProfileId, "Arena match travel profile id cannot be blank."),
            normalizeInstanceTemplateId(instanceTemplateId),
            normalizeOptional(instanceWorldName),
            normalizeOptional(matchResolutionTriggerId),
            normalizeOptional(assignmentId),
            normalizeOptional(externalMatchId),
            normalizePlayers(expectedPlayerUuids),
            Math.max(expectedPlayerCount, 0),
            normalizePlayers(arrivedPlayerUuids),
            normalizePlayers(activePlayerUuids),
            normalizePlayers(eliminatedPlayerUuids),
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            List.copyOf(arrived),
            List.copyOf(active),
            eliminatedPlayerUuids(),
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            List.copyOf(eliminated),
            pendingReturns,
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
    public ArenaActiveMatch withWinner(@Nonnull UUID playerUuid, long returnAtEpochMs, long nowEpochMs) {
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.put(playerUuid, returnAtEpochMs);
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            pendingReturns,
            playerUuid.toString(),
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withPendingReturn(@Nonnull UUID playerUuid, long returnAtEpochMs, long nowEpochMs) {
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.put(playerUuid, returnAtEpochMs);
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
            pendingReturns,
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            normalizedExpected,
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            activePlayerUuids(),
            eliminatedPlayerUuids(),
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
        LinkedHashMap<UUID, Long> pendingReturns = new LinkedHashMap<>(pendingReturnAtEpochMsByPlayerUuid());
        pendingReturns.remove(playerUuid);
        String winner = winnerPlayerUuid();
        if (winner.equalsIgnoreCase(playerUuid.toString())) {
            winner = "";
        }
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
            assignmentId(),
            externalMatchId(),
            expectedPlayerUuids(),
            expectedPlayerCount(),
            arrivedPlayerUuids(),
            List.copyOf(active),
            List.copyOf(eliminated),
            pendingReturns,
            winner,
            completedAtEpochMs(),
            resultSubmittedAtEpochMs(),
            resultPayloadHash(),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    public boolean hasPlayer(@Nonnull UUID playerUuid) {
        return activePlayerUuids().contains(playerUuid);
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
        return List.copyOf(alive);
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
        return activePlayerUuids().isEmpty() && pendingReturnAtEpochMsByPlayerUuid().isEmpty();
    }

    @Nonnull
    private static List<UUID> normalizePlayers(List<UUID> rawPlayerUuids) {
        if (rawPlayerUuids == null || rawPlayerUuids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<UUID> players = new LinkedHashSet<>();
        for (UUID playerUuid : rawPlayerUuids) {
            if (playerUuid != null) {
                players.add(playerUuid);
            }
        }
        return List.copyOf(new ArrayList<>(players));
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
}
