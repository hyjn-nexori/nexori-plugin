package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class AfkActivityService {

    public static final long DEFAULT_INACTIVITY_TIMEOUT_MS = 30_000L;

    private final HytaleLogger logger;
    private final Function<UUID, Optional<String>> activeMatchIdLookup;
    private final long inactivityTimeoutMs;
    private final Map<UUID, PlayerActivityState> statesByPlayerUuid = new LinkedHashMap<>();

    public AfkActivityService(@Nonnull Function<UUID, Optional<String>> activeMatchIdLookup) {
        this(null, activeMatchIdLookup, DEFAULT_INACTIVITY_TIMEOUT_MS);
    }

    public AfkActivityService(
        @Nullable HytaleLogger logger,
        @Nonnull Function<UUID, Optional<String>> activeMatchIdLookup
    ) {
        this(logger, activeMatchIdLookup, DEFAULT_INACTIVITY_TIMEOUT_MS);
    }

    public AfkActivityService(
        @Nullable HytaleLogger logger,
        @Nonnull Function<UUID, Optional<String>> activeMatchIdLookup,
        long inactivityTimeoutMs
    ) {
        this.logger = logger;
        this.activeMatchIdLookup = activeMatchIdLookup;
        this.inactivityTimeoutMs = Math.max(1L, inactivityTimeoutMs);
    }

    public synchronized void handlePlayerInputTick(@Nonnull PlayerRef playerRef, boolean hasInputActivity, long nowEpochMs) {
        handlePlayerInputTick(playerRef.getUuid(), playerRef.getUsername(), hasInputActivity, nowEpochMs);
    }

    synchronized void handlePlayerInputTick(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        boolean hasInputActivity,
        long nowEpochMs
    ) {
        String matchId = findActiveMatchId(playerUuid).orElse(null);
        if (matchId == null) {
            statesByPlayerUuid.remove(playerUuid);
            return;
        }

        PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
        if (state == null || !state.matchId().equals(matchId)) {
            state = new PlayerActivityState(matchId, nowEpochMs, false);
            statesByPlayerUuid.put(playerUuid, state);
        }

        if (hasInputActivity) {
            markActivityLocked(playerUuid, username, matchId, nowEpochMs, "PLAYER_INPUT");
            return;
        }

        evaluateAfkLocked(playerUuid, username, state, nowEpochMs);
    }

    public synchronized void markInventoryActivity(@Nonnull PlayerRef playerRef, long nowEpochMs) {
        markInventoryActivity(playerRef.getUuid(), playerRef.getUsername(), nowEpochMs);
    }

    synchronized void markInventoryActivity(@Nonnull UUID playerUuid, @Nonnull String username, long nowEpochMs) {
        String matchId = findActiveMatchId(playerUuid).orElse(null);
        if (matchId == null) {
            statesByPlayerUuid.remove(playerUuid);
            return;
        }
        markActivityLocked(playerUuid, username, matchId, nowEpochMs, "INVENTORY_PACKET");
    }

    public synchronized boolean isAfk(@Nonnull UUID playerUuid) {
        PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
        return state != null && state.afk();
    }

    public synchronized long lastActivityEpochMs(@Nonnull UUID playerUuid) {
        PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
        return state == null ? 0L : state.lastActivityEpochMs();
    }

    public synchronized void removePlayer(@Nonnull UUID playerUuid) {
        statesByPlayerUuid.remove(playerUuid);
    }

    private Optional<String> findActiveMatchId(@Nonnull UUID playerUuid) {
        Optional<String> matchId = activeMatchIdLookup.apply(playerUuid);
        if (matchId == null || matchId.isEmpty() || matchId.get().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(matchId.get());
    }

    private void markActivityLocked(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        @Nonnull String matchId,
        long nowEpochMs,
        @Nonnull String source
    ) {
        PlayerActivityState previous = statesByPlayerUuid.get(playerUuid);
        boolean wasAfk = previous != null && previous.afk();
        statesByPlayerUuid.put(playerUuid, new PlayerActivityState(matchId, nowEpochMs, false));
        if (wasAfk && logger != null) {
            logger.atInfo().log(
                "Nexori AFK state changed player=" + username
                    + " uuid=" + playerUuid
                    + " matchId=" + matchId
                    + " state=ACTIVE"
                    + " source=" + source
            );
        }
    }

    private void evaluateAfkLocked(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        @Nonnull PlayerActivityState state,
        long nowEpochMs
    ) {
        if (state.afk() || nowEpochMs - state.lastActivityEpochMs() < inactivityTimeoutMs) {
            return;
        }
        statesByPlayerUuid.put(playerUuid, new PlayerActivityState(state.matchId(), state.lastActivityEpochMs(), true));
        if (logger != null) {
            logger.atInfo().log(
                "Nexori AFK state changed player=" + username
                    + " uuid=" + playerUuid
                    + " matchId=" + state.matchId()
                    + " state=AFK"
                    + " idleMs=" + (nowEpochMs - state.lastActivityEpochMs())
            );
        }
    }

    private record PlayerActivityState(
        @Nonnull String matchId,
        long lastActivityEpochMs,
        boolean afk
    ) {
    }
}
