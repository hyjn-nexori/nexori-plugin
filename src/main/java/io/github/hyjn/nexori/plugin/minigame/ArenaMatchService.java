package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.backend.BackendMatchAdmissionStateReportingService;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchPlacementState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementOutcome;
import io.github.hyjn.nexori.plugin.minigame.logic.BackfillAdmissionDecider;
import io.github.hyjn.nexori.plugin.minigame.logic.BackfillAdmissionDecision;
import io.github.hyjn.nexori.plugin.minigame.logic.LaunchContextData;
import io.github.hyjn.nexori.plugin.minigame.logic.LaunchContextParser;
import io.github.hyjn.nexori.plugin.minigame.logic.MatchPlacementEvaluation;
import io.github.hyjn.nexori.plugin.minigame.logic.MatchPlacementEvaluator;
import io.github.hyjn.nexori.plugin.minigame.logic.MatchResultValidationResult;
import io.github.hyjn.nexori.plugin.minigame.logic.MatchResultValidator;
import io.github.hyjn.nexori.plugin.minigame.spectator.NoopSpectatorRuntimeController;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorRuntimeController;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorRuntimeReason;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorRuntimeResult;
import io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshot;
import io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshotResolver;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tracks active arena matches, observes player arrivals and returns, and coordinates the
 * runtime lifecycle that sits between queue launch and secure return to the lobby.
 */
public class ArenaMatchService {

