package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record ArenaActiveMatch(
    String matchId,
    String queueId,
    String arenaId,
    String originLobbyId,
    String returnConnectionAddress,
    String returnFallbackTargetId,
    String launchTravelProfileId,
    List<UUID> playerUuids,
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
            normalizePlayers(playerUuids),
            createdAtEpochMs <= 0L ? now : createdAtEpochMs,
            lastUpdatedAtEpochMs <= 0L ? now : lastUpdatedAtEpochMs,
            normalizeOptional(lastError)
        );
    }

    @Nonnull
    public ArenaActiveMatch withPlayer(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> players = new LinkedHashSet<>(playerUuids());
        players.add(playerUuid);
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            List.copyOf(players),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    @Nonnull
    public ArenaActiveMatch withPlayers(@Nonnull List<UUID> playerIds, long nowEpochMs) {
        LinkedHashSet<UUID> players = new LinkedHashSet<>(playerUuids());
        players.addAll(playerIds);
        return new ArenaActiveMatch(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            List.copyOf(players),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    public boolean isEmpty() {
        return playerUuids().isEmpty();
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
}
