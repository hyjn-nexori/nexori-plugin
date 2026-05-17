package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import io.github.hyjn.nexori.plugin.minigame.logic.MinigameLaunchContextBuildResult;
import io.github.hyjn.nexori.plugin.minigame.logic.MinigameLaunchContextFactory;
import io.github.hyjn.nexori.plugin.minigame.logic.QueueCountdownPlanner;
import io.github.hyjn.nexori.plugin.minigame.logic.QueueLaunchOutcomePlanner;
import io.github.hyjn.nexori.plugin.minigame.logic.QueueMembershipPlan;
import io.github.hyjn.nexori.plugin.minigame.logic.QueueMembershipPlanner;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Coordinates live queue membership, countdowns, batch readiness, and queue-driven match launch.
 */
public class QueueCoordinatorService {

    private static final int ADMISSION_POLICY_SCHEMA_VERSION = 1;
    private static final long LAUNCH_RETRY_INTERVAL_MS = 3000L;
    private static final long WORLD_TICK_ADVANCE_INTERVAL_MS = 1000L;
    private static final String ASSIGNMENT_TYPE_INITIAL_MATCH = "INITIAL_MATCH";
    private static final String ASSIGNMENT_TYPE_BACKFILL = "BACKFILL";

    private final QueueService queueService;
    private final ArenaService arenaService;
    private final MatchSessionService matchSessionService;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final SecureTravelService secureTravelService;
    private final HytaleLogger logger;
    private final MinigameLaunchContextFactory launchContextFactory = new MinigameLaunchContextFactory();
    private final QueueCountdownPlanner countdownPlanner = new QueueCountdownPlanner();
    private final QueueMembershipPlanner membershipPlanner = new QueueMembershipPlanner();
    private final QueueLaunchOutcomePlanner launchOutcomePlanner = new QueueLaunchOutcomePlanner();
    private final Map<String, QueueRuntimeState> stateByQueueId = new LinkedHashMap<>();
    private final Map<UUID, String> queueIdByPlayerUuid = new LinkedHashMap<>();
    private long lastWorldTickAdvanceAtEpochMs;

    /**
     * Creates the runtime queue coordinator for the current server.
     */
    public QueueCoordinatorService(
        @Nonnull QueueService queueService,
        @Nonnull ArenaService arenaService,
        @Nonnull MatchSessionService matchSessionService,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull HytaleLogger logger
    ) {
        this.queueService = queueService;
        this.arenaService = arenaService;
        this.matchSessionService = matchSessionService;
        this.localConnectionAddressService = localConnectionAddressService;
        this.secureTravelService = secureTravelService;
        this.logger = logger;
    }

    @Nonnull
    /**
     * Attempts to enqueue one player into one queue from one lobby portal.
     */
    public synchronized JoinResult joinQueue(
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String rawQueueId,
        @Nonnull String sourceLobbyId,
        @Nonnull String sourcePortalId
    ) {
        String normalizedQueueId = QueueDefinition.normalizeId(rawQueueId);
        Optional<QueueDefinition> queue = queueService.find(normalizedQueueId);
        if (queue.isEmpty()) {
            return JoinResult.queueMissing(normalizedQueueId);
        }
        if (!queue.get().enabled()) {
            return JoinResult.queueDisabled(normalizedQueueId);
        }

        String existingQueueId = queueIdByPlayerUuid.get(playerUuid);
        if (existingQueueId != null) {
            return JoinResult.alreadyQueued(existingQueueId);
        }

        long now = System.currentTimeMillis();
        QueueRuntimeState currentState = state(normalizedQueueId, now);
        QueueMembershipPlan plan = membershipPlanner.join(
            currentState,
            queue.get(),
            playerUuid,
            playerName,
            sourceLobbyId,
            sourcePortalId,
            now
        );
        queueIdByPlayerUuid.put(playerUuid, normalizedQueueId);
        QueueRuntimeState updated = plan.state();
        stateByQueueId.put(normalizedQueueId, updated);
        return JoinResult.joined(updated);
    }

    @Nonnull
    /**
     * Removes one player from whichever queue they currently belong to.
     */
    public synchronized LeaveResult leaveCurrentQueue(@Nonnull UUID playerUuid) {
        RemovedPlayerResult removed = removePlayerFromQueue(playerUuid, System.currentTimeMillis());
        if (!removed.removed()) {
            return LeaveResult.notQueued();
        }
        return LeaveResult.left(removed.queueId(), removed.state());
    }

