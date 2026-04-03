package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record MatchSessionState(
    String matchId,
    String queueId,
    String arenaId,
    String originLobbyId,
    String returnConnectionAddress,
    String returnFallbackTargetId,
    String launchTravelProfileId,
    List<UUID> expectedPlayerUuids,
    List<UUID> returnedPlayerUuids,
    long createdAtEpochMs,
    long updatedAtEpochMs,
    String lastError
) {

    @Nonnull
    public MatchSessionState normalized() {
        long now = System.currentTimeMillis();
        return new MatchSessionState(
            normalizeRequiredLower(matchId, "Match session id cannot be blank."),
            QueueDefinition.normalizeId(queueId),
            ArenaDefinition.normalizeId(arenaId),
            LobbyDefinition.normalizeId(originLobbyId),
            normalizeRequired(returnConnectionAddress, "Match session return connection address cannot be blank."),
            normalizeRequiredLower(returnFallbackTargetId, "Match session return fallback target id cannot be blank."),
            normalizeRequiredLower(launchTravelProfileId, "Match session travel profile id cannot be blank."),
            normalizePlayers(expectedPlayerUuids),
            normalizePlayers(returnedPlayerUuids),
            createdAtEpochMs <= 0L ? now : createdAtEpochMs,
            updatedAtEpochMs <= 0L ? now : updatedAtEpochMs,
            normalizeOptional(lastError)
        );
    }

    @Nonnull
    public MatchSessionState withExpectedPlayers(@Nonnull List<UUID> playerUuids, long nowEpochMs, @Nonnull String rawLastError) {
        return new MatchSessionState(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            playerUuids,
            filterExistingReturned(playerUuids, returnedPlayerUuids()),
            createdAtEpochMs(),
            nowEpochMs,
            rawLastError
        ).normalized();
    }

    @Nonnull
    public MatchSessionState withReturnedPlayer(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> returned = new LinkedHashSet<>(returnedPlayerUuids());
        returned.add(playerUuid);
        return new MatchSessionState(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            expectedPlayerUuids(),
            List.copyOf(returned),
            createdAtEpochMs(),
            nowEpochMs,
            lastError()
        ).normalized();
    }

    public boolean expectsPlayer(@Nonnull UUID playerUuid) {
        return expectedPlayerUuids().contains(playerUuid);
    }

    public boolean hasReturned(@Nonnull UUID playerUuid) {
        return returnedPlayerUuids().contains(playerUuid);
    }

    public boolean isComplete() {
        return !expectedPlayerUuids().isEmpty() && returnedPlayerUuids().containsAll(expectedPlayerUuids());
    }

    @Nonnull
    private static List<UUID> filterExistingReturned(@Nonnull List<UUID> expectedPlayerUuids, @Nonnull List<UUID> returnedPlayerUuids) {
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(expectedPlayerUuids);
        LinkedHashSet<UUID> filtered = new LinkedHashSet<>();
        for (UUID returned : returnedPlayerUuids) {
            if (expected.contains(returned)) {
                filtered.add(returned);
            }
        }
        return List.copyOf(filtered);
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
    private static String normalizeRequiredLower(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptionalLower(rawValue);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
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
    private static String normalizeOptionalLower(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? "" : normalized;
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
