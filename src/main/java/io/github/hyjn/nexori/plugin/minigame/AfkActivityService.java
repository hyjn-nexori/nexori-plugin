package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivitySource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AfkActivityService {

    private final HytaleLogger logger;
    private final Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup;
    private final Consumer<AfkActivityTransition> transitionConsumer;
    private final Map<UUID, PlayerActivityState> statesByPlayerUuid = new LinkedHashMap<>();
    private final Map<String, Map<UUID, PlayerAfkReportState>> afkReportStatesByMatchId = new LinkedHashMap<>();
    private final Map<String, JsonObject> finalAfkReportsByMatchId = new LinkedHashMap<>();

    public AfkActivityService(@Nonnull Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup) {
        this(null, effectivePolicyLookup, ignored -> {
        });
    }

    public AfkActivityService(
        @Nullable HytaleLogger logger,
        @Nonnull Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup
    ) {
        this(logger, effectivePolicyLookup, ignored -> {
        });
    }

    public AfkActivityService(
        @Nullable HytaleLogger logger,
        @Nonnull Function<UUID, Optional<EffectiveAfkDetectionPolicy>> effectivePolicyLookup,
        @Nonnull Consumer<AfkActivityTransition> transitionConsumer
    ) {
        this.logger = logger;
        this.effectivePolicyLookup = effectivePolicyLookup;
        this.transitionConsumer = transitionConsumer;
    }

    public void handlePlayerInputTick(@Nonnull PlayerRef playerRef, boolean hasInputActivity, long nowEpochMs) {
        handlePlayerInputTick(playerRef.getUuid(), playerRef.getUsername(), hasInputActivity, nowEpochMs);
    }

    void handlePlayerInputTick(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        boolean hasInputActivity,
        long nowEpochMs
    ) {
        AfkActivityTransition transition;
        synchronized (this) {
            EffectiveAfkDetectionPolicy effectivePolicy = findEffectivePolicy(playerUuid).orElse(null);
            if (effectivePolicy == null || !effectivePolicy.policy().enabled()) {
                removeStaleTrackingStateLocked(playerUuid, effectivePolicy);
                return;
            }
            String matchId = effectivePolicy.matchId();

            PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
            if (state == null || !state.matchId().equals(matchId)) {
                state = new PlayerActivityState(matchId, username, nowEpochMs, false);
                statesByPlayerUuid.put(playerUuid, state);
            }

            if (hasInputActivity) {
                transition = markActivityLocked(playerUuid, username, effectivePolicy, nowEpochMs, NexoriAfkActivitySource.PLAYER_INPUT);
            } else {
                transition = evaluateAfkLocked(playerUuid, username, state, effectivePolicy, nowEpochMs);
            }
        }
        dispatchTransition(transition);
    }

    public void markInventoryActivity(@Nonnull PlayerRef playerRef, long nowEpochMs) {
        markInventoryActivity(playerRef.getUuid(), playerRef.getUsername(), nowEpochMs);
    }

    void markInventoryActivity(@Nonnull UUID playerUuid, @Nonnull String username, long nowEpochMs) {
        AfkActivityTransition transition;
        synchronized (this) {
            EffectiveAfkDetectionPolicy effectivePolicy = findEffectivePolicy(playerUuid).orElse(null);
            if (effectivePolicy == null || !effectivePolicy.policy().enabled()) {
                removeStaleTrackingStateLocked(playerUuid, effectivePolicy);
                return;
            }
            transition = markActivityLocked(playerUuid, username, effectivePolicy, nowEpochMs, NexoriAfkActivitySource.INVENTORY_PACKET);
        }
        dispatchTransition(transition);
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

    public synchronized void resetPlayerActivity(@Nonnull UUID playerUuid) {
        statesByPlayerUuid.remove(playerUuid);
    }

    public void clearPlayerForPolicyChange(
        @Nonnull UUID playerUuid,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        long nowEpochMs
    ) {
        AfkActivityTransition transition;
        synchronized (this) {
            PlayerActivityState previous = statesByPlayerUuid.remove(playerUuid);
            if (previous == null || !previous.afk()) {
                return;
            }
            String username = previous.username();
            long idleMs = Math.max(0L, nowEpochMs - previous.lastActivityEpochMs());
            if (logger != null) {
                logger.atInfo().log(
                    "Nexori AFK state changed player=" + username
                        + " uuid=" + playerUuid
                        + " matchId=" + effectivePolicy.matchId()
                        + " state=ACTIVE"
                        + " source=" + NexoriAfkActivitySource.POLICY_CHANGE
                );
            }
            transition = new AfkActivityTransition(
                effectivePolicy.matchId(),
                effectivePolicy.queueId(),
                effectivePolicy.arenaId(),
                effectivePolicy.rulesEngineId(),
                playerUuid,
                username,
                false,
                nowEpochMs,
                idleMs,
                NexoriAfkActivitySource.POLICY_CHANGE
            );
        }
        dispatchTransition(transition);
    }

    public synchronized void removeMatch(@Nonnull String matchId) {
        String normalizedMatchId = normalizeMatchId(matchId);
        if (normalizedMatchId.isBlank()) {
            return;
        }
        statesByPlayerUuid.entrySet().removeIf(entry -> entry.getValue().matchId().equals(normalizedMatchId));
        afkReportStatesByMatchId.remove(normalizedMatchId);
        finalAfkReportsByMatchId.remove(normalizedMatchId);
    }

    /**
     * Read-only snapshot for the AFK HUD. Returns:
     * <ul>
     *   <li>{@link Optional#empty()} — player not tracked, detection disabled, or idle
     *       time is well below the threshold. No AFK HUD should be shown.</li>
     *   <li>{@code AfkHudState(afk=true, 0)} — player is already AFK.</li>
     *   <li>{@code AfkHudState(afk=false, N)} — player is within the 5-second
     *       warning window before being marked AFK; N is seconds remaining.</li>
     * </ul>
     */
    public synchronized Optional<AfkHudState> findAfkHudState(@Nonnull UUID playerUuid, long nowEpochMs) {
        PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
        if (state == null) {
            return Optional.empty();
        }
        if (state.afk()) {
            return state.showHud() ? Optional.of(new AfkHudState(true, 0)) : Optional.empty();
        }
        Optional<EffectiveAfkDetectionPolicy> policyOpt = findEffectivePolicy(playerUuid);
        if (policyOpt.isEmpty() || !policyOpt.get().policy().enabled()) {
            return Optional.empty();
        }
        long timeoutMs = policyOpt.get().policy().inactivityTimeoutMs();
        long idleMs = Math.max(0L, nowEpochMs - state.lastActivityEpochMs());
        long remainingMs = Math.max(0L, timeoutMs - idleMs);
        int remainingSeconds = (int) Math.max(0L, (remainingMs + 999L) / 1000L);
        if (remainingSeconds <= 5 && remainingSeconds > 0) {
            return Optional.of(new AfkHudState(false, remainingSeconds));
        }
        return Optional.empty();
    }

    public record AfkHudState(boolean afk, int secondsRemaining) {}

    @Nonnull
    public synchronized List<UUID> afkPlayerUuids(@Nonnull String matchId) {
        String normalizedMatchId = normalizeMatchId(matchId);
        if (normalizedMatchId.isBlank()) {
            return List.of();
        }
        List<UUID> afkPlayerUuids = new ArrayList<>();
        for (Map.Entry<UUID, PlayerActivityState> entry : statesByPlayerUuid.entrySet()) {
            if (entry.getValue().afk() && entry.getValue().matchId().equals(normalizedMatchId)) {
                afkPlayerUuids.add(entry.getKey());
            }
        }
        return afkPlayerUuids.stream().distinct().toList();
    }

    @Nonnull
    public synchronized Optional<JsonObject> findMatchAfkReport(@Nonnull String matchId, long nowEpochMs) {
        String normalizedMatchId = normalizeMatchId(matchId);
        if (normalizedMatchId.isBlank()) {
            return Optional.empty();
        }
        JsonObject finalReport = finalAfkReportsByMatchId.get(normalizedMatchId);
        if (finalReport != null) {
            return Optional.of(finalReport.deepCopy());
        }
        Map<UUID, PlayerAfkReportState> statesByPlayer = afkReportStatesByMatchId.get(normalizedMatchId);
        if (statesByPlayer == null || statesByPlayer.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buildAfkReport(normalizedMatchId, statesByPlayer, nowEpochMs));
    }

    public synchronized void rememberFinalMatchAfkReport(@Nonnull String matchId, @Nonnull JsonObject report) {
        String normalizedMatchId = normalizeMatchId(matchId);
        if (normalizedMatchId.isBlank() || report == null || report.entrySet().isEmpty()) {
            return;
        }
        finalAfkReportsByMatchId.put(normalizedMatchId, report.deepCopy());
    }

    /**
     * Sets or clears the AFK state for one player via the external API.
     *
     * <p>Operates on the single unified AFK state. Calling with {@code afk=false} resets the
     * activity timer to {@code nowEpochMs} so the automatic detector does not re-trigger
     * immediately on the next tick if it is still enabled.</p>
     *
     * <p>When there is no prior activity state for this player (i.e. the automatic detector has
     * not yet seen any ticks), the emitted transition uses {@code usernameHint} as the player
     * name. If the player already has a cached username from prior activity ticks, that name
     * takes precedence.</p>
     *
     * @return {@code true} if the state changed and a transition was dispatched,
     *         {@code false} if the state was already as requested (UNCHANGED)
     */
    public boolean setExternalAfk(
        @Nonnull UUID playerUuid,
        @Nonnull String usernameHint,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        boolean afk,
        boolean showHud,
        long nowEpochMs
    ) {
        AfkActivityTransition transition;
        synchronized (this) {
            PlayerActivityState current = statesByPlayerUuid.get(playerUuid);
            boolean currentlyAfk = current != null && current.afk();
            if (afk == currentlyAfk) {
                return false;
            }
            String cachedUsername = current != null ? current.username() : null;
            String username = (cachedUsername != null && !cachedUsername.isBlank()) ? cachedUsername : usernameHint;
            long idleMs = current != null ? Math.max(0L, nowEpochMs - current.lastActivityEpochMs()) : 0L;
            if (afk) {
                long lastActivity = current != null ? current.lastActivityEpochMs() : nowEpochMs;
                statesByPlayerUuid.put(playerUuid, new PlayerActivityState(
                    effectivePolicy.matchId(), username, lastActivity, true, showHud
                ));
                if (logger != null) {
                    logger.atInfo().log(
                        "Nexori AFK state changed player=" + username
                            + " uuid=" + playerUuid
                            + " matchId=" + effectivePolicy.matchId()
                            + " state=AFK"
                            + " source=" + NexoriAfkActivitySource.EXTERNAL_API
                    );
                }
            } else {
                // Reset timer to now so automatic detector does not re-trigger immediately
                statesByPlayerUuid.put(playerUuid, new PlayerActivityState(
                    effectivePolicy.matchId(), username, nowEpochMs, false
                ));
                if (logger != null) {
                    logger.atInfo().log(
                        "Nexori AFK state changed player=" + username
                            + " uuid=" + playerUuid
                            + " matchId=" + effectivePolicy.matchId()
                            + " state=ACTIVE"
                            + " source=" + NexoriAfkActivitySource.EXTERNAL_API
                    );
                }
            }
            transition = new AfkActivityTransition(
                effectivePolicy.matchId(),
                effectivePolicy.queueId(),
                effectivePolicy.arenaId(),
                effectivePolicy.rulesEngineId(),
                playerUuid,
                username,
                afk,
                nowEpochMs,
                idleMs,
                NexoriAfkActivitySource.EXTERNAL_API
            );
        }
        dispatchTransition(transition);
        return true;
    }

    public boolean setExternalAfk(
        @Nonnull UUID playerUuid,
        @Nonnull String usernameHint,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        boolean afk,
        long nowEpochMs
    ) {
        return setExternalAfk(playerUuid, usernameHint, effectivePolicy, afk, true, nowEpochMs);
    }

    public boolean setExternalAfk(
        @Nonnull UUID playerUuid,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        boolean afk,
        long nowEpochMs
    ) {
        return setExternalAfk(playerUuid, "", effectivePolicy, afk, true, nowEpochMs);
    }

    // Removes automatic-tracking state when the policy is disabled or the player is not in a match,
    // but preserves AFK state so externally-asserted AFK survives across ticks when detection is disabled.
    private void removeStaleTrackingStateLocked(
        @Nonnull UUID playerUuid,
        @Nullable EffectiveAfkDetectionPolicy effectivePolicy
    ) {
        if (effectivePolicy == null) {
            // Player not in any active match — always clean up
            statesByPlayerUuid.remove(playerUuid);
            return;
        }
        // Detector disabled but player is in match — only remove non-AFK tracking state
        PlayerActivityState state = statesByPlayerUuid.get(playerUuid);
        if (state == null || !state.afk()) {
            statesByPlayerUuid.remove(playerUuid);
        }
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

    private AfkActivityTransition markActivityLocked(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        long nowEpochMs,
        @Nonnull NexoriAfkActivitySource source
    ) {
        PlayerActivityState previous = statesByPlayerUuid.get(playerUuid);
        boolean wasAfk = previous != null && previous.afk();
        long idleMs = previous == null ? 0L : Math.max(0L, nowEpochMs - previous.lastActivityEpochMs());
        statesByPlayerUuid.put(playerUuid, new PlayerActivityState(effectivePolicy.matchId(), username, nowEpochMs, false));
        if (wasAfk && logger != null) {
            logger.atInfo().log(
                "Nexori AFK state changed player=" + username
                    + " uuid=" + playerUuid
                    + " matchId=" + effectivePolicy.matchId()
                    + " state=ACTIVE"
                    + " source=" + source
            );
        }
        if (!wasAfk) {
            return null;
        }
        return new AfkActivityTransition(
            effectivePolicy.matchId(),
            effectivePolicy.queueId(),
            effectivePolicy.arenaId(),
            effectivePolicy.rulesEngineId(),
            playerUuid,
            username,
            false,
            nowEpochMs,
            idleMs,
            source
        );
    }

    private AfkActivityTransition evaluateAfkLocked(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        @Nonnull PlayerActivityState state,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        long nowEpochMs
    ) {
        long idleMs = nowEpochMs - state.lastActivityEpochMs();
        if (state.afk() || idleMs < effectivePolicy.policy().inactivityTimeoutMs()) {
            return null;
        }
        statesByPlayerUuid.put(playerUuid, new PlayerActivityState(state.matchId(), username, state.lastActivityEpochMs(), true));
        if (logger != null) {
            logger.atInfo().log(
                "Nexori AFK state changed player=" + username
                    + " uuid=" + playerUuid
                    + " matchId=" + state.matchId()
                    + " state=AFK"
                    + " idleMs=" + idleMs
            );
        }
        return new AfkActivityTransition(
            effectivePolicy.matchId(),
            effectivePolicy.queueId(),
            effectivePolicy.arenaId(),
            effectivePolicy.rulesEngineId(),
            playerUuid,
            username,
            true,
            nowEpochMs,
            idleMs,
            NexoriAfkActivitySource.IDLE_TIMEOUT
        );
    }

    private void dispatchTransition(AfkActivityTransition transition) {
        if (transition != null) {
            synchronized (this) {
                recordAfkReportTransitionLocked(transition);
            }
            transitionConsumer.accept(transition);
        }
    }

    private void recordAfkReportTransitionLocked(@Nonnull AfkActivityTransition transition) {
        String normalizedMatchId = normalizeMatchId(transition.matchId());
        if (normalizedMatchId.isBlank()) {
            return;
        }
        Map<UUID, PlayerAfkReportState> statesByPlayer = afkReportStatesByMatchId.computeIfAbsent(
            normalizedMatchId,
            ignored -> new LinkedHashMap<>()
        );
        PlayerAfkReportState state = statesByPlayer.computeIfAbsent(
            transition.playerUuid(),
            ignored -> new PlayerAfkReportState()
        );
        state.record(transition);
        finalAfkReportsByMatchId.remove(normalizedMatchId);
    }

    @Nonnull
    private JsonObject buildAfkReport(
        @Nonnull String matchId,
        @Nonnull Map<UUID, PlayerAfkReportState> statesByPlayer,
        long nowEpochMs
    ) {
        JsonObject report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        report.addProperty("matchId", matchId);
        report.add("playerFields", afkReportPlayerFields());
        JsonArray players = new JsonArray();
        for (Map.Entry<UUID, PlayerAfkReportState> entry : statesByPlayer.entrySet()) {
            players.add(entry.getValue().toJsonRow(entry.getKey(), nowEpochMs));
        }
        report.add("players", players);
        return report;
    }

    @Nonnull
    private JsonArray afkReportPlayerFields() {
        JsonArray fields = new JsonArray();
        fields.add("playerUuid");
        fields.add("playerName");
        fields.add("currentlyAfk");
        fields.add("totalAfkMs");
        fields.add("afkCount");
        fields.add("currentStartedAtEpochMs");
        fields.add("lastIdleMs");
        fields.add("sources");
        return fields;
    }

    @Nonnull
    private static String normalizeMatchId(String matchId) {
        return matchId == null ? "" : matchId.trim();
    }

    public record AfkActivityTransition(
        @Nonnull String matchId,
        @Nonnull String queueId,
        @Nonnull String arenaId,
        @Nonnull String rulesEngineId,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        boolean afk,
        long changedAtEpochMs,
        long idleMs,
        @Nonnull NexoriAfkActivitySource source
    ) {
    }

    private record PlayerActivityState(
        @Nonnull String matchId,
        @Nonnull String username,
        long lastActivityEpochMs,
        boolean afk,
        boolean showHud
    ) {
        PlayerActivityState(@Nonnull String matchId, @Nonnull String username, long lastActivityEpochMs, boolean afk) {
            this(matchId, username, lastActivityEpochMs, afk, true);
        }
    }

    private static final class PlayerAfkReportState {
        private String playerName = "";
        private boolean currentlyAfk;
        private long currentStartedAtEpochMs;
        private long totalAfkMs;
        private int afkCount;
        private long lastIdleMs;
        private final LinkedHashSet<NexoriAfkActivitySource> sources = new LinkedHashSet<>();

        private void record(@Nonnull AfkActivityTransition transition) {
            if (transition.playerName() != null && !transition.playerName().isBlank()) {
                playerName = transition.playerName();
            }
            sources.add(transition.source());
            lastIdleMs = Math.max(0L, transition.idleMs());
            if (transition.afk()) {
                if (!currentlyAfk) {
                    currentStartedAtEpochMs = transition.changedAtEpochMs();
                    afkCount++;
                }
                currentlyAfk = true;
                return;
            }
            if (currentlyAfk && currentStartedAtEpochMs > 0L) {
                totalAfkMs += Math.max(0L, transition.changedAtEpochMs() - currentStartedAtEpochMs);
            }
            currentlyAfk = false;
            currentStartedAtEpochMs = 0L;
        }

        @Nonnull
        private JsonArray toJsonRow(@Nonnull UUID playerUuid, long nowEpochMs) {
            JsonArray row = new JsonArray();
            row.add(playerUuid.toString());
            row.add(playerName);
            row.add(currentlyAfk);
            row.add(totalAfkMs(nowEpochMs));
            row.add(afkCount);
            row.add(currentlyAfk ? currentStartedAtEpochMs : 0L);
            row.add(lastIdleMs);
            JsonArray sourceNames = new JsonArray();
            for (NexoriAfkActivitySource source : sources) {
                sourceNames.add(source.name());
            }
            row.add(sourceNames);
            return row;
        }

        private long totalAfkMs(long nowEpochMs) {
            if (!currentlyAfk || currentStartedAtEpochMs <= 0L) {
                return totalAfkMs;
            }
            return totalAfkMs + Math.max(0L, nowEpochMs - currentStartedAtEpochMs);
        }
    }
}