    /**
     * Removes disconnected players from queue runtime state.
     */
    public synchronized void handlePlayerDisconnect(@Nonnull PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }
        removePlayerFromQueue(playerRef.getUuid(), System.currentTimeMillis());
    }

    /**
     * Returns whether one player is currently tracked inside any queue.
     */
    public synchronized boolean isQueued(@Nonnull UUID playerUuid) {
        return queueIdByPlayerUuid.containsKey(playerUuid);
    }

    @Nonnull
    /**
     * Returns the queue id for one queued player, if present.
     */
    public synchronized Optional<String> findQueuedQueueId(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(queueIdByPlayerUuid.get(playerUuid));
    }

    @Nonnull
    /**
     * Returns the current runtime state for one queue id when that queue exists in config.
     */
    public synchronized Optional<QueueRuntimeState> getQueueState(@Nonnull String rawQueueId) {
        try {
            String normalizedQueueId = QueueDefinition.normalizeId(rawQueueId);
            if (queueService.find(normalizedQueueId).isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(state(normalizedQueueId, System.currentTimeMillis()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Nonnull
    /**
     * Builds the HUD snapshot shown to one queued player.
     */
    public synchronized Optional<QueueHudState> findQueueHudState(@Nonnull UUID playerUuid, long nowEpochMs) {
        String queueId = queueIdByPlayerUuid.get(playerUuid);
        if (queueId == null || queueId.isBlank()) {
            return Optional.empty();
        }

        QueueDefinition queue = queueService.find(queueId).orElse(null);
        if (queue == null) {
            return Optional.empty();
        }

        QueueRuntimeState state = state(queue.queueId(), nowEpochMs);
        return Optional.of(new QueueHudState(
            queue.queueId(),
            queue.displayName(),
            queue.minPlayers(),
            queue.maxPlayers(),
            state.queuedPlayerCount(),
            state.phase(),
            state.countdownEndsAtEpochMs()
        ));
    }

    @Nonnull
    /**
     * Returns every known queue runtime ordered by queue id.
     */
    public synchronized List<QueueRuntimeState> listQueueStates() {
        long now = System.currentTimeMillis();
        for (QueueDefinition queue : queueService.list()) {
            state(queue.queueId(), now);
        }
        return stateByQueueId.values().stream()
            .sorted(Comparator.comparing(QueueRuntimeState::queueId))
            .toList();
    }

    /**
     * Advances countdown, launch, and handoff cleanup work on a throttled world-tick cadence.
     */
    public synchronized void advanceWorldTick(long nowEpochMs) {
        if (nowEpochMs - lastWorldTickAdvanceAtEpochMs < WORLD_TICK_ADVANCE_INTERVAL_MS) {
            return;
        }
        lastWorldTickAdvanceAtEpochMs = nowEpochMs;
        advanceCountdowns(nowEpochMs);
        launchReadyBatches(nowEpochMs);
        try {
            matchSessionService.pruneExpired(nowEpochMs);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to prune Nexori handoff records on world tick.");
        }
    }

    /**
     * Moves queues from countdown into ready batches once their timer expires.
     */
    public synchronized void advanceCountdowns(long nowEpochMs) {
        for (QueueDefinition queue : queueService.list()) {
            if (queue.effectiveMatchmakingMode() == QueueMatchmakingMode.BACKEND_DRIVEN) {
                continue;
            }
            QueueRuntimeState currentState = state(queue.queueId(), nowEpochMs);
            if (currentState.phase() != QueuePhase.COUNTDOWN) {
                continue;
            }
            QueueRuntimeState updated = countdownPlanner.advanceCountdown(currentState, queue, nowEpochMs);
            stateByQueueId.put(queue.queueId(), updated);
        }
    }

    /**
     * Launches ready queue batches into arena instances when their destination is available.
     */
    public synchronized void launchReadyBatches(long nowEpochMs) {
        for (QueueDefinition queue : queueService.list()) {
            if (!queue.enabled()) {
                continue;
            }
            if (queue.effectiveMatchmakingMode() == QueueMatchmakingMode.BACKEND_DRIVEN) {
                continue;
            }

            QueueRuntimeState currentState = state(queue.queueId(), nowEpochMs);
            if (!currentState.hasReadyBatch()) {
                continue;
            }
            if (currentState.lastLaunchAttemptAtEpochMs() > 0L
                && nowEpochMs - currentState.lastLaunchAttemptAtEpochMs() < LAUNCH_RETRY_INTERVAL_MS) {
                continue;
            }

            Optional<ArenaDefinition> arena = selectLaunchArena(queue);
            if (arena.isEmpty()) {
                stateByQueueId.put(queue.queueId(), launchOutcomePlanner.rememberLaunchFailure(
                    currentState,
                    nowEpochMs,
                    "No enabled arena is currently available for this queue."
                ).state());
                continue;
            }

            List<LaunchCandidate> launchCandidates = new ArrayList<>();
            List<QueueMemberState> liveReadyMembers = new ArrayList<>();
            for (QueueMemberState member : currentState.readyMembers()) {
                PlayerRef playerRef = Universe.get().getPlayer(member.playerUuid());
                if (playerRef == null || !playerRef.isValid()) {
                    queueIdByPlayerUuid.remove(member.playerUuid());
                    continue;
                }
                launchCandidates.add(new LaunchCandidate(member, playerRef));
                liveReadyMembers.add(member);
            }

            if (liveReadyMembers.size() < queue.minPlayers()) {
                stateByQueueId.put(queue.queueId(), launchOutcomePlanner.collapseInsufficientLiveReadyMembers(
                    currentState,
                    queue,
                    liveReadyMembers,
                    nowEpochMs
                ).state());
                continue;
            }

            QueueRuntimeState readyState = launchOutcomePlanner.prepareReadyBatchLaunchAttempt(
                currentState,
                liveReadyMembers,
                nowEpochMs
            ).state();
            stateByQueueId.put(queue.queueId(), readyState);

            DispatchLaunchResult dispatch = dispatchLaunch(
                queue,
                arena.get(),
                launchCandidates,
                nowEpochMs,
                "",
                ASSIGNMENT_TYPE_INITIAL_MATCH,
                "",
                "",
                List.of(),
                List.of(),
                ""
            );
            if (dispatch.failedBeforeLaunch()) {
                stateByQueueId.put(queue.queueId(), launchOutcomePlanner.rememberLaunchFailure(
                    readyState,
                    nowEpochMs,
                    dispatch.errorMessage()
                ).state());
                continue;
            }
            for (LaunchCandidate candidate : dispatch.launched()) {
                queueIdByPlayerUuid.remove(candidate.member().playerUuid());
            }
            QueueRuntimeState updated = launchOutcomePlanner.completeReadyBatchLaunch(
                readyState,
                queue,
                liveReadyMembers,
                dispatch.launched().size(),
                dispatch.succeeded(),
                nowEpochMs
            ).state();
            stateByQueueId.put(queue.queueId(), updated);
        }
    }

    @Nonnull
    public synchronized AssignmentLaunchResult launchBackendAssignment(
        @Nonnull String assignmentId,
        @Nonnull String matchId,
        @Nonnull String externalMatchId,
        @Nonnull String rawQueueId,
        @Nonnull String rawArenaId,
        @Nonnull List<UUID> playerUuids,
        @Nonnull List<UUID> expectedPlayerUuids
    ) {
        List<BackendAssignmentPlayerTicket> tickets = new ArrayList<>();
        if (playerUuids != null) {
            for (UUID playerUuid : playerUuids) {
                tickets.add(new BackendAssignmentPlayerTicket(playerUuid, "", 0L));
            }
        }
        return launchBackendAssignment(
            assignmentId,
            ASSIGNMENT_TYPE_INITIAL_MATCH,
            matchId,
            externalMatchId,
            rawQueueId,
            rawArenaId,
            tickets,
            expectedPlayerUuids,
            "",
            ""
        );
    }

    @Nonnull
    public synchronized AssignmentLaunchResult launchBackendAssignment(
        @Nonnull String assignmentId,
        @Nonnull String assignmentType,
        @Nonnull String matchId,
        @Nonnull String externalMatchId,
        @Nonnull String rawQueueId,
        @Nonnull String rawArenaId,
        @Nonnull List<BackendAssignmentPlayerTicket> players,
        @Nonnull List<UUID> expectedPlayerUuids,
        @Nonnull String reportingServerId,
        @Nonnull String targetConnectionAddress
    ) {
        long nowEpochMs = System.currentTimeMillis();
        if (assignmentId == null || assignmentId.isBlank()) {
            return AssignmentLaunchResult.rejected("", "Assignment id cannot be blank.");
        }
        String normalizedAssignmentType = assignmentType == null || assignmentType.isBlank()
            ? ASSIGNMENT_TYPE_INITIAL_MATCH
            : assignmentType.trim().toUpperCase();
        if (!ASSIGNMENT_TYPE_INITIAL_MATCH.equals(normalizedAssignmentType) && !ASSIGNMENT_TYPE_BACKFILL.equals(normalizedAssignmentType)) {
            return AssignmentLaunchResult.rejected("", "Assignment type must be INITIAL_MATCH or BACKFILL.");
        }
        if (ASSIGNMENT_TYPE_BACKFILL.equals(normalizedAssignmentType)) {
            return launchBackendBackfillAssignment(
                assignmentId,
                matchId,
                externalMatchId,
                rawQueueId,
                rawArenaId,
                players,
                reportingServerId,
                targetConnectionAddress
            );
        }
        if (players == null || players.isEmpty()) {
            return AssignmentLaunchResult.rejected("", "Assignment must include at least one player.");
        }
        List<UUID> playerUuids = new ArrayList<>();
        Set<UUID> uniquePlayerUuids = new LinkedHashSet<>();
        Set<String> uniqueReservationIds = new LinkedHashSet<>();
        List<BackendAssignmentPlayerTicket> normalizedTickets = new ArrayList<>();
        for (BackendAssignmentPlayerTicket player : players) {
            if (player == null || player.playerUuid() == null) {
                return AssignmentLaunchResult.rejected("", "Assignment includes a null player UUID.");
            }
            if (!uniquePlayerUuids.add(player.playerUuid())) {
                return AssignmentLaunchResult.rejected("", "Assignment includes duplicate players.");
            }
            if (ASSIGNMENT_TYPE_BACKFILL.equals(normalizedAssignmentType)) {
                if (player.admissionReservationId() == null || player.admissionReservationId().isBlank()) {
                    return AssignmentLaunchResult.rejected("", "BACKFILL assignment requires one admissionReservationId per player.");
                }
                if (!uniqueReservationIds.add(player.admissionReservationId().trim())) {
                    return AssignmentLaunchResult.rejected("", "BACKFILL assignment includes duplicate admissionReservationIds.");
                }
                if (player.admissionExpiresAtEpochMs() <= 0L) {
                    return AssignmentLaunchResult.rejected("", "BACKFILL assignment requires a positive admission expiry per player.");
                }
            }
            normalizedTickets.add(player.normalized());
            playerUuids.add(player.playerUuid());
        }
        String normalizedMatchId;
        try {
            normalizedMatchId = NexoriMatchIds.normalizeBackendOwnedMatchId(matchId);
        } catch (IllegalArgumentException exception) {
            return AssignmentLaunchResult.rejected("", exception.getMessage());
        }
        List<UUID> canonicalExpectedPlayerUuids = PlayerUuidLists.canonicalize(expectedPlayerUuids);
        if (ASSIGNMENT_TYPE_INITIAL_MATCH.equals(normalizedAssignmentType)
            && !canonicalExpectedPlayerUuids.isEmpty()
            && !PlayerUuidLists.isSubset(playerUuids, canonicalExpectedPlayerUuids)) {
            return AssignmentLaunchResult.rejected("", "Assignment playerUuids must be a subset of expectedPlayerUuids.");
        }

        QueueDefinition queue = queueService.find(rawQueueId).orElse(null);
        if (queue == null) {
            return AssignmentLaunchResult.rejected("", "Queue does not exist.");
        }
        if (!queue.enabled()) {
            return AssignmentLaunchResult.rejected("", "Queue is disabled.");
        }
        if (queue.effectiveMatchmakingMode() != QueueMatchmakingMode.BACKEND_DRIVEN) {
            return AssignmentLaunchResult.rejected("", "Queue is not BACKEND_DRIVEN.");
        }

        ArenaDefinition arena = arenaService.find(rawArenaId).orElse(null);
        if (arena == null) {
            return AssignmentLaunchResult.rejected("", "Arena does not exist.");
        }
        if (!arena.enabled()) {
            return AssignmentLaunchResult.rejected("", "Arena is disabled.");
        }
        if (!queue.arenaIds().contains(arena.arenaId())) {
            return AssignmentLaunchResult.rejected("", "Arena does not belong to the queue.");
        }
        if (playerUuids.size() > arena.maxSupportedPlayers()) {
            return AssignmentLaunchResult.rejected("", "Assignment exceeds arena max supported players.");
        }
        try {
            ConfiguredPeer.parse(arena.destinationConnectionAddress());
        } catch (IllegalArgumentException exception) {
            return AssignmentLaunchResult.rejected("", exception.getMessage());
        }
        if (targetConnectionAddress != null
            && !targetConnectionAddress.isBlank()
            && !arena.destinationConnectionAddress().equalsIgnoreCase(targetConnectionAddress.trim())) {
            return AssignmentLaunchResult.rejected("", "Assignment targetConnectionAddress does not match the arena destination.");
        }

        QueueRuntimeState currentState = state(queue.queueId(), nowEpochMs);
        List<QueueMemberState> assignmentMembers = new ArrayList<>();
        for (UUID playerUuid : playerUuids) {
            String queuedQueueId = queueIdByPlayerUuid.get(playerUuid);
            if (!queue.queueId().equals(queuedQueueId)) {
                return AssignmentLaunchResult.rejected("", "Player " + playerUuid + " is not in queue " + queue.queueId() + ".");
            }
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                return AssignmentLaunchResult.rejected("", "Player " + playerUuid + " is not online.");
            }
            QueueMemberState member = findMember(currentState, playerUuid).orElse(null);
            if (member == null) {
                return AssignmentLaunchResult.rejected("", "Player " + playerUuid + " is missing from queue runtime state.");
            }
            assignmentMembers.add(member);
        }

        QueueRuntimeState launchingState = launchOutcomePlanner.prepareAssignmentLaunchAttempt(
            currentState,
            assignmentMembers,
            nowEpochMs
        ).state();
        stateByQueueId.put(queue.queueId(), launchingState);

        List<LaunchCandidate> launchCandidates = new ArrayList<>();
        for (QueueMemberState member : assignmentMembers) {
            PlayerRef playerRef = Universe.get().getPlayer(member.playerUuid());
            if (playerRef == null || !playerRef.isValid()) {
                stateByQueueId.put(queue.queueId(), launchOutcomePlanner.rememberLaunchFailure(
                    launchingState,
                    nowEpochMs,
                    "Assigned player went offline before launch."
                ).state());
                return AssignmentLaunchResult.rejected("", "Assigned player went offline before launch.");
            }
            launchCandidates.add(new LaunchCandidate(member, playerRef));
        }

        DispatchLaunchResult dispatch = dispatchLaunch(
            queue,
            arena,
            launchCandidates,
            nowEpochMs,
            assignmentId,
            normalizedAssignmentType,
            normalizedMatchId,
            externalMatchId,
            canonicalExpectedPlayerUuids,
            normalizedTickets,
            reportingServerId
        );
        for (LaunchCandidate candidate : dispatch.launched()) {
            queueIdByPlayerUuid.remove(candidate.member().playerUuid());
        }

        QueueRuntimeState updated = launchOutcomePlanner.completeAssignmentLaunch(
            launchingState,
            assignmentMembers,
            dispatch.launched().size(),
            dispatch.succeeded(),
            dispatch.errorMessage(),
            nowEpochMs
        ).state();
        stateByQueueId.put(queue.queueId(), updated);

        if (dispatch.succeeded()) {
            return AssignmentLaunchResult.launched(dispatch.matchId());
        }
        if (dispatch.launched().isEmpty()) {
            return AssignmentLaunchResult.failed(dispatch.matchId(), dispatch.errorMessage());
        }
        return AssignmentLaunchResult.failed(dispatch.matchId(), "Partial launch: " + dispatch.errorMessage());
    }

    @Nonnull
    public synchronized AssignmentLaunchResult launchBackendBackfillAssignment(
        @Nonnull String assignmentId,
        @Nonnull String matchId,
        @Nonnull String externalMatchId,
        @Nonnull String rawQueueId,
        @Nonnull String rawArenaId,
        @Nonnull List<BackendAssignmentPlayerTicket> players,
        @Nonnull String reportingServerId,
        @Nonnull String targetConnectionAddress
    ) {
        long nowEpochMs = System.currentTimeMillis();
        if (assignmentId == null || assignmentId.isBlank()) {
            return AssignmentLaunchResult.rejected("", "Assignment id cannot be blank.");
        }
        if (players == null || players.isEmpty()) {
            return AssignmentLaunchResult.rejected("", "BACKFILL assignment must include at least one player.");
        }
        String normalizedMatchId;
        try {
            normalizedMatchId = NexoriMatchIds.normalizeBackendOwnedMatchId(matchId);
        } catch (IllegalArgumentException exception) {
            return AssignmentLaunchResult.rejected("", exception.getMessage());
        }
        String normalizedArenaId = rawArenaId == null ? "" : rawArenaId.trim().toLowerCase();
        if (normalizedArenaId.isBlank()) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, "Arena id cannot be blank.");
        }
        String normalizedTargetConnectionAddress = targetConnectionAddress == null ? "" : targetConnectionAddress.trim();
        if (normalizedTargetConnectionAddress.isBlank()) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, "BACKFILL assignment requires targetConnectionAddress.");
        }
        ConfiguredPeer destination;
        try {
            destination = ConfiguredPeer.parse(normalizedTargetConnectionAddress);
        } catch (IllegalArgumentException exception) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, exception.getMessage());
        }

        List<UUID> playerUuids = new ArrayList<>();
        Set<UUID> uniquePlayerUuids = new LinkedHashSet<>();
        Set<String> uniqueReservationIds = new LinkedHashSet<>();
        List<BackendAssignmentPlayerTicket> normalizedTickets = new ArrayList<>();
        for (BackendAssignmentPlayerTicket player : players) {
            if (player == null || player.playerUuid() == null) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "BACKFILL assignment includes a null player UUID.");
            }
            if (!uniquePlayerUuids.add(player.playerUuid())) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "BACKFILL assignment includes duplicate players.");
            }
            BackendAssignmentPlayerTicket normalizedTicket;
            try {
                normalizedTicket = player.normalized();
            } catch (IllegalArgumentException exception) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, exception.getMessage());
            }
            if (normalizedTicket.admissionReservationId().isBlank()) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "BACKFILL assignment requires one admissionReservationId per player.");
            }
            if (!uniqueReservationIds.add(normalizedTicket.admissionReservationId())) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "BACKFILL assignment includes duplicate admissionReservationIds.");
            }
            if (normalizedTicket.admissionExpiresAtEpochMs() <= 0L) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "BACKFILL assignment requires a positive admission expiry per player.");
            }
            normalizedTickets.add(normalizedTicket);
            playerUuids.add(normalizedTicket.playerUuid());
        }

        QueueDefinition queue = queueService.find(rawQueueId).orElse(null);
        if (queue == null) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, "Queue does not exist.");
        }
        if (!queue.enabled()) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, "Queue is disabled.");
        }
        if (queue.effectiveMatchmakingMode() != QueueMatchmakingMode.BACKEND_DRIVEN) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, "Queue is not BACKEND_DRIVEN.");
        }

        QueueRuntimeState currentState = state(queue.queueId(), nowEpochMs);
        List<QueueMemberState> assignmentMembers = new ArrayList<>();
        for (UUID playerUuid : playerUuids) {
            String queuedQueueId = queueIdByPlayerUuid.get(playerUuid);
            if (!queue.queueId().equals(queuedQueueId)) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "Player " + playerUuid + " is not in queue " + queue.queueId() + ".");
            }
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            if (playerRef == null || !playerRef.isValid()) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "Player " + playerUuid + " is not online.");
            }
            QueueMemberState member = findMember(currentState, playerUuid).orElse(null);
            if (member == null) {
                return AssignmentLaunchResult.rejected(normalizedMatchId, "Player " + playerUuid + " is missing from queue runtime state.");
            }
            assignmentMembers.add(member);
        }

        PreparedLaunch preparedLaunch;
        try {
            preparedLaunch = prepareBackfillLaunch(
                queue,
                normalizedArenaId,
                assignmentMembers,
                nowEpochMs,
                assignmentId,
                normalizedMatchId,
                externalMatchId,
                normalizedTickets,
                reportingServerId
            );
        } catch (IllegalStateException exception) {
            return AssignmentLaunchResult.rejected(normalizedMatchId, exception.getMessage());
        }

        QueueRuntimeState launchingState = launchOutcomePlanner.prepareAssignmentLaunchAttempt(
            currentState,
            assignmentMembers,
            nowEpochMs
        ).state();
        stateByQueueId.put(queue.queueId(), launchingState);

        List<LaunchCandidate> launchCandidates = new ArrayList<>();
        for (QueueMemberState member : assignmentMembers) {
            PlayerRef playerRef = Universe.get().getPlayer(member.playerUuid());
            if (playerRef == null || !playerRef.isValid()) {
                stateByQueueId.put(queue.queueId(), launchOutcomePlanner.rememberLaunchFailure(
                    launchingState,
                    nowEpochMs,
                    "Assigned player went offline before launch."
                ).state());
                return AssignmentLaunchResult.rejected(normalizedMatchId, "Assigned player went offline before launch.");
            }
            launchCandidates.add(new LaunchCandidate(member, playerRef));
        }

        DispatchLaunchResult dispatch = dispatchBackfillLaunch(
            queue,
            destination,
            launchCandidates,
            preparedLaunch,
            nowEpochMs
        );
        for (LaunchCandidate candidate : dispatch.launched()) {
            queueIdByPlayerUuid.remove(candidate.member().playerUuid());
        }

        QueueRuntimeState updated = launchOutcomePlanner.completeAssignmentLaunch(
            launchingState,
            assignmentMembers,
            dispatch.launched().size(),
            dispatch.succeeded(),
            dispatch.errorMessage(),
            nowEpochMs
        ).state();
        stateByQueueId.put(queue.queueId(), updated);

        if (!dispatch.launched().isEmpty()) {
            return AssignmentLaunchResult.launched(dispatch.matchId());
        }
        return AssignmentLaunchResult.failed(dispatch.matchId(), dispatch.errorMessage());
    }

    @Nonnull
    private QueueRuntimeState maybeStartCountdown(@Nonnull QueueRuntimeState state, @Nonnull QueueDefinition queue, long nowEpochMs) {
        return countdownPlanner.maybeStartCountdown(state, queue, nowEpochMs);
    }

    @Nonnull
    private Optional<ArenaDefinition> selectLaunchArena(@Nonnull QueueDefinition queue) {
        for (String arenaId : queue.arenaIds()) {
            ArenaDefinition arena = arenaService.find(arenaId).orElse(null);
            if (arena != null && arena.enabled()) {
                return Optional.of(arena);
            }
        }
        return Optional.empty();
    }

    @Nonnull
    private DispatchLaunchResult dispatchLaunch(
        @Nonnull QueueDefinition queue,
        @Nonnull ArenaDefinition arena,
        @Nonnull List<LaunchCandidate> launchCandidates,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String assignmentType,
        @Nonnull String matchIdOverride,
        @Nonnull String externalMatchId,
        @Nonnull List<UUID> expectedPlayerUuidsOverride,
        @Nonnull List<BackendAssignmentPlayerTicket> assignmentPlayerTickets,
        @Nonnull String reportingServerId
    ) {
        List<QueueMemberState> readyMembers = launchCandidates.stream()
            .map(LaunchCandidate::member)
            .toList();

        ConfiguredPeer destination;
        try {
            destination = ConfiguredPeer.parse(arena.destinationConnectionAddress());
        } catch (IllegalArgumentException exception) {
            return DispatchLaunchResult.failedBeforeLaunch("", exception.getMessage());
        }

        PreparedLaunch preparedLaunch;
        try {
            preparedLaunch = prepareLaunch(
                queue,
                arena,
                readyMembers,
                nowEpochMs,
                assignmentId,
                assignmentType,
                matchIdOverride,
                externalMatchId,
                expectedPlayerUuidsOverride,
                assignmentPlayerTickets,
                reportingServerId
            );
        } catch (IllegalStateException exception) {
            return DispatchLaunchResult.failedBeforeLaunch("", exception.getMessage());
        }
        if (!ASSIGNMENT_TYPE_BACKFILL.equals(preparedLaunch.assignmentType())) {
            try {
                matchSessionService.upsert(preparedLaunch.matchSessionState());
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to persist Nexori match session before launch.");
                return DispatchLaunchResult.failedBeforeLaunch(
                    preparedLaunch.matchId(),
                    "Failed to persist match session state before launch."
                );
            }
        }

        List<LaunchCandidate> launched = new ArrayList<>();
        String launchError = "";
        for (int launchIndex = 0; launchIndex < launchCandidates.size(); launchIndex++) {
            LaunchCandidate candidate = launchCandidates.get(launchIndex);
            try {
                if (arena.usesInstanceTemplate()) {
                    secureTravelService.travelToServer(
                        candidate.playerRef(),
                        destination,
                        queue.launchTravelProfileId(),
                        contextJsonWithLaunchIndex(
                            preparedLaunch.contextJson(),
                            launchIndex,
                            candidate.member(),
                            preparedLaunch.playerReturnTargetsByUuid(),
                            preparedLaunch.assignmentType(),
                            preparedLaunch.assignmentPlayerTicketsByUuid(),
                            preparedLaunch.reportingServerId()
                        )
                    );
                } else {
                    secureTravelService.travel(
                        candidate.playerRef(),
                        destination,
                        arena.destinationTargetId(),
                        "",
                        queue.launchTravelProfileId(),
                        contextJsonWithLaunchIndex(
                            preparedLaunch.contextJson(),
                            launchIndex,
                            candidate.member(),
                            preparedLaunch.playerReturnTargetsByUuid(),
                            preparedLaunch.assignmentType(),
                            preparedLaunch.assignmentPlayerTicketsByUuid(),
                            preparedLaunch.reportingServerId()
                        )
                    );
                }
                launched.add(candidate);
            } catch (IOException | GeneralSecurityException | IllegalStateException exception) {
                launchError = exception.getMessage();
                logger.atWarning().withCause(exception).log(
                    "Failed to launch Nexori queue "
                        + queue.queueId()
                        + " to arena "
                        + arena.arenaId()
                        + "."
                );
                break;
            }
        }

        if (launchError.isBlank()) {
            try {
                matchSessionService.upsert(completeSessionHandoff(
                    preparedLaunch,
                    launched.stream().map(candidate -> candidate.member().playerUuid()).toList(),
                    nowEpochMs + MatchSessionService.HANDOFF_RECORD_RETENTION_MS,
                    nowEpochMs,
                    ""
                ));
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to finalize Nexori match handoff record after launch.");
            }
            return DispatchLaunchResult.succeeded(preparedLaunch.matchId(), launched);
        }

        if (launched.isEmpty()) {
            if (!ASSIGNMENT_TYPE_BACKFILL.equals(preparedLaunch.assignmentType())) {
                try {
                    matchSessionService.remove(preparedLaunch.matchId());
                } catch (IOException exception) {
                    logger.atWarning().withCause(exception).log("Failed to remove unused Nexori match session after launch failure.");
                }
            }
            return DispatchLaunchResult.failedBeforeLaunch(preparedLaunch.matchId(), launchError);
        }

        try {
            matchSessionService.upsert(completeSessionHandoff(
                preparedLaunch,
                launched.stream().map(candidate -> candidate.member().playerUuid()).toList(),
                nowEpochMs + MatchSessionService.HANDOFF_RECORD_RETENTION_MS,
                nowEpochMs,
                launchError
            ));
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to finalize partial Nexori match handoff record after launch.");
        }
        return DispatchLaunchResult.partial(preparedLaunch.matchId(), launched, launchError);
    }

    @Nonnull
    private DispatchLaunchResult dispatchBackfillLaunch(
        @Nonnull QueueDefinition queue,
        @Nonnull ConfiguredPeer destination,
        @Nonnull List<LaunchCandidate> launchCandidates,
        @Nonnull PreparedLaunch preparedLaunch,
        long nowEpochMs
    ) {
        List<LaunchCandidate> launched = new ArrayList<>();
        String launchError = "";
        for (LaunchCandidate candidate : launchCandidates) {
            try {
                secureTravelService.travelToServer(
                    candidate.playerRef(),
                    destination,
                    queue.launchTravelProfileId(),
                    backfillContextJsonForPlayer(
                        preparedLaunch.contextJson(),
                        candidate.member(),
                        preparedLaunch.playerReturnTargetsByUuid(),
                        preparedLaunch.assignmentPlayerTicketsByUuid(),
                        preparedLaunch.reportingServerId()
                    )
                );
                launched.add(candidate);
            } catch (IOException | GeneralSecurityException | IllegalStateException exception) {
                launchError = exception.getMessage();
                logger.atWarning().withCause(exception).log(
                    "Failed to launch Nexori BACKFILL assignment for queue "
                        + queue.queueId()
                        + " to match "
                        + preparedLaunch.matchId()
                        + "."
                );
                break;
            }
        }

        if (!launched.isEmpty()) {
            try {
                matchSessionService.upsert(completeSessionHandoff(
                    preparedLaunch,
                    launched.stream().map(candidate -> candidate.member().playerUuid()).toList(),
                    nowEpochMs + MatchSessionService.HANDOFF_RECORD_RETENTION_MS,
                    nowEpochMs,
                    launchError
                ));
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to finalize Nexori BACKFILL handoff record after launch.");
            }
        }

        if (launchError.isBlank()) {
            return DispatchLaunchResult.succeeded(preparedLaunch.matchId(), launched);
        }
        if (launched.isEmpty()) {
            return DispatchLaunchResult.failedBeforeLaunch(preparedLaunch.matchId(), launchError);
        }
        return DispatchLaunchResult.partial(preparedLaunch.matchId(), launched, launchError);
    }

    @Nonnull
    private MatchSessionState completeSessionHandoff(
        @Nonnull PreparedLaunch preparedLaunch,
        @Nonnull List<UUID> launchedPlayerUuids,
        long expiresAtEpochMs,
        long nowEpochMs,
        @Nonnull String lastError
    ) {
        if (!ASSIGNMENT_TYPE_BACKFILL.equals(preparedLaunch.assignmentType())) {
            return preparedLaunch.matchSessionState().withHandoffCompleted(
                launchedPlayerUuids,
                expiresAtEpochMs,
                nowEpochMs,
                lastError
            );
        }
        MatchSessionState base = matchSessionService.find(preparedLaunch.matchId())
            .orElse(preparedLaunch.matchSessionState());
        return base.withMergedHandoffCompleted(
            launchedPlayerUuids,
            expiresAtEpochMs,
            nowEpochMs,
            lastError
        );
    }

    @Nonnull
    private PreparedLaunch prepareBackfillLaunch(
        @Nonnull QueueDefinition queue,
        @Nonnull String arenaId,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String matchId,
        @Nonnull String externalMatchId,
        @Nonnull List<BackendAssignmentPlayerTicket> assignmentPlayerTickets,
        @Nonnull String reportingServerId
    ) {
        String returnConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        MinigameLaunchContextBuildResult result = launchContextFactory.buildBackfillLaunchContext(
            queue,
            arenaId,
            readyMembers,
            nowEpochMs,
            assignmentId,
            matchId,
            externalMatchId,
            toLaunchTickets(assignmentPlayerTickets),
            reportingServerId,
            returnConnectionAddress
        );
        return toPreparedLaunch(result);
    }

    @Nonnull
    private PreparedLaunch prepareLaunch(
        @Nonnull QueueDefinition queue,
        @Nonnull ArenaDefinition arena,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String assignmentType,
        @Nonnull String matchIdOverride,
        @Nonnull String externalMatchId,
        @Nonnull List<UUID> expectedPlayerUuidsOverride,
        @Nonnull List<BackendAssignmentPlayerTicket> assignmentPlayerTickets,
        @Nonnull String reportingServerId
    ) {
        String returnConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        String generatedMatchId = UUID.randomUUID().toString().toLowerCase();
        MinigameLaunchContextBuildResult result = launchContextFactory.buildInitialMatchLaunchContext(
            queue,
            arena,
            readyMembers,
            nowEpochMs,
            assignmentId,
            assignmentType,
            matchIdOverride,
            generatedMatchId,
            externalMatchId,
            expectedPlayerUuidsOverride,
            toLaunchTickets(assignmentPlayerTickets),
            reportingServerId,
            returnConnectionAddress,
            ADMISSION_POLICY_SCHEMA_VERSION
        );
        return toPreparedLaunch(result);
    }

    @Nonnull
    private String contextJsonWithLaunchIndex(
        @Nonnull String baseContextJson,
        int launchIndex,
        @Nonnull QueueMemberState member,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        @Nonnull String assignmentType,
        @Nonnull Map<UUID, MinigameLaunchContextFactory.AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
        @Nonnull String reportingServerId
    ) {
        return launchContextFactory.contextJsonWithLaunchIndex(
            baseContextJson,
            launchIndex,
            member,
            playerReturnTargetsByUuid,
            assignmentType,
            assignmentPlayerTicketsByUuid,
            reportingServerId
        );
    }

    @Nonnull
    private String backfillContextJsonForPlayer(
        @Nonnull String baseContextJson,
        @Nonnull QueueMemberState member,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        @Nonnull Map<UUID, MinigameLaunchContextFactory.AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
        @Nonnull String reportingServerId
    ) {
        return launchContextFactory.backfillContextJsonForPlayer(
            baseContextJson,
            member,
            playerReturnTargetsByUuid,
            assignmentPlayerTicketsByUuid,
            reportingServerId
        );
    }

    @Nonnull
    private static List<MinigameLaunchContextFactory.AssignmentPlayerTicket> toLaunchTickets(List<BackendAssignmentPlayerTicket> tickets) {
        if (tickets == null || tickets.isEmpty()) {
            return List.of();
        }
        List<MinigameLaunchContextFactory.AssignmentPlayerTicket> converted = new ArrayList<>();
        for (BackendAssignmentPlayerTicket ticket : tickets) {
            if (ticket != null) {
                converted.add(new MinigameLaunchContextFactory.AssignmentPlayerTicket(
                    ticket.playerUuid(),
                    ticket.admissionReservationId(),
                    ticket.admissionExpiresAtEpochMs()
                ));
            }
        }
        return List.copyOf(converted);
    }

    @Nonnull
    private static PreparedLaunch toPreparedLaunch(@Nonnull MinigameLaunchContextBuildResult result) {
        return new PreparedLaunch(
            result.matchId(),
            result.contextJson(),
            result.matchSessionState(),
            result.playerReturnTargetsByUuid(),
            result.assignmentType(),
            result.assignmentPlayerTicketsByUuid(),
            result.reportingServerId()
        );
    }

    @Nonnull
    private QueueRuntimeState state(@Nonnull String queueId, long nowEpochMs) {
        return stateByQueueId.computeIfAbsent(QueueDefinition.normalizeId(queueId), ignored -> QueueRuntimeState.empty(queueId, nowEpochMs));
    }

    @Nonnull
    private Optional<QueueMemberState> findMember(@Nonnull QueueRuntimeState state, @Nonnull UUID playerUuid) {
        for (QueueMemberState member : state.waitingMembers()) {
            if (member.playerUuid().equals(playerUuid)) {
                return Optional.of(member);
            }
        }
        for (QueueMemberState member : state.readyMembers()) {
            if (member.playerUuid().equals(playerUuid)) {
                return Optional.of(member);
            }
        }
        return Optional.empty();
    }

    @Nonnull
    private RemovedPlayerResult removePlayerFromQueue(@Nonnull UUID playerUuid, long now) {
        String queueId = queueIdByPlayerUuid.remove(playerUuid);
        if (queueId == null) {
            return RemovedPlayerResult.notRemoved();
        }

        QueueDefinition queue = queueService.find(queueId)
            .orElseThrow(() -> new IllegalStateException("Queue runtime references missing queue '" + queueId + "'."));

        QueueRuntimeState currentState = state(queueId, now);
        QueueRuntimeState updated = membershipPlanner.remove(currentState, queue, playerUuid, now).state();
        stateByQueueId.put(queueId, updated);
        return RemovedPlayerResult.removed(queueId, updated);
    }

    private record LaunchCandidate(
        QueueMemberState member,
        PlayerRef playerRef
    ) {
    }

    public record BackendAssignmentPlayerTicket(
        UUID playerUuid,
        String admissionReservationId,
        long admissionExpiresAtEpochMs
    ) {

        @Nonnull
        public BackendAssignmentPlayerTicket normalized() {
            if (playerUuid == null) {
                throw new IllegalArgumentException("Assignment playerUuid cannot be null.");
            }
            return new BackendAssignmentPlayerTicket(
                playerUuid,
                admissionReservationId == null ? "" : admissionReservationId.trim(),
                Math.max(0L, admissionExpiresAtEpochMs)
            );
        }
    }

    private record PreparedLaunch(
        String matchId,
        String contextJson,
        MatchSessionState matchSessionState,
        Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid,
        String assignmentType,
        Map<UUID, MinigameLaunchContextFactory.AssignmentPlayerTicket> assignmentPlayerTicketsByUuid,
        String reportingServerId
    ) {
    }

    private record DispatchLaunchResult(
        String matchId,
        List<LaunchCandidate> launched,
        String errorMessage,
        boolean failedBeforeLaunch
    ) {

        @Nonnull
        private static DispatchLaunchResult succeeded(@Nonnull String matchId, @Nonnull List<LaunchCandidate> launched) {
            return new DispatchLaunchResult(matchId, List.copyOf(launched), "", false);
        }

        @Nonnull
        private static DispatchLaunchResult partial(
            @Nonnull String matchId,
            @Nonnull List<LaunchCandidate> launched,
            @Nonnull String errorMessage
        ) {
            return new DispatchLaunchResult(matchId, List.copyOf(launched), normalizeError(errorMessage), false);
        }

        @Nonnull
        private static DispatchLaunchResult failedBeforeLaunch(@Nonnull String matchId, @Nonnull String errorMessage) {
            return new DispatchLaunchResult(matchId, List.of(), normalizeError(errorMessage), true);
        }

        private boolean succeeded() {
            return !failedBeforeLaunch && errorMessage.isBlank();
        }
    }

    public enum AssignmentLaunchOutcome {
        LAUNCHED,
        REJECTED,
        FAILED
    }

    public record AssignmentLaunchResult(
        AssignmentLaunchOutcome outcome,
        String localMatchId,
        String reason
    ) {

        @Nonnull
        public static AssignmentLaunchResult launched(@Nonnull String localMatchId) {
            return new AssignmentLaunchResult(AssignmentLaunchOutcome.LAUNCHED, localMatchId, "");
        }

        @Nonnull
        public static AssignmentLaunchResult rejected(@Nonnull String localMatchId, @Nonnull String reason) {
            return new AssignmentLaunchResult(AssignmentLaunchOutcome.REJECTED, localMatchId, normalizeError(reason));
        }

        @Nonnull
        public static AssignmentLaunchResult failed(@Nonnull String localMatchId, @Nonnull String reason) {
            return new AssignmentLaunchResult(AssignmentLaunchOutcome.FAILED, localMatchId, normalizeError(reason));
        }
    }

    @Nonnull
    private static String normalizeError(String rawError) {
        return rawError == null || rawError.isBlank() ? "Unknown queue launch failure." : rawError.trim();
    }

    private record RemovedPlayerResult(
        boolean removed,
        String queueId,
        QueueRuntimeState state
    ) {

        @Nonnull
        private static RemovedPlayerResult removed(@Nonnull String queueId, @Nonnull QueueRuntimeState state) {
            return new RemovedPlayerResult(true, QueueDefinition.normalizeId(queueId), state);
        }

        @Nonnull
        private static RemovedPlayerResult notRemoved() {
            return new RemovedPlayerResult(false, "", null);
        }
    }

    public enum JoinOutcome {
        JOINED,
        ALREADY_QUEUED,
        QUEUE_MISSING,
        QUEUE_DISABLED
    }

    public record JoinResult(
        JoinOutcome outcome,
        String queueId,
        String existingQueueId,
        QueueRuntimeState state
    ) {

        @Nonnull
        public static JoinResult joined(@Nonnull QueueRuntimeState state) {
            return new JoinResult(JoinOutcome.JOINED, state.queueId(), "", state);
        }

        @Nonnull
        public static JoinResult alreadyQueued(@Nonnull String existingQueueId) {
            return new JoinResult(JoinOutcome.ALREADY_QUEUED, "", QueueDefinition.normalizeId(existingQueueId), null);
        }

        @Nonnull
        public static JoinResult queueMissing(@Nonnull String queueId) {
            return new JoinResult(JoinOutcome.QUEUE_MISSING, QueueDefinition.normalizeId(queueId), "", null);
        }

        @Nonnull
        public static JoinResult queueDisabled(@Nonnull String queueId) {
            return new JoinResult(JoinOutcome.QUEUE_DISABLED, QueueDefinition.normalizeId(queueId), "", null);
        }

    }

    public enum LeaveOutcome {
        LEFT,
        NOT_QUEUED
    }

    public record LeaveResult(
        LeaveOutcome outcome,
        String queueId,
        QueueRuntimeState state
    ) {

        @Nonnull
        public static LeaveResult left(@Nonnull String queueId, @Nonnull QueueRuntimeState state) {
            return new LeaveResult(LeaveOutcome.LEFT, QueueDefinition.normalizeId(queueId), state);
        }

        @Nonnull
        public static LeaveResult notQueued() {
            return new LeaveResult(LeaveOutcome.NOT_QUEUED, "", null);
        }
    }

    public record QueueHudState(
        String queueId,
        String displayName,
        int minPlayers,
        int maxPlayers,
        int queuedPlayers,
        QueuePhase phase,
        long countdownEndsAtEpochMs
    ) {
    }
}
