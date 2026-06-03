package io.github.hyjn.nexori.plugin.minigame.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaInstanceRuntime;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotDefinition;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotService;
import io.github.hyjn.nexori.plugin.minigame.logic.BackfillAdmissionDecider;
import io.github.hyjn.nexori.plugin.minigame.logic.BackfillAdmissionDecision;
import io.github.hyjn.nexori.plugin.minigame.logic.LaunchContextData;
import io.github.hyjn.nexori.plugin.minigame.logic.LaunchContextParser;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Unified state machine that moves every minigame player from default-world arrival through
 * instance teleport to confirmed in-world placement.
 *
 * <p>Handles all three cases with one code path:</p>
 * <ul>
 *   <li>INITIAL_MATCH with instance template – materializes the instance world, then teleports.</li>
 *   <li>BACKFILL with instance template – resolves the existing instance world, then teleports.</li>
 *   <li>No-instance arena – skips instance phases and confirms immediately after match accepts.</li>
 * </ul>
 *
 * <p><strong>Thread safety:</strong> all public mutating methods must be called from within the
 * synchronized context of {@link io.github.hyjn.nexori.plugin.minigame.ArenaMatchService}.
 * This class does not synchronize on its own.</p>
 */
public final class MinigameTransferService {

    private static final Gson GSON = new Gson();
    private static final String ASSIGNMENT_TYPE_BACKFILL = BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL;

    // Phase timeouts
    private static final long TIMEOUT_INSTANCE_WORLD_CREATING_MS = 30_000L;
    private static final long TIMEOUT_TELEPORT_TO_INSTANCE_MS = 15_000L;
    private static final long TIMEOUT_WRONG_WORLD_MS = 15_000L;
    private static final long TIMEOUT_POST_READY_GRACE_MS = 750L;
    private static final long TIMEOUT_VALIDATING_POSITION_MS = 10_000L;
    private static final long TIMEOUT_TELEPORT_COMPONENT_STUCK_MS = 12_000L;

    // Placement validation
    private static final double PLACEMENT_POSITION_EPSILON_SQUARED = 1.0D;
    private static final int PLACEMENT_REQUIRED_STABLE_TICKS = 2;

    private final HytaleLogger logger;
    private final InstanceSpawnSlotService instanceSpawnSlotService;
    private final InstanceTeleportIssuer instanceTeleportIssuer;
    private final TeleportComponentChecker teleportComponentChecker;
    private final LaunchContextParser launchContextParser = new LaunchContextParser();
    private final BackfillAdmissionDecider backfillAdmissionDecider = new BackfillAdmissionDecider();

    @FunctionalInterface
    interface InstanceTeleportIssuer {
        void teleport(
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull Store<EntityStore> store,
            @Nonnull CompletableFuture<World> targetWorld,
            @Nonnull Transform transform
        );
    }

    @FunctionalInterface
    interface TeleportComponentChecker {
        boolean hasPendingTeleport(
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull Store<EntityStore> store
        );
    }

    /**
     * Callback interface so MinigameTransferService can drive match mutations in ArenaMatchService
     * without a circular constructor dependency.
     */
    public interface MatchGateway {

        /**
         * Returns the existing match for a matchId, or empty. Called without re-acquiring
         * ArenaMatchService's lock (caller is already inside it).
         */
        @Nonnull
        Optional<ArenaActiveMatch> findMatchRaw(@Nonnull String matchId);

        /**
         * Creates or updates the match record for a new player arrival.
         * Adds the player to arrivedPlayerUuids only — NOT to activePlayerUuids yet.
         * Handles backfill reservation consumption and lifecycle dispatch setup.
         *
         * @return list of lifecycle Runnables to dispatch after the synchronized block exits
         */
        @Nonnull
        List<Runnable> acceptTransferArrival(
            @Nonnull UUID playerUuid,
            @Nonnull String username,
            @Nonnull LaunchContextData launch,
            @Nullable ArenaActiveMatch existingMatch,
            long nowEpochMs
        );

        /**
         * Moves the player from arrived to active and emits placement-confirmed lifecycle events.
         *
         * @return list of lifecycle Runnables to dispatch after the synchronized block exits
         */
        @Nonnull
        List<Runnable> confirmTransferPlacement(@Nonnull UUID playerUuid, long nowEpochMs);

        /**
         * Handles a transfer failure: removes the player from the match if they were added,
         * and triggers controlled return-to-lobby if returnConnectionAddress is non-blank.
         *
         * @param returnConnectionAddress lobby connection address from the launch context (may be blank)
         * @param returnFallbackTargetId  lobby target id (may be blank)
         * @param launchTravelProfileId   travel profile id (may be blank)
         * @param originLobbyId           origin lobby id for the return context (may be blank)
         * @param matchId                 match id used in the return travel context
         * @return list of lifecycle Runnables to dispatch after the synchronized block exits
         */
        @Nonnull
        List<Runnable> failTransferPlacement(
            @Nonnull UUID playerUuid,
            @Nonnull String reason,
            @Nonnull String returnConnectionAddress,
            @Nonnull String returnFallbackTargetId,
            @Nonnull String launchTravelProfileId,
            @Nonnull String originLobbyId,
            @Nonnull String matchId,
            long nowEpochMs
        );
    }

    // Pending backfill retries: player UUID → retry entry.
    // Used when a BACKFILL arrival is rejected because the match does not exist yet on this server.
    // Retried when a match with the matching ID is created (via processPendingBackfillRetries).
    private static final long BACKFILL_RETRY_TTL_MS = 30_000L;

    private MatchGateway matchGateway;

    private final Map<UUID, MinigameTransferSession> sessionsByPlayerUuid = new LinkedHashMap<>();
    private final Map<UUID, PendingBackfillRetry> pendingBackfillRetries = new LinkedHashMap<>();

    // Single-flight coordinator: ensures at most one spawnInstance(...) per match (keyed by matchId).
    private final InstanceMaterializationRegistry materializationRegistry;

    private record PendingBackfillRetry(
        @Nonnull ReadyPlayerSnapshot snapshot,
        @Nonnull PendingArrival arrival,
        @Nonnull String targetMatchId,
        long expiresAtEpochMs
    ) {}

    public MinigameTransferService(
        @Nonnull HytaleLogger logger,
        @Nonnull InstanceSpawnSlotService instanceSpawnSlotService
    ) {
        this(
            logger,
            instanceSpawnSlotService,
            InstancesPlugin::teleportPlayerToLoadingInstance,
            (ref, store) -> store.getComponent(ref, Teleport.getComponentType()) != null
        );
    }

