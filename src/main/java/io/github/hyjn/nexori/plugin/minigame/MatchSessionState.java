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
    long handoffCompletedAtEpochMs,
    long expiresAtEpochMs,
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
            Math.max(0L, handoffCompletedAtEpochMs),
            Math.max(0L, expiresAtEpochMs),
            normalizeOptional(lastError)
        );
    }

    @Nonnull
    public MatchSessionState withLaunchedPlayers(@Nonnull List<UUID> playerUuids, long nowEpochMs, @Nonnull String rawLastError) {
        return new MatchSessionState(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            playerUuids,
            filterExistingObserved(playerUuids, returnedPlayerUuids()),
            createdAtEpochMs(),
            nowEpochMs,
            handoffCompletedAtEpochMs(),
            expiresAtEpochMs(),
            rawLastError
        ).normalized();
    }

    @Nonnull
    public MatchSessionState withObservedPlayer(@Nonnull UUID playerUuid, long nowEpochMs) {
        LinkedHashSet<UUID> observed = new LinkedHashSet<>(returnedPlayerUuids());
        observed.add(playerUuid);
        return new MatchSessionState(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            expectedPlayerUuids(),
            List.copyOf(observed),
            createdAtEpochMs(),
            nowEpochMs,
            handoffCompletedAtEpochMs(),
            expiresAtEpochMs(),
            lastError()
        ).normalized();
    }

    @Nonnull
    public MatchSessionState withHandoffCompleted(
        @Nonnull List<UUID> launchedPlayerUuids,
        long expiresAtEpochMs,
        long nowEpochMs,
        @Nonnull String rawLastError
    ) {
        return new MatchSessionState(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            launchedPlayerUuids,
            filterExistingObserved(launchedPlayerUuids, returnedPlayerUuids()),
            createdAtEpochMs(),
            nowEpochMs,
            nowEpochMs,
            expiresAtEpochMs,
            rawLastError
        ).normalized();
    }

    @Nonnull
    public MatchSessionState withLastError(@Nonnull String rawLastError, long nowEpochMs) {
        return new MatchSessionState(
            matchId(),
            queueId(),
            arenaId(),
            originLobbyId(),
            returnConnectionAddress(),
            returnFallbackTargetId(),
            launchTravelProfileId(),
            expectedPlayerUuids(),
            returnedPlayerUuids(),
            createdAtEpochMs(),
            nowEpochMs,
            handoffCompletedAtEpochMs(),
            expiresAtEpochMs(),
            rawLastError
        ).normalized();
    }

    @Nonnull
    public List<UUID> launchedPlayerUuids() {
        return expectedPlayerUuids();
    }

    @Nonnull
    public List<UUID> observedPlayerUuids() {
        return returnedPlayerUuids();
    }

    public boolean expectsPlayer(@Nonnull UUID playerUuid) {
        return launchedPlayerUuids().contains(playerUuid);
    }

    public boolean hasObservedPlayer(@Nonnull UUID playerUuid) {
        return observedPlayerUuids().contains(playerUuid);
    }

    public boolean hasHandoffCompleted() {
        return handoffCompletedAtEpochMs() > 0L;
    }

    public boolean allLaunchedPlayersObserved() {
        return !launchedPlayerUuids().isEmpty() && observedPlayerUuids().containsAll(launchedPlayerUuids());
    }

    public boolean hasObservedReturns() {
        return !observedPlayerUuids().isEmpty();
    }

    public boolean hasReturned(@Nonnull UUID playerUuid) {
        return hasObservedPlayer(playerUuid);
    }

    public boolean isExpired(long nowEpochMs) {
        return expiresAtEpochMs() > 0L && expiresAtEpochMs() <= nowEpochMs;
    }

    @Nonnull
    private static List<UUID> filterExistingObserved(@Nonnull List<UUID> expectedPlayerUuids, @Nonnull List<UUID> returnedPlayerUuids) {
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
