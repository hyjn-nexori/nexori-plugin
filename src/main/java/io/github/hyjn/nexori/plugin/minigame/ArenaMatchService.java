package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.interface_.CustomPage;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.backend.BackendMatchAdmissionStateReportingService;
import io.github.hyjn.nexori.plugin.minigame.logic.BackfillAdmissionDecider;
import io.github.hyjn.nexori.plugin.minigame.logic.BackfillAdmissionDecision;
import io.github.hyjn.nexori.plugin.minigame.logic.MatchResultValidationResult;
import io.github.hyjn.nexori.plugin.minigame.logic.MatchResultValidator;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Tracks active arena matches, observes player arrivals and returns, and coordinates the
 * runtime lifecycle that sits between queue launch and secure return to the lobby.
 */
public final class ArenaMatchService {

    private static final Gson GSON = new Gson();
    private static final long ELIMINATED_RETURN_DELAY_MS = 5_000L;
    private static final long WINNER_RETURN_DELAY_MS = 10_000L;
    private static final long RETURN_RETRY_DELAY_MS = 5_000L;
    private static final double INITIAL_PLACEMENT_POSITION_EPSILON_SQUARED = 1.0D;
    private static final int INITIAL_PLACEMENT_REQUIRED_STABLE_TICKS = 2;
    private static final long INITIAL_PLACEMENT_TIMEOUT_MS = 7_500L;
    private static final long INITIAL_PLACEMENT_POST_READY_GRACE_MS = 750L;
    private static final String RESPAWN_PAGE_CLASS_NAME = "com.hypixel.hytale.server.core.entity.entities.player.pages.RespawnPage";
    private static final String ASSIGNMENT_TYPE_INITIAL_MATCH = "INITIAL_MATCH";
    private static final String ASSIGNMENT_TYPE_BACKFILL = BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL;
    private static final String CLOSE_REASON_MATCH_RUNTIME_ENDED = "MATCH_RUNTIME_ENDED";
    public static final int MAX_RESULT_REASON_LENGTH = MatchResultValidator.MAX_RESULT_REASON_LENGTH;
    public static final int MAX_RESULT_METADATA_ENTRIES = MatchResultValidator.MAX_RESULT_METADATA_ENTRIES;
    public static final int MAX_RESULT_METADATA_KEY_LENGTH = MatchResultValidator.MAX_RESULT_METADATA_KEY_LENGTH;
    public static final int MAX_RESULT_METADATA_VALUE_LENGTH = MatchResultValidator.MAX_RESULT_METADATA_VALUE_LENGTH;
    public static final int MAX_RESULT_PLAYER_REASON_LENGTH = MatchResultValidator.MAX_RESULT_PLAYER_REASON_LENGTH;
    public static final int MAX_CUSTOM_DATA_BYTES = MatchResultValidator.MAX_CUSTOM_DATA_BYTES;
    public static final int MAX_CUSTOM_DATA_DEPTH = MatchResultValidator.MAX_CUSTOM_DATA_DEPTH;
    public static final int MAX_CUSTOM_DATA_PROPERTIES = MatchResultValidator.MAX_CUSTOM_DATA_PROPERTIES;
    public static final int MAX_CUSTOM_DATA_ARRAY_LENGTH = MatchResultValidator.MAX_CUSTOM_DATA_ARRAY_LENGTH;
    public static final int MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH = MatchResultValidator.MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH;
    public static final int MAX_CUSTOM_DATA_STRING_LENGTH = MatchResultValidator.MAX_CUSTOM_DATA_STRING_LENGTH;

    private final HytaleLogger logger;
    private final SecureTravelService secureTravelService;
    private final MatchSessionService matchSessionService;
    private final ArenaService arenaService;
    private final InstanceSpawnSlotService instanceSpawnSlotService;
    private final BackfillAdmissionDecider backfillAdmissionDecider = new BackfillAdmissionDecider();
    private final MatchResultValidator matchResultValidator = new MatchResultValidator();
    private BackendMatchAdmissionStateReportingService backendMatchAdmissionStateReportingService;
    private final Map<String, ArenaActiveMatch> matchesById = new LinkedHashMap<>();
    private final Map<UUID, String> matchIdByPlayerUuid = new LinkedHashMap<>();
    private final Map<UUID, PendingInstanceSpawnTeleport> pendingInstanceSpawnTeleportsByPlayerUuid = new LinkedHashMap<>();
    private final Map<String, String> lastLoggedPlacementStatesByMatchId = new LinkedHashMap<>();
    private final Set<UUID> patchedRespawnPagePlayers = new HashSet<>();

    /**
     * Creates the arena match runtime service used by queue launch, match resolution, and return HUDs.
     */
    public ArenaMatchService(
        @Nonnull HytaleLogger logger,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull MatchSessionService matchSessionService,
        @Nonnull ArenaService arenaService,
        @Nonnull InstanceSpawnSlotService instanceSpawnSlotService
    ) {
        this.logger = logger;
        this.secureTravelService = secureTravelService;
        this.matchSessionService = matchSessionService;
        this.arenaService = arenaService;
        this.instanceSpawnSlotService = instanceSpawnSlotService;
    }

    public synchronized void setBackendMatchAdmissionStateReportingService(
        BackendMatchAdmissionStateReportingService backendMatchAdmissionStateReportingService
    ) {
        this.backendMatchAdmissionStateReportingService = backendMatchAdmissionStateReportingService;
    }