    private static final Gson GSON = new Gson();
    private static final long ELIMINATED_RETURN_DELAY_MS = 5_000L;
    private static final long WINNER_RETURN_DELAY_MS = 10_000L;
    private static final long BACKEND_AFK_CANCEL_RETURN_DELAY_MS = 10_000L;
    private static final long RETURN_RETRY_DELAY_MS = 5_000L;
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
    private final SpectatorRuntimeController spectatorRuntimeController;
    private final NexoriMatchLifecycleDispatcher matchLifecycleDispatcher;
    private final BackfillAdmissionDecider backfillAdmissionDecider = new BackfillAdmissionDecider();
    private final LaunchContextParser launchContextParser = new LaunchContextParser();
    private final MatchPlacementEvaluator matchPlacementEvaluator = new MatchPlacementEvaluator();
    private final MatchResultValidator matchResultValidator = new MatchResultValidator();
    private final ReadyPlayerSnapshotResolver snapshotResolver = new ReadyPlayerSnapshotResolver();
    private BackendMatchAdmissionStateReportingService backendMatchAdmissionStateReportingService;
    private Consumer<String> matchRuntimeClosedCallback = ignored -> {
    };
    private MinigameTransferService minigameTransferService;
    private final Map<String, ArenaActiveMatch> matchesById = new LinkedHashMap<>();
    private final Map<UUID, String> matchIdByPlayerUuid = new LinkedHashMap<>();
    private final Map<String, String> lastLoggedPlacementStatesByMatchId = new LinkedHashMap<>();
    private final Map<String, AfkDetectionPolicy> matchAfkPolicyOverridesByMatchId = new LinkedHashMap<>();
    private final Map<String, Map<UUID, AfkDetectionPolicy>> playerAfkPolicyOverridesByMatchId = new LinkedHashMap<>();

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
        this(
            logger,
            secureTravelService,
            matchSessionService,
            arenaService,
            instanceSpawnSlotService,
            NoopSpectatorRuntimeController.INSTANCE,
            new NexoriMatchLifecycleDispatcher(logger)
        );
    }

    public ArenaMatchService(
        @Nonnull HytaleLogger logger,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull MatchSessionService matchSessionService,
        @Nonnull ArenaService arenaService,
        @Nonnull InstanceSpawnSlotService instanceSpawnSlotService,
        @Nonnull SpectatorRuntimeController spectatorRuntimeController
    ) {
        this(
            logger,
            secureTravelService,
            matchSessionService,
            arenaService,
            instanceSpawnSlotService,
            spectatorRuntimeController,
            new NexoriMatchLifecycleDispatcher(logger)
        );
    }

    public ArenaMatchService(
        @Nonnull HytaleLogger logger,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull MatchSessionService matchSessionService,
        @Nonnull ArenaService arenaService,
        @Nonnull InstanceSpawnSlotService instanceSpawnSlotService,
        @Nonnull SpectatorRuntimeController spectatorRuntimeController,
        @Nonnull NexoriMatchLifecycleDispatcher matchLifecycleDispatcher
    ) {
        this.logger = logger;
        this.secureTravelService = secureTravelService;
        this.matchSessionService = matchSessionService;
        this.arenaService = arenaService;
        this.instanceSpawnSlotService = instanceSpawnSlotService;
        this.spectatorRuntimeController = spectatorRuntimeController;
        this.matchLifecycleDispatcher = matchLifecycleDispatcher;
    }

    public synchronized void setBackendMatchAdmissionStateReportingService(
        BackendMatchAdmissionStateReportingService backendMatchAdmissionStateReportingService
    ) {
        this.backendMatchAdmissionStateReportingService = backendMatchAdmissionStateReportingService;
    }

    public synchronized void setMatchRuntimeClosedCallback(Consumer<String> matchRuntimeClosedCallback) {
        this.matchRuntimeClosedCallback = matchRuntimeClosedCallback == null ? ignored -> {
        } : matchRuntimeClosedCallback;
    }

    public synchronized void setMinigameTransferService(MinigameTransferService service) {
        this.minigameTransferService = service;
        if (service != null) {
            service.setMatchGateway(new TransferMatchGateway());
        }
    }

    /**
     * Returns a read-only view of all active match ids (for diagnostics / admin commands).
     */
    @Nonnull
    public synchronized Optional<ArenaActiveMatch> findMatchRaw(@Nonnull String matchId) {
        if (matchId == null || matchId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matchesById.get(matchId.trim().toLowerCase()));
    }

    /**
     * Package-private — used only by lifecycle instrumentation tests.
     * Triggers the same placement-confirmed lifecycle dispatch as the MinigameTransferService
     * callback without needing the full transfer state machine.
     */
    synchronized List<Runnable> collectConfirmTransferPlacementForTest(
        @Nonnull UUID playerUuid,
        long nowEpochMs
    ) {
        return new TransferMatchGateway().confirmTransferPlacement(playerUuid, nowEpochMs);
    }

    /**
     * Package-private — used only by lifecycle instrumentation tests.
     * Triggers the same placement-fallback lifecycle dispatch as confirmTransferPlacement
     * but via the failTransferPlacement path, recording a FALLBACK outcome.
     * Callers must ensure the player is in the match before calling this.
     */
    synchronized List<Runnable> collectFallbackTransferPlacementForTest(
        @Nonnull UUID playerUuid,
        long nowEpochMs
    ) {
        return new TransferMatchGateway().failTransferPlacement(
            playerUuid, "PLACEMENT_FALLBACK", "", "", "", "", "", nowEpochMs
        );
    }

    /**
     * Observes player-ready events and consumes Nexori launch or return arrivals for that player.
     */
    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        List<Runnable> lifecycleDispatches;
        synchronized (this) {
            lifecycleDispatches = handlePlayerReadyLocked(event);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
    }

    @Nonnull
    private List<Runnable> handlePlayerReadyLocked(@Nonnull PlayerReadyEvent event) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();

        ReadyPlayerSnapshot snapshot = snapshotResolver.resolve(event);
        if (!snapshot.safe()) {
            logger.atInfo().log(
                "NEXORI_TRANSFER_UNSAFE_READY reason=" + snapshot.unsafeReason()
            );
            return lifecycleDispatches;
        }

        PlayerRef playerRef = snapshot.playerRef();

        // Notify the transfer service that the player is ready in a world — this advances
        // TELEPORT_ISSUED / WAITING_FOR_INSTANCE_READY → POST_READY_GRACE when the player
        // arrives in the expected instance world.  Called regardless of arrival presence
        // because the instance-world PlayerReadyEvent typically has no new arrival attached.
        if (snapshot.world() != null) {
            refreshRuntimeSpectatorForReadyPlayer(playerRef);
            if (minigameTransferService != null) {
                minigameTransferService.onPlayerReadyObserved(
                    playerRef.getUuid(),
                    snapshot.world().getName(),
                    System.currentTimeMillis()
                );
            }
        }

        PendingArrival arrival = secureTravelService.peekRecentArrival(playerRef.getUuid()).orElse(null);
        if (arrival == null) {
            return lifecycleDispatches;
        }

        JsonObject context = parseContext(arrival.contextJson());
        if (context == null || !context.has("flowType")) {
            return lifecycleDispatches;
        }

        String flowType = context.get("flowType").getAsString();

        if ("minigame.launch".equalsIgnoreCase(flowType)) {
            if (snapshot.world() == null) {
                // Engine fired PlayerReadyEvent before the player had a valid world.
                // Do NOT consume the arrival; the transfer service cannot safely place
                // the player yet.  The arrival will be acknowledged on the next safe ready.
                logger.atInfo().log(
                    "NEXORI_TRANSFER_UNSAFE_READY player=" + playerRef.getUuid()
                        + " username=" + snapshot.username()
                        + " reason=NULL_WORLD matchId="
                        + (context.has("matchId") ? context.get("matchId").getAsString() : "<unknown>")
                );
                return lifecycleDispatches;
            }
            logger.atInfo().log(
                "NEXORI_TRANSFER_SAFE_READY player=" + playerRef.getUuid()
                    + " username=" + snapshot.username()
                    + " world=" + snapshot.world().getName()
            );

            if (minigameTransferService != null) {
                // Do NOT acknowledge the arrival before onMinigameLaunchSafeReady returns.
                // The result indicates whether the transfer service took ownership or reached
                // a terminal decision; ACKED arrivals are removed from recentArrivals so they
                // cannot be reprocessed.
                io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferOnReadyResult transferResult =
                    minigameTransferService.onMinigameLaunchSafeReady(
                        snapshot,
                        arrival,
                        System.currentTimeMillis()
                    );
                if (transferResult.arrivalAcknowledged()) {
                    secureTravelService.acknowledgeRecentArrival(playerRef.getUuid(), arrival);
                }
                lifecycleDispatches.addAll(transferResult.lifecycleDispatches());
            } else {
                // Fallback: legacy path if transfer service is not wired
                secureTravelService.acknowledgeRecentArrival(playerRef.getUuid(), arrival);
                handleLaunchArrival(event, playerRef, context, lifecycleDispatches);
            }
            return lifecycleDispatches;
        }

        if ("minigame.return".equalsIgnoreCase(flowType)) {
            if (snapshot.world() == null) {
                logger.atInfo().log(
                    "NEXORI_TRANSFER_UNSAFE_READY player=" + playerRef.getUuid()
                        + " username=" + snapshot.username()
                        + " reason=NULL_WORLD flowType=minigame.return matchId="
                        + (context.has("matchId") ? context.get("matchId").getAsString() : "<unknown>")
                );
                return lifecycleDispatches;
            }
            secureTravelService.acknowledgeRecentArrival(playerRef.getUuid(), arrival);
            handleReturnArrival(playerRef, context);
        }
        return lifecycleDispatches;
    }

    /**
     * Removes disconnecting players from the active match runtime and reevaluates automatic resolution.
     */
    public void handlePlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        List<Runnable> lifecycleDispatches;
        synchronized (this) {
            lifecycleDispatches = handlePlayerDisconnectLocked(event);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
    }

    @Nonnull
    private List<Runnable> handlePlayerDisconnectLocked(@Nonnull PlayerDisconnectEvent event) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return lifecycleDispatches;
        }

        if (minigameTransferService != null) {
            minigameTransferService.onPlayerDisconnect(playerRef.getUuid(), System.currentTimeMillis());
        }
        restoreRuntimeSpectator(playerRef.getUuid(), SpectatorRuntimeReason.PLAYER_DISCONNECT);
        String matchId = matchIdByPlayerUuid.remove(playerRef.getUuid());
        if (matchId == null) {
            return lifecycleDispatches;
        }
        removePlayerAfkPolicyOverride(matchId, playerRef.getUuid());

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerRef.getUuid())) {
            return lifecycleDispatches;
        }

        long now = System.currentTimeMillis();
        ArenaActiveMatch updated = match.withoutReturnedPlayer(playerRef.getUuid(), now)
            .withLastError("Player disconnected: " + event.getDisconnectReason(), now);
        updated = reconcileAdmissionLifecycle(scheduleWinnerReturnIfNeeded(updated, now), now);

        storeUpdatedMatchOrCloseEmptyRuntime(match, updated, now, "", lifecycleDispatches);
        return lifecycleDispatches;
    }

    /**
     * Handles setup disconnects that happen before a launch or return arrival can finish.
     */
    public void handlePlayerSetupDisconnect(@Nonnull PlayerSetupDisconnectEvent event) {
        List<Runnable> lifecycleDispatches;
        synchronized (this) {
            lifecycleDispatches = handlePlayerSetupDisconnectLocked(event);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
    }

    @Nonnull
    private List<Runnable> handlePlayerSetupDisconnectLocked(@Nonnull PlayerSetupDisconnectEvent event) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();
        PendingArrival arrival = secureTravelService.peekPendingArrival(event.getUuid()).orElse(null);
        if (arrival == null) {
            return lifecycleDispatches;
        }

        JsonObject context = parseContext(arrival.contextJson());
        if (context == null || !context.has("flowType")) {
            return lifecycleDispatches;
        }

        String flowType = context.get("flowType").getAsString();
        if (!"minigame.launch".equalsIgnoreCase(flowType) && !"minigame.return".equalsIgnoreCase(flowType)) {
            return lifecycleDispatches;
        }

        secureTravelService.removePendingArrival(event.getUuid());
        if (minigameTransferService != null) {
            minigameTransferService.onPlayerDisconnect(event.getUuid(), System.currentTimeMillis());
        }
        restoreRuntimeSpectator(event.getUuid(), SpectatorRuntimeReason.PLAYER_DISCONNECT);
        long now = System.currentTimeMillis();
        String reason = "Player setup disconnect before ready: " + event.getDisconnectReason();

        try {
            if ("minigame.launch".equalsIgnoreCase(flowType)) {
                LaunchContextData launch = launchContextParser.parse(context);
                if (ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType())) {
                    logger.atInfo().log(
                        "Nexori BACKFILL setup disconnect did not consume admission reservation "
                            + launch.admissionReservationId()
                            + " for match "
                            + launch.matchId()
                            + "; backend reservation will expire by TTL."
                    );
                    return lifecycleDispatches;
                }
                ArenaActiveMatch match = matchesById.get(launch.matchId());
                if (match != null) {
                    ArenaActiveMatch updated = match.withExpectedPlayerCount(match.expectedPlayerCount() - 1, now)
                        .withLastError(reason, now);
                    updated = reconcileAdmissionLifecycle(scheduleWinnerReturnIfNeeded(updated, now), now);
                    storeUpdatedMatchOrCloseEmptyRuntime(match, updated, now, "", lifecycleDispatches);
                }
                return lifecycleDispatches;
            }
        } catch (IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log("Failed to process Nexori player setup disconnect context.");
        }
        return lifecycleDispatches;
    }

    /**
     * Advances per-player match runtime such as elimination handling and pending lobby returns.
     */
    public void handlePlayerTick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        long nowEpochMs
    ) {
        List<Runnable> lifecycleDispatches;
        synchronized (this) {
            lifecycleDispatches = handlePlayerTickLocked(ref, store, nowEpochMs);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
    }

    @Nonnull
    private List<Runnable> handlePlayerTickLocked(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        long nowEpochMs
    ) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();
        if (minigameTransferService != null) {
            // Pending backfill retries are owned by MinigameTransferService and may belong to
            // players that are not associated with a match yet. Tick them before match lookup.
            lifecycleDispatches.addAll(minigameTransferService.tickPendingBackfillRetries(nowEpochMs));
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return lifecycleDispatches;
        }

        PlayerRef playerRef = store.getComponent(ref, Universe.get().getPlayerRefComponentType());
        if (playerRef == null) {
            return lifecycleDispatches;
        }

        String matchId = matchIdByPlayerUuid.get(playerRef.getUuid());
        if (matchId == null) {
            return lifecycleDispatches;
        }

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerRef.getUuid())) {
            return lifecycleDispatches;
        }

        if (minigameTransferService != null) {
            List<Runnable> transferDispatches = minigameTransferService.tickTransfer(ref, store, playerRef, nowEpochMs);
            lifecycleDispatches.addAll(transferDispatches);
            // confirmTransferPlacement inside tickTransfer may have updated matchesById; refetch.
            match = matchesById.get(matchId);
            if (match == null || !match.hasPlayer(playerRef.getUuid())) {
                return lifecycleDispatches;
            }
        }

        ArenaActiveMatch updated = match;
        ArenaActiveMatch beforeLifecycleReconcile = scheduleWinnerReturnIfNeeded(updated, nowEpochMs);
        updated = reconcileAdmissionLifecycle(beforeLifecycleReconcile, nowEpochMs);
        collectMatchPlacementCompletedTransition(
            beforeLifecycleReconcile,
            updated,
            "MATCH_PLACEMENT_COMPLETED",
            nowEpochMs,
            lifecycleDispatches
        );
        collectMatchCompletedTransition(
            beforeLifecycleReconcile,
            updated,
            "MATCH_COMPLETED",
            nowEpochMs,
            lifecycleDispatches
        );

        if (updated.hasPendingReturn(playerRef.getUuid())) {
            Long dueAt = updated.pendingReturnAtEpochMsByPlayerUuid().get(playerRef.getUuid());
            if (dueAt != null && dueAt <= nowEpochMs) {
                updated = attemptReturn(updated, playerRef, ref, store, nowEpochMs);
            }
        }

        storeUpdatedMatchOrCloseEmptyRuntime(match, updated, nowEpochMs, "", lifecycleDispatches);
        return lifecycleDispatches;
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
        ArenaActiveMatch.ArenaPlayerOutcomeState outcome = match.playerOutcomeByUuid().get(playerUuid);
        if (outcome != null && outcome.outcome() == ArenaPlayerResolutionOutcome.NO_CONTEST) {
            outcomeLabel = "No Contest";
        } else if (playerUuid.toString().equalsIgnoreCase(match.winnerPlayerUuid())) {
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
     * Resolves the AFK policy currently effective for a player in an active match.
     *
     * <p>This is intentionally match/player scoped so future phase, role, or player overrides
     * can be layered here without making the activity service read persistent arena config.</p>
     */
    @Nonnull
    public synchronized Optional<EffectiveAfkDetectionPolicy> findEffectiveAfkDetectionPolicy(@Nonnull UUID playerUuid) {
        String matchId = matchIdByPlayerUuid.get(playerUuid);
        if (matchId == null || matchId.isBlank()) {
            return Optional.empty();
        }

        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerUuid)) {
            return Optional.empty();
        }
        AfkDetectionPolicy policy = resolveEffectiveAfkDetectionPolicy(match, playerUuid);
        return Optional.of(new EffectiveAfkDetectionPolicy(
            match.matchId(),
            match.queueId(),
            match.arenaId(),
            match.rulesEngineId(),
            policy
        ));
    }

    @Nonnull
    public synchronized SetAfkDetectionPolicyResult setMatchAfkDetectionPolicy(
        @Nonnull String rawMatchId,
        @Nonnull AfkDetectionPolicy rawPolicy
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetAfkDetectionPolicyResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetAfkDetectionPolicyResult.matchAlreadyCompleted(match, null);
        }
        AfkDetectionPolicy policy = AfkDetectionPolicy.normalize(rawPolicy);
        Map<UUID, EffectiveAfkDetectionPolicy> previousPolicies = effectiveAfkPoliciesForMatch(match);
        matchAfkPolicyOverridesByMatchId.put(match.matchId(), policy);
        List<AfkPolicyStateAction> actions = collectAfkPolicyStateActions(match, previousPolicies);
        return SetAfkDetectionPolicyResult.updated(match, null, policy, actions);
    }

    @Nonnull
    public synchronized SetAfkDetectionPolicyResult clearMatchAfkDetectionPolicy(@Nonnull String rawMatchId) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetAfkDetectionPolicyResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetAfkDetectionPolicyResult.matchAlreadyCompleted(match, null);
        }
        Map<UUID, EffectiveAfkDetectionPolicy> previousPolicies = effectiveAfkPoliciesForMatch(match);
        matchAfkPolicyOverridesByMatchId.remove(match.matchId());
        List<AfkPolicyStateAction> actions = collectAfkPolicyStateActions(match, previousPolicies);
        return SetAfkDetectionPolicyResult.cleared(match, null, actions);
    }

    @Nonnull
    public synchronized SetAfkDetectionPolicyResult setPlayerAfkDetectionPolicy(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid,
        @Nonnull AfkDetectionPolicy rawPolicy
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetAfkDetectionPolicyResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetAfkDetectionPolicyResult.matchAlreadyCompleted(match, playerUuid);
        }
        if (playerUuid == null || !match.hasPlayer(playerUuid)) {
            return SetAfkDetectionPolicyResult.playerMissing(match, playerUuid);
        }
        EffectiveAfkDetectionPolicy previousPolicy = effectiveAfkDetectionPolicyFor(match, playerUuid);
        AfkDetectionPolicy policy = AfkDetectionPolicy.normalize(rawPolicy);
        playerAfkPolicyOverridesByMatchId
            .computeIfAbsent(match.matchId(), ignored -> new LinkedHashMap<>())
            .put(playerUuid, policy);
        List<AfkPolicyStateAction> actions = collectAfkPolicyStateActions(
            playerUuid,
            previousPolicy,
            effectiveAfkDetectionPolicyFor(match, playerUuid)
        );
        return SetAfkDetectionPolicyResult.updated(match, playerUuid, policy, actions);
    }

    @Nonnull
    public synchronized SetAfkDetectionPolicyResult clearPlayerAfkDetectionPolicy(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetAfkDetectionPolicyResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetAfkDetectionPolicyResult.matchAlreadyCompleted(match, playerUuid);
        }
        if (playerUuid == null || !match.hasPlayer(playerUuid)) {
            return SetAfkDetectionPolicyResult.playerMissing(match, playerUuid);
        }
        EffectiveAfkDetectionPolicy previousPolicy = effectiveAfkDetectionPolicyFor(match, playerUuid);
        Map<UUID, AfkDetectionPolicy> playerOverrides = playerAfkPolicyOverridesByMatchId.get(match.matchId());
        if (playerOverrides != null) {
            playerOverrides.remove(playerUuid);
            if (playerOverrides.isEmpty()) {
                playerAfkPolicyOverridesByMatchId.remove(match.matchId());
            }
        }
        List<AfkPolicyStateAction> actions = collectAfkPolicyStateActions(
            playerUuid,
            previousPolicy,
            effectiveAfkDetectionPolicyFor(match, playerUuid)
        );
        return SetAfkDetectionPolicyResult.cleared(match, playerUuid, actions);
    }

    @Nonnull
    private AfkDetectionPolicy resolveEffectiveAfkDetectionPolicy(
        @Nonnull ArenaActiveMatch match,
        @Nonnull UUID playerUuid
    ) {
        Map<UUID, AfkDetectionPolicy> playerOverrides = playerAfkPolicyOverridesByMatchId.get(match.matchId());
        if (playerOverrides != null && playerOverrides.containsKey(playerUuid)) {
            return AfkDetectionPolicy.normalize(playerOverrides.get(playerUuid));
        }
        AfkDetectionPolicy matchOverride = matchAfkPolicyOverridesByMatchId.get(match.matchId());
        if (matchOverride != null) {
            return AfkDetectionPolicy.normalize(matchOverride);
        }
        return AfkDetectionPolicy.normalize(match.afkDetectionPolicy());
    }

    @Nonnull
    private EffectiveAfkDetectionPolicy effectiveAfkDetectionPolicyFor(
        @Nonnull ArenaActiveMatch match,
        @Nonnull UUID playerUuid
    ) {
        return new EffectiveAfkDetectionPolicy(
            match.matchId(),
            match.queueId(),
            match.arenaId(),
            match.rulesEngineId(),
            resolveEffectiveAfkDetectionPolicy(match, playerUuid)
        ).normalized();
    }

    @Nonnull
    private Map<UUID, EffectiveAfkDetectionPolicy> effectiveAfkPoliciesForMatch(@Nonnull ArenaActiveMatch match) {
        LinkedHashMap<UUID, EffectiveAfkDetectionPolicy> policiesByPlayerUuid = new LinkedHashMap<>();
        for (UUID playerUuid : afkPolicyPlayerUuids(match)) {
            policiesByPlayerUuid.put(playerUuid, effectiveAfkDetectionPolicyFor(match, playerUuid));
        }
        return Map.copyOf(policiesByPlayerUuid);
    }

    @Nonnull
    private List<AfkPolicyStateAction> collectAfkPolicyStateActions(
        @Nonnull ArenaActiveMatch match,
        @Nonnull Map<UUID, EffectiveAfkDetectionPolicy> previousPolicies
    ) {
        List<AfkPolicyStateAction> actions = new ArrayList<>();
        for (UUID playerUuid : afkPolicyPlayerUuids(match)) {
            EffectiveAfkDetectionPolicy previousPolicy = previousPolicies.get(playerUuid);
            if (previousPolicy == null) {
                previousPolicy = effectiveAfkDetectionPolicyFor(match, playerUuid);
            }
            actions.addAll(collectAfkPolicyStateActions(
                playerUuid,
                previousPolicy,
                effectiveAfkDetectionPolicyFor(match, playerUuid)
            ));
        }
        return List.copyOf(actions);
    }

    @Nonnull
    private List<AfkPolicyStateAction> collectAfkPolicyStateActions(
        @Nonnull UUID playerUuid,
        @Nonnull EffectiveAfkDetectionPolicy previousPolicy,
        @Nonnull EffectiveAfkDetectionPolicy updatedPolicy
    ) {
        AfkDetectionPolicy previous = AfkDetectionPolicy.normalize(previousPolicy.policy());
        AfkDetectionPolicy updated = AfkDetectionPolicy.normalize(updatedPolicy.policy());
        if (sameAfkDetectionPolicy(previous, updated)) {
            return List.of();
        }
        if (!updated.enabled()) {
            return List.of(new AfkPolicyStateAction(playerUuid, updatedPolicy, AfkPolicyStateActionType.CLEAR_FOR_POLICY_CHANGE));
        }
        return List.of(new AfkPolicyStateAction(playerUuid, updatedPolicy, AfkPolicyStateActionType.RESET_TIMER));
    }

    @Nonnull
    private List<UUID> afkPolicyPlayerUuids(@Nonnull ArenaActiveMatch match) {
        LinkedHashSet<UUID> playerUuids = new LinkedHashSet<>();
        playerUuids.addAll(match.arrivedPlayerUuids());
        playerUuids.addAll(match.activePlayerUuids());
        playerUuids.addAll(match.eliminatedPlayerUuids());
        playerUuids.addAll(match.spectatorPlayerUuids());
        playerUuids.addAll(match.expectedPlayerUuids());
        return PlayerUuidLists.canonicalize(playerUuids);
    }

    private static boolean sameAfkDetectionPolicy(@Nonnull AfkDetectionPolicy left, @Nonnull AfkDetectionPolicy right) {
        AfkDetectionPolicy normalizedLeft = AfkDetectionPolicy.normalize(left);
        AfkDetectionPolicy normalizedRight = AfkDetectionPolicy.normalize(right);
        return normalizedLeft.enabled() == normalizedRight.enabled()
            && normalizedLeft.inactivityTimeoutSeconds() == normalizedRight.inactivityTimeoutSeconds();
    }

    private void removePlayerAfkPolicyOverride(@Nonnull String rawMatchId, @Nonnull UUID playerUuid) {
        String matchId = normalizeOptional(rawMatchId);
        if (matchId.isBlank()) {
            return;
        }
        Map<UUID, AfkDetectionPolicy> playerOverrides = playerAfkPolicyOverridesByMatchId.get(matchId);
        if (playerOverrides == null) {
            return;
        }
        playerOverrides.remove(playerUuid);
        if (playerOverrides.isEmpty()) {
            playerAfkPolicyOverridesByMatchId.remove(matchId);
        }
    }

    private void clearAfkPolicyOverrides(@Nonnull String rawMatchId) {
        String matchId = normalizeOptional(rawMatchId);
        if (matchId.isBlank()) {
            return;
        }
        matchAfkPolicyOverridesByMatchId.remove(matchId);
        playerAfkPolicyOverridesByMatchId.remove(matchId);
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

        MatchPlacementEvaluation evaluation = evaluatePlacement(match);

        maybeLogPlacementState(
            match,
            evaluation.expectedPlayers(),
            evaluation.arrivedInitialPlayers(),
            evaluation.placedInitialPlayers(),
            evaluation.placementComplete()
        );

        return Optional.of(new MatchPlacementState(
            evaluation.expectedPlayers(),
            evaluation.arrivedInitialPlayers(),
            evaluation.placedInitialPlayers(),
            evaluation.placementComplete()
        ));
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
     * Returns the exact player set a rules mod must resolve before submitting a final match result.
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
        return setPlayerSpectator(rawMatchId, playerUuid, spectator, rawReason, null);
    }

    /**
     * Stores logical spectator state and optionally applies a temporary online spectator model.
     */
    @Nonnull
    public synchronized SetPlayerSpectatorResult setPlayerSpectator(
        @Nonnull String rawMatchId,
        @Nonnull UUID playerUuid,
        boolean spectator,
        @Nonnull String rawReason,
        @Nullable String spectatorModelId
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
        applyRuntimeSpectatorChange(updated, playerUuid, spectator, spectatorModelId);
        refreshRuntimeSpectatorVisibility(updated);
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
    public SubmitMatchResult submitFinalMatchResult(
        @Nonnull String rawMatchId,
        @Nonnull String rawReason,
        JsonObject rawCustomData
    ) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();
        SubmitMatchResult result;
        synchronized (this) {
            result = submitFinalMatchResultLocked(rawMatchId, rawReason, Map.of(), rawCustomData, lifecycleDispatches);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
        return result;
    }

    @Nonnull
    private SubmitMatchResult submitFinalMatchResultLocked(
        @Nonnull String rawMatchId,
        @Nonnull String rawReason,
        @Nonnull Map<String, String> metadata,
        JsonObject rawCustomData,
        @Nonnull List<Runnable> lifecycleDispatches
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
        collectMatchCompletedTransition(match, updated, "MATCH_COMPLETED", now, lifecycleDispatches);
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
     * Forces an active match to end and schedules every remaining player to return immediately.
     */
    @Nonnull
    public EndMatchResult endMatch(@Nonnull String rawMatchId, @Nonnull String rawReason) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();
        EndMatchResult result;
        synchronized (this) {
            result = endMatchLocked(rawMatchId, rawReason, lifecycleDispatches);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
        return result;
    }

    @Nonnull
    private EndMatchResult endMatchLocked(
        @Nonnull String rawMatchId,
        @Nonnull String rawReason,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
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
        restoreRuntimeSpectators(updated, SpectatorRuntimeReason.MATCH_CLEANUP);
        maybeScheduleAdmissionReporting(match, updated, now, "");
        collectMatchCompletedTransition(match, updated, "MATCH_COMPLETED", now, lifecycleDispatches);
        return EndMatchResult.completed(match.matchId(), updated.pendingReturnAtEpochMsByPlayerUuid().size());
    }

    /**
     * Cancels a match after the backend decides an AFK player should stop continuation.
     */
    @Nonnull
    public SubmitMatchResult cancelMatchForBackendAfk(
        @Nonnull String rawMatchId,
        @Nullable UUID triggeringPlayerUuid,
        @Nonnull String rawReasonCode,
        @Nonnull String rawMessage
    ) {
        List<Runnable> lifecycleDispatches = new ArrayList<>();
        SubmitMatchResult result;
        synchronized (this) {
            result = cancelMatchForBackendAfkLocked(rawMatchId, triggeringPlayerUuid, rawReasonCode, rawMessage, lifecycleDispatches);
        }
        dispatchLifecycleEvents(lifecycleDispatches);
        return result;
    }

    @Nonnull
    private SubmitMatchResult cancelMatchForBackendAfkLocked(
        @Nonnull String rawMatchId,
        @Nullable UUID triggeringPlayerUuid,
        @Nonnull String rawReasonCode,
        @Nonnull String rawMessage,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SubmitMatchResult.matchMissing(matchId);
        }
        if (match.hasSubmittedResult()) {
            return SubmitMatchResult.alreadySubmitted(match, match.resultPayloadHash(), false);
        }
        if (match.hasCompleted()) {
            return SubmitMatchResult.invalid(match, "Match was already completed.");
        }

        long now = System.currentTimeMillis();
        long returnAt = now + BACKEND_AFK_CANCEL_RETURN_DELAY_MS;
        String reasonCode = normalizeOptional(rawReasonCode, "BACKEND_AFK_CANCEL");
        String playerReason = normalizeOptional(rawMessage, "Match cancelled because a player went AFK.");
        collectMatchCancellationRequested(match, "BACKEND_AFK_CANCEL", now, lifecycleDispatches);
        ArenaActiveMatch updated = match;
        for (UUID playerUuid : buildRequiredResultPlayerUuids(match)) {
            updated = updated
                .withPlayerOutcome(playerUuid, ArenaPlayerResolutionOutcome.NO_CONTEST, "NO_CONTEST", playerReason, now)
                .withPendingReturn(playerUuid, returnAt, now);
        }

        String reason = "BACKEND_AFK_CANCEL";
        JsonObject customData = new JsonObject();
        customData.addProperty("cancelledBy", "nexori");
        customData.addProperty("cancelReason", reason);
        customData.addProperty("backendReasonCode", reasonCode);
        customData.addProperty("backendMessage", playerReason);
        if (triggeringPlayerUuid != null) {
            customData.addProperty("triggeringPlayerUuid", triggeringPlayerUuid.toString());
        }
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("cancelled_by", "nexori");
        metadata.put("cancel_reason", reason);
        metadata.put("backend_reason_code", reasonCode);

        MatchResultValidationResult validation = matchResultValidator.validateFinalResult(updated, reason, metadata, customData);
        if (!validation.valid()) {
            return SubmitMatchResult.invalid(updated, validation.message());
        }
        String payloadHash = hashFinalSubmittedResult(
            updated,
            toSubmitMatchPlayerResults(validation.players()),
            validation.metadata(),
            validation.reason(),
            validation.customData()
        );
        updated = updated
            .withLastError("Backend AFK cancellation requested: " + reasonCode, now)
            .withSubmittedResult(now, now, payloadHash);
        matchesById.put(updated.matchId(), updated);
        restoreRuntimeSpectators(updated, SpectatorRuntimeReason.MATCH_CLEANUP);
        maybeScheduleAdmissionReporting(match, updated, now, "");
        collectMatchCompletedTransition(match, updated, "MATCH_COMPLETED", now, lifecycleDispatches);
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

    @Nonnull
    private List<UUID> buildRequiredResultPlayerUuids(@Nonnull ArenaActiveMatch match) {
        LinkedHashSet<UUID> required = new LinkedHashSet<>();
        required.addAll(match.expectedPlayerUuids());
        required.addAll(match.arrivedPlayerUuids());
        required.addAll(match.activePlayerUuids());
        required.addAll(match.eliminatedPlayerUuids());
        return List.copyOf(required);
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

    private void handleLaunchArrival(
        @Nonnull PlayerReadyEvent event,
        @Nonnull PlayerRef playerRef,
        @Nonnull JsonObject context,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        LaunchContextData launch;
        try {
            launch = launchContextParser.parse(context);
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
        boolean matchCreated = existing == null;
        boolean playerAlreadyAssociated = existing != null
            && (existing.arrivedPlayerUuids().contains(playerRef.getUuid())
                || existing.activePlayerUuids().contains(playerRef.getUuid()));
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
                launch.afkDetectionPolicy(),
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
                storeUpdatedMatchOrCloseEmptyRuntime(previous, previousUpdated, now, "", lifecycleDispatches);
            }
        }

        ArenaActiveMatch beforeLifecycleReconcile = scheduleWinnerReturnIfNeeded(updated, now);
        updated = reconcileAdmissionLifecycle(beforeLifecycleReconcile, now);
        matchesById.put(updated.matchId(), updated);
        refreshRuntimeSpectatorVisibility(updated);
        if (updated.spectatorPlayerUuids().contains(playerRef.getUuid())) {
            enterRuntimeSpectator(updated, playerRef, null);
        }
        maybeScheduleAdmissionReporting(existing, updated, now, backfillArrival ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED");
        collectLaunchArrivalLifecycleEvents(
            matchCreated,
            playerAlreadyAssociated,
            updated,
            playerRef,
            backfillArrival,
            now,
            lifecycleDispatches
        );
        collectMatchPlacementCompletedTransition(
            beforeLifecycleReconcile,
            updated,
            "MATCH_PLACEMENT_COMPLETED",
            now,
            lifecycleDispatches
        );
        collectMatchCompletedTransition(
            beforeLifecycleReconcile,
            updated,
            "MATCH_COMPLETED",
            now,
            lifecycleDispatches
        );
        playerRef.sendMessage(Message.raw(
            "Joined Nexori match " + updated.matchId() + " on arena " + updated.arenaId() + "."
        ));
    }

    private void collectLaunchArrivalLifecycleEvents(
        boolean matchCreated,
        boolean playerAlreadyAssociated,
        @Nonnull ArenaActiveMatch match,
        @Nonnull PlayerRef playerRef,
        boolean backfillArrival,
        long eventAtEpochMs,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(
            match,
            matchCreated ? "MATCH_CREATED" : (backfillArrival ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED"),
            eventAtEpochMs
        );
        if (matchCreated) {
            lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchMatchCreated(matchEvent));
        }
        if (!playerAlreadyAssociated
            && (match.arrivedPlayerUuids().contains(playerRef.getUuid())
                || match.activePlayerUuids().contains(playerRef.getUuid()))) {
            NexoriPlayerMatchLifecycleEvent playerEvent = buildPlayerMatchLifecycleEvent(
                matchEvent,
                match,
                playerRef,
                backfillArrival ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED",
                eventAtEpochMs
            );
            lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchPlayerArrived(playerEvent));
        }
    }

    @Nonnull
    private NexoriPlayerMatchLifecycleEvent buildPlayerMatchLifecycleEvent(
        @Nonnull NexoriMatchLifecycleEvent matchEvent,
        @Nonnull ArenaActiveMatch match,
        @Nonnull PlayerRef playerRef,
        @Nonnull String reason,
        long eventAtEpochMs
    ) {
        return new NexoriPlayerMatchLifecycleEvent(
            matchEvent,
            playerRef.getUuid(),
            playerRef.getUsername(),
            match.assignmentIdsByPlayerUuid().get(playerRef.getUuid()),
            reason,
            eventAtEpochMs
        );
    }

    @Nonnull
    private NexoriMatchLifecycleEvent buildMatchLifecycleEvent(
        @Nonnull ArenaActiveMatch match,
        @Nonnull String reason,
        long eventAtEpochMs
    ) {
        MatchPlacementEvaluation evaluation = evaluatePlacement(match);
        return new NexoriMatchLifecycleEvent(
            match.matchId(),
            match.queueId(),
            match.arenaId(),
            match.assignmentId(),
            match.externalMatchId(),
            match.rulesEngineId(),
            "",
            match.expectedPlayerUuids(),
            match.arrivedPlayerUuids(),
            match.activePlayerUuids(),
            match.spectatorPlayerUuids(),
            buildRequiredResultPlayerUuids(match),
            new NexoriMatchPlacementState(
                evaluation.expectedPlayers(),
                evaluation.arrivedInitialPlayers(),
                evaluation.placedInitialPlayers(),
                evaluation.placementComplete()
            ),
            reason,
            match.createdAtEpochMs(),
            eventAtEpochMs
        );
    }

    private void dispatchLifecycleEvents(@Nonnull List<Runnable> lifecycleDispatches) {
        for (Runnable lifecycleDispatch : lifecycleDispatches) {
            lifecycleDispatch.run();
        }
    }

    private void collectMatchPlacementCompletedTransition(
        @Nonnull ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated,
        @Nonnull String reason,
        long eventAtEpochMs,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        if (previous.placementCompletedAtEpochMs() > 0L || updated.placementCompletedAtEpochMs() <= 0L) {
            return;
        }
        NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(updated, reason, eventAtEpochMs);
        lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchMatchPlacementCompleted(matchEvent));
    }

    private void collectMatchCompletedTransition(
        @Nonnull ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated,
        @Nonnull String reason,
        long eventAtEpochMs,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        if (previous.completedAtEpochMs() > 0L || updated.completedAtEpochMs() <= 0L) {
            return;
        }
        NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(updated, reason, eventAtEpochMs);
        lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchMatchCompleted(matchEvent));
    }

    private void collectMatchCancellationRequested(
        @Nonnull ArenaActiveMatch match,
        @Nonnull String reason,
        long eventAtEpochMs,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        if (match.hasCompleted()) {
            return;
        }
        NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(match, reason, eventAtEpochMs);
        lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchMatchCancellationRequested(matchEvent));
    }

    private void collectMatchRuntimeClosedTransition(
        @Nonnull ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated,
        @Nonnull String reason,
        long eventAtEpochMs,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        if (previous.isEmpty() || !isRuntimeEmptyAfterPlacement(updated)) {
            return;
        }
        NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(updated, reason, eventAtEpochMs);
        clearAfkPolicyOverrides(matchEvent.matchId());
        lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchMatchRuntimeClosed(matchEvent));
        lifecycleDispatches.add(() -> matchRuntimeClosedCallback.accept(matchEvent.matchId()));
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
        @Nonnull LaunchContextData launch
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
     * Schedules lobby return for an already reported winner.
     */
    @Nonnull
    private ArenaActiveMatch scheduleWinnerReturnIfNeeded(@Nonnull ArenaActiveMatch match, long nowEpochMs) {
        if (match.hasWinner()) {
            UUID winnerUuid = parseWinnerUuid(match.winnerPlayerUuid());
            if (winnerUuid != null && !match.hasPendingReturn(winnerUuid) && match.hasPlayer(winnerUuid)) {
                return match.withPendingReturn(winnerUuid, nowEpochMs + WINNER_RETURN_DELAY_MS, nowEpochMs);
            }
            return match;
        }
        return match;
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
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
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
            restoreRuntimeSpectator(playerRef.getUuid(), SpectatorRuntimeReason.RETURN_TO_LOBBY);
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
            restoreRuntimeSpectator(playerUuid, SpectatorRuntimeReason.MATCH_CLEANUP);
            String currentMatchId = matchIdByPlayerUuid.get(playerUuid);
            if (matchId.equals(currentMatchId)) {
                matchIdByPlayerUuid.remove(playerUuid);
            }
        }
    }

    private void applyRuntimeSpectatorChange(
        @Nonnull ArenaActiveMatch match,
        @Nonnull UUID playerUuid,
        boolean spectator,
        @Nullable String spectatorModelId
    ) {
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        if (spectator) {
            if (playerRef == null) {
                return;
            }
            enterRuntimeSpectator(match, playerRef, spectatorModelId);
        } else {
            restoreRuntimeSpectator(playerUuid, SpectatorRuntimeReason.MATCH_CLEANUP);
        }
    }

    private void refreshRuntimeSpectatorForReadyPlayer(@Nonnull PlayerRef playerRef) {
        String matchId = matchIdByPlayerUuid.get(playerRef.getUuid());
        if (matchId == null) {
            return;
        }
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null || !match.hasPlayer(playerRef.getUuid())) {
            return;
        }
        if (match.spectatorPlayerUuids().contains(playerRef.getUuid())) {
            enterRuntimeSpectator(match, playerRef, null);
        }
        refreshRuntimeSpectatorVisibility(match);
    }

    private void enterRuntimeSpectator(
        @Nonnull ArenaActiveMatch match,
        @Nonnull PlayerRef playerRef,
        @Nullable String spectatorModelId
    ) {
        SpectatorRuntimeResult result = spectatorRuntimeController.enterSpectator(
            playerRef,
            runtimeSpectatorViewerUuids(match, playerRef.getUuid()),
            SpectatorRuntimeReason.MINIGAME_SPECTATOR,
            spectatorModelId
        );
        logRuntimeSpectatorWarning(playerRef.getUuid(), result);
    }

    private void restoreRuntimeSpectators(@Nonnull ArenaActiveMatch match, @Nonnull SpectatorRuntimeReason reason) {
        LinkedHashSet<UUID> playerUuids = new LinkedHashSet<>();
        playerUuids.addAll(match.spectatorPlayerUuids());
        playerUuids.addAll(match.activePlayerUuids());
        playerUuids.addAll(match.eliminatedPlayerUuids());
        for (UUID playerUuid : playerUuids) {
            restoreRuntimeSpectator(playerUuid, reason);
        }
    }

    private void restoreRuntimeSpectator(@Nonnull UUID playerUuid, @Nonnull SpectatorRuntimeReason reason) {
        SpectatorRuntimeResult result = spectatorRuntimeController.restoreIfTracked(playerUuid, reason);
        logRuntimeSpectatorWarning(playerUuid, result);
    }

    private void refreshRuntimeSpectatorVisibility(@Nonnull ArenaActiveMatch match) {
        for (UUID spectatorUuid : match.spectatorPlayerUuids()) {
            SpectatorRuntimeResult result = spectatorRuntimeController.refreshHiddenViewers(
                spectatorUuid,
                runtimeSpectatorViewerUuids(match, spectatorUuid)
            );
            logRuntimeSpectatorWarning(spectatorUuid, result);
        }
    }

    @Nonnull
    private List<UUID> runtimeSpectatorViewerUuids(@Nonnull ArenaActiveMatch match, @Nonnull UUID spectatorUuid) {
        LinkedHashSet<UUID> viewerUuids = new LinkedHashSet<>(match.activePlayerUuids());
        viewerUuids.remove(spectatorUuid);
        viewerUuids.removeAll(match.spectatorPlayerUuids());
        return List.copyOf(viewerUuids);
    }

    private void logRuntimeSpectatorWarning(@Nonnull UUID playerUuid, @Nonnull SpectatorRuntimeResult result) {
        if (result.succeeded()) {
            return;
        }
        logger.atWarning().log(
            "Nexori spectator runtime operation failed for player "
                + playerUuid
                + ". "
                + result.summary()
        );
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
        MatchPlacementEvaluation evaluation = evaluatePlacement(updated);
        if (evaluation.shouldMarkPlacementCompleted()) {
            updated = updated.withPlacementCompleted(nowEpochMs, nowEpochMs);
        }
        evaluation = evaluatePlacement(updated);
        if (updated.completedAtEpochMs() <= 0L && evaluation.shouldMarkMatchCompleted()) {
            updated = updated.withCompleted(nowEpochMs, nowEpochMs);
        }
        return updated;
    }

    @Nonnull
    private MatchPlacementEvaluation evaluatePlacement(@Nonnull ArenaActiveMatch match) {
        return matchPlacementEvaluator.evaluate(match, pendingUnconfirmedPlacementPlayerUuids(match));
    }

    @Nonnull
    private Set<UUID> pendingUnconfirmedPlacementPlayerUuids(@Nonnull ArenaActiveMatch match) {
        if (match.expectedPlayerUuids().isEmpty() || match.arrivedPlayerUuids().isEmpty()) {
            return Set.of();
        }
        if (minigameTransferService == null) {
            return Set.of();
        }
        LinkedHashSet<UUID> pendingUnconfirmed = new LinkedHashSet<>();
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            if (!expected.contains(playerUuid)) {
                continue;
            }
            if (minigameTransferService.hasPendingPlacementSession(playerUuid)) {
                pendingUnconfirmed.add(playerUuid);
            }
        }
        return Set.copyOf(pendingUnconfirmed);
    }

    private void storeUpdatedMatchOrCloseEmptyRuntime(
        @Nonnull ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated,
        long nowEpochMs,
        @Nonnull String primaryReason,
        @Nonnull List<Runnable> lifecycleDispatches
    ) {
        restoreRuntimeForPlayersRemovedFromMatch(previous, updated);
        collectMatchRuntimeClosedTransition(
            previous,
            updated,
            "MATCH_RUNTIME_CLOSED",
            nowEpochMs,
            lifecycleDispatches
        );
        ArenaActiveMatch stored = updated;
        String reason = primaryReason;
        if (isRuntimeEmptyAfterPlacement(updated)) {
            if (!shouldReportEmptyRuntimeAdmissionClosure(updated)) {
                matchesById.remove(updated.matchId());
                clearAfkPolicyOverrides(updated.matchId());
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

    private boolean isRuntimeEmptyAfterPlacement(@Nonnull ArenaActiveMatch match) {
        return match.isEmpty() && !hasPendingInitialPlacement(match);
    }

    private boolean hasPendingInitialPlacement(@Nonnull ArenaActiveMatch match) {
        return match.placementCompletedAtEpochMs() <= 0L
            && !pendingUnconfirmedPlacementPlayerUuids(match).isEmpty();
    }

    private void restoreRuntimeForPlayersRemovedFromMatch(
        @Nonnull ArenaActiveMatch previous,
        @Nonnull ArenaActiveMatch updated
    ) {
        LinkedHashSet<UUID> previousPlayers = new LinkedHashSet<>();
        previousPlayers.addAll(previous.activePlayerUuids());
        previousPlayers.addAll(previous.eliminatedPlayerUuids());
        previousPlayers.addAll(previous.spectatorPlayerUuids());
        previousPlayers.removeAll(updated.activePlayerUuids());
        previousPlayers.removeAll(updated.eliminatedPlayerUuids());
        previousPlayers.removeAll(updated.spectatorPlayerUuids());
        for (UUID playerUuid : previousPlayers) {
            restoreRuntimeSpectator(playerUuid, SpectatorRuntimeReason.MATCH_CLEANUP);
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

    public enum SetAfkDetectionPolicyOutcome {
        UPDATED,
        CLEARED,
        MATCH_MISSING,
        PLAYER_MISSING,
        MATCH_ALREADY_COMPLETED,
        INVALID_POLICY
    }

    public enum AfkPolicyStateActionType {
        RESET_TIMER,
        CLEAR_FOR_POLICY_CHANGE
    }

    public record AfkPolicyStateAction(
        @Nonnull UUID playerUuid,
        @Nonnull EffectiveAfkDetectionPolicy effectivePolicy,
        @Nonnull AfkPolicyStateActionType type
    ) {
    }

    public record SetAfkDetectionPolicyResult(
        SetAfkDetectionPolicyOutcome outcome,
        String matchId,
        UUID playerUuid,
        AfkDetectionPolicy policy,
        String message,
        List<AfkPolicyStateAction> stateActions
    ) {

        @Nonnull
        public static SetAfkDetectionPolicyResult updated(
            @Nonnull ArenaActiveMatch activeMatch,
            UUID playerUuid,
            @Nonnull AfkDetectionPolicy policy,
            @Nonnull List<AfkPolicyStateAction> stateActions
        ) {
            return new SetAfkDetectionPolicyResult(
                SetAfkDetectionPolicyOutcome.UPDATED,
                activeMatch.matchId(),
                playerUuid,
                AfkDetectionPolicy.normalize(policy),
                "",
                List.copyOf(stateActions)
            );
        }

        @Nonnull
        public static SetAfkDetectionPolicyResult cleared(
            @Nonnull ArenaActiveMatch activeMatch,
            UUID playerUuid,
            @Nonnull List<AfkPolicyStateAction> stateActions
        ) {
            return new SetAfkDetectionPolicyResult(
                SetAfkDetectionPolicyOutcome.CLEARED,
                activeMatch.matchId(),
                playerUuid,
                null,
                "",
                List.copyOf(stateActions)
            );
        }

        @Nonnull
        public static SetAfkDetectionPolicyResult matchMissing(@Nonnull String matchId) {
            return new SetAfkDetectionPolicyResult(
                SetAfkDetectionPolicyOutcome.MATCH_MISSING,
                normalizeRequired(matchId, "Match id cannot be blank."),
                null,
                null,
                "Match is not active.",
                List.of()
            );
        }

        @Nonnull
        public static SetAfkDetectionPolicyResult playerMissing(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid) {
            return new SetAfkDetectionPolicyResult(
                SetAfkDetectionPolicyOutcome.PLAYER_MISSING,
                activeMatch.matchId(),
                playerUuid,
                null,
                "Player is not part of the active match.",
                List.of()
            );
        }

        @Nonnull
        public static SetAfkDetectionPolicyResult matchAlreadyCompleted(@Nonnull ArenaActiveMatch activeMatch, UUID playerUuid) {
            return new SetAfkDetectionPolicyResult(
                SetAfkDetectionPolicyOutcome.MATCH_ALREADY_COMPLETED,
                activeMatch.matchId(),
                playerUuid,
                null,
                "Match result was already submitted.",
                List.of()
            );
        }

        @Nonnull
        public static SetAfkDetectionPolicyResult invalidPolicy(@Nonnull String matchId, UUID playerUuid, @Nonnull String message) {
            return new SetAfkDetectionPolicyResult(
                SetAfkDetectionPolicyOutcome.INVALID_POLICY,
                normalizeOptional(matchId),
                playerUuid,
                null,
                normalizeOptional(message, "Invalid AFK detection policy."),
                List.of()
            );
        }
    }

    public enum SetPlayerAfkOutcome {
        VALIDATED,
        MATCH_MISSING,
        PLAYER_MISSING,
        MATCH_ALREADY_COMPLETED
    }

    public record SetPlayerAfkResult(
        SetPlayerAfkOutcome outcome,
        String matchId,
        String queueId,
        String arenaId,
        String rulesEngineId,
        @Nullable UUID playerUuid,
        String username,
        String message
    ) {

        @Nonnull
        public static SetPlayerAfkResult validated(
            @Nonnull ArenaActiveMatch match,
            @Nonnull UUID playerUuid,
            @Nonnull String username
        ) {
            return new SetPlayerAfkResult(
                SetPlayerAfkOutcome.VALIDATED,
                match.matchId(),
                match.queueId(),
                match.arenaId(),
                match.rulesEngineId(),
                playerUuid,
                username,
                ""
            );
        }

        @Nonnull
        public static SetPlayerAfkResult matchMissing(@Nonnull String matchId) {
            return new SetPlayerAfkResult(
                SetPlayerAfkOutcome.MATCH_MISSING,
                matchId,
                "", "", "", null, "",
                "Match is not active."
            );
        }

        @Nonnull
        public static SetPlayerAfkResult playerMissing(@Nonnull ArenaActiveMatch match, UUID playerUuid) {
            return new SetPlayerAfkResult(
                SetPlayerAfkOutcome.PLAYER_MISSING,
                match.matchId(),
                match.queueId(),
                match.arenaId(),
                match.rulesEngineId(),
                playerUuid,
                "",
                "Player is not part of the active match."
            );
        }

        @Nonnull
        public static SetPlayerAfkResult matchAlreadyCompleted(@Nonnull ArenaActiveMatch match, UUID playerUuid) {
            return new SetPlayerAfkResult(
                SetPlayerAfkOutcome.MATCH_ALREADY_COMPLETED,
                match.matchId(),
                match.queueId(),
                match.arenaId(),
                match.rulesEngineId(),
                playerUuid,
                "",
                "Match result was already submitted."
            );
        }
    }

    @Nonnull
    public synchronized SetPlayerAfkResult validateForExternalAfk(
        @Nonnull String rawMatchId,
        @Nullable UUID playerUuid
    ) {
        String matchId = normalizeRequired(rawMatchId, "Match id cannot be blank.");
        ArenaActiveMatch match = matchesById.get(matchId);
        if (match == null) {
            return SetPlayerAfkResult.matchMissing(matchId);
        }
        if (match.hasCompleted()) {
            return SetPlayerAfkResult.matchAlreadyCompleted(match, playerUuid);
        }
        if (playerUuid == null || !match.hasPlayer(playerUuid)) {
            return SetPlayerAfkResult.playerMissing(match, playerUuid);
        }
        PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
        String username = playerRef != null && playerRef.isValid() ? playerRef.getUsername() : "";
        return SetPlayerAfkResult.validated(match, playerUuid, username);
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

    /**
     * Implements the MinigameTransferService.MatchGateway so MinigameTransferService can drive
     * match mutations without a circular constructor dependency.
     *
     * All methods on this gateway must be called from within ArenaMatchService's synchronized block.
     */
    private final class TransferMatchGateway implements MinigameTransferService.MatchGateway {

        @Override
        @Nonnull
        public Optional<ArenaActiveMatch> findMatchRaw(@Nonnull String matchId) {
            return Optional.ofNullable(matchesById.get(matchId));
        }

        @Override
        @Nonnull
        public List<Runnable> acceptTransferArrival(
            @Nonnull UUID playerUuid,
            @Nonnull String username,
            @Nonnull LaunchContextData launch,
            @Nullable ArenaActiveMatch existingMatch,
            long nowEpochMs
        ) {
            List<Runnable> lifecycleDispatches = new ArrayList<>();
            boolean matchCreated = existingMatch == null;
            boolean playerAlreadyAssociated = existingMatch != null
                && (existingMatch.arrivedPlayerUuids().contains(playerUuid)
                    || existingMatch.activePlayerUuids().contains(playerUuid));
            boolean isBackfill = ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType());
            String instanceWorldName = launch.usesInstanceTemplate()
                ? ArenaInstanceRuntime.buildInstanceWorldName(launch.matchId())
                : "";

            ArenaActiveMatch updated;
            if (existingMatch == null) {
                updated = new ArenaActiveMatch(
                    launch.matchId(),
                    launch.queueId(),
                    launch.arenaId(),
                    launch.originLobbyId(),
                    launch.returnConnectionAddress(),
                    launch.returnFallbackTargetId(),
                    launch.launchTravelProfileId(),
                    launch.instanceTemplateId(),
                    instanceWorldName,
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
                    launch.afkDetectionPolicy(),
                    launch.expectedPlayerUuids(),
                    launch.expectedPlayerCount(),
                    List.of(playerUuid),          // arrivedPlayerUuids
                    List.<UUID>of(),               // activePlayerUuids – deferred until CONFIRMED
                    List.<UUID>of(),
                    List.<UUID>of(),
                    launch.assignmentId().isBlank() ? Map.of() : Map.of(playerUuid, launch.assignmentId()),
                    Map.of(playerUuid, launch.playerReturnTarget()),
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
                    nowEpochMs,
                    nowEpochMs,
                    ""
                ).normalized();
            } else {
                updated = existingMatch
                    .withPlayerReturnTarget(playerUuid, launch.playerReturnTarget(), nowEpochMs)
                    .withPlayerAssignmentId(playerUuid, launch.assignmentId(), nowEpochMs)
                    .withPlayerArrivedOnly(playerUuid, nowEpochMs);
            }

            if (isBackfill) {
                updated = updated
                    .withAcceptedBackfillReservation(launch.admissionReservationId(), nowEpochMs)
                    .withConsumedBackfillAdmissionIncrement(nowEpochMs);
                if (backendMatchAdmissionStateReportingService != null) {
                    backendMatchAdmissionStateReportingService.markAdmissionReservationConsumed(
                        updated.matchId(), launch.admissionReservationId(), nowEpochMs
                    );
                }
            }

            String previousMatchId = matchIdByPlayerUuid.put(playerUuid, updated.matchId());
            if (previousMatchId != null && !previousMatchId.equals(updated.matchId())) {
                ArenaActiveMatch previous = matchesById.get(previousMatchId);
                if (previous != null) {
                    ArenaActiveMatch previousUpdated = previous.withoutReturnedPlayer(playerUuid, nowEpochMs);
                    storeUpdatedMatchOrCloseEmptyRuntime(previous, previousUpdated, nowEpochMs, "", lifecycleDispatches);
                }
            }

            ArenaActiveMatch beforeLifecycleReconcile = scheduleWinnerReturnIfNeeded(updated, nowEpochMs);
            updated = reconcileAdmissionLifecycle(beforeLifecycleReconcile, nowEpochMs);
            matchesById.put(updated.matchId(), updated);
            refreshRuntimeSpectatorVisibility(updated);
            if (updated.spectatorPlayerUuids().contains(playerUuid)) {
                PlayerRef spectatorRef = Universe.get().getPlayer(playerUuid);
                if (spectatorRef != null) {
                    enterRuntimeSpectator(updated, spectatorRef, null);
                }
            }
            maybeScheduleAdmissionReporting(
                existingMatch, updated, nowEpochMs,
                isBackfill ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED"
            );

            NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(
                updated,
                matchCreated ? "MATCH_CREATED" : (isBackfill ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED"),
                nowEpochMs
            );
            if (matchCreated) {
                lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchMatchCreated(matchEvent));
                logger.atInfo().log(
                    "NEXORI_TRANSFER_MATCH_CREATED matchId=" + updated.matchId()
                        + " arenaId=" + updated.arenaId()
                );
            } else {
                logger.atInfo().log(
                    "NEXORI_TRANSFER_MATCH_RESOLVED matchId=" + updated.matchId()
                        + " arenaId=" + updated.arenaId()
                );
            }

            if (!playerAlreadyAssociated && updated.arrivedPlayerUuids().contains(playerUuid)) {
                PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
                NexoriPlayerMatchLifecycleEvent playerEvent = buildPlayerMatchLifecycleEvent(
                    matchEvent, updated,
                    playerRef != null ? playerRef : buildFallbackPlayerRef(playerUuid, username),
                    isBackfill ? "BACKFILL_PLAYER_ARRIVED" : "PLAYER_ARRIVED",
                    nowEpochMs
                );
                lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchPlayerArrived(playerEvent));
                logger.atInfo().log(
                    "NEXORI_TRANSFER_CONTEXT_ACCEPTED player=" + playerUuid
                        + " username=" + username
                        + " matchId=" + updated.matchId()
                        + " assignmentType=" + launch.assignmentType()
                );
            }

            collectMatchPlacementCompletedTransition(
                beforeLifecycleReconcile, updated, "MATCH_PLACEMENT_COMPLETED", nowEpochMs, lifecycleDispatches
            );
            collectMatchCompletedTransition(
                beforeLifecycleReconcile, updated, "MATCH_COMPLETED", nowEpochMs, lifecycleDispatches
            );

            // If a new match was just created, retry any backfill arrivals that were waiting for it.
            if (matchCreated && minigameTransferService != null) {
                List<Runnable> retryDispatches = minigameTransferService.processPendingBackfillRetries(launch.matchId(), nowEpochMs);
                lifecycleDispatches.addAll(retryDispatches);
            }

            return lifecycleDispatches;
        }

        @Override
        @Nonnull
        public List<Runnable> confirmTransferPlacement(@Nonnull UUID playerUuid, long nowEpochMs) {
            List<Runnable> lifecycleDispatches = new ArrayList<>();
            String matchId = matchIdByPlayerUuid.get(playerUuid);
            if (matchId == null) {
                return lifecycleDispatches;
            }
            ArenaActiveMatch match = matchesById.get(matchId);
            if (match == null || !match.hasPlayer(playerUuid)) {
                return lifecycleDispatches;
            }

            // Guard against double-dispatch: if the player is already in activePlayerUuids,
            // placement was already confirmed and we don't re-emit the lifecycle event.
            if (match.activePlayerUuids().contains(playerUuid)) {
                return lifecycleDispatches;
            }

            ArenaActiveMatch before = match;
            ArenaActiveMatch updated = match.withPlayerPlacementConfirmed(playerUuid, nowEpochMs);
            ArenaActiveMatch beforeLifecycleReconcile = scheduleWinnerReturnIfNeeded(updated, nowEpochMs);
            updated = reconcileAdmissionLifecycle(beforeLifecycleReconcile, nowEpochMs);
            matchesById.put(updated.matchId(), updated);

            // Dispatch placement confirmed event
            NexoriMatchLifecycleEvent matchEvent = buildMatchLifecycleEvent(
                updated, "PLACEMENT_CONFIRMED", nowEpochMs
            );
            Universe universe = Universe.get();
            PlayerRef playerRef = (universe != null) ? universe.getPlayer(playerUuid) : null;
            if (playerRef != null) {
                NexoriPlayerMatchLifecycleEvent playerMatchEvent = buildPlayerMatchLifecycleEvent(
                    matchEvent, updated, playerRef, "PLACEMENT_CONFIRMED", nowEpochMs
                );
                NexoriPlayerPlacementLifecycleEvent placementEvent = new NexoriPlayerPlacementLifecycleEvent(
                    playerMatchEvent,
                    NexoriPlayerPlacementOutcome.CONFIRMED,
                    matchEvent.placementState(),
                    minigameTransferService != null
                        ? minigameTransferService.findSession(playerUuid).map(s -> s.expectedWorldName()).orElse("")
                        : "",
                    minigameTransferService != null
                        ? minigameTransferService.findSession(playerUuid).map(s -> s.instanceTemplateId()).orElse("")
                        : "",
                    nowEpochMs
                );
                lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchPlayerPlacementConfirmed(placementEvent));
            } else {
                // Universe not available (e.g. in tests) — still collect the lifecycle dispatch
                // using a no-player placement event so listener logic can be exercised.
                String assignmentId = normalizeOptional(updated.assignmentIdsByPlayerUuid().get(playerUuid));
                NexoriPlayerPlacementLifecycleEvent placementEvent = new NexoriPlayerPlacementLifecycleEvent(
                    new NexoriPlayerMatchLifecycleEvent(matchEvent, playerUuid, "", assignmentId, "PLACEMENT_CONFIRMED", nowEpochMs),
                    NexoriPlayerPlacementOutcome.CONFIRMED,
                    matchEvent.placementState(),
                    "",
                    "",
                    nowEpochMs
                );
                lifecycleDispatches.add(() -> matchLifecycleDispatcher.dispatchPlayerPlacementConfirmed(placementEvent));
            }

            collectMatchPlacementCompletedTransition(
                beforeLifecycleReconcile, updated, "MATCH_PLACEMENT_COMPLETED", nowEpochMs, lifecycleDispatches
            );
            collectMatchCompletedTransition(
                beforeLifecycleReconcile, updated, "MATCH_COMPLETED", nowEpochMs, lifecycleDispatches
            );
            return lifecycleDispatches;
        }

        @Override
        @Nonnull
        public List<Runnable> failTransferPlacement(
            @Nonnull UUID playerUuid,
            @Nonnull String reason,
            @Nonnull String returnConnectionAddress,
            @Nonnull String returnFallbackTargetId,
            @Nonnull String launchTravelProfileId,
            @Nonnull String originLobbyId,
            @Nonnull String sessionMatchId,
            long nowEpochMs
        ) {
            List<Runnable> lifecycleDispatches = new ArrayList<>();

            // Remove from match runtime first so the player cannot be double-processed.
            String matchId = matchIdByPlayerUuid.get(playerUuid);
            if (matchId != null) {
                ArenaActiveMatch match = matchesById.get(matchId);
                if (match != null && match.hasPlayer(playerUuid)) {
                    ArenaActiveMatch updated = match.withoutReturnedPlayer(playerUuid, nowEpochMs)
                        .withLastError("Transfer placement failed: " + reason, nowEpochMs);
                    updated = reconcileAdmissionLifecycle(scheduleWinnerReturnIfNeeded(updated, nowEpochMs), nowEpochMs);
                    storeUpdatedMatchOrCloseEmptyRuntime(match, updated, nowEpochMs, "TRANSFER_FAILED", lifecycleDispatches);
                } else if (match == null) {
                    // nothing to clean up in match
                }
            }
            matchIdByPlayerUuid.remove(playerUuid);

            // Attempt controlled return-to-lobby if return info is available and player is online.
            String effectiveReturnAddr = normalizeOptional(returnConnectionAddress);
            String effectiveReturnTarget = normalizeOptional(returnFallbackTargetId);
            String effectiveTravelProfile = normalizeOptional(launchTravelProfileId);
            if (!effectiveReturnAddr.isBlank() && !effectiveReturnTarget.isBlank() && !effectiveTravelProfile.isBlank()) {
                Universe universe = Universe.get();
                PlayerRef playerRef = (universe != null) ? universe.getPlayer(playerUuid) : null;
                if (playerRef != null && playerRef.isValid()) {
                    try {
                        ConfiguredPeer destination = ConfiguredPeer.parse(effectiveReturnAddr);
                        String effectiveMatchId = normalizeOptional(sessionMatchId, normalizeOptional(matchId, ""));
                        String effectiveLobbyId = normalizeOptional(originLobbyId, "lobby");
                        String returnContextJson = buildFailedPlacementReturnContextJson(
                            effectiveMatchId, effectiveLobbyId, reason, nowEpochMs
                        );
                        secureTravelService.travel(
                            playerRef, destination, effectiveReturnTarget, "",
                            effectiveTravelProfile, returnContextJson
                        );
                        logger.atInfo().log(
                            "NEXORI_TRANSFER_RETURNING_TO_LOBBY player=" + playerUuid
                                + " matchId=" + effectiveMatchId
                                + " reason=" + reason
                                + " destination=" + effectiveReturnAddr
                        );
                    } catch (Exception ex) {
                        logger.atWarning().withCause(ex).log(
                            "NEXORI_TRANSFER_FAILED_RETURN_ERROR player=" + playerUuid
                                + " reason=return_to_lobby_failed detail=" + ex.getClass().getSimpleName()
                        );
                    }
                } else {
                    logger.atWarning().log(
                        "NEXORI_TRANSFER_FAILED player=" + playerUuid
                            + " reason=RETURN_INFO_MISSING detail=player_offline_or_universe_null"
                    );
                }
            } else {
                logger.atWarning().log(
                    "NEXORI_TRANSFER_FAILED player=" + playerUuid
                        + " reason=RETURN_INFO_MISSING detail=blank_return_address"
                );
            }

            return lifecycleDispatches;
        }

        @Nonnull
        private String buildFailedPlacementReturnContextJson(
            @Nonnull String matchId,
            @Nonnull String originLobbyId,
            @Nonnull String failureReason,
            long nowEpochMs
        ) {
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("flowType", "minigame.return");
            root.addProperty("matchId", matchId);
            root.addProperty("queueId", "");
            root.addProperty("originLobbyId", originLobbyId);
            root.addProperty("sourceArenaId", "");
            root.addProperty("returnReason", "PLACEMENT_FAILED:" + failureReason);
            root.addProperty("returnedAtEpochMs", nowEpochMs);
            return new com.google.gson.Gson().toJson(root);
        }

        private PlayerRef buildFallbackPlayerRef(@Nonnull UUID playerUuid, @Nonnull String username) {
            // Used only when playerRef is not available online (rare edge case).
            // In practice the player should be online if acceptTransferArrival is being called.
            return Universe.get().getPlayer(playerUuid);
        }
    }

}
