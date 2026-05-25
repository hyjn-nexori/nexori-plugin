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

    private final HytaleLogger logger;
    private final Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup;
    private final Map<UUID, PlayerActivityState> statesByPlayerUuid = new LinkedHashMap<>();

    public AfkActivityService(@Nonnull Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup) {
        this(null, effectivePolicyLookup);
    }

    public AfkActivityService(
        @Nullable HytaleLogger logger,
        @Nonnull Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup
    ) {
        this.logger = logger;
        this.effectivePolicyLookup = effectivePolicyLookup;
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
        EffectiveAfkDetectionPolicy effectivePolicy = findEffectivePolicy(playerUuid).orElse(null);
        if (effectivePolicy == null || !effectivePolicy.policy().enabled()) {
            statesByPlayerUuid.remove(playerUuid);
            return;
        }
        String matchId = effectivePolicy.matchId();

        PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
        if (state == null || !state.matchId().equals(matchId)) {
            state = new PlayerActivityState(matchId, nowEpochMs, false);
            statesByPlayerUuid.put(playerUuid, state);
        }

        if (hasInputActivity) {
            markActivityLocked(playerUuid, username, matchId, nowEpochMs, "PLAYER_INPUT");
            return;
        }

        evaluateAfkLocked(playerUuid, username, state, effectivePolicy.policy(), nowEpochMs);
    }

    public synchronized void markInventoryActivity(@Nonnull PlayerRef playerRef, long nowEpochMs) {
        markInventoryActivity(playerRef.getUuid(), playerRef.getUsername(), nowEpochMs);
    }

    synchronized void markInventoryActivity(@Nonnull UUID playerUuid, @Nonnull String username, long nowEpochMs) {
        EffectiveAfkDetectionPolicy effectivePolicy = findEffectivePolicy(playerUuid).orElse(null);
        if (effectivePolicy == null || !effectivePolicy.policy().enabled()) {
            statesByPlayerUuid.remove(playerUuid);
            return;
        }
        markActivityLocked(playerUuid, username, effectivePolicy.matchId(), nowEpochMs, "INVENTORY_PACKET");
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

    private Optional<EffectiveAfkDetectionPolicy> findEffectivePolicy(@Nonnull UUID playerUuid) {
        Optional<EffectiveAfkDetectionPolicy> effectivePolicy = effectivePolicyLookup.apply(playerUuid);
        if (effectivePolicy == null || effectivePolicy.isEmpty()) {
            return Optional.empty();
        }
        EffectiveAfkDetectionPolicy normalized = effectivePolicy.get().normalized();
        if (normalized.matchId().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
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
        @Nonnull AfkDetectionPolicy policy,
        long nowEpochMs
    ) {
        long idleMs = nowEpochMs - state.lastActivityEpochMs();
        if (state.afk() || idleMs < policy.inactivityTimeoutMs()) {
            return;
        }
        statesByPlayerUuid.put(playerUuid, new PlayerActivityState(state.matchId(), state.lastActivityEpochMs(), true));
        if (logger != null) {
            logger.atInfo().log(
                "Nexori AFK state changed player=" + username
                    + " uuid=" + playerUuid
                    + " matchId=" + state.matchId()
                    + " state=AFK"
                    + " idleMs=" + idleMs
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