    /**
     * Observes player-ready events and consumes Nexori launch or return arrivals for that player.
     */
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
            handleLaunchArrival(event, playerRef, context);
            return;
        }
        if ("minigame.return".equalsIgnoreCase(flowType)) {
            handleReturnArrival(playerRef, context);
        }
    }

    /**
     * Removes disconnecting players from the active match runtime and reevaluates automatic resolution.
     */
    public synchronized void handlePlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        patchedRespawnPagePlayers.remove(playerRef.getUuid());
        pendingInstanceSpawnTeleportsByPlayerUuid.remove(playerRef.getUuid());
        String matchId = matchIdByPlayerUuid.remove(playerRef.getUuid());
        if (matchId == null) {
            return;
        }

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerRef.getUuid())) {
            return;
        }

        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match.withoutReturnedPlayer(playerRef.getUuid(), now)
            .withLastError("Player disconnected: " + event.getDisconnectReason(), now);
        updated = reconcileAdmissionLifecycle(applyAutomaticResolutionTrigger(updated, now), now);

        storeUpdatedMatchOrCloseEmptyRuntime(match, updated, now, "");
    }

    /**
     * Handles setup disconnects that happen before a launch or return arrival can finish.
     */
    public synchronized void handlePlayerSetupDisconnect(@Nonnull PlayerSetupDisconnectEvent event) {
        PendingArrival arrival = secureTravelService.peekPendingArrival(event.getUuid()).orElse(null);
        if (arrival == null) {
            return;
        }

        JsonObject context = parseContext(arrival.contextJson());
        if (context == null || !context.has("flowType")) {
            return;
        }

        String flowType = context.get("flowType").getAsString();
        if (!"minigame.launch".equalsIgnoreCase(flowType) && !"minigame.return".equalsIgnoreCase(flowType)) {
            return;
        }

        secureTravelService.removePendingArrival(event.getUuid());
        patchedRespawnPagePlayers.remove(event.getUuid());
        long now = System.currentTimeMillis();
        String reason = "Player setup disconnect before ready: " + event.getDisconnectReason();

        try {
            if ("minigame.launch".equalsIgnoreCase(flowType)) {
                LaunchContext launch = LaunchContext.from(context);
                if (ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType())) {
                    logger.atInfo().log(
                        "Nexori BACKFILL setup disconnect did not consume admission reservation "
                            + launch.admissionReservationId()
                            + " for match "
                            + launch.matchId()
                            + "; backend reservation will expire by TTL."
                    );
                    return;
                }
                ArenaActiveMatch match = matchesById.get(launch.matchId());
                if (match != null) {
                    ArenaActiveMatch updated = match.withExpectedPlayerCount(match.expectedPlayerCount() - 1, now)
                        .withLastError(reason, now);
                    updated = reconcileAdmissionLifecycle(applyAutomaticResolutionTrigger(updated, now), now);
                    storeUpdatedMatchOrCloseEmptyRuntime(match, updated, now, "");
                }
                return;
            }
        } catch (IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log("Failed to process Nexori player setup disconnect context.");
        }
    }

    /**
     * Advances per-player match runtime such as elimination handling and pending lobby returns.
     */
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

        observePendingPlacementReady(ref, store, playerRef);
        applyPendingInstanceSpawnTeleport(ref, store, playerRef);

        ArenaActiveMatch updated = match;
        boolean useBuiltInDeathElimination =
            LastPlayerAliveArenaMatchResolutionTrigger.ID.equalsIgnoreCase(updated.matchResolutionTriggerId());
        DeathComponent deathComponent = store.getComponent(ref, DeathComponent.getComponentType());
        if (useBuiltInDeathElimination && deathComponent != null) {
            patchActiveRespawnPageForLastPlayerAlive(ref, store, player, playerRef);
        } else {
            patchedRespawnPagePlayers.remove(playerRef.getUuid());
        }
        if (useBuiltInDeathElimination
            && !updated.isPlayerEliminated(playerRef.getUuid())
            && deathComponent != null) {
            updated = updated.withEliminatedPlayer(
                playerRef.getUuid(),
                nowEpochMs + ELIMINATED_RETURN_DELAY_MS,
                nowEpochMs
            );
            playerRef.sendMessage(Message.raw("You were eliminated. Returning to the lobby in 5 seconds."));
        }

        updated = reconcileAdmissionLifecycle(applyAutomaticResolutionTrigger(updated, nowEpochMs), nowEpochMs);

        if (updated.hasPendingReturn(playerRef.getUuid())) {
            Long dueAt = updated.pendingReturnAtEpochMsByPlayerUuid().get(playerRef.getUuid());
            if (dueAt != null && dueAt <= nowEpochMs) {
                updated = attemptReturn(updated, playerRef, ref, store, nowEpochMs);
            }
        }

        storeUpdatedMatchOrCloseEmptyRuntime(match, updated, nowEpochMs, "");
    }

    /**
     * Lists the currently tracked active matches.
     */
    @Nonnull
    public synchronized List<ArenaActiveMatch> listMatches() {
        return matchesById.values().stream()
            .sorted(Comparator.comparing(ArenaActiveMatch::matchId))
            .toList();
    }

    /**
     * Finds an active match by id.
     */
    @Nonnull
    public synchronized Optional<ArenaActiveMatch> find(@Nonnull String rawMatchId) {
        if (rawMatchId == null || rawMatchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchesById.get(rawMatchId.trim().toLowerCase()));
    }

    /**
     * Returns the countdown state that should be rendered for a player who is waiting to return to the lobby.
     */
    @Nonnull
    public synchronized Optional<ReturnHudState> findReturnHudState(@Nonnull UUID playerUuid, long nowEpochMs) {
        String matchId = matchIdByPlayerUuid.get(playerUuid);
        if (matchId == null || matchId.isBlank()) {
            return Optional.empty();
        }

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPendingReturn(playerUuid)) {
            return Optional.empty();
        }

        Long returnAtEpochMs = match.pendingReturnAtEpochMsByPlayerUuid().get(playerUuid);
        if (returnAtEpochMs == null || returnAtEpochMs <= 0L) {
            return Optional.empty();
        }

        String arenaDisplayName = arenaService.find(match.arenaId())
            .map(ArenaDefinition::displayName)
            .orElse(match.arenaId());

        String outcomeLabel;
        if (playerUuid.toString().equalsIgnoreCase(match.winnerPlayerUuid())) {
            outcomeLabel = "Victory";
        } else if (match.isPlayerEliminated(playerUuid)) {
            outcomeLabel = "Eliminated";
        } else {
            outcomeLabel = "Match Complete";
        }

        return Optional.of(new ReturnHudState(
            match.matchId(),
            match.queueId(),
            arenaDisplayName,
            outcomeLabel,
            returnAtEpochMs,
            Math.max(0L, returnAtEpochMs - nowEpochMs)
        ));
    }

    /**
     * Resolves a player token within an active match into the runtime player UUID tracked by Nexori.
     */
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

    /**
     * Finds the active match currently associated with a player UUID.
     */
    @Nonnull
    public synchronized Optional<String> findActiveMatchId(@Nonnull UUID playerUuid) {
        String matchId = matchIdByPlayerUuid.get(playerUuid);
        if (matchId == null || matchId.isBlank()) {
            return Optional.empty();
        }

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerUuid)) {
            return Optional.empty();
        }
        return Optional.of(match.matchId());
    }

    /**
     * Returns the public runtime snapshot for an active match.
     */
    @Nonnull
    public synchronized Optional<ActiveMatchInfo> findActiveMatchInfo(@Nonnull String rawMatchId) {
        ArenaActiveMatch match = find(rawMatchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(toActiveMatchInfo(match));
    }

    /**
     * Returns the runtime placement state for an active match.
     */
    @Nonnull
    public synchronized Optional<MatchPlacementState> findMatchPlacementState(@Nonnull String rawMatchId) {
        ArenaActiveMatch match = find(rawMatchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }

        int expectedPlayers = match.expectedPlayerCount();
        int arrivedPlayers = countArrivedInitialPlayers(match);
        int placedPlayers = countPlacedInitialPlayers(match);
        boolean placementComplete = isPlacementComplete(match);

        maybeLogPlacementState(
            match,
            expectedPlayers,
            arrivedPlayers,
            placedPlayers,
            placementComplete
        );

        return Optional.of(new MatchPlacementState(
            expectedPlayers,
            arrivedPlayers,
            placedPlayers,
            placementComplete
        ));
    }

    /**
     * Returns the configured resolution trigger id for an active match.
     */
    @Nonnull
    public synchronized Optional<String> findMatchResolutionTriggerId(@Nonnull String rawMatchId) {
        return find(rawMatchId)
            .map(ArenaActiveMatch::matchResolutionTriggerId);
    }

    @Nonnull
    public synchronized CloseMatchAdmissionResult closeMatchAdmission(
        @Nonnull String rawMatchId,
        CloseMatchAdmissionReason reason,
        @Nonnull String rawMessage
    ) {
        String matchId;
        try {
            matchId = NexoriMatchIds.normalizeRequiredMatchId(rawMatchId, "Match id cannot be blank.");
        } catch (IllegalArgumentException exception) {
            return new CloseMatchAdmissionResult(
                CloseMatchAdmissionOutcome.MATCH_MISSING,
                "",
                false,
                "Match id cannot be blank."
            );
        }
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return CloseMatchAdmissionResult.matchMissing(matchId);
        }
        if (reason == null) {
            return CloseMatchAdmissionResult.invalidReason(matchId, "Close reason cannot be null.");
        }
        if (match.effectiveMatchSource() != ArenaMatchSource.BACKEND_DRIVEN) {
            return CloseMatchAdmissionResult.matchNotBackendDriven(matchId);
        }
        if (match.explicitAdmissionClosed()) {
            return CloseMatchAdmissionResult.alreadyClosed(matchId, true, "Admission was already closed locally.");
        }
        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match.withExplicitAdmissionClosed(closeAdmissionReasonId(reason), normalizeOptional(rawMessage), now, now);
        matchesById.put(matchId, updated);
        boolean reportingAvailable = backendMatchAdmissionStateReportingService != null
            && backendMatchAdmissionStateReportingService.isMatchStateReportingEnabled();
        if (backendMatchAdmissionStateReportingService != null) {
            backendMatchAdmissionStateReportingService.markMatchDirty(matchId, closeAdmissionReasonId(reason), now);
        }
        return reportingAvailable
            ? CloseMatchAdmissionResult.closed(matchId, true, "Admission was closed locally.")
            : CloseMatchAdmissionResult.reportingDisabled(matchId, true, "Admission was closed locally, but backend may not have been notified.");
    }

    /**
     * Returns the rules engine id for an active match.
     */
    @Nonnull
    public synchronized Optional<String> findRulesEngineId(@Nonnull String rawMatchId) {
        return find(rawMatchId)
            .map(ArenaActiveMatch::rulesEngineId);
    }

    /**
     * Returns the exact player set a rules mod must include in submitMatchResult.
     */
    @Nonnull
    public synchronized Optional<MatchResultRequirements> findMatchResultRequirements(@Nonnull String rawMatchId) {
        ArenaActiveMatch match = find(rawMatchId).orElse(null);
        if (match == null) {
            return Optional.empty();
        }
        return Optional.of(new MatchResultRequirements(
            match.matchId(),
            match.queueId(),
            match.arenaId(),
            buildRequiredResultPlayerUuids(match),
            match.expectedPlayerUuids(),
            match.arrivedPlayerUuids(),
            match.activePlayerUuids(),
            match.eliminatedPlayerUuids()
        ));
    }

    /**
     * Stores or replaces one player's accumulated outcome without returning the player or reporting results.
     */
    @Nonnull
    public synchronized SetPlayerOutcomeResult setPlayerOutcome(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid,
        @Nonnull ArenaPlayerResolutionOutcome outcome,
        @Nonnull String backendOutcome,
        @Nonnull String rawReason
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetPlayerOutcomeResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetPlayerOutcomeResult.matchAlreadyCompleted(match, playerUuid);
        }
        if (playerUuid == null || !match.hasPlayer(playerUuid)) {
            return SetPlayerOutcomeResult.playerMissing(match, playerUuid);
        }
        if (outcome == null) {
            return SetPlayerOutcomeResult.invalidOutcome(match, playerUuid, "Outcome cannot be null.");
        }
        String reason = normalizeOptional(rawReason);
        if (reason.length() > MAX_RESULT_PLAYER_REASON_LENGTH) {
            return SetPlayerOutcomeResult.invalidReason(
                match,
                playerUuid,
                "Player result reason exceeds " + MAX_RESULT_PLAYER_REASON_LENGTH + " characters."
            );
        }
        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match.withPlayerOutcome(playerUuid, outcome, normalizeOptional(backendOutcome, outcome.name()), reason, now);
        matchesById.put(updated.matchId(), updated);
        return SetPlayerOutcomeResult.updated(updated, playerUuid, outcome);
    }

    /**
     * Stores logical spectator state for one player without changing outcome or transport.
     */
    @Nonnull
    public synchronized SetPlayerSpectatorResult setPlayerSpectator(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid,
        boolean spectator,
        @Nonnull String rawReason
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetPlayerSpectatorResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetPlayerSpectatorResult.matchAlreadyCompleted(match, playerUuid, spectator);
        }
        if (playerUuid == null || !match.hasPlayer(playerUuid)) {
            return SetPlayerSpectatorResult.playerMissing(match, playerUuid, spectator);
        }
        String reason = normalizeOptional(rawReason);
        if (reason.length() > MAX_RESULT_PLAYER_REASON_LENGTH) {
            return SetPlayerSpectatorResult.invalidReason(
                match,
                playerUuid,
                spectator,
                "Spectator reason exceeds " + MAX_RESULT_PLAYER_REASON_LENGTH + " characters."
            );
        }
        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match.withSpectatorPlayer(playerUuid, spectator, now);
        if (!reason.isBlank()) {
            updated = updated.withLastError(reason, now);
        }
        matchesById.put(updated.matchId(), updated);
        return SetPlayerSpectatorResult.updated(updated, playerUuid, spectator);
    }

    /**
     * Schedules one player's return without changing outcome or completing the match.
     */
    @Nonnull
    public synchronized ReturnPlayerResult returnPlayerToLobby(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid,
        int delaySeconds,
        @Nonnull String rawReason
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return ReturnPlayerResult.matchMissing(matchId);
        }
        if (playerUuid == null || !match.hasPlayer(playerUuid)) {
            return ReturnPlayerResult.playerMissing(match, playerUuid);
        }
        if (delaySeconds < 0) {
            return ReturnPlayerResult.invalidDelay(match, playerUuid, "return delay cannot be negative.");
        }
        String reason = normalizeOptional(rawReason);
        if (reason.length() > MAX_RESULT_PLAYER_REASON_LENGTH) {
            return ReturnPlayerResult.invalidReason(
                match,
                playerUuid,
                "Return reason exceeds " + MAX_RESULT_PLAYER_REASON_LENGTH + " characters."
            );
        }
        long now = System.currentTimeMillis();
        long returnAtEpochMs = now + (Math.max(delaySeconds, 0) * 1000L);
        ArenaActiveMatch updated = match.withPendingReturn(playerUuid, returnAtEpochMs, now);
        if (!reason.isBlank()) {
            updated = updated.withLastError(reason, now);
        }
        matchesById.put(updated.matchId(), updated);
        return ReturnPlayerResult.scheduled(updated, playerUuid, returnAtEpochMs);
    }

    /**
     * Completes a match using accumulated outcomes, without moving players.
     */
    @Nonnull
    public synchronized SubmitMatchResult submitFinalMatchResult(
        @Nonnull String rawMatchId,
        @Nonnull String rawReason,
        JsonObject rawCustomData
    ) {
        return submitFinalMatchResult(rawMatchId, rawReason, Map.of(), rawCustomData);
    }

    /**
     * Completes a whole match from an external rules mod while keeping local completion independent from backend reporting.
     */
    @Nonnull
    public synchronized SubmitMatchResult submitMatchResult(
        @Nonnull String rawMatchId,
        @Nonnull List<SubmitMatchPlayerResult> rawPlayerResults,
        @Nonnull Map<String, String> rawMetadata,
        int returnDelaySeconds,
        @Nonnull String rawReason
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SubmitMatchResult.matchMissing(matchId);
        }

        MatchResultValidationResult validation = matchResultValidator.validateSubmittedResult(
            match,
            toValidationPlayerResults(rawPlayerResults),
            rawMetadata,
            rawReason,
            returnDelaySeconds
        );
        if (!validation.valid()) {
            return SubmitMatchResult.invalid(match, validation.message());
        }

        if (match.hasSubmittedResult()) {
            String payloadHash = hashFinalSubmittedResult(
                match,
                toSubmitMatchPlayerResults(validation.players()),
                validation.metadata(),
                validation.reason(),
                metadataToJson(validation.metadata())
            );
            boolean samePayload = match.resultPayloadHash().equals(payloadHash);
            return SubmitMatchResult.alreadySubmitted(match, payloadHash, !samePayload);
        }
        if (match.hasCompleted()) {
            return SubmitMatchResult.invalid(match, "Match result was already submitted.");
        }

        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match;
        for (MatchResultValidationResult.PlayerResult playerResult : validation.players()) {
            updated = updated.withPlayerOutcome(
                playerResult.playerUuid(),
                playerResult.runtimeOutcome(),
                playerResult.backendOutcome(),
                playerResult.reason(),
                now
            );
        }
        matchesById.put(updated.matchId(), updated);
        SubmitMatchResult result = submitFinalMatchResult(updated.matchId(), validation.reason(), validation.metadata(), metadataToJson(validation.metadata()));
        if (result.outcome() != SubmitMatchOutcome.ACCEPTED) {
            return result;
        }

        long delayMillis = Math.max(returnDelaySeconds, 0) * 1000L;
        ArenaActiveMatch returned = result.activeMatch();
        for (MatchResultValidationResult.PlayerResult playerResult : validation.players()) {
            returned = returned.withPendingReturn(playerResult.playerUuid(), now + delayMillis, now);
        }
        matchesById.put(returned.matchId(), returned);
        return SubmitMatchResult.accepted(
            returned,
            result.players(),
            result.metadata(),
            result.customData(),
            result.reason(),
            result.resultPayloadHash(),
            result.endedAtEpochMs()
        );
    }

    @Nonnull
    private SubmitMatchResult submitFinalMatchResult(
        @Nonnull String rawMatchId,
        @Nonnull String rawReason,
        @Nonnull Map<String, String> metadata,
        JsonObject rawCustomData
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SubmitMatchResult.matchMissing(matchId);
        }

        MatchResultValidationResult validation = matchResultValidator.validateFinalResult(match, rawReason, metadata, rawCustomData);
        if (!validation.valid()) {
            return SubmitMatchResult.invalid(match, validation.message());
        }

        String payloadHash = hashFinalSubmittedResult(
            match,
            toSubmitMatchPlayerResults(validation.players()),
            validation.metadata(),
            validation.reason(),
            validation.customData()
        );
        if (match.hasSubmittedResult()) {
            boolean samePayload = match.resultPayloadHash().equals(payloadHash);
            return SubmitMatchResult.alreadySubmitted(match, payloadHash, !samePayload);
        }
        if (match.hasCompleted()) {
            return SubmitMatchResult.invalid(match, "Match result was already submitted.");
        }

        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match.withSubmittedResult(now, now, payloadHash);
        if (!validation.reason().isBlank()) {
            updated = updated.withLastError(validation.reason(), now);
        }
        matchesById.put(updated.matchId(), updated);
        maybeScheduleAdmissionReporting(match, updated, now, "");
        return SubmitMatchResult.accepted(
            updated,
            toSubmitMatchPlayerResults(validation.players()),
            validation.metadata(),
            validation.customData(),
            validation.reason(),
            payloadHash,
            now
        );
    }

    /**
     * Records the resolved outcome for one player in an active match and schedules the return countdown.
     */
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
        ArenaActiveMatch updated = match.withPlayerOutcome(playerUuid, outcome, outcome.name(), reason, now)
            .withPendingReturn(playerUuid, now + delayMillis, now);
        if (!reason.isBlank()) {
            updated = updated.withLastError(reason, now);
        }
        updated = reconcileAdmissionLifecycle(updated, now);
        matchesById.put(updated.matchId(), updated);
        maybeScheduleAdmissionReporting(match, updated, now, "");
        return ResolvePlayerResult.updated(updated, playerUuid, outcome);
    }

    /**
     * Forces an active match to end and schedules every remaining player to return immediately.
     */
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
        updated = updated.withLastError("Manual match end requested: " + returnReason, now)
            .withCompleted(now, now);
        matchesById.put(updated.matchId(), updated);
        maybeScheduleAdmissionReporting(match, updated, now, "");
        return EndMatchResult.completed(match.matchId(), updated.pendingReturnAtEpochMsByPlayerUuid().size());
    }

    @Nonnull
    private List<UUID> buildRequiredResultPlayerUuids(@Nonnull ArenaActiveMatch match) {
        if (!match.expectedPlayerUuids().isEmpty()) {
            return match.expectedPlayerUuids();
        }
        LinkedHashSet<UUID> required = new LinkedHashSet<>();
        required.addAll(match.arrivedPlayerUuids());
        required.addAll(match.activePlayerUuids());
        required.addAll(match.eliminatedPlayerUuids());
        return List.copyOf(required);
    }

    @Nonnull
    private String hashSubmittedResult(
        @Nonnull ArenaActiveMatch match,
        @Nonnull List<SubmitMatchPlayerResult> playerResults,
        @Nonnull Map<String, String> metadata,
        @Nonnull String reason,
        int returnDelaySeconds
    ) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("matchId=").append(match.matchId()).append('\n');
        canonical.append("queueId=").append(match.queueId()).append('\n');
        canonical.append("arenaId=").append(match.arenaId()).append('\n');
        canonical.append("externalMatchId=").append(match.externalMatchId()).append('\n');
        canonical.append("reason=").append(reason).append('\n');
        canonical.append("returnDelaySeconds=").append(Math.max(returnDelaySeconds, 0)).append('\n');
        playerResults.stream()
            .sorted(Comparator.comparing(result -> result.playerUuid().toString()))
            .forEach(result -> canonical
                .append("player=")
                .append(result.playerUuid())
                .append('|')
                .append(result.backendOutcome())
                .append('|')
                .append(result.reason())
                .append('\n'));
        metadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> canonical
                .append("metadata=")
                .append(entry.getKey())
                .append('|')
                .append(entry.getValue())
                .append('\n'));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    @Nonnull
    private String hashFinalSubmittedResult(
        @Nonnull ArenaActiveMatch match,
        @Nonnull List<SubmitMatchPlayerResult> playerResults,
        @Nonnull Map<String, String> metadata,
        @Nonnull String reason,
        @Nonnull JsonObject customData
    ) {
        StringBuilder canonical = new StringBuilder();
        canonical.append("matchId=").append(match.matchId()).append('\n');
        canonical.append("queueId=").append(match.queueId()).append('\n');
        canonical.append("arenaId=").append(match.arenaId()).append('\n');
        canonical.append("externalMatchId=").append(match.externalMatchId()).append('\n');
        canonical.append("rulesEngineId=").append(match.rulesEngineId()).append('\n');
        canonical.append("reason=").append(reason).append('\n');
        playerResults.stream()
            .sorted(Comparator.comparing(result -> result.playerUuid().toString()))
            .forEach(result -> canonical
                .append("player=")
                .append(result.playerUuid())
                .append('|')
                .append(result.backendOutcome())
                .append('|')
                .append(result.reason())
                .append('\n'));
        metadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> canonical
                .append("metadata=")
                .append(entry.getKey())
                .append('|')
                .append(entry.getValue())
                .append('\n'));
        canonical.append("customData=").append(GSON.toJson(customData)).append('\n');
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    @Nonnull
    private ActiveMatchInfo toActiveMatchInfo(@Nonnull ArenaActiveMatch match) {
        List<PlayerOutcomeState> outcomes = new ArrayList<>();
        for (ArenaActiveMatch.ArenaPlayerOutcomeState outcome : match.canonicalPlayerOutcomes()) {
            outcomes.add(new PlayerOutcomeState(
                outcome.playerUuid(),
                outcome.outcome(),
                outcome.backendOutcome(),
                outcome.reason(),
                outcome.updatedAtEpochMs()
            ));
        }
        return new ActiveMatchInfo(
            match.matchId(),
            match.queueId(),
            match.arenaId(),
            match.assignmentId(),
            match.externalMatchId(),
            match.rulesEngineId(),
            match.matchResolutionTriggerId(),
            match.expectedPlayerUuids(),
            match.arrivedPlayerUuids(),
            match.activePlayerUuids(),
            match.eliminatedPlayerUuids(),
            match.spectatorPlayerUuids(),
            buildRequiredResultPlayerUuids(match),
            List.copyOf(outcomes),
            match.expectedPlayerCount(),
            match.completedAtEpochMs(),
            match.resultSubmittedAtEpochMs()
        );
    }

    @Nonnull
    private JsonObject metadataToJson(@Nonnull Map<String, String> metadata) {
        JsonObject root = new JsonObject();
        metadata.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> root.addProperty(entry.getKey(), entry.getValue()));
        return root;
    }

    @Nonnull
    private List<MatchResultValidationResult.PlayerResult> toValidationPlayerResults(List<SubmitMatchPlayerResult> rawPlayerResults) {
        if (rawPlayerResults == null || rawPlayerResults.isEmpty()) {
            return List.of();
        }
        List<MatchResultValidationResult.PlayerResult> results = new ArrayList<>();
        for (SubmitMatchPlayerResult playerResult : rawPlayerResults) {
            if (playerResult == null) {
                results.add(null);
                continue;
            }
            results.add(new MatchResultValidationResult.PlayerResult(
                playerResult.playerUuid(),
                playerResult.runtimeOutcome(),
                playerResult.backendOutcome(),
                playerResult.reason()
            ));
        }
        return List.copyOf(results);
    }

    @Nonnull
    private List<SubmitMatchPlayerResult> toSubmitMatchPlayerResults(@Nonnull List<MatchResultValidationResult.PlayerResult> rawPlayerResults) {
        if (rawPlayerResults.isEmpty()) {
            return List.of();
        }
        List<SubmitMatchPlayerResult> results = new ArrayList<>();
        for (MatchResultValidationResult.PlayerResult playerResult : rawPlayerResults) {
            results.add(new SubmitMatchPlayerResult(
                playerResult.playerUuid(),
                playerResult.runtimeOutcome(),
                playerResult.backendOutcome(),
                playerResult.reason()
            ));
        }
        return List.copyOf(results);
    }

    private void handleLaunchArrival(@Nonnull PlayerReadyEvent event, @Nonnull PlayerRef playerRef, @Nonnull JsonObject context) {
        LaunchContext launch;
        try {
            launch = LaunchContext.from(context);
        } catch (IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log(
                "Rejected Nexori minigame launch arrival for player "
                    + playerRef.getUuid()
                    + ": invalid launch context."
            );
            return;
        }
        long now = System.currentTimeMillis();
        ArenaActiveMatch existing = matchesById.get(launch.matchId());
        boolean backfillArrival = ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType());
        if (!backfillArrival
            && !launch.expectedPlayerUuids().isEmpty()
            && !launch.expectedPlayerUuids().contains(playerRef.getUuid())) {
            logRejectedLaunchArrival(playerRef.getUuid(), launch.matchId(), "player is not in expectedPlayerUuids");
            return;
        }
        if (backfillArrival && existing == null) {
            logRejectedLaunchArrival(playerRef.getUuid(), launch.matchId(), "backfill launch requires an existing match");
            return;
        }
        if (existing != null) {
            Optional<String> inconsistency = findLaunchContextInconsistency(existing, launch);
            if (inconsistency.isPresent()) {
                logRejectedLaunchArrival(playerRef.getUuid(), launch.matchId(), inconsistency.get());
                return;
            }
            if (!backfillArrival && !existing.expectsPlayer(playerRef.getUuid())) {
                logRejectedLaunchArrival(playerRef.getUuid(), launch.matchId(), "player is not in stored expectedPlayerUuids");
                return;
            }
        }
        if (backfillArrival) {
            BackfillAdmissionDecision decision = backfillAdmissionDecider.decide(
                existing,
                launch.assignmentType(),
                launch.playerUuid(),
                playerRef.getUuid(),
                launch.admissionReservationId(),
                launch.admissionExpiresAtEpochMs(),
                now
            );
            if (decision.rejected()) {
                logRejectedLaunchArrival(playerRef.getUuid(), launch.matchId(), decision.reason());
                return;
            }
        }
        String instanceWorldName = launch.usesInstanceTemplate()
            ? ArenaInstanceRuntime.buildInstanceWorldName(launch.matchId())
            : "";
        ArenaActiveMatch updated = existing == null
            ? new ArenaActiveMatch(
                launch.matchId(),
                launch.queueId(),
                launch.arenaId(),
                launch.originLobbyId(),
                launch.returnConnectionAddress(),
                launch.returnFallbackTargetId(),
                launch.launchTravelProfileId(),
                launch.instanceTemplateId(),
                instanceWorldName,
                launch.matchResolutionTriggerId(),
                launch.rulesEngineId(),
                launch.assignmentId(),
                launch.assignmentType(),
                launch.externalMatchId(),
                launch.matchSource(),
                launch.admissionPolicySchemaVersion(),
                launch.admissionCapacity(),
                launch.backfillEnabled(),
                launch.backfillMode(),
                launch.backfillWindowSeconds(),
                launch.expectedPlayerUuids(),
                launch.expectedPlayerCount(),
                List.of(playerRef.getUuid()),
                List.of(playerRef.getUuid()),
                List.<UUID>of(),
                List.<UUID>of(),
                launch.assignmentId().isBlank() ? Map.of() : Map.of(playerRef.getUuid(), launch.assignmentId()),
                Map.of(playerRef.getUuid(), launch.playerReturnTarget()),
                Map.<UUID, ArenaActiveMatch.ArenaPlayerOutcomeState>of(),
                Map.<UUID, Long>of(),
                0,
                Set.<String>of(),
                false,
                "",
                "",
                0L,
                "",
                0L,
                0L,
                0L,
                0L,
                "",
                now,
                now,
                ""
            ).normalized()
            : existing
                .withPlayerReturnTarget(playerRef.getUuid(), launch.playerReturnTarget(), now)
                .withPlayerAssignmentId(playerRef.getUuid(), launch.assignmentId(), now)
                .withPlayerArrival(playerRef.getUuid(), now);
        if (backfillArrival) {
            updated = updated
                .withAcceptedBackfillReservation(launch.admissionReservationId(), now)
                .withConsumedBackfillAdmissionIncrement(now);
            if (backendMatchAdmissionStateReportingService != null) {
                backendMatchAdmissionStateReportingService.markAdmissionReservationConsumed(updated.matchId(), launch.admissionReservationId(), now);
            }
        }

        // A player can only belong to one active match at a time.
        String previousMatchId = matchIdByPlayerUuid.put(playerRef.getUuid(), updated.matchId());
        if (previousMatchId != null && !previousMatchId.equals(updated.matchId())) {
            ArenaActiveMatch previous = matchesById.get(previousMatchId);
            if (previous != null) {
                ArenaActiveMatch previousUpdated = previous.withoutReturnedPlayer(playerRef.getUuid(), now);
                storeUpdatedMatchOrCloseEmptyRuntime(previous, previousUpdated, now, "");
            }
        }

        updated = reconcileAdmissionLifecycle(applyAutomaticResolutionTrigger(updated, now), now);
        matchesById.put(updated.matchId(), updated);
        maybeScheduleAdmissionReporting(existing, updated, now, backfillArrival ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED");
        rememberPendingInstanceSpawnTeleport(event.getPlayerRef(), playerRef.getUuid(), launch, updated);
        if (backfillArrival) {
            issueBackfillInstancePlacement(event.getPlayerRef(), playerRef);
        }
        playerRef.sendMessage(Message.raw(
            "Joined Nexori match " + updated.matchId() + " on arena " + updated.arenaId() + "."
        ));
    }

    @Nonnull
    private String closeAdmissionReasonId(@Nonnull CloseMatchAdmissionReason reason) {
        return switch (reason) {
            case MOD_REQUEST -> "EXPLICIT_MOD_REQUEST";
            case GAME_PHASE_LOCKED -> "EXPLICIT_GAME_PHASE_LOCKED";
            case ROSTER_LOCKED -> "EXPLICIT_ROSTER_LOCKED";
            case ADMIN_FORCED -> "EXPLICIT_ADMIN_FORCED";
        };
    }

    @Nonnull
    private Optional<String> findLaunchContextInconsistency(
        @Nonnull ArenaActiveMatch existing,
        @Nonnull LaunchContext launch
    ) {
        if (!existing.queueId().equals(launch.queueId())) {
            return Optional.of("queueId mismatch");
        }
        if (!existing.arenaId().equals(launch.arenaId())) {
            return Optional.of("arenaId mismatch");
        }
        if (optionalIdentityMismatch(existing.externalMatchId(), launch.externalMatchId())) {
            return Optional.of("externalMatchId mismatch");
        }
        if (ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType())) {
            return Optional.empty();
        }
        if (optionalIdentityMismatch(existing.rulesEngineId(), launch.rulesEngineId())) {
            return Optional.of("rulesEngineId mismatch");
        }
        if (existing.admissionPolicySchemaVersion() != launch.admissionPolicySchemaVersion()) {
            return Optional.of("admissionPolicySchemaVersion mismatch");
        }
        if (existing.effectiveMatchSource() != ArenaMatchSource.tryParse(launch.matchSource()).orElse(ArenaMatchSource.defaultSource())) {
            return Optional.of("matchSource mismatch");
        }
        if (existing.admissionCapacity() != launch.admissionCapacity()) {
            return Optional.of("admissionCapacity mismatch");
        }
        if (existing.backfillEnabled() != launch.backfillEnabled()) {
            return Optional.of("backfillEnabled mismatch");
        }
        if (existing.effectiveBackfillMode() != QueueBackfillMode.tryParse(launch.backfillMode()).orElse(QueueBackfillMode.NONE)) {
            return Optional.of("backfillMode mismatch");
        }
        if (existing.backfillWindowSeconds() != launch.backfillWindowSeconds()) {
            return Optional.of("backfillWindowSeconds mismatch");
        }
        if (!existing.expectedPlayerUuids().isEmpty()
            && !launch.expectedPlayerUuids().isEmpty()
            && !PlayerUuidLists.sameCanonicalPlayers(existing.expectedPlayerUuids(), launch.expectedPlayerUuids())) {
            return Optional.of("expectedPlayerUuids mismatch");
        }
        return Optional.empty();
    }

    private void logRejectedLaunchArrival(@Nonnull UUID playerUuid, @Nonnull String matchId, @Nonnull String reason) {
        logger.atWarning().log(
            "Rejected Nexori minigame launch arrival for player "
                + playerUuid
                + ": inconsistent launch context for match "
                + matchId
                + ". "
                + reason
                + "."
        );
    }

    private void rememberPendingInstanceSpawnTeleport(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull UUID playerUuid,
        @Nonnull LaunchContext launch,
        @Nonnull ArenaActiveMatch match
    ) {
        pendingInstanceSpawnTeleportsByPlayerUuid.remove(playerUuid);
        if (!ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType())) {
            return;
        }
        if (match.instanceTemplateId().isBlank()
            || ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(match.instanceTemplateId())) {
            return;
        }
        if (match.instanceWorldName().isBlank()) {
            logger.atWarning().log(
                "Cannot queue Nexori BACKFILL instance placement for player "
                    + playerUuid
                    + " on match "
                    + match.matchId()
                    + ": match has no instance world name."
            );
            return;
        }

        List<InstanceSpawnSlotDefinition> slots = instanceSpawnSlotService.listByInstanceTemplateId(match.instanceTemplateId());
        Transform transform;
        int initialRosterSize = Math.max(match.expectedPlayerCount(), match.expectedPlayerUuids().size());
        int launchIndex = Math.max(0, initialRosterSize + match.consumedBackfillAdmissionCount() - 1);
        if (slots.isEmpty()) {
            transform = resolveCurrentPlayerTransform(playerEntityRef).orElse(null);
            if (transform == null) {
                logger.atWarning().log(
                    "Cannot queue Nexori BACKFILL instance placement for player "
                        + playerUuid
                        + " on match "
                        + match.matchId()
                        + ": no spawn slots for template "
                        + match.instanceTemplateId()
                        + " and current player transform is unavailable."
                );
                return;
            }
            logger.atWarning().log(
                "Queued Nexori BACKFILL instance placement fallback for player "
                    + playerUuid
                    + " on match "
                    + match.matchId()
                    + ": no spawn slots for template "
                    + match.instanceTemplateId()
                    + "; using current arrival transform."
                    + "."
            );
        } else {
            InstanceSpawnSlotDefinition slot = slots.get(launchIndex % slots.size());
            transform = new Transform(
                slot.x(),
                slot.y(),
                slot.z(),
                slot.pitch(),
                slot.yaw(),
                slot.roll()
            );
        }

        pendingInstanceSpawnTeleportsByPlayerUuid.put(
            playerUuid,
            new PendingInstanceSpawnTeleport(
                match.instanceWorldName(),
                match.instanceTemplateId(),
                transform,
                PlacementPhase.PENDING_ISSUE,
                false,
                0L,
                0L,
                0
            )
        );
        logger.atInfo().log(
            "Queued Nexori BACKFILL instance placement for player "
                + playerUuid
                + " matchId="
                + match.matchId()
                + " templateId="
                + match.instanceTemplateId()
                + " world="
                + match.instanceWorldName()
                + " launchIndex="
                + launchIndex
                + " spawnSlotSource="
                + (slots.isEmpty() ? "arrival_transform" : "configured_slot")
                + "."
        );
    }

    @Nonnull
    private Optional<Transform> resolveCurrentPlayerTransform(@Nonnull Ref<EntityStore> playerEntityRef) {
        Store<EntityStore> store = playerEntityRef.getStore();
        TransformComponent transformComponent = store.getComponent(playerEntityRef, TransformComponent.getComponentType());
        if (transformComponent == null) {
            return Optional.empty();
        }

        Vector3f rotation = transformComponent.getRotation();
        HeadRotation headRotation = store.getComponent(playerEntityRef, HeadRotation.getComponentType());
        if (headRotation != null) {
            rotation = headRotation.getRotation();
        }
        return Optional.of(new Transform(transformComponent.getPosition(), rotation));
    }

    private void applyPendingInstanceSpawnTeleport(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PendingInstanceSpawnTeleport pending = pendingInstanceSpawnTeleportsByPlayerUuid.get(playerRef.getUuid());
        if (pending == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (player == null || player.getWorld() == null || transformComponent == null) {
            return;
        }

        World currentWorld = player.getWorld();

        long nowEpochMs = System.currentTimeMillis();
        double distanceSquared = transformComponent.getPosition().distanceSquaredTo(pending.transform().getPosition());
        boolean withinTolerance = distanceSquared <= INITIAL_PLACEMENT_POSITION_EPSILON_SQUARED;
        boolean teleportPending = store.getComponent(ref, Teleport.getComponentType()) != null;
        String matchId = matchIdByPlayerUuid.get(playerRef.getUuid());

        if (pending.phase() == PlacementPhase.CONFIRMED || pending.phase() == PlacementPhase.FALLBACK) {
            return;
        }

        if (pending.phase() == PlacementPhase.PENDING_ISSUE) {
            if (teleportPending) {
                logger.atInfo().log(
                    "NEXORI_PLACEMENT_REPLACING_PENDING_TELEPORT player=" + playerRef.getUsername()
                        + " matchId=" + normalizeOptional(matchId, "<unknown>")
                        + " templateId=" + pending.instanceTemplateId()
                        + " world=" + pending.expectedWorldName()
                );
            }
            World targetWorld = Universe.get().getWorld(pending.expectedWorldName());
            if (targetWorld == null) {
                PendingInstanceSpawnTeleport fallback = pending.withPhase(PlacementPhase.FALLBACK);
                pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), fallback);
                logger.atWarning().log(
                    "NEXORI_PLACEMENT_FALLBACK player=" + playerRef.getUsername()
                        + " matchId=" + normalizeOptional(matchId, "<unknown>")
                        + " templateId=" + pending.instanceTemplateId()
                        + " world=" + pending.expectedWorldName()
                        + " reason=missing_world"
                );
                return;
            }
            PendingInstanceSpawnTeleport issued = pending
                .withPhase(PlacementPhase.WAITING_FOR_POST_READY)
                .withTeleportIssued(true, nowEpochMs)
                .withStableTicks(0);
            pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), issued);
            PendingInstanceSpawnTeleport issuedFinal = issued;
            Teleport teleport = Teleport.createForPlayer(targetWorld, pending.transform().clone());
            targetWorld.execute(() -> {
                store.addComponent(ref, Teleport.getComponentType(), teleport);
                logger.atInfo().log(
                    "NEXORI_PLACEMENT_ISSUED player=" + playerRef.getUsername()
                        + " matchId=" + normalizeOptional(matchId, "<unknown>")
                        + " templateId=" + issuedFinal.instanceTemplateId()
                        + " world=" + targetWorld.getName()
                        + " target=" + issuedFinal.transform().getPosition()
                        + " current=" + transformComponent.getPosition()
                );
            });
            return;
        }

        if (!currentWorld.getName().equalsIgnoreCase(pending.expectedWorldName())) {
            return;
        }

        if (pending.phase() == PlacementPhase.WAITING_FOR_POST_READY) {
            if (pending.readyObservedAtEpochMs() <= 0L) {
                return;
            }
            if (nowEpochMs - pending.readyObservedAtEpochMs() < INITIAL_PLACEMENT_POST_READY_GRACE_MS) {
                return;
            }
            if (teleportPending) {
                return;
            }

            pending = pending.withPhase(PlacementPhase.VALIDATING_SLOT).withStableTicks(0);
            pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), pending);
        }

        if (pending.phase() == PlacementPhase.VALIDATING_SLOT && !teleportPending && withinTolerance) {
            PendingInstanceSpawnTeleport stabilized = pending.withStableTicks(pending.stableTicks() + 1);
            if (stabilized.stableTicks() >= INITIAL_PLACEMENT_REQUIRED_STABLE_TICKS) {
                PendingInstanceSpawnTeleport confirmed = stabilized.withPhase(PlacementPhase.CONFIRMED);
                pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), confirmed);
                logPlacementConfirmed(playerRef, matchId, currentWorld, transformComponent, confirmed, distanceSquared);
                return;
            }
            pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), stabilized);
            return;
        }

        PendingInstanceSpawnTeleport updatedPending = pending.stableTicks() == 0
            ? pending
            : pending.withStableTicks(0);
        pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), updatedPending);

        if (pending.issuedAtEpochMs() > 0L && nowEpochMs - pending.issuedAtEpochMs() >= INITIAL_PLACEMENT_TIMEOUT_MS) {
            PendingInstanceSpawnTeleport fallback = pending.withPhase(PlacementPhase.FALLBACK);
            pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), fallback);
            logger.atWarning().log(
                "NEXORI_PLACEMENT_FALLBACK player=" + playerRef.getUsername()
                    + " matchId=" + normalizeOptional(matchId, "<unknown>")
                    + " templateId=" + pending.instanceTemplateId()
                    + " world=" + currentWorld.getName()
                    + " target=" + pending.transform().getPosition()
                    + " current=" + transformComponent.getPosition()
                    + " teleportPending=" + teleportPending
                    + " distanceSquared=" + distanceSquared
            );
        }
    }

    private void issueBackfillInstancePlacement(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull PlayerRef playerRef
    ) {
        PendingInstanceSpawnTeleport pending = pendingInstanceSpawnTeleportsByPlayerUuid.get(playerRef.getUuid());
        if (pending == null
            || pending.phase() == PlacementPhase.CONFIRMED
            || pending.phase() == PlacementPhase.FALLBACK) {
            return;
        }

        String matchId = matchIdByPlayerUuid.get(playerRef.getUuid());
        World targetWorld = Universe.get().getWorld(pending.expectedWorldName());
        if (targetWorld == null) {
            logger.atWarning().log(
                "NEXORI_PLACEMENT_FALLBACK player=" + playerRef.getUsername()
                    + " matchId=" + normalizeOptional(matchId, "<unknown>")
                    + " templateId=" + pending.instanceTemplateId()
                    + " world=" + pending.expectedWorldName()
                    + " reason=missing_world"
            );
            pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), pending.withPhase(PlacementPhase.FALLBACK));
            return;
        }

        long nowEpochMs = System.currentTimeMillis();
        PendingInstanceSpawnTeleport issued = pending
            .withPhase(PlacementPhase.WAITING_FOR_POST_READY)
            .withTeleportIssued(true, nowEpochMs)
            .withStableTicks(0);
        pendingInstanceSpawnTeleportsByPlayerUuid.put(playerRef.getUuid(), issued);
        InstancesPlugin.teleportPlayerToLoadingInstance(
            playerEntityRef,
            playerEntityRef.getStore(),
            CompletableFuture.completedFuture(targetWorld),
            issued.transform().clone()
        );
        logger.atInfo().log(
            "NEXORI_BACKFILL_INSTANCE_TELEPORT_ISSUED player=" + playerRef.getUsername()
                + " matchId=" + normalizeOptional(matchId, "<unknown>")
                + " templateId=" + issued.instanceTemplateId()
                + " world=" + targetWorld.getName()
                + " target=" + issued.transform().getPosition()
        );
    }

    private void observePendingPlacementReady(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PendingInstanceSpawnTeleport pending = pendingInstanceSpawnTeleportsByPlayerUuid.get(playerRef.getUuid());
        if (pending == null || pending.readyObservedAtEpochMs() > 0L) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return;
        }
        if (!player.getWorld().getName().equalsIgnoreCase(pending.expectedWorldName())) {
            return;
        }

        long nowEpochMs = System.currentTimeMillis();
        pendingInstanceSpawnTeleportsByPlayerUuid.put(
            playerRef.getUuid(),
            pending.withReadyObservedAtEpochMs(nowEpochMs)
        );
        logger.atInfo().log(
            "NEXORI_PLACEMENT_READY_OBSERVED player=" + playerRef.getUsername()
                + " matchId=" + normalizeOptional(matchIdByPlayerUuid.get(playerRef.getUuid()), "<unknown>")
                + " world=" + player.getWorld().getName()
                + " readyObservedAtEpochMs=" + nowEpochMs
        );
    }

    @Nonnull
    private Optional<Transform> resolveLaunchSpawnSlotTransform(
        @Nonnull LaunchContext launch,
        @Nonnull JsonObject context
    ) {
        if (!launch.usesInstanceTemplate()) {
            return Optional.empty();
        }

        int launchIndex = context.has("launchIndex")
            ? Math.max(context.get("launchIndex").getAsInt(), 0)
            : -1;
        if (launchIndex < 0) {
            return Optional.empty();
        }

        List<InstanceSpawnSlotDefinition> slots = instanceSpawnSlotService.listByInstanceTemplateId(launch.instanceTemplateId());
        if (slots.isEmpty()) {
            return Optional.empty();
        }

        InstanceSpawnSlotDefinition slot = slots.get(launchIndex % slots.size());
        return Optional.of(new Transform(
            slot.x(),
            slot.y(),
            slot.z(),
            slot.pitch(),
            slot.yaw(),
            slot.roll()
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
                "Returned from Nexori arena " + sourceArenaId + ". The lobby has now observed every launched player for match " + matchId + "."
            ));
            case ALREADY_RETURNED -> playerRef.sendMessage(Message.raw(
                "This Nexori match return had already been observed by the lobby."
            ));
            case MISSING, INVALID -> playerRef.sendMessage(Message.raw(
                "Returned from Nexori arena, but the lobby handoff record was missing or did not validate this context."
                    + (result.errorMessage().isBlank() ? "" : " " + result.errorMessage())
            ));
        }
    }

    /**
     * Applies Nexori-owned automatic resolution rules to a match runtime.
     */
    @Nonnull
    private ArenaActiveMatch applyAutomaticResolutionTrigger(@Nonnull ArenaActiveMatch match, long nowEpochMs) {
        if (match.hasWinner()) {
            UUID winnerUuid = parseWinnerUuid(match.winnerPlayerUuid());
            if (winnerUuid != null && !match.hasPendingReturn(winnerUuid) && match.hasPlayer(winnerUuid)) {
                return match.withPendingReturn(winnerUuid, nowEpochMs + WINNER_RETURN_DELAY_MS, nowEpochMs);
            }
            return match;
        }

        if (match.matchResolutionTriggerId().isBlank()
            || ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equals(match.matchResolutionTriggerId())) {
            return match;
        }

        if (LastPlayerAliveArenaMatchResolutionTrigger.ID.equalsIgnoreCase(match.matchResolutionTriggerId())) {
            return LastPlayerAliveArenaMatchResolutionTrigger.evaluate(this, match, nowEpochMs);
        }

        return match.withLastError(
            "Unknown arena match resolution trigger '" + match.matchResolutionTriggerId() + "'.",
            nowEpochMs
        );
    }

    /**
     * Marks a player as the winner inside the in-memory match runtime.
     */
    @Nonnull
    ArenaActiveMatch markPlayerWinInternal(
        @Nonnull ArenaActiveMatch match,
        @Nonnull UUID playerUuid,
        @Nonnull String reason,
        long nowEpochMs
    ) {
        ArenaActiveMatch updated = match.withWinner(playerUuid, nowEpochMs + WINNER_RETURN_DELAY_MS, nowEpochMs);
        if (reason != null && !reason.isBlank()) {
            updated = updated.withLastError(reason, nowEpochMs);
        }
        return updated;
    }

    /**
     * Marks a player as eliminated inside the in-memory match runtime.
     */
    @Nonnull
    ArenaActiveMatch markPlayerLossInternal(
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
        boolean builtInLastPlayerAlive =
            LastPlayerAliveArenaMatchResolutionTrigger.ID.equalsIgnoreCase(match.matchResolutionTriggerId());
        if (!builtInLastPlayerAlive && store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            tryRespawn(store, ref, playerRef);
            return match.withPendingReturn(playerRef.getUuid(), nowEpochMs + RETURN_RETRY_DELAY_MS, nowEpochMs);
        }

        ArenaPlayerReturnTarget playerReturnTarget = match.findPlayerReturnTarget(playerRef.getUuid());
        String returnConnectionAddress = playerReturnTarget == null
            ? match.returnConnectionAddress()
            : playerReturnTarget.returnConnectionAddress();
        String returnFallbackTargetId = playerReturnTarget == null
            ? match.returnFallbackTargetId()
            : playerReturnTarget.returnFallbackTargetId();
        String launchTravelProfileId = playerReturnTarget == null
            ? match.launchTravelProfileId()
            : playerReturnTarget.launchTravelProfileId();
        String originLobbyId = playerReturnTarget == null
            ? match.originLobbyId()
            : playerReturnTarget.originLobbyId();
        if (returnConnectionAddress.isBlank()
            || returnFallbackTargetId.isBlank()
            || launchTravelProfileId.isBlank()
            || originLobbyId.isBlank()) {
            return match.withLastError("No valid return target exists for player " + playerRef.getUuid() + ".", nowEpochMs)
                .withPendingReturn(playerRef.getUuid(), nowEpochMs + RETURN_RETRY_DELAY_MS, nowEpochMs);
        }

        ConfiguredPeer destination;
        try {
            destination = ConfiguredPeer.parse(returnConnectionAddress);
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
                returnFallbackTargetId,
                "",
                launchTravelProfileId,
                buildReturnContextJson(match, originLobbyId, returnReason, nowEpochMs)
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
            patchedRespawnPagePlayers.remove(playerRef.getUuid());
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log(
                "Failed to request respawn for eliminated Nexori player " + playerRef.getUuid() + "."
            );
        }
    }

    private void patchActiveRespawnPageForLastPlayerAlive(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef
    ) {
        if (patchedRespawnPagePlayers.contains(playerRef.getUuid())) {
            return;
        }

        PageManager pageManager = player.getPageManager();
        if (pageManager == null || pageManager.getCustomPage() == null) {
            return;
        }
        if (!RESPAWN_PAGE_CLASS_NAME.equals(pageManager.getCustomPage().getClass().getName())) {
            return;
        }

        UICommandBuilder commands = new UICommandBuilder();
        commands.set("#RespawnButton.Visible", false);
        commands.set("#RespawnButton.Disabled", true);
        commands.set("#DeathData.Visible", false);

        CustomPage patch = new CustomPage(
            RESPAWN_PAGE_CLASS_NAME,
            false,
            false,
            pageManager.getCustomPage().getLifetime(),
            commands.getCommands(),
            new CustomUIEventBinding[0]
        );
        pageManager.updateCustomPage(patch);
        patchedRespawnPagePlayers.add(playerRef.getUuid());
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
            patchedRespawnPagePlayers.remove(playerUuid);
            String currentMatchId = matchIdByPlayerUuid.get(playerUuid);
            if (matchId.equals(currentMatchId)) {
                matchIdByPlayerUuid.remove(playerUuid);
            }
        }
    }

    private int countPlacedPlayers(@Nonnull ArenaActiveMatch match) {
        int placedPlayers = 0;
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            PendingInstanceSpawnTeleport pending = pendingInstanceSpawnTeleportsByPlayerUuid.get(playerUuid);
            if (pending == null
                || pending.phase() == PlacementPhase.CONFIRMED
                || pending.phase() == PlacementPhase.FALLBACK) {
                placedPlayers++;
            }
        }
        return placedPlayers;
    }

    private int countArrivedInitialPlayers(@Nonnull ArenaActiveMatch match) {
        if (match.expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        int arrivedInitialPlayers = 0;
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            if (expected.contains(playerUuid)) {
                arrivedInitialPlayers++;
            }
        }
        return arrivedInitialPlayers;
    }

    private int countPlacedInitialPlayers(@Nonnull ArenaActiveMatch match) {
        if (match.expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        int placedInitialPlayers = 0;
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            if (!expected.contains(playerUuid)) {
                continue;
            }
            PendingInstanceSpawnTeleport pending = pendingInstanceSpawnTeleportsByPlayerUuid.get(playerUuid);
            if (pending == null
                || pending.phase() == PlacementPhase.CONFIRMED
                || pending.phase() == PlacementPhase.FALLBACK) {
                placedInitialPlayers++;
            }
        }
        return placedInitialPlayers;
    }

    @Nonnull
    private String buildReturnContextJson(
        @Nonnull ArenaActiveMatch match,
        @Nonnull String originLobbyId,
        @Nonnull String returnReason,
        long nowEpochMs
    ) {
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.return");
        root.addProperty("matchId", match.matchId());
        root.addProperty("queueId", match.queueId());
        root.addProperty("originLobbyId", originLobbyId);
        root.addProperty("sourceArenaId", match.arenaId());
        if (!match.assignmentId().isBlank()) {
            root.addProperty("assignmentId", match.assignmentId());
        }
        if (!match.externalMatchId().isBlank()) {
            root.addProperty("externalMatchId", match.externalMatchId());
        }
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
    private static List<UUID> readExpectedPlayerUuids(@Nonnull JsonObject root) {
        if (!root.has("expectedPlayerUuids")) {
            return List.of();
        }
        if (!root.get("expectedPlayerUuids").isJsonArray()) {
            throw new IllegalArgumentException("Minigame context field 'expectedPlayerUuids' must be an array.");
        }
        JsonArray array = root.getAsJsonArray("expectedPlayerUuids");
        List<UUID> playerUuids = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            String rawUuid = normalizeOptional(element.getAsString(), "");
            if (rawUuid.isBlank()) {
                continue;
            }
            try {
                playerUuids.add(UUID.fromString(rawUuid));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Minigame context field 'expectedPlayerUuids' contains invalid UUID '" + rawUuid + "'.", exception);
            }
        }
        return PlayerUuidLists.canonicalize(playerUuids);
    }

    private static UUID readOptionalUuid(@Nonnull JsonObject root, @Nonnull String key) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return null;
        }
        try {
            return UUID.fromString(root.get(key).getAsString());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Minigame context field '" + key + "' contains invalid UUID.", exception);
        }
    }

    private static boolean optionalIdentityMismatch(String left, String right) {
        String normalizedLeft = normalizeOptional(left, "");
        String normalizedRight = normalizeOptional(right, "");
        if (normalizedLeft.isBlank() && normalizedRight.isBlank()) {
            return false;
        }
        return !normalizedLeft.equals(normalizedRight);
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
    private static String normalizeOptional(String rawValue) {
        return normalizeOptional(rawValue, "");
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    private void maybeLogPlacementState(
        @Nonnull ArenaActiveMatch match,
        int expectedPlayers,
        int arrivedPlayers,
        int placedPlayers,
        boolean placementComplete
    ) {
        String summary = expectedPlayers + "|" + arrivedPlayers + "|" + placedPlayers + "|" + placementComplete;
        String previous = lastLoggedPlacementStatesByMatchId.put(match.matchId(), summary);
        if (summary.equals(previous)) {
            return;
        }

        logger.atInfo().log(
            "NEXORI_PLACEMENT_STATE matchId=" + match.matchId()
                + " expectedPlayers=" + expectedPlayers
                + " arrivedPlayers=" + arrivedPlayers
                + " placedPlayers=" + placedPlayers
                + " placementComplete=" + placementComplete
        );
    }

    @Nonnull
    private ArenaActiveMatch reconcileAdmissionLifecycle(@Nonnull ArenaActiveMatch match, long nowEpochMs) {
        ArenaActiveMatch updated = match;
        if (updated.placementCompletedAtEpochMs() <= 0L && isPlacementComplete(updated)) {
            updated = updated.withPlacementCompleted(nowEpochMs, nowEpochMs);
        }
        if (updated.completedAtEpochMs() <= 0L && shouldMarkMatchCompleted(updated)) {
            updated = updated.withCompleted(nowEpochMs, nowEpochMs);
        }
        return updated;
    }

    private boolean isPlacementComplete(@Nonnull ArenaActiveMatch match) {
        int expectedPlayers = match.expectedPlayerCount();
        int arrivedPlayers = countArrivedInitialPlayers(match);
        int placedPlayers = countPlacedInitialPlayers(match);
        return expectedPlayers > 0
            && arrivedPlayers >= expectedPlayers
            && placedPlayers >= expectedPlayers;
    }

    private boolean shouldMarkMatchCompleted(@Nonnull ArenaActiveMatch match) {
        return match.hasWinner() || match.hasSubmittedResult();
    }

    private void storeUpdatedMatchOrCloseEmptyRuntime(
        @Nonnull ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated,
        long nowEpochMs,
        @Nonnull String primaryReason
    ) {
        ArenaActiveMatch stored = updated;
        String reason = primaryReason;
        if (updated.isEmpty()) {
            if (!shouldReportEmptyRuntimeAdmissionClosure(updated)) {
                matchesById.remove(updated.matchId());
                return;
            }
            stored = updated.withExplicitAdmissionClosed(
                CLOSE_REASON_MATCH_RUNTIME_ENDED,
                "Match runtime ended locally before admission was closed.",
                nowEpochMs,
                nowEpochMs
            );
            reason = "ADMISSION_CLOSED";
            logger.atInfo().log(
                "Closing Nexori backend admission because empty match runtime ended locally matchId="
                    + stored.matchId()
                    + " externalMatchId="
                    + stored.externalMatchId()
                    + "."
            );
        }

        matchesById.put(stored.matchId(), stored);
        if (CLOSE_REASON_MATCH_RUNTIME_ENDED.equals(stored.explicitAdmissionCloseReason())) {
            backendMatchAdmissionStateReportingService.flushMatchImmediately(stored.matchId(), CLOSE_REASON_MATCH_RUNTIME_ENDED, nowEpochMs);
        } else {
            maybeScheduleAdmissionReporting(previous, stored, nowEpochMs, reason);
        }
    }

    private boolean shouldReportEmptyRuntimeAdmissionClosure(@Nonnull ArenaActiveMatch match) {
        return backendMatchAdmissionStateReportingService != null
            && backendMatchAdmissionStateReportingService.isMatchStateReportingEnabled()
            && match.effectiveMatchSource() == ArenaMatchSource.BACKEND_DRIVEN
            && !match.explicitAdmissionClosed()
            && match.backfillEnabled()
            && !match.externalMatchId().isBlank()
            && !match.expectedPlayerUuids().isEmpty();
    }

    private void maybeScheduleAdmissionReporting(
        ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated,
        long nowEpochMs,
        @Nonnull String primaryReason
    ) {
        if (backendMatchAdmissionStateReportingService == null) {
            return;
        }
        if (previous == null) {
            backendMatchAdmissionStateReportingService.markMatchDirty(updated.matchId(), "MATCH_CREATED", nowEpochMs);
        }
        if (!primaryReason.isBlank()) {
            backendMatchAdmissionStateReportingService.markMatchDirty(updated.matchId(), primaryReason, nowEpochMs);
        }
        if ((previous == null || previous.placementCompletedAtEpochMs() <= 0L)
            && updated.placementCompletedAtEpochMs() > 0L) {
            backendMatchAdmissionStateReportingService.markMatchDirty(updated.matchId(), "PLACEMENT_COMPLETED", nowEpochMs);
            backendMatchAdmissionStateReportingService.markMatchDirty(updated.matchId(), "MATCH_STARTED", nowEpochMs);
        }
    }

    private void logPlacementConfirmed(
        @Nonnull PlayerRef playerRef,
        String matchId,
        @Nonnull World world,
        @Nonnull TransformComponent transformComponent,
        @Nonnull PendingInstanceSpawnTeleport pending,
        double distanceSquared
    ) {
        logger.atInfo().log(
            "NEXORI_PLACEMENT_CONFIRMED player=" + playerRef.getUsername()
                + " matchId=" + normalizeOptional(matchId, "<unknown>")
                + " templateId=" + pending.instanceTemplateId()
                + " world=" + world.getName()
                + " current=" + transformComponent.getPosition()
                + " target=" + pending.transform().getPosition()
                + " stableTicks=" + pending.stableTicks()
                + " distanceSquared=" + distanceSquared
        );
    }

    private record LaunchContext(
        String matchId,
        String queueId,
        String arenaId,
        String originLobbyId,
        String returnConnectionAddress,
        String returnFallbackTargetId,
        String launchTravelProfileId,
        String instanceTemplateId,
        String matchResolutionTriggerId,
        String rulesEngineId,
        String assignmentId,
        String assignmentType,
        String externalMatchId,
        String matchSource,
        int admissionPolicySchemaVersion,
        int admissionCapacity,
        boolean backfillEnabled,
        String backfillMode,
        int backfillWindowSeconds,
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        UUID playerUuid,
        String admissionReservationId,
        long admissionExpiresAtEpochMs,
        String reportingServerId,
        ArenaPlayerReturnTarget playerReturnTarget
    ) {

        private boolean usesInstanceTemplate() {
            return !instanceTemplateId.isBlank()
                && !ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId);
        }

        @Nonnull
        private static LaunchContext from(@Nonnull JsonObject root) {
            return new LaunchContext(
                NexoriMatchIds.normalizeRequiredMatchId(readRequired(root, "matchId"), "Minigame context field 'matchId' cannot be blank."),
                QueueDefinition.normalizeId(readRequired(root, "queueId")),
                ArenaDefinition.normalizeId(readRequired(root, "arenaId")),
                SourceContextId.normalizeId(readRequired(root, "originLobbyId")),
                readRequired(root, "returnConnectionAddress"),
                readRequired(root, "returnFallbackTargetId"),
                readRequired(root, "launchTravelProfileId").toLowerCase(),
                root.has("instanceTemplateId")
                    ? normalizeOptional(root.get("instanceTemplateId").getAsString(), ArenaDefinition.NO_INSTANCE_TEMPLATE_ID)
                    : ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
                root.has("matchResolutionTriggerId")
                    ? normalizeOptional(root.get("matchResolutionTriggerId").getAsString(), ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID).toLowerCase()
                    : ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID,
                root.has("rulesEngineId")
                    ? ArenaDefinition.normalizeRulesEngineId(root.get("rulesEngineId").getAsString())
                    : "",
                root.has("assignmentId")
                    ? normalizeOptional(root.get("assignmentId").getAsString(), "")
                    : "",
                root.has("assignmentType")
                    ? normalizeOptional(root.get("assignmentType").getAsString(), ASSIGNMENT_TYPE_INITIAL_MATCH)
                    : ASSIGNMENT_TYPE_INITIAL_MATCH,
                root.has("externalMatchId")
                    ? normalizeOptional(root.get("externalMatchId").getAsString(), "")
                    : "",
                root.has("matchSource")
                    ? normalizeOptional(root.get("matchSource").getAsString(), ArenaMatchSource.defaultSource().id())
                    : ArenaMatchSource.defaultSource().id(),
                root.has("admissionPolicySchemaVersion") ? Math.max(root.get("admissionPolicySchemaVersion").getAsInt(), 0) : 0,
                root.has("admissionCapacity") ? Math.max(root.get("admissionCapacity").getAsInt(), 0) : 0,
                root.has("backfillEnabled") && root.get("backfillEnabled").getAsBoolean(),
                root.has("backfillMode")
                    ? normalizeOptional(root.get("backfillMode").getAsString(), QueueBackfillMode.defaultMode().id())
                    : QueueBackfillMode.defaultMode().id(),
                root.has("backfillWindowSeconds") ? Math.max(root.get("backfillWindowSeconds").getAsInt(), 0) : 0,
                readExpectedPlayerUuids(root),
                root.has("expectedPlayerCount") ? Math.max(root.get("expectedPlayerCount").getAsInt(), 0) : 0,
                readOptionalUuid(root, "playerUuid"),
                root.has("admissionReservationId")
                    ? normalizeOptional(root.get("admissionReservationId").getAsString(), "")
                    : "",
                root.has("admissionExpiresAtEpochMs") ? Math.max(root.get("admissionExpiresAtEpochMs").getAsLong(), 0L) : 0L,
                root.has("reportingServerId")
                    ? normalizeOptional(root.get("reportingServerId").getAsString(), "")
                    : "",
                new ArenaPlayerReturnTarget(
                    SourceContextId.normalizeId(readRequired(root, "originLobbyId")),
                    readRequired(root, "returnConnectionAddress"),
                    readRequired(root, "returnFallbackTargetId"),
                    readRequired(root, "launchTravelProfileId").toLowerCase()
                ).normalized()
            );
        }
    }

    public enum EndMatchOutcome {
        COMPLETED,
        MATCH_MISSING,
        FAILED
    }

    public enum CloseMatchAdmissionReason {
        MOD_REQUEST,
        GAME_PHASE_LOCKED,
        ROSTER_LOCKED,
        ADMIN_FORCED
    }

    public enum CloseMatchAdmissionOutcome {
        CLOSED,
        ALREADY_CLOSED,
        MATCH_MISSING,
        MATCH_NOT_BACKEND_DRIVEN,
        INVALID_REASON,
        REPORTING_DISABLED
    }

    public record CloseMatchAdmissionResult(
        CloseMatchAdmissionOutcome outcome,
        String matchId,
        boolean closedLocally,
        String message
    ) {

        @Nonnull
        public static CloseMatchAdmissionResult closed(@Nonnull String matchId, boolean closedLocally, @Nonnull String message) {
            return new CloseMatchAdmissionResult(CloseMatchAdmissionOutcome.CLOSED, matchId, closedLocally, normalizeOptional(message));
        }

        @Nonnull
        public static CloseMatchAdmissionResult alreadyClosed(@Nonnull String matchId, boolean closedLocally, @Nonnull String message) {
            return new CloseMatchAdmissionResult(CloseMatchAdmissionOutcome.ALREADY_CLOSED, matchId, closedLocally, normalizeOptional(message));
        }

        @Nonnull
        public static CloseMatchAdmissionResult matchMissing(@Nonnull String matchId) {
            return new CloseMatchAdmissionResult(CloseMatchAdmissionOutcome.MATCH_MISSING, normalizeRequired(matchId, "Match id cannot be blank."), false, "Match is not active.");
        }

        @Nonnull
        public static CloseMatchAdmissionResult matchNotBackendDriven(@Nonnull String matchId) {
            return new CloseMatchAdmissionResult(CloseMatchAdmissionOutcome.MATCH_NOT_BACKEND_DRIVEN, normalizeRequired(matchId, "Match id cannot be blank."), false, "Match is not BACKEND_DRIVEN.");
        }

        @Nonnull
        public static CloseMatchAdmissionResult invalidReason(@Nonnull String matchId, @Nonnull String message) {
            return new CloseMatchAdmissionResult(CloseMatchAdmissionOutcome.INVALID_REASON, normalizeRequired(matchId, "Match id cannot be blank."), false, normalizeOptional(message, "Invalid close reason."));
        }

        @Nonnull
        public static CloseMatchAdmissionResult reportingDisabled(@Nonnull String matchId, boolean closedLocally, @Nonnull String message) {
            return new CloseMatchAdmissionResult(CloseMatchAdmissionOutcome.REPORTING_DISABLED, normalizeRequired(matchId, "Match id cannot be blank."), closedLocally, normalizeOptional(message, "Admission reporting is disabled."));
        }
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

    public enum SetPlayerOutcomeOutcome {
        UPDATED,
        MATCH_MISSING,
        PLAYER_MISSING,
        MATCH_ALREADY_COMPLETED,
        INVALID_OUTCOME,
        INVALID_REASON
    }

    public record SetPlayerOutcomeResult(
        SetPlayerOutcomeOutcome outcome,
        String matchId,
        UUID playerUuid,
        ArenaPlayerResolutionOutcome playerOutcome,
        String message,
        ArenaActiveMatch activeMatch
    ) {

        @Nonnull
        public static SetPlayerOutcomeResult updated(
            @Nonnull ArenaActiveMatch activeMatch,
            @Nonnull UUID playerUuid,
            @Nonnull ArenaPlayerResolutionOutcome playerOutcome
        ) {
            return new SetPlayerOutcomeResult(SetPlayerOutcomeOutcome.UPDATED, activeMatch.matchId(), playerUuid, playerOutcome, "", activeMatch);
        }

        @Nonnull
        public static SetPlayerOutcomeResult matchMissing(@Nonnull String matchId) {
            return new SetPlayerOutcomeResult(SetPlayerOutcomeOutcome.MATCH_MISSING, matchId, null, null, "Match is not active.", null);
        }

        @Nonnull
        public static SetPlayerOutcomeResult playerMissing(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid) {
            return new SetPlayerOutcomeResult(SetPlayerOutcomeOutcome.PLAYER_MISSING, activeMatch.matchId(), playerUuid, null, "Player is not part of the active match.", activeMatch);
        }

        @Nonnull
        public static SetPlayerOutcomeResult matchAlreadyCompleted(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid) {
            return new SetPlayerOutcomeResult(SetPlayerOutcomeOutcome.MATCH_ALREADY_COMPLETED, activeMatch.matchId(), playerUuid, null, "Match result was already submitted.", activeMatch);
        }

        @Nonnull
        public static SetPlayerOutcomeResult invalidOutcome(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, @Nonnull String message) {
            return new SetPlayerOutcomeResult(SetPlayerOutcomeOutcome.INVALID_OUTCOME, activeMatch.matchId(), playerUuid, null, message, activeMatch);
        }

        @Nonnull
        public static SetPlayerOutcomeResult invalidReason(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, @Nonnull String message) {
            return new SetPlayerOutcomeResult(SetPlayerOutcomeOutcome.INVALID_REASON, activeMatch.matchId(), playerUuid, null, message, activeMatch);
        }
    }

    public enum SetPlayerSpectatorOutcome {
        UPDATED,
        MATCH_MISSING,
        PLAYER_MISSING,
        MATCH_ALREADY_COMPLETED,
        INVALID_REASON
    }

    public record SetPlayerSpectatorResult(
        SetPlayerSpectatorOutcome outcome,
        String matchId,
        UUID playerUuid,
        boolean spectator,
        String message,
        ArenaActiveMatch activeMatch
    ) {

        @Nonnull
        public static SetPlayerSpectatorResult updated(@Nonnull ArenaActiveMatch activeMatch, @Nonnull UUID playerUuid, boolean spectator) {
            return new SetPlayerSpectatorResult(SetPlayerSpectatorOutcome.UPDATED, activeMatch.matchId(), playerUuid, spectator, "", activeMatch);
        }

        @Nonnull
        public static SetPlayerSpectatorResult matchMissing(@Nonnull String matchId) {
            return new SetPlayerSpectatorResult(SetPlayerSpectatorOutcome.MATCH_MISSING, matchId, null, false, "Match is not active.", null);
        }

        @Nonnull
        public static SetPlayerSpectatorResult playerMissing(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, boolean spectator) {
            return new SetPlayerSpectatorResult(SetPlayerSpectatorOutcome.PLAYER_MISSING, activeMatch.matchId(), playerUuid, spectator, "Player is not part of the active match.", activeMatch);
        }

        @Nonnull
        public static SetPlayerSpectatorResult matchAlreadyCompleted(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, boolean spectator) {
            return new SetPlayerSpectatorResult(SetPlayerSpectatorOutcome.MATCH_ALREADY_COMPLETED, activeMatch.matchId(), playerUuid, spectator, "Match result was already submitted.", activeMatch);
        }

        @Nonnull
        public static SetPlayerSpectatorResult invalidReason(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, boolean spectator, @Nonnull String message) {
            return new SetPlayerSpectatorResult(SetPlayerSpectatorOutcome.INVALID_REASON, activeMatch.matchId(), playerUuid, spectator, message, activeMatch);
        }
    }

    public enum ReturnPlayerOutcome {
        SCHEDULED,
        MATCH_MISSING,
        PLAYER_MISSING,
        INVALID_DELAY,
        INVALID_REASON
    }

    public record ReturnPlayerResult(
        ReturnPlayerOutcome outcome,
        String matchId,
        UUID playerUuid,
        long returnAtEpochMs,
        String message,
        ArenaActiveMatch activeMatch
    ) {

        @Nonnull
        public static ReturnPlayerResult scheduled(@Nonnull ArenaActiveMatch activeMatch, @Nonnull UUID playerUuid, long returnAtEpochMs) {
            return new ReturnPlayerResult(ReturnPlayerOutcome.SCHEDULED, activeMatch.matchId(), playerUuid, returnAtEpochMs, "", activeMatch);
        }

        @Nonnull
        public static ReturnPlayerResult matchMissing(@Nonnull String matchId) {
            return new ReturnPlayerResult(ReturnPlayerOutcome.MATCH_MISSING, matchId, null, 0L, "Match is not active.", null);
        }

        @Nonnull
        public static ReturnPlayerResult playerMissing(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid) {
            return new ReturnPlayerResult(ReturnPlayerOutcome.PLAYER_MISSING, activeMatch.matchId(), playerUuid, 0L, "Player is not part of the active match.", activeMatch);
        }

        @Nonnull
        public static ReturnPlayerResult invalidDelay(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, @Nonnull String message) {
            return new ReturnPlayerResult(ReturnPlayerOutcome.INVALID_DELAY, activeMatch.matchId(), playerUuid, 0L, message, activeMatch);
        }

        @Nonnull
        public static ReturnPlayerResult invalidReason(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid, @Nonnull String message) {
            return new ReturnPlayerResult(ReturnPlayerOutcome.INVALID_REASON, activeMatch.matchId(), playerUuid, 0L, message, activeMatch);
        }
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

    public enum SubmitMatchOutcome {
        ACCEPTED,
        ALREADY_SUBMITTED,
        MATCH_MISSING,
        INVALID_RESULT
    }

    public record SubmitMatchPlayerResult(
        UUID playerUuid,
        ArenaPlayerResolutionOutcome runtimeOutcome,
        String backendOutcome,
        String reason
    ) {
    }

    public record SubmitMatchResult(
        SubmitMatchOutcome outcome,
        String matchId,
        ArenaActiveMatch activeMatch,
        List<SubmitMatchPlayerResult> players,
        Map<String, String> metadata,
        JsonObject customData,
        String reason,
        String resultPayloadHash,
        long endedAtEpochMs,
        boolean duplicateConflict,
        String message
    ) {

        @Nonnull
        public static SubmitMatchResult accepted(
            @Nonnull ArenaActiveMatch activeMatch,
            @Nonnull List<SubmitMatchPlayerResult> players,
            @Nonnull Map<String, String> metadata,
            @Nonnull JsonObject customData,
            @Nonnull String reason,
            @Nonnull String resultPayloadHash,
            long endedAtEpochMs
        ) {
            return new SubmitMatchResult(
                SubmitMatchOutcome.ACCEPTED,
                activeMatch.matchId(),
                activeMatch,
                List.copyOf(players),
                Map.copyOf(metadata),
                customData.deepCopy(),
                reason,
                resultPayloadHash,
                endedAtEpochMs,
                false,
                ""
            );
        }

        @Nonnull
        public static SubmitMatchResult alreadySubmitted(
            @Nonnull ArenaActiveMatch activeMatch,
            @Nonnull String resultPayloadHash,
            boolean duplicateConflict
        ) {
            return new SubmitMatchResult(
                SubmitMatchOutcome.ALREADY_SUBMITTED,
                activeMatch.matchId(),
                activeMatch,
                List.of(),
                Map.of(),
                new JsonObject(),
                "",
                resultPayloadHash,
                activeMatch.completedAtEpochMs(),
                duplicateConflict,
                duplicateConflict ? "Match already has a different submitted result." : "Match result was already submitted."
            );
        }

        @Nonnull
        public static SubmitMatchResult matchMissing(@Nonnull String matchId) {
            return new SubmitMatchResult(
                SubmitMatchOutcome.MATCH_MISSING,
                normalizeRequired(matchId, "Match id cannot be blank."),
                null,
                List.of(),
                Map.of(),
                new JsonObject(),
                "",
                "",
                0L,
                false,
                "Match is not active."
            );
        }

        @Nonnull
        public static SubmitMatchResult invalid(@Nonnull ArenaActiveMatch activeMatch, @Nonnull String message) {
            return new SubmitMatchResult(
                SubmitMatchOutcome.INVALID_RESULT,
                activeMatch.matchId(),
                activeMatch,
                List.of(),
                Map.of(),
                new JsonObject(),
                "",
                "",
                0L,
                false,
                normalizeOptional(message, "Invalid match result.")
            );
        }
    }

    public record MatchResultRequirements(
        String matchId,
        String queueId,
        String arenaId,
        List<UUID> requiredPlayerUuids,
        List<UUID> expectedPlayerUuids,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids
    ) {
    }

    public record ActiveMatchInfo(
        String matchId,
        String queueId,
        String arenaId,
        String assignmentId,
        String externalMatchId,
        String rulesEngineId,
        String matchResolutionTriggerId,
        List<UUID> expectedPlayerUuids,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids,
        List<UUID> spectatorPlayerUuids,
        List<UUID> requiredResultPlayerUuids,
        List<PlayerOutcomeState> playerOutcomes,
        int expectedPlayerCount,
        long completedAtEpochMs,
        long resultSubmittedAtEpochMs
    ) {
    }

    public record PlayerOutcomeState(
        UUID playerUuid,
        ArenaPlayerResolutionOutcome outcome,
        String backendOutcome,
        String reason,
        long updatedAtEpochMs
    ) {
    }

    public record ReturnHudState(
        String matchId,
        String queueId,
        String arenaDisplayName,
        String outcomeLabel,
        long returnAtEpochMs,
        long remainingReturnDelayMs
    ) {
    }

    public record MatchPlacementState(
        int expectedPlayers,
        int arrivedPlayers,
        int placedPlayers,
        boolean placementComplete
    ) {
    }

    private record PendingInstanceSpawnTeleport(
        @Nonnull String expectedWorldName,
        @Nonnull String instanceTemplateId,
        @Nonnull Transform transform,
        @Nonnull PlacementPhase phase,
        boolean teleportIssued,
        long issuedAtEpochMs,
        long readyObservedAtEpochMs,
        int stableTicks
    ) {
        @Nonnull
        private PendingInstanceSpawnTeleport withTeleportIssued(boolean rawTeleportIssued, long rawIssuedAtEpochMs) {
            return new PendingInstanceSpawnTeleport(
                expectedWorldName,
                instanceTemplateId,
                transform,
                phase,
                rawTeleportIssued,
                rawIssuedAtEpochMs,
                readyObservedAtEpochMs,
                stableTicks
            );
        }

        @Nonnull
        private PendingInstanceSpawnTeleport withStableTicks(int rawStableTicks) {
            return new PendingInstanceSpawnTeleport(
                expectedWorldName,
                instanceTemplateId,
                transform,
                phase,
                teleportIssued,
                issuedAtEpochMs,
                readyObservedAtEpochMs,
                rawStableTicks
            );
        }

        @Nonnull
        private PendingInstanceSpawnTeleport withReadyObservedAtEpochMs(long rawReadyObservedAtEpochMs) {
            return new PendingInstanceSpawnTeleport(
                expectedWorldName,
                instanceTemplateId,
                transform,
                phase,
                teleportIssued,
                issuedAtEpochMs,
                rawReadyObservedAtEpochMs,
                stableTicks
            );
        }

        @Nonnull
        private PendingInstanceSpawnTeleport withPhase(@Nonnull PlacementPhase rawPhase) {
            return new PendingInstanceSpawnTeleport(
                expectedWorldName,
                instanceTemplateId,
                transform,
                rawPhase,
                teleportIssued,
                issuedAtEpochMs,
                readyObservedAtEpochMs,
                stableTicks
            );
        }
    }

    private enum PlacementPhase {
        PENDING_ISSUE,
        WAITING_FOR_POST_READY,
        VALIDATING_SLOT,
        CONFIRMED,
        FALLBACK
    }
}
