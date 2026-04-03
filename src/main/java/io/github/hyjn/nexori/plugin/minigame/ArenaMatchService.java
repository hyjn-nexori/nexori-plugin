package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ArenaMatchService {

    private static final Gson GSON = new Gson();
    private static final long ELIMINATED_RETURN_DELAY_MS = 10_000L;
    private static final long RETURN_RETRY_DELAY_MS = 1_000L;

    private final HytaleLogger logger;
    private final SecureTravelService secureTravelService;
    private final MatchSessionService matchSessionService;
    private final ArenaService arenaService;
    private final ArenaMatchResolutionTriggerRegistry triggerRegistry;
    private final Map<String, ArenaActiveMatch> matchesById = new LinkedHashMap<>();
    private final Map<UUID, String> matchIdByPlayerUuid = new LinkedHashMap<>();

    public ArenaMatchService(
        @Nonnull HytaleLogger logger,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull MatchSessionService matchSessionService,
        @Nonnull ArenaService arenaService,
        @Nonnull ArenaMatchResolutionTriggerRegistry triggerRegistry
    ) {
        this.logger = logger;
        this.secureTravelService = secureTravelService;
        this.matchSessionService = matchSessionService;
        this.arenaService = arenaService;
        this.triggerRegistry = triggerRegistry;
    }

    public synchronized void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingArrival arrival = secureTravelService.consumeRecentArrival(playerRef.getUuid()).orElse(null);
        if (arrival == null) {
            return;
        }

        JsonObject context = parseContext(arrival.contextJson());
        if (context == null || !context.has("flowType")) {
            return;
        }

        String flowType = context.get("flowType").getAsString();
        if ("minigame.launch".equalsIgnoreCase(flowType)) {
            handleLaunchArrival(playerRef, context);
            return;
        }
        if ("minigame.return".equalsIgnoreCase(flowType)) {
            handleReturnArrival(playerRef, context);
        }
    }

    public synchronized void handlePlayerTick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        long nowEpochMs
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, Universe.get().getPlayerRefComponentType());
        if (playerRef == null) {
            return;
        }

        String matchId = matchIdByPlayerUuid.get(playerRef.getUuid());
        if (matchId == null) {
            return;
        }

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerRef.getUuid())) {
            return;
        }

        ArenaActiveMatch updated = match;
        if (!updated.isPlayerEliminated(playerRef.getUuid())
            && store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            updated = updated.withEliminatedPlayer(
                playerRef.getUuid(),
                nowEpochMs + ELIMINATED_RETURN_DELAY_MS,
                nowEpochMs
            );
            playerRef.sendMessage(Message.raw("You were eliminated. Returning to the lobby in 10 seconds."));
        }

        updated = applyAutomaticResolutionTrigger(updated, nowEpochMs);

        if (updated.hasPendingReturn(playerRef.getUuid())) {
            Long dueAt = updated.pendingReturnAtEpochMsByPlayerUuid().get(playerRef.getUuid());
            if (dueAt != null && dueAt <= nowEpochMs) {
                updated = attemptReturn(updated, playerRef, ref, store, nowEpochMs);
            }
        }

        if (updated.isEmpty()) {
            matchesById.remove(updated.matchId());
        } else {
            matchesById.put(updated.matchId(), updated);
        }
    }

    @Nonnull
    public synchronized List<ArenaActiveMatch> listMatches() {
        return matchesById.values().stream()
            .sorted(Comparator.comparing(ArenaActiveMatch::matchId))
            .toList();
    }

    @Nonnull
    public synchronized Optional<ArenaActiveMatch> find(@Nonnull String rawMatchId) {
        if (rawMatchId == null || rawMatchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchesById.get(rawMatchId.trim().toLowerCase()));
    }

    @Nonnull
    public synchronized Optional<UUID> findActivePlayerUuid(@Nonnull String rawMatchId, @Nonnull String rawPlayerToken) {
        ArenaActiveMatch match = find(rawMatchId).orElse(null);
        if (match == null || rawPlayerToken == null || rawPlayerToken.isBlank()) {
            return Optional.empty();
        }

        String playerToken = rawPlayerToken.trim();
        try {
            UUID playerUuid = UUID.fromString(playerToken);
            return match.hasPlayer(playerUuid) ? Optional.of(playerUuid) : Optional.empty();
        } catch (IllegalArgumentException ignored) {
        }

        for (UUID playerUuid : match.activePlayerUuids()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef != null && playerRef.isValid() && playerRef.getUsername().equalsIgnoreCase(playerToken)) {
                return Optional.of(playerUuid);
            }
        }
        return Optional.empty();
    }

    @Nonnull
    public synchronized ResolvePlayerResult resolvePlayerOutcome(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid,
        @Nonnull ArenaPlayerResolutionOutcome outcome,
        int returnDelaySeconds,
        @Nonnull String rawReason
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return ResolvePlayerResult.matchMissing(matchId);
        }
        if (!match.hasPlayer(playerUuid)) {
            return ResolvePlayerResult.playerMissing(match, playerUuid);
        }

        long now = System.currentTimeMillis();
        long delayMillis = Math.max(returnDelaySeconds, 0) * 1000L;
        String reason = normalizeOptional(rawReason, outcome.name().toLowerCase());
        ArenaActiveMatch updated = switch (outcome) {
            case WIN -> markPlayerWinInternal(match, playerUuid, reason, now)
                .withPendingReturn(playerUuid, now + delayMillis, now);
            case LOSS -> markPlayerLossInternal(match, playerUuid, reason, now)
                .withPendingReturn(playerUuid, now + delayMillis, now);
        };
        matchesById.put(updated.matchId(), updated);
        return ResolvePlayerResult.updated(updated, playerUuid, outcome);
    }

    @Nonnull
    public synchronized EndMatchResult endMatch(@Nonnull String rawMatchId, @Nonnull String rawReason) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return EndMatchResult.matchMissing(matchId);
        }

        String returnReason = normalizeOptional(rawReason, "MATCH_ENDED");
        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match;
        for (UUID playerUuid : match.activePlayerUuids()) {
            updated = updated.withPendingReturn(playerUuid, now, now);
        }
        updated = updated.withLastError("Manual match end requested: " + returnReason, now);
        matchesById.put(updated.matchId(), updated);
        return EndMatchResult.completed(match.matchId(), updated.pendingReturnAtEpochMsByPlayerUuid().size());
    }

    private void handleLaunchArrival(@Nonnull PlayerRef playerRef, @Nonnull JsonObject context) {
        LaunchContext launch = LaunchContext.from(context);
        long now = System.currentTimeMillis();
        ArenaActiveMatch existing = matchesById.get(launch.matchId());
        ArenaActiveMatch updated = existing == null
            ? new ArenaActiveMatch(
                launch.matchId(),
                launch.queueId(),
                launch.arenaId(),
                launch.originLobbyId(),
                launch.returnConnectionAddress(),
                launch.returnFallbackTargetId(),
                launch.launchTravelProfileId(),
                launch.expectedPlayerCount(),
                List.of(playerRef.getUuid()),
                List.of(playerRef.getUuid()),
                List.of(),
                Map.of(),
                "",
                now,
                now,
                ""
            ).normalized()
            : existing.withPlayerArrival(playerRef.getUuid(), now);

        String previousMatchId = matchIdByPlayerUuid.put(playerRef.getUuid(), updated.matchId());
        if (previousMatchId != null && !previousMatchId.equals(updated.matchId())) {
            ArenaActiveMatch previous = matchesById.get(previousMatchId);
            if (previous != null) {
                ArenaActiveMatch previousUpdated = previous.withoutReturnedPlayer(playerRef.getUuid(), now);
                if (previousUpdated.isEmpty()) {
                    matchesById.remove(previousMatchId);
                } else {
                    matchesById.put(previousMatchId, previousUpdated);
                }
            }
        }

        updated = applyAutomaticResolutionTrigger(updated, now);
        matchesById.put(updated.matchId(), updated);
        playerRef.sendMessage(Message.raw(
            "Joined Nexori match " + updated.matchId() + " on arena " + updated.arenaId() + "."
        ));
    }

    private void handleReturnArrival(@Nonnull PlayerRef playerRef, @Nonnull JsonObject context) {
        String matchId = readRequired(context, "matchId");
        String queueId = readRequired(context, "queueId");
        String originLobbyId = readRequired(context, "originLobbyId");
        String sourceArenaId = readRequired(context, "sourceArenaId");
        String reason = readRequired(context, "returnReason");
        MatchSessionService.ReturnResult result;
        try {
            result = matchSessionService.registerReturn(
                matchId,
                queueId,
                originLobbyId,
                sourceArenaId,
                reason,
                playerRef.getUuid()
            );
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist Nexori match return state.");
            playerRef.sendMessage(Message.raw("Returned from Nexori arena, but saving return state failed: " + exception.getMessage()));
            return;
        }

        switch (result.outcome()) {
            case RETURNED -> playerRef.sendMessage(Message.raw(
                "Returned from Nexori arena " + sourceArenaId + " for match " + matchId + "."
            ));
            case COMPLETED -> playerRef.sendMessage(Message.raw(
                "Returned from Nexori arena " + sourceArenaId + ". Match " + matchId + " is now complete."
            ));
            case ALREADY_RETURNED -> playerRef.sendMessage(Message.raw(
                "This Nexori match return had already been recorded."
            ));
            case MISSING, INVALID -> playerRef.sendMessage(Message.raw(
                "Returned from Nexori arena, but the lobby could not validate the match context."
                    + (result.errorMessage().isBlank() ? "" : " " + result.errorMessage())
            ));
        }
    }

    @Nonnull
    private ArenaActiveMatch applyAutomaticResolutionTrigger(@Nonnull ArenaActiveMatch match, long nowEpochMs) {
        if (match.hasWinner()) {
            UUID winnerUuid = parseWinnerUuid(match.winnerPlayerUuid());
            if (winnerUuid != null && !match.hasPendingReturn(winnerUuid) && match.hasPlayer(winnerUuid)) {
                return match.withPendingReturn(winnerUuid, nowEpochMs + ELIMINATED_RETURN_DELAY_MS, nowEpochMs);
            }
            return match;
        }

        ArenaDefinition arena = arenaService.find(match.arenaId()).orElse(null);
        if (arena == null || ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equals(arena.matchResolutionTriggerId())) {
            return match;
        }

        ArenaMatchResolutionTrigger trigger = triggerRegistry.find(arena.matchResolutionTriggerId()).orElse(null);
        if (trigger == null) {
            return match.withLastError(
                "Unknown arena match resolution trigger '" + arena.matchResolutionTriggerId() + "'.",
                nowEpochMs
            );
        }
        return trigger.evaluate(this, match, nowEpochMs);
    }

    @Nonnull
    ArenaActiveMatch markPlayerWinInternal(
        @Nonnull ArenaActiveMatch match,
        @Nonnull UUID playerUuid,
        @Nonnull String reason,
        long nowEpochMs
    ) {
        ArenaActiveMatch updated = match.withWinner(playerUuid, nowEpochMs + ELIMINATED_RETURN_DELAY_MS, nowEpochMs);
        if (reason != null && !reason.isBlank()) {
            updated = updated.withLastError(reason, nowEpochMs);
        }
        return updated;
    }

    @Nonnull
    private ArenaActiveMatch markPlayerLossInternal(
        @Nonnull ArenaActiveMatch match,
        @Nonnull UUID playerUuid,
        @Nonnull String reason,
        long nowEpochMs
    ) {
        ArenaActiveMatch updated = match.withEliminatedPlayer(playerUuid, nowEpochMs + ELIMINATED_RETURN_DELAY_MS, nowEpochMs);
        if (reason != null && !reason.isBlank()) {
            updated = updated.withLastError(reason, nowEpochMs);
        }
        return updated;
    }

    @Nonnull
    private ArenaActiveMatch attemptReturn(
        @Nonnull ArenaActiveMatch match,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        long nowEpochMs
    ) {
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            tryRespawn(store, ref, playerRef);
            return match.withPendingReturn(playerRef.getUuid(), nowEpochMs + RETURN_RETRY_DELAY_MS, nowEpochMs);
        }

        ConfiguredPeer destination;
        try {
            destination = ConfiguredPeer.parse(match.returnConnectionAddress());
        } catch (IllegalArgumentException exception) {
            return match.withLastError(normalizeOptional(exception.getMessage(), exception.getClass().getSimpleName()), nowEpochMs)
                .withPendingReturn(playerRef.getUuid(), nowEpochMs + RETURN_RETRY_DELAY_MS, nowEpochMs);
        }

        String returnReason = playerRef.getUuid().toString().equalsIgnoreCase(match.winnerPlayerUuid())
            ? "MATCH_WON"
            : "ELIMINATED";
        try {
            secureTravelService.travel(
                playerRef,
                destination,
                match.returnFallbackTargetId(),
                "",
                match.launchTravelProfileId(),
                buildReturnContextJson(match, returnReason, nowEpochMs)
            );
            removeMatchPlayers(match.matchId(), List.of(playerRef.getUuid()));
            return match.withoutReturnedPlayer(playerRef.getUuid(), nowEpochMs);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            logger.atWarning().withCause(exception).log(
                "Failed to return Nexori match player " + playerRef.getUuid() + " for match " + match.matchId() + "."
            );
            return match.withLastError(normalizeOptional(exception.getMessage(), exception.getClass().getSimpleName()), nowEpochMs)
                .withPendingReturn(playerRef.getUuid(), nowEpochMs + RETURN_RETRY_DELAY_MS, nowEpochMs);
        }
    }

    private void tryRespawn(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef) {
        try {
            DeathComponent.respawn(store, ref);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log(
                "Failed to request respawn for eliminated Nexori player " + playerRef.getUuid() + "."
            );
        }
    }

    private UUID parseWinnerUuid(@Nonnull String rawWinnerPlayerUuid) {
        if (rawWinnerPlayerUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(rawWinnerPlayerUuid.trim());
        } catch (IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log(
                "Failed to parse Nexori arena winner UUID '" + rawWinnerPlayerUuid + "'."
            );
            return null;
        }
    }

    private void removeMatchPlayers(@Nonnull String matchId, @Nonnull List<UUID> playerUuids) {
        for (UUID playerUuid : playerUuids) {
            String currentMatchId = matchIdByPlayerUuid.get(playerUuid);
            if (matchId.equals(currentMatchId)) {
                matchIdByPlayerUuid.remove(playerUuid);
            }
        }
    }

    @Nonnull
    private String buildReturnContextJson(@Nonnull ArenaActiveMatch match, @Nonnull String returnReason, long nowEpochMs) {
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.return");
        root.addProperty("matchId", match.matchId());
        root.addProperty("queueId", match.queueId());
        root.addProperty("originLobbyId", match.originLobbyId());
        root.addProperty("sourceArenaId", match.arenaId());
        root.addProperty("returnReason", returnReason);
        root.addProperty("returnedAtEpochMs", nowEpochMs);
        return GSON.toJson(root);
    }

    private JsonObject parseContext(String rawContextJson) {
        if (rawContextJson == null || rawContextJson.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(rawContextJson, JsonObject.class);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to parse Nexori minigame travel context JSON.");
            return null;
        }
    }

    @Nonnull
    private static String readRequired(@Nonnull JsonObject root, @Nonnull String key) {
        if (!root.has(key)) {
            throw new IllegalArgumentException("Missing required minigame context field '" + key + "'.");
        }
        return normalizeRequired(root.get(key).getAsString(), "Minigame context field '" + key + "' cannot be blank.");
    }

    @Nonnull
    private static String normalizeRequired(@Nonnull String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private record LaunchContext(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId,
        int expectedPlayerCount
    ) {

        @Nonnull
        private static LaunchContext from(@Nonnull JsonObject root) {
            return new LaunchContext(
                readRequired(root, "matchId").toLowerCase(),
                QueueDefinition.normalizeId(readRequired(root, "queueId")),
                ArenaDefinition.normalizeId(readRequired(root, "arenaId")),
                LobbyDefinition.normalizeId(readRequired(root, "originLobbyId")),
                readRequired(root, "returnConnectionAddress"),
                readRequired(root, "returnFallbackTargetId"),
                readRequired(root, "launchTravelProfileId").toLowerCase(),
                root.has("expectedPlayerCount") ? Math.max(root.get("expectedPlayerCount").getAsInt(), 0) : 0
            );
        }
    }

    public enum EndMatchOutcome {
        COMPLETED,
        MATCH_MISSING,
        FAILED
    }

    public record EndMatchResult(
        EndMatchOutcome outcome,
        String matchId,
        int returnedPlayerCount,
        String errorMessage,
        ArenaActiveMatch activeMatch
    ) {

        @Nonnull
        public static EndMatchResult completed(@Nonnull String matchId, int returnedPlayerCount) {
            return new EndMatchResult(EndMatchOutcome.COMPLETED, matchId, returnedPlayerCount, "", null);
        }

        @Nonnull
        public static EndMatchResult matchMissing(@Nonnull String matchId) {
            return new EndMatchResult(EndMatchOutcome.MATCH_MISSING, normalizeRequired(matchId, "Match id cannot be blank."), 0, "", null);
        }

        @Nonnull
        public static EndMatchResult failed(@Nonnull ArenaActiveMatch activeMatch, @Nonnull String errorMessage) {
            return new EndMatchResult(EndMatchOutcome.FAILED, activeMatch.matchId(), 0, normalizeOptional(errorMessage, "Unknown match end failure."), activeMatch);
        }
    }

    public enum ResolvePlayerOutcome {
        UPDATED,
        MATCH_MISSING,
        PLAYER_MISSING
    }

    public record ResolvePlayerResult(
        ResolvePlayerOutcome outcome,
        String matchId,
        UUID playerUuid,
        ArenaPlayerResolutionOutcome playerOutcome,
        ArenaActiveMatch activeMatch
    ) {

        @Nonnull
        public static ResolvePlayerResult updated(
            @Nonnull ArenaActiveMatch activeMatch,
            @Nonnull UUID playerUuid,
            @Nonnull ArenaPlayerResolutionOutcome playerOutcome
        ) {
            return new ResolvePlayerResult(ResolvePlayerOutcome.UPDATED, activeMatch.matchId(), playerUuid, playerOutcome, activeMatch);
        }

        @Nonnull
        public static ResolvePlayerResult matchMissing(@Nonnull String matchId) {
            return new ResolvePlayerResult(ResolvePlayerOutcome.MATCH_MISSING, normalizeRequired(matchId, "Match id cannot be blank."), null, null, null);
        }

        @Nonnull
        public static ResolvePlayerResult playerMissing(@Nonnull ArenaActiveMatch activeMatch, @Nonnull UUID playerUuid) {
            return new ResolvePlayerResult(ResolvePlayerOutcome.PLAYER_MISSING, activeMatch.matchId(), playerUuid, null, activeMatch);
        }
    }
}