    MinigameTransferService(
        @Nonnull HytaleLogger logger,
        @Nonnull InstanceSpawnSlotService instanceSpawnSlotService,
        @Nonnull InstanceTeleportIssuer instanceTeleportIssuer,
        @Nonnull TeleportComponentChecker teleportComponentChecker
    ) {
        this.logger = logger;
        this.instanceSpawnSlotService = instanceSpawnSlotService;
        this.instanceTeleportIssuer = instanceTeleportIssuer;
        this.teleportComponentChecker = teleportComponentChecker;
        this.materializationRegistry = new InstanceMaterializationRegistry(logger);
    }

    public void setMatchGateway(@Nonnull MatchGateway gateway) {
        this.matchGateway = gateway;
    }

    // -------------------------------------------------------------------------
    // Session lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called when a player is safely ready in the default world and has a minigame.launch
     * arrival context.
     *
     * <p>Returns a {@link MinigameTransferOnReadyResult} indicating whether the transfer service
     * took ownership of the arrival (and thus the caller should acknowledge it from
     * SecureTravelService) or whether the arrival should be left untouched because the launch
     * was rejected (invalid context, missing match, rejected reservation, etc.).</p>
     *
     * <p>Key invariants enforced here:
     * <ul>
     *   <li>The session phase is advanced to ACCEPTED_BY_MINIGAME <em>before</em> calling
     *       {@code acceptTransferArrival}, so {@code hasPendingPlacementSession} returns true
     *       during {@code reconcileAdmissionLifecycle} and prevents premature
     *       MATCH_PLACEMENT_COMPLETED.</li>
     *   <li>Backfill launchIndex uses post-increment slot counting:
     *       {@code initialRosterSize + consumedBackfillAdmissionCount} (no -1) so the first
     *       backfill player lands on the slot after the initial roster, not on the last initial
     *       slot.</li>
     *   <li>All failure paths pass full return info to {@code failTransferPlacement} so the
     *       gateway can dispatch a controlled return-to-lobby.</li>
     * </ul>
     * </p>
     */
    @Nonnull
    public MinigameTransferOnReadyResult onMinigameLaunchSafeReady(
        @Nonnull ReadyPlayerSnapshot snapshot,
        @Nonnull PendingArrival arrival,
        long nowEpochMs
    ) {
        UUID playerUuid = snapshot.playerUuid();
        String username = snapshot.username();
        pendingBackfillRetries.remove(playerUuid);

        // Parse launch context — terminal failure (acknowledge and remove arrival).
        JsonObject context = parseContext(arrival.contextJson());
        if (context == null) {
            logger.atWarning().log(
                "NEXORI_TRANSFER_FAILED player=" + playerUuid
                    + " reason=" + MinigameTransferFailureReason.INVALID_LAUNCH_CONTEXT
                    + " detail=context_json_null_or_unparseable"
            );
            return MinigameTransferOnReadyResult.terminalFailure();
        }

        LaunchContextData launch;
        try {
            launch = launchContextParser.parse(context);
        } catch (IllegalArgumentException ex) {
            logger.atWarning().withCause(ex).log(
                "NEXORI_TRANSFER_FAILED player=" + playerUuid
                    + " reason=" + MinigameTransferFailureReason.INVALID_LAUNCH_CONTEXT
            );
            return MinigameTransferOnReadyResult.terminalFailure();
        }

        boolean isBackfill = ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(launch.assignmentType());
        ArenaActiveMatch existingMatch = matchGateway.findMatchRaw(launch.matchId()).orElse(null);

        if (isBackfill && existingMatch == null) {
            // Timing issue: initial match players may not have arrived yet on this server.
            // Store as a pending retry so that when the match is created, we re-attempt.
            logger.atInfo().log(
                "NEXORI_TRANSFER_RETRY_LATER player=" + playerUuid
                    + " username=" + username
                    + " matchId=" + launch.matchId()
                    + " reason=" + MinigameTransferFailureReason.MISSING_MATCH_FOR_BACKFILL
                    + " expiresIn=" + BACKFILL_RETRY_TTL_MS + "ms"
            );
            pendingBackfillRetries.put(playerUuid, new PendingBackfillRetry(
                snapshot, arrival, launch.matchId(), nowEpochMs + BACKFILL_RETRY_TTL_MS
            ));
            return MinigameTransferOnReadyResult.retryLater();
        }

        if (isBackfill) {
            BackfillAdmissionDecision decision = backfillAdmissionDecider.decide(
                existingMatch,
                launch.assignmentType(),
                launch.playerUuid(),
                playerUuid,
                launch.admissionReservationId(),
                launch.admissionExpiresAtEpochMs(),
                nowEpochMs
            );
            if (decision.rejected()) {
                logger.atWarning().log(
                    "NEXORI_TRANSFER_FAILED player=" + playerUuid
                        + " username=" + username
                        + " matchId=" + launch.matchId()
                        + " assignmentType=BACKFILL"
                        + " reason=" + MinigameTransferFailureReason.BACKFILL_RESERVATION_REJECTED
                        + " detail=" + decision.reason()
                );
                // Player was never added to the match (no session yet); failTransferPlacement
                // will skip match cleanup and go straight to return-to-lobby travel.
                List<Runnable> failDispatches = matchGateway.failTransferPlacement(
                    playerUuid, MinigameTransferFailureReason.BACKFILL_RESERVATION_REJECTED,
                    launch.returnConnectionAddress(),
                    launch.returnFallbackTargetId(),
                    launch.launchTravelProfileId(),
                    launch.originLobbyId(),
                    launch.matchId(),
                    nowEpochMs
                );
                return MinigameTransferOnReadyResult.terminalFailure(failDispatches);
            }
        }

        // Build return info once from the launch context so all failure paths can use it.
        String returnAddr = launch.returnConnectionAddress();
        String returnTarget = launch.returnFallbackTargetId();
        String travelProfile = launch.launchTravelProfileId();
        String originLobbyId = launch.originLobbyId();
        String matchId = launch.matchId();

        if (!isBackfill
            && existingMatch != null
            && isLateInitialArrival(existingMatch, playerUuid, nowEpochMs)) {
            logger.atWarning().log(
                "NEXORI_LATE_INITIAL_ARRIVAL_REJECTED player=" + playerUuid
                    + " username=" + username
                    + " matchId=" + matchId
                    + " assignmentType=" + launch.assignmentType()
                    + " reason=" + MinigameTransferFailureReason.LATE_INITIAL_ARRIVAL
                    + " windowCloseReason=" + existingMatch.initialPlacementWindowCloseReason()
                    + " expiresAt=" + existingMatch.initialPlacementWindowExpiresAtEpochMs()
                    + " startGateOpen=" + existingMatch.startGateOpen()
            );
            List<Runnable> failDispatches = matchGateway.failTransferPlacement(
                playerUuid, MinigameTransferFailureReason.LATE_INITIAL_ARRIVAL,
                returnAddr,
                returnTarget,
                travelProfile,
                originLobbyId,
                matchId,
                nowEpochMs
            );
            return MinigameTransferOnReadyResult.terminalFailure(failDispatches);
        }

        // Create session and advance to ACCEPTED_BY_MINIGAME BEFORE calling acceptTransferArrival.
        // This ensures hasPendingPlacementSession() returns true during reconcileAdmissionLifecycle
        // inside acceptTransferArrival, preventing premature MATCH_PLACEMENT_COMPLETED.
        MinigameTransferSession session = new MinigameTransferSession(playerUuid, username, nowEpochMs);
        session.matchId = matchId;
        session.queueId = launch.queueId();
        session.arenaId = launch.arenaId();
        session.assignmentType = launch.assignmentType();
        session.assignmentId = launch.assignmentId();
        session.admissionReservationId = launch.admissionReservationId();
        session.returnConnectionAddress = returnAddr;
        session.returnFallbackTargetId = returnTarget;
        session.launchTravelProfileId = travelProfile;
        session.originLobbyId = originLobbyId;
        session.arrivalAcknowledged = true;
        advancePhase(session, MinigameTransferPhase.ACCEPTED_BY_MINIGAME, nowEpochMs);
        sessionsByPlayerUuid.put(playerUuid, session);

        logger.atInfo().log(
            "NEXORI_TRANSFER_SESSION_CREATED player=" + playerUuid
                + " username=" + username
                + " matchId=" + matchId
                + " arenaId=" + launch.arenaId()
                + " assignmentType=" + launch.assignmentType()
        );

        // Tell ArenaMatchService to create/update the match with player as arrived-only.
        List<Runnable> lifecycleDispatches = matchGateway.acceptTransferArrival(
            playerUuid, username, launch, existingMatch, nowEpochMs
        );
        session.playerArrivedEventDispatched = true;

        // No instance template: confirm placement immediately in the default/staging world.
        if (!launch.usesInstanceTemplate()) {
            advancePhase(session, MinigameTransferPhase.CONFIRMED, nowEpochMs);
            logger.atInfo().log(
                "NEXORI_TRANSFER_CONFIRMED player=" + playerUuid
                    + " username=" + username
                    + " matchId=" + matchId
                    + " arenaId=" + launch.arenaId()
                    + " phase=CONFIRMED reason=no_instance_template"
            );
            List<Runnable> confirmDispatches = matchGateway.confirmTransferPlacement(playerUuid, nowEpochMs);
            session.placementConfirmedEventDispatched = true;
            return MinigameTransferOnReadyResult.accepted(mergeDispatches(lifecycleDispatches, confirmDispatches));
        }

        // Instance template: prepare placement.
        String instanceTemplateId = launch.instanceTemplateId();
        String instanceWorldName = ArenaInstanceRuntime.buildInstanceWorldName(matchId);
        session.instanceTemplateId = instanceTemplateId;
        session.expectedWorldName = instanceWorldName;

        // Resolve expected spawn slot.
        List<InstanceSpawnSlotDefinition> slots = instanceSpawnSlotService.listByInstanceTemplateId(instanceTemplateId);
        int launchIndex;
        if (isBackfill && existingMatch != null) {
            // consumedBackfillAdmissionCount is the pre-increment value at this point.
            // Using initialRosterSize + count (no -1) puts first backfill on the first slot
            // after the initial roster, not on the last initial slot.
            int initialRosterSize = Math.max(existingMatch.expectedPlayerCount(), existingMatch.expectedPlayerUuids().size());
            launchIndex = initialRosterSize + existingMatch.consumedBackfillAdmissionCount();
        } else {
            launchIndex = context.has("launchIndex") ? Math.max(context.get("launchIndex").getAsInt(), 0) : 0;
        }
        if (!slots.isEmpty()) {
            InstanceSpawnSlotDefinition slot = slots.get(launchIndex % slots.size());
            session.expectedTransform = new Transform(slot.x(), slot.y(), slot.z(), slot.pitch(), slot.yaw(), slot.roll());
        }

        // BACKFILL: the match/reservation is already accepted. If the instance world is not
        // alive yet, keep ownership in the session instead of retrying and revalidating expiry.
        if (isBackfill) {
            Universe universe = Universe.get();
            World existingInstanceWorld = universe != null ? universe.getWorld(instanceWorldName) : null;
            if (existingInstanceWorld == null || !existingInstanceWorld.isAlive()) {
                advancePhase(session, MinigameTransferPhase.WAITING_FOR_INSTANCE_READY, nowEpochMs);
                logger.atInfo().log(
                    "NEXORI_TRANSFER_WAITING_FOR_INSTANCE_WORLD player=" + playerUuid
                        + " username=" + username
                        + " matchId=" + matchId
                        + " world=" + instanceWorldName
                );
                return MinigameTransferOnReadyResult.accepted(lifecycleDispatches);
            }

            List<Runnable> teleportDispatches = deferInstanceTeleport(
                snapshot.entityRef(), snapshot.store(), playerUuid, session, existingInstanceWorld, nowEpochMs
            );
            return MinigameTransferOnReadyResult.accepted(mergeDispatches(lifecycleDispatches, teleportDispatches));
        }

        // INITIAL_MATCH: materialize instance world.
        advancePhase(session, MinigameTransferPhase.INSTANCE_WORLD_CREATING, nowEpochMs);

        World defaultWorld = Universe.get().getWorld(arrival.worldName());
        if (defaultWorld == null) {
            logger.atWarning().log(
                "NEXORI_TRANSFER_FAILED player=" + playerUuid
                    + " username=" + username
                    + " matchId=" + matchId
                    + " reason=" + MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED
                    + " detail=base_world_null world=" + arrival.worldName()
            );
            advancePhase(session, MinigameTransferPhase.FAILED, nowEpochMs);
            session.failureReason = MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED;
            List<Runnable> failDispatches = matchGateway.failTransferPlacement(
                playerUuid, MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED,
                returnAddr, returnTarget, travelProfile, originLobbyId, matchId, nowEpochMs
            );
            return MinigameTransferOnReadyResult.accepted(mergeDispatches(lifecycleDispatches, failDispatches));
        }

        Transform baseArrivalTransform = session.expectedTransform != null
            ? session.expectedTransform.clone()
            : new Transform(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f);

        try {
            World existingWorld = Universe.get().getWorld(instanceWorldName);
            // Fast-path: world already alive (late arrival) -> use it directly; no registry, no spawn.
            // Otherwise single-flight via the registry: only the FIRST player of this match calls
            // spawnInstance(...); every other player joins the same shared world future (no duplicate).
            CompletableFuture<World> materializedFuture = (existingWorld != null && existingWorld.isAlive())
                ? CompletableFuture.completedFuture(existingWorld)
                : materializationRegistry.materializeOrJoin(
                    matchId, instanceWorldName, instanceTemplateId, playerUuid, nowEpochMs,
                    () -> InstancesPlugin.get().spawnInstance(
                        instanceTemplateId, instanceWorldName, defaultWorld, baseArrivalTransform.clone()));
            // Per-player prepare is composed on top of the shared world future: the world is materialized
            // once, but each session still runs its own prepareInstanceForMatch (its own spawn-slot).
            final List<InstanceSpawnSlotDefinition> slotsForPrepare = slots;
            final UUID puuid = playerUuid;
            final int idx = launchIndex;
            CompletableFuture<World> preparedFuture = materializedFuture.thenCompose(instanceWorld ->
                ArenaInstanceRuntime.prepareInstanceForMatch(instanceWorld, slotsForPrepare, puuid, idx)
            );
            session.instanceWorldFuture = preparedFuture;
            logger.atInfo().log(
                "NEXORI_TRANSFER_INSTANCE_PREPARE_STARTED player=" + playerUuid
                    + " username=" + username
                    + " matchId=" + matchId
                    + " templateId=" + instanceTemplateId
                    + " world=" + instanceWorldName
            );
        } catch (Exception ex) {
            logger.atWarning().withCause(ex).log(
                "NEXORI_TRANSFER_FAILED player=" + playerUuid
                    + " username=" + username
                    + " matchId=" + matchId
                    + " reason=" + MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED
            );
            advancePhase(session, MinigameTransferPhase.FAILED, nowEpochMs);
            session.failureReason = MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED;
            List<Runnable> failDispatches = matchGateway.failTransferPlacement(
                playerUuid, MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED,
                returnAddr, returnTarget, travelProfile, originLobbyId, matchId, nowEpochMs
            );
            return MinigameTransferOnReadyResult.accepted(mergeDispatches(lifecycleDispatches, failDispatches));
        }

        return MinigameTransferOnReadyResult.accepted(lifecycleDispatches);
    }

    /**
     * Advances the transfer state machine for a single player on each entity tick.
     *
     * <p>Must be called from within ArenaMatchService's synchronized tick.</p>
     *
     * @return lifecycle Runnables to dispatch after the caller's synchronized block exits
     */
    @Nonnull
    public List<Runnable> tickTransfer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        long nowEpochMs
    ) {
        MinigameTransferSession session = sessionsByPlayerUuid.get(playerRef.getUuid());
        if (session == null || session.isTerminal()) {
            return List.of();
        }

        switch (session.phase) {
            case INSTANCE_WORLD_CREATING -> {
                return tickInstanceWorldCreating(ref, store, playerRef, session, nowEpochMs);
            }
            case INSTANCE_WORLD_READY -> {
                return tickInstanceWorldReady(ref, store, playerRef, session, nowEpochMs);
            }
            case TELEPORT_ISSUED, WAITING_FOR_INSTANCE_READY -> {
                return tickWaitingForInstanceWorld(ref, store, playerRef, session, nowEpochMs);
            }
            case POST_READY_GRACE -> {
                return tickPostReadyGrace(ref, store, playerRef, session, nowEpochMs);
            }
            case VALIDATING_POSITION -> {
                return tickValidatingPosition(ref, store, playerRef, session, nowEpochMs);
            }
            default -> {
                return List.of();
            }
        }
    }

    /**
     * Records that a PlayerReady event was observed for this player in any world.
     * Used to detect when the player is ready inside the instance world.
     */
    public void onPlayerReadyObserved(@Nonnull UUID playerUuid, @Nullable String worldName, long nowEpochMs) {
        MinigameTransferSession session = sessionsByPlayerUuid.get(playerUuid);
        if (session == null || session.isTerminal()) {
            return;
        }
        if (!session.expectedWorldName.isBlank()
            && session.expectedWorldName.equalsIgnoreCase(worldName)
            && (session.phase == MinigameTransferPhase.TELEPORT_ISSUED
                || session.phase == MinigameTransferPhase.WAITING_FOR_INSTANCE_READY)) {
            session.lastReadyObservedAtEpochMs = nowEpochMs;
            session.lastObservedWorldName = worldName != null ? worldName : "";
            advancePhase(session, MinigameTransferPhase.POST_READY_GRACE, nowEpochMs);
            logger.atInfo().log(
                "NEXORI_TRANSFER_INSTANCE_READY_OBSERVED player=" + playerUuid
                    + " username=" + session.username
                    + " matchId=" + session.matchId
                    + " world=" + worldName
            );
        }
    }

    /**
     * Removes the session for a disconnected player.
     */
    @Nonnull
    public List<Runnable> onPlayerDisconnect(@Nonnull UUID playerUuid, long nowEpochMs) {
        pendingBackfillRetries.remove(playerUuid);
        MinigameTransferSession session = sessionsByPlayerUuid.get(playerUuid);
        if (session == null) {
            return List.of();
        }
        boolean wasActive = session.hasAcceptedLaunch() && !session.isTerminal();
        if (wasActive) {
            logger.atInfo().log(
                "NEXORI_TRANSFER_FAILED player=" + playerUuid
                    + " username=" + session.username
                    + " matchId=" + session.matchId
                    + " phase=" + session.phase
                    + " reason=" + MinigameTransferFailureReason.PLAYER_DISCONNECTED_DURING_PLACEMENT
            );
        }
        sessionsByPlayerUuid.remove(playerUuid);
        return List.of();
    }

    /**
     * Returns the current session for a player, or empty if none exists.
     * Safe for read-only HUD queries.
     */
    @Nonnull
    public Optional<MinigameTransferSession> findSession(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(sessionsByPlayerUuid.get(playerUuid));
    }

    /**
     * Returns true if the player has an active (non-terminal) placement session.
     * Used by ArenaMatchService to delay placement-complete evaluation.
     */
    public boolean hasPendingPlacementSession(@Nonnull UUID playerUuid) {
        MinigameTransferSession session = sessionsByPlayerUuid.get(playerUuid);
        if (session == null) {
            return false;
        }
        MinigameTransferPhase p = session.phase;
        return p != MinigameTransferPhase.CONFIRMED
            && p != MinigameTransferPhase.FAILED
            && p != MinigameTransferPhase.FALLBACK
            && p != MinigameTransferPhase.RETURNING_TO_LOBBY
            && p != MinigameTransferPhase.CLOSED
            && p != MinigameTransferPhase.WAITING_FOR_SAFE_READY
            && p != MinigameTransferPhase.SAFE_DEFAULT_READY;
    }

    /**
     * On-lock backstop that evicts completed single-flight materialization entries past their TTL.
     * Invoked from {@code ArenaMatchService}'s server-global window sweep so entries do not leak when a
     * match closes through a path that does not call {@link #closePendingSessionsForMatch}.
     */
    public void evictExpiredMaterializations(long nowEpochMs) {
        materializationRegistry.evictExpired(nowEpochMs);
    }

    /**
     * Explicit single-flight materialization cleanup for a match that is being removed definitively from
     * {@code ArenaMatchService}. This closes the edge case where a FAILED materialization entry (which is
     * intentionally NOT evicted by the TTL sweep) would otherwise be retained indefinitely if the match is
     * removed through a path that does not call {@link #closePendingSessionsForMatch}. No-op (no log) when
     * the match has no entry.
     */
    public void evictMaterializationForMatch(@Nonnull String matchId, @Nonnull String reason) {
        String normalizedMatchId = normalizeOptional(matchId);
        if (normalizedMatchId.isBlank()) {
            return;
        }
        materializationRegistry.evict(normalizedMatchId, reason);
    }

    public void closePendingSessionsForMatch(@Nonnull String matchId, @Nonnull String reason, long nowEpochMs) {
        String normalizedMatchId = normalizeOptional(matchId);
        if (normalizedMatchId.isBlank()) {
            return;
        }
        // The match is closing/cancelling: drop any single-flight materialization entry for it.
        materializationRegistry.evict(normalizedMatchId, reason);
        List<UUID> playersToClose = new ArrayList<>();
        for (MinigameTransferSession session : sessionsByPlayerUuid.values()) {
            if (normalizedMatchId.equals(session.matchId) && hasPendingPlacementSession(session.playerUuid)) {
                session.failureReason = reason;
                advancePhase(session, MinigameTransferPhase.FAILED, nowEpochMs);
                playersToClose.add(session.playerUuid);
            }
        }
        for (UUID playerUuid : playersToClose) {
            sessionsByPlayerUuid.remove(playerUuid);
        }
        if (!playersToClose.isEmpty()) {
            logger.atWarning().log(
                "NEXORI_TRANSFER_PENDING_SESSIONS_CLOSED matchId=" + normalizedMatchId
                    + " reason=" + reason
                    + " count=" + playersToClose.size()
            );
        }
    }

    /**
     * Fails the pending placement sessions of the given expected initial players who missed the
     * initial placement window (start gate opened on a partial roster).
     *
     * <p>Player-scoped on purpose: unlike {@link #closePendingSessionsForMatch}, this only touches
     * the supplied player UUIDs, so valid backfill sessions for the same match are left alone. Only
     * non-terminal sessions whose {@code matchId} matches are failed; players that never arrived
     * (no session) and already-placed/terminal sessions are skipped. Marking the session terminal
     * also stops any pending tick from issuing a deferred teleport.</p>
     */
    public void failPendingInitialPlacementSessions(
        @Nonnull String matchId,
        @Nonnull Set<UUID> playerUuids,
        @Nonnull String reason,
        long nowEpochMs
    ) {
        String normalizedMatchId = normalizeOptional(matchId);
        if (normalizedMatchId.isBlank() || playerUuids.isEmpty()) {
            return;
        }
        List<UUID> closed = new ArrayList<>();
        for (UUID playerUuid : playerUuids) {
            if (playerUuid == null) {
                continue;
            }
            MinigameTransferSession session = sessionsByPlayerUuid.get(playerUuid);
            if (session == null
                || !normalizedMatchId.equals(session.matchId)
                || !hasPendingPlacementSession(playerUuid)) {
                continue;
            }
            session.failureReason = reason;
            session.instanceTeleportDeferred = false;
            advancePhase(session, MinigameTransferPhase.FAILED, nowEpochMs);
            closed.add(playerUuid);
        }
        for (UUID playerUuid : closed) {
            sessionsByPlayerUuid.remove(playerUuid);
        }
        if (!closed.isEmpty()) {
            logger.atWarning().log(
                "NEXORI_INITIAL_PLACEMENT_PENDING_SESSIONS_FAILED matchId=" + normalizedMatchId
                    + " reason=" + reason
                    + " count=" + closed.size()
            );
        }
    }

    private boolean isLateInitialArrival(@Nonnull ArenaActiveMatch match, @Nonnull UUID playerUuid, long nowEpochMs) {
        if (match.activePlayerUuids().contains(playerUuid)) {
            return false;
        }
        return match.initialPlacementWindowClosed()
            || (match.initialPlacementWindowExpiresAtEpochMs() > 0L && nowEpochMs >= match.initialPlacementWindowExpiresAtEpochMs())
            || match.startGateOpen()
            || match.hasCompleted()
            || match.hasSubmittedResult();
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    // -------------------------------------------------------------------------
    // Tick helpers per phase
    // -------------------------------------------------------------------------

    @Nonnull
    private List<Runnable> tickInstanceWorldCreating(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull MinigameTransferSession session,
        long nowEpochMs
    ) {
        if (session.instanceWorldFuture == null) {
            return failSession(session, MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED,
                "future_null", nowEpochMs);
        }

        if (!session.instanceWorldFuture.isDone()) {
            // Check timeout
            if (nowEpochMs - session.phaseStartedAtEpochMs >= TIMEOUT_INSTANCE_WORLD_CREATING_MS) {
                return failSession(session, MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED,
                    "materialization_timeout", nowEpochMs);
            }
            return List.of();
        }

        if (session.instanceWorldFuture.isCompletedExceptionally()) {
            try {
                session.instanceWorldFuture.get();
            } catch (ExecutionException ex) {
                logger.atWarning().withCause(ex.getCause()).log(
                    "NEXORI_TRANSFER_FAILED player=" + playerRef.getUuid()
                        + " username=" + session.username
                        + " matchId=" + session.matchId
                        + " reason=" + MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED
                );
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            return failSession(session, MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED,
                "future_failed", nowEpochMs);
        }

        // Future succeeded - world is ready
        World instanceWorld;
        try {
            instanceWorld = session.instanceWorldFuture.get();
        } catch (Exception ex) {
            return failSession(session, MinigameTransferFailureReason.INSTANCE_WORLD_MATERIALIZATION_FAILED,
                "future_get_failed", nowEpochMs);
        }
        if (instanceWorld == null || !instanceWorld.isAlive()) {
            return failSession(session, MinigameTransferFailureReason.INSTANCE_WORLD_MISSING,
                "instance_world_null_or_dead", nowEpochMs);
        }

        logger.atInfo().log(
            "NEXORI_TRANSFER_INSTANCE_READY player=" + playerRef.getUuid()
                + " username=" + session.username
                + " matchId=" + session.matchId
                + " world=" + instanceWorld.getName()
        );

        advancePhase(session, MinigameTransferPhase.INSTANCE_WORLD_READY, nowEpochMs);

        return deferInstanceTeleport(ref, store, playerRef.getUuid(), session, instanceWorld, nowEpochMs);
    }

    @Nonnull
    private List<Runnable> tickInstanceWorldReady(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull MinigameTransferSession session,
        long nowEpochMs
    ) {
        if (!session.deferredTeleportFailureDetail.isBlank()) {
            String detail = session.deferredTeleportFailureDetail;
            session.deferredTeleportFailureDetail = "";
            return failSession(session, MinigameTransferFailureReason.TELEPORT_ISSUE_FAILED, detail, nowEpochMs);
        }
        if (session.instanceTeleportDeferred) {
            if (nowEpochMs - session.phaseStartedAtEpochMs >= TIMEOUT_TELEPORT_TO_INSTANCE_MS) {
                return failSession(
                    session,
                    MinigameTransferFailureReason.TELEPORT_ISSUE_FAILED,
                    "teleport_deferred_timeout",
                    nowEpochMs
                );
            }
            return List.of();
        }
        Universe universe = Universe.get();
        World instanceWorld = universe != null ? universe.getWorld(session.expectedWorldName) : null;
        if (instanceWorld == null || !instanceWorld.isAlive()) {
            return failSession(session, MinigameTransferFailureReason.INSTANCE_WORLD_MISSING,
                "instance_world_null_or_dead_after_ready", nowEpochMs);
        }
        return deferInstanceTeleport(ref, store, playerRef.getUuid(), session, instanceWorld, nowEpochMs);
    }

    @Nonnull
    private List<Runnable> tickWaitingForInstanceWorld(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull MinigameTransferSession session,
        long nowEpochMs
    ) {
        // This tick method handles both TELEPORT_ISSUED and WAITING_FOR_INSTANCE_READY.
        // The transition to POST_READY_GRACE happens from onPlayerReadyObserved().
        // Here we only handle the timeout cases.

        if (session.phase == MinigameTransferPhase.WAITING_FOR_INSTANCE_READY
            && session.lastTeleportIssuedAtEpochMs <= 0L) {
            Universe universe = Universe.get();
            World instanceWorld = universe != null ? universe.getWorld(session.expectedWorldName) : null;
            if (instanceWorld != null && instanceWorld.isAlive()) {
                return deferInstanceTeleport(ref, store, playerRef.getUuid(), session, instanceWorld, nowEpochMs);
            }
        }

        if (session.phase == MinigameTransferPhase.TELEPORT_ISSUED) {
            // Timeout if instance ready never observed
            if (nowEpochMs - session.phaseStartedAtEpochMs >= TIMEOUT_TELEPORT_TO_INSTANCE_MS) {
                return failSession(session, MinigameTransferFailureReason.INSTANCE_READY_TIMEOUT,
                    "teleport_issued_timeout", nowEpochMs);
            }
        } else if (session.phase == MinigameTransferPhase.WAITING_FOR_INSTANCE_READY) {
            // Wrong world timeout
            if (nowEpochMs - session.phaseStartedAtEpochMs >= TIMEOUT_WRONG_WORLD_MS) {
                return failSession(session, MinigameTransferFailureReason.WRONG_WORLD_TIMEOUT,
                    "wrong_world_timeout", nowEpochMs);
            }
        }
        return List.of();
    }

    @Nonnull
    private List<Runnable> tickPostReadyGrace(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull MinigameTransferSession session,
        long nowEpochMs
    ) {
        long elapsed = nowEpochMs - session.phaseStartedAtEpochMs;
        if (elapsed < TIMEOUT_POST_READY_GRACE_MS) {
            return List.of();
        }

        // Check if Teleport component is still present
        boolean teleportPending = teleportComponentChecker.hasPendingTeleport(ref, store);
        if (teleportPending) {
            // Teleport component still present after grace - wait or timeout
            if (nowEpochMs - session.lastTeleportIssuedAtEpochMs >= TIMEOUT_TELEPORT_COMPONENT_STUCK_MS) {
                return failSession(session, MinigameTransferFailureReason.TELEPORT_COMPONENT_STUCK_TIMEOUT,
                    "teleport_stuck", nowEpochMs);
            }
            return List.of();
        }

        // Grace elapsed and no pending teleport: move to position validation
        advancePhase(session, MinigameTransferPhase.VALIDATING_POSITION, nowEpochMs);
        session.stableTicks = 0;
        return List.of();
    }

    @Nonnull
    private List<Runnable> tickValidatingPosition(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull MinigameTransferSession session,
        long nowEpochMs
    ) {
        if (session.expectedTransform == null) {
            // No expected transform (slot-less arena): confirm immediately.
            return confirmSession(session, nowEpochMs);
        }

        // Evaluate the global phase timeout before any early return so that stalled states
        // (teleport stuck, missing transform, player in wrong position) always escape.
        if (nowEpochMs - session.phaseStartedAtEpochMs >= TIMEOUT_VALIDATING_POSITION_MS) {
            return failSession(session, MinigameTransferFailureReason.POSITION_VALIDATION_TIMEOUT,
                "position_timeout", nowEpochMs);
        }

        boolean teleportPending = teleportComponentChecker.hasPendingTeleport(ref, store);
        if (teleportPending) {
            session.stableTicks = 0;
            return List.of();
        }

        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            session.stableTicks = 0;
            return List.of();
        }

        double distanceSquared = transformComponent.getPosition().distanceSquared(
            session.expectedTransform.getPosition()
        );
        boolean withinTolerance = distanceSquared <= PLACEMENT_POSITION_EPSILON_SQUARED;

        if (withinTolerance) {
            session.stableTicks++;
            logger.atInfo().log(
                "NEXORI_TRANSFER_POSITION_STABLE_TICK player=" + playerRef.getUuid()
                    + " username=" + session.username
                    + " matchId=" + session.matchId
                    + " stableTicks=" + session.stableTicks
                    + " distanceSquared=" + distanceSquared
            );
            if (session.stableTicks >= PLACEMENT_REQUIRED_STABLE_TICKS) {
                return confirmSession(session, nowEpochMs);
            }
        } else {
            session.stableTicks = 0;
        }
        return List.of();
    }

    // -------------------------------------------------------------------------
    // Session terminal transitions
    // -------------------------------------------------------------------------

    @Nonnull
    private List<Runnable> confirmSession(@Nonnull MinigameTransferSession session, long nowEpochMs) {
        UUID playerUuid = session.playerUuid;
        advancePhase(session, MinigameTransferPhase.CONFIRMED, nowEpochMs);
        logger.atInfo().log(
            "NEXORI_TRANSFER_CONFIRMED player=" + playerUuid
                + " username=" + session.username
                + " matchId=" + session.matchId
                + " arenaId=" + session.arenaId
                + " world=" + session.expectedWorldName
        );
        List<Runnable> dispatches = matchGateway.confirmTransferPlacement(playerUuid, nowEpochMs);
        session.placementConfirmedEventDispatched = true;
        return dispatches;
    }

    @Nonnull
    private List<Runnable> failSession(
        @Nonnull MinigameTransferSession session,
        @Nonnull String reason,
        @Nonnull String detail,
        long nowEpochMs
    ) {
        UUID playerUuid = session.playerUuid;
        session.failureReason = reason;
        advancePhase(session, MinigameTransferPhase.FAILED, nowEpochMs);
        logger.atWarning().log(
            "NEXORI_TRANSFER_FAILED player=" + playerUuid
                + " username=" + session.username
                + " matchId=" + session.matchId
                + " arenaId=" + session.arenaId
                + " phase=" + session.phase
                + " reason=" + reason
                + " detail=" + detail
        );
        // The gateway may successfully dispatch return-to-lobby travel and log
        // NEXORI_TRANSFER_RETURNING_TO_LOBBY, but it does not currently return that side-effect
        // status to the transfer service. Keep FAILED as the terminal session phase.
        return matchGateway.failTransferPlacement(
            playerUuid, reason,
            session.returnConnectionAddress,
            session.returnFallbackTargetId,
            session.launchTravelProfileId,
            session.originLobbyId,
            session.matchId,
            nowEpochMs
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void advancePhase(
        @Nonnull MinigameTransferSession session,
        @Nonnull MinigameTransferPhase newPhase,
        long nowEpochMs
    ) {
        session.phase = newPhase;
        session.phaseStartedAtEpochMs = nowEpochMs;
        session.lastUpdatedAtEpochMs = nowEpochMs;
    }

    @Nonnull
    private List<Runnable> deferInstanceTeleport(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull MinigameTransferSession session,
        @Nonnull World targetWorld,
        long nowEpochMs
    ) {
        if (session.instanceTeleportDeferred || session.phase == MinigameTransferPhase.TELEPORT_ISSUED) {
            return List.of();
        }
        advancePhase(session, MinigameTransferPhase.INSTANCE_WORLD_READY, nowEpochMs);
        session.instanceTeleportDeferred = true;
        logger.atInfo().log(
            "NEXORI_TRANSFER_TELEPORT_DEFERRED player=" + playerUuid
                + " username=" + session.username
                + " matchId=" + session.matchId
                + " world=" + targetWorld.getName()
                + " reason=store_tick_context"
        );
        World executionWorld = resolveTeleportExecutionWorld(playerEntityRef, store, targetWorld);
        try {
            executionWorld.execute(() -> issueDeferredInstanceTeleport(
                playerEntityRef,
                store,
                playerUuid,
                session,
                targetWorld
            ));
        } catch (Exception ex) {
            session.instanceTeleportDeferred = false;
            return failSession(
                session,
                MinigameTransferFailureReason.TELEPORT_ISSUE_FAILED,
                "teleport_defer_exception:" + ex.getClass().getSimpleName(),
                nowEpochMs
            );
        }
        return List.of();
    }

    private World resolveTeleportExecutionWorld(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull World fallbackWorld
    ) {
        try {
            Player player = store.getComponent(playerEntityRef, Player.getComponentType());
            World currentWorld = player == null ? null : player.getWorld();
            return currentWorld == null ? fallbackWorld : currentWorld;
        } catch (Exception ignored) {
            return fallbackWorld;
        }
    }

    private void issueDeferredInstanceTeleport(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID playerUuid,
        @Nonnull MinigameTransferSession session,
        @Nonnull World targetWorld
    ) {
        // The session may have been failed (e.g. the initial placement window expired and this
        // expected player was marked NO_CONTEST) between scheduling and executing this deferred
        // teleport. Do not place a player whose session is already terminal.
        if (session.isTerminal()) {
            logger.atInfo().log(
                "NEXORI_TRANSFER_DEFERRED_TELEPORT_SKIPPED player=" + playerUuid
                    + " matchId=" + session.matchId
                    + " phase=" + session.phase
                    + " reason=" + session.failureReason
            );
            return;
        }
        Transform transform = session.expectedTransform != null
            ? session.expectedTransform.clone()
            : new Transform(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f);
        long issuedAtEpochMs = System.currentTimeMillis();
        session.lastTeleportIssuedAtEpochMs = issuedAtEpochMs;
        session.instanceTeleportDeferred = false;
        advancePhase(session, MinigameTransferPhase.TELEPORT_ISSUED, issuedAtEpochMs);
        try {
            instanceTeleportIssuer.teleport(
                playerEntityRef,
                store,
                CompletableFuture.completedFuture(targetWorld),
                transform
            );
        } catch (Exception ex) {
            session.lastTeleportIssuedAtEpochMs = 0L;
            session.deferredTeleportFailureDetail = "teleport_issuer_exception:" + ex.getClass().getSimpleName();
            advancePhase(session, MinigameTransferPhase.INSTANCE_WORLD_READY, issuedAtEpochMs);
            return;
        }
        logger.atInfo().log(
            "NEXORI_TRANSFER_TELEPORT_ISSUED player=" + playerUuid
                + " username=" + session.username
                + " matchId=" + session.matchId
                + " world=" + targetWorld.getName()
                + " target=" + (session.expectedTransform != null ? session.expectedTransform.getPosition() : "null")
        );
    }

    // Rate-limiting for tickPendingBackfillRetries
    private static final long BACKFILL_RETRY_TICK_INTERVAL_MS = 1_000L;
    private long lastBackfillRetryTickMs = 0L;

    /**
     * Retries pending backfill arrivals whose target match was just created.
     * For instance-template matches, skips until the instance world is alive —
     * {@link #tickPendingBackfillRetries} handles that check on subsequent ticks.
     *
     * @return lifecycle Runnables to dispatch after the caller's synchronized block exits
     */
    @Nonnull
    public List<Runnable> processPendingBackfillRetries(@Nonnull String matchId, long nowEpochMs) {
        if (pendingBackfillRetries.isEmpty()) {
            return List.of();
        }

        // If the match uses an instance template and the world isn't alive yet, skip.
        // tickPendingBackfillRetries will pick this up once the world is ready.
        ArenaActiveMatch match = matchGateway.findMatchRaw(matchId).orElse(null);
        if (match != null && !match.instanceWorldName().isBlank()) {
            com.hypixel.hytale.server.core.universe.Universe universe = com.hypixel.hytale.server.core.universe.Universe.get();
            World world = universe != null ? universe.getWorld(match.instanceWorldName()) : null;
            if (world == null || !world.isAlive()) {
                return List.of();
            }
        }

        return executeBackfillRetries(matchId, nowEpochMs);
    }

    /**
     * Periodically called (at most once per {@value #BACKFILL_RETRY_TICK_INTERVAL_MS} ms) to:
     * <ol>
     *   <li>Expire TTL-past entries and attempt return-to-lobby for those players.</li>
     *   <li>Retry entries whose target match now exists and (if applicable) instance world is alive.</li>
     * </ol>
     * Safe to call from every per-player tick — the rate-limit gate makes it a no-op on most calls.
     *
     * @return lifecycle Runnables to dispatch after the caller's synchronized block exits
     */
    @Nonnull
    public List<Runnable> tickPendingBackfillRetries(long nowEpochMs) {
        if (pendingBackfillRetries.isEmpty()) {
            return List.of();
        }
        if (nowEpochMs - lastBackfillRetryTickMs < BACKFILL_RETRY_TICK_INTERVAL_MS) {
            return List.of();
        }
        lastBackfillRetryTickMs = nowEpochMs;

        List<UUID> toExpire = new ArrayList<>();
        Map<String, List<UUID>> toRetryByMatchId = new LinkedHashMap<>();

        for (Map.Entry<UUID, PendingBackfillRetry> entry : pendingBackfillRetries.entrySet()) {
            UUID playerUuid = entry.getKey();
            PendingBackfillRetry retry = entry.getValue();
            if (nowEpochMs >= retry.expiresAtEpochMs()) {
                toExpire.add(playerUuid);
                continue;
            }
            ArenaActiveMatch match = matchGateway.findMatchRaw(retry.targetMatchId()).orElse(null);
            if (match == null) {
                continue; // still waiting for match creation
            }
            if (!match.instanceWorldName().isBlank()) {
                com.hypixel.hytale.server.core.universe.Universe universe = com.hypixel.hytale.server.core.universe.Universe.get();
                World world = universe != null ? universe.getWorld(match.instanceWorldName()) : null;
                if (world == null || !world.isAlive()) {
                    continue; // still waiting for world
                }
            }
            toRetryByMatchId.computeIfAbsent(retry.targetMatchId(), ignored -> new ArrayList<>()).add(playerUuid);
        }

        List<Runnable> dispatches = new ArrayList<>();

        for (UUID expiredUuid : toExpire) {
            PendingBackfillRetry retry = pendingBackfillRetries.remove(expiredUuid);
            if (retry == null) {
                continue;
            }
            logger.atWarning().log(
                "NEXORI_TRANSFER_BACKFILL_RETRY_EXPIRED player=" + expiredUuid
                    + " matchId=" + retry.targetMatchId()
            );
            try {
                JsonObject ctx = parseContext(retry.arrival().contextJson());
                if (ctx != null) {
                    LaunchContextData launch = launchContextParser.parse(ctx);
                    List<Runnable> failDispatches = matchGateway.failTransferPlacement(
                        expiredUuid,
                        MinigameTransferFailureReason.MISSING_MATCH_FOR_BACKFILL,
                        launch.returnConnectionAddress(),
                        launch.returnFallbackTargetId(),
                        launch.launchTravelProfileId(),
                        launch.originLobbyId(),
                        launch.matchId(),
                        nowEpochMs
                    );
                    dispatches.addAll(failDispatches);
                }
            } catch (Exception ex) {
                logger.atWarning().withCause(ex).log(
                    "NEXORI_TRANSFER_BACKFILL_EXPIRE_RETURN_FAILED player=" + expiredUuid
                );
            }
        }

        for (Map.Entry<String, List<UUID>> entry : toRetryByMatchId.entrySet()) {
            String matchId = entry.getKey();
            List<Runnable> retryDispatches = executeBackfillRetries(matchId, nowEpochMs);
            dispatches.addAll(retryDispatches);
        }

        return dispatches;
    }

    @Nonnull
    private List<Runnable> executeBackfillRetries(@Nonnull String matchId, long nowEpochMs) {
        List<UUID> toRetry = new ArrayList<>();
        List<UUID> toExpire = new ArrayList<>();
        for (Map.Entry<UUID, PendingBackfillRetry> entry : pendingBackfillRetries.entrySet()) {
            if (!matchId.equalsIgnoreCase(entry.getValue().targetMatchId())) {
                continue;
            }
            if (nowEpochMs >= entry.getValue().expiresAtEpochMs()) {
                toExpire.add(entry.getKey());
            } else {
                toRetry.add(entry.getKey());
            }
        }
        List<Runnable> dispatches = new ArrayList<>();
        for (UUID expiredUuid : toExpire) {
            PendingBackfillRetry retry = pendingBackfillRetries.remove(expiredUuid);
            if (retry == null) {
                continue;
            }
            logger.atWarning().log(
                "NEXORI_TRANSFER_BACKFILL_RETRY_EXPIRED player=" + expiredUuid
                    + " matchId=" + matchId
            );
            try {
                JsonObject ctx = parseContext(retry.arrival().contextJson());
                if (ctx != null) {
                    LaunchContextData launch = launchContextParser.parse(ctx);
                    List<Runnable> failDispatches = matchGateway.failTransferPlacement(
                        expiredUuid,
                        MinigameTransferFailureReason.MISSING_MATCH_FOR_BACKFILL,
                        launch.returnConnectionAddress(),
                        launch.returnFallbackTargetId(),
                        launch.launchTravelProfileId(),
                        launch.originLobbyId(),
                        launch.matchId(),
                        nowEpochMs
                    );
                    dispatches.addAll(failDispatches);
                }
            } catch (Exception ex) {
                logger.atWarning().withCause(ex).log(
                    "NEXORI_TRANSFER_BACKFILL_EXPIRE_RETURN_FAILED player=" + expiredUuid
                );
            }
        }
        for (UUID playerUuid : toRetry) {
            PendingBackfillRetry retry = pendingBackfillRetries.remove(playerUuid);
            if (retry == null) {
                continue;
            }
            logger.atInfo().log(
                "NEXORI_TRANSFER_BACKFILL_RETRY player=" + playerUuid
                    + " matchId=" + matchId
            );
            MinigameTransferOnReadyResult retryResult = onMinigameLaunchSafeReady(
                retry.snapshot(), retry.arrival(), nowEpochMs
            );
            dispatches.addAll(retryResult.lifecycleDispatches());
        }
        return dispatches;
    }

    @Nullable
    private JsonObject parseContext(String rawContextJson) {
        if (rawContextJson == null || rawContextJson.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(rawContextJson, JsonObject.class);
        } catch (Exception ex) {
            return null;
        }
    }

    @Nonnull
    private static List<Runnable> mergeDispatches(
        @Nonnull List<Runnable> a,
        @Nonnull List<Runnable> b
    ) {
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        java.util.ArrayList<Runnable> merged = new java.util.ArrayList<>(a.size() + b.size());
        merged.addAll(a);
        merged.addAll(b);
        return java.util.Collections.unmodifiableList(merged);
    }
}
