package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
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
public final class QueueCoordinatorService {

    private static final Gson GSON = new Gson();
    private static final long LAUNCH_RETRY_INTERVAL_MS = 3000L;
    private static final long WORLD_TICK_ADVANCE_INTERVAL_MS = 1000L;

    private final QueueService queueService;
    private final ArenaService arenaService;
    private final MatchSessionService matchSessionService;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final SecureTravelService secureTravelService;
    private final HytaleLogger logger;
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
        QueueMemberState member = new QueueMemberState(playerUuid, playerName, sourceLobbyId, sourcePortalId, now).normalized();
        List<QueueMemberState> waitingMembers = new ArrayList<>(currentState.waitingMembers());
        waitingMembers.add(member);
        queueIdByPlayerUuid.put(playerUuid, normalizedQueueId);

        QueueRuntimeState updated = new QueueRuntimeState(
            currentState.queueId(),
            currentState.phase(),
            List.copyOf(waitingMembers),
            currentState.readyMembers(),
            currentState.countdownEndsAtEpochMs(),
            currentState.readyAtEpochMs(),
            now,
            currentState.lastLaunchAttemptAtEpochMs(),
            currentState.lastLaunchError()
        ).normalized();
        updated = maybeStartCountdown(updated, queue.get(), now);
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
            if (currentState.waitingMembers().size() < queue.minPlayers()) {
                stateByQueueId.put(queue.queueId(), new QueueRuntimeState(
                    currentState.queueId(),
                    QueuePhase.WAITING,
                    currentState.waitingMembers(),
                    currentState.readyMembers(),
                    0L,
                    currentState.readyAtEpochMs(),
                    nowEpochMs,
                    currentState.lastLaunchAttemptAtEpochMs(),
                    currentState.lastLaunchError()
                ).normalized());
                continue;
            }
            if (currentState.countdownEndsAtEpochMs() > nowEpochMs) {
                continue;
            }

            int readyCount = Math.min(queue.maxPlayers(), currentState.waitingMembers().size());
            List<QueueMemberState> readyMembers = new ArrayList<>(currentState.waitingMembers().subList(0, readyCount));
            List<QueueMemberState> remainingWaiting = new ArrayList<>(currentState.waitingMembers().subList(readyCount, currentState.waitingMembers().size()));
            QueueRuntimeState updated = new QueueRuntimeState(
                currentState.queueId(),
                QueuePhase.READY,
                List.copyOf(remainingWaiting),
                List.copyOf(readyMembers),
                0L,
                nowEpochMs,
                nowEpochMs,
                0L,
                ""
            ).normalized();
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
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(
                    currentState,
                    nowEpochMs,
                    "No enabled arena is currently available for this queue."
                ));
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
                List<QueueMemberState> mergedWaiting = new ArrayList<>(currentState.waitingMembers());
                mergedWaiting.addAll(liveReadyMembers);
                QueueRuntimeState updated = new QueueRuntimeState(
                    currentState.queueId(),
                    QueuePhase.WAITING,
                    List.copyOf(mergedWaiting),
                    List.of(),
                    0L,
                    0L,
                    nowEpochMs,
                    0L,
                    ""
                ).normalized();
                updated = maybeStartCountdown(updated, queue, nowEpochMs);
                stateByQueueId.put(queue.queueId(), updated);
                continue;
            }

            QueueRuntimeState readyState = new QueueRuntimeState(
                currentState.queueId(),
                QueuePhase.READY,
                currentState.waitingMembers(),
                List.copyOf(liveReadyMembers),
                0L,
                currentState.readyAtEpochMs(),
                nowEpochMs,
                nowEpochMs,
                ""
            ).normalized();
            stateByQueueId.put(queue.queueId(), readyState);

            DispatchLaunchResult dispatch = dispatchLaunch(queue, arena.get(), launchCandidates, nowEpochMs, "", "", "", List.of());
            if (dispatch.failedBeforeLaunch()) {
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(readyState, nowEpochMs, dispatch.errorMessage()));
                continue;
            }
            for (LaunchCandidate candidate : dispatch.launched()) {
                queueIdByPlayerUuid.remove(candidate.member().playerUuid());
            }
            if (dispatch.succeeded()) {
                QueueRuntimeState updated = new QueueRuntimeState(
                    readyState.queueId(),
                    QueuePhase.WAITING,
                    readyState.waitingMembers(),
                    List.of(),
                    0L,
                    0L,
                    nowEpochMs,
                    0L,
                    ""
                ).normalized();
                updated = maybeStartCountdown(updated, queue, nowEpochMs);
                stateByQueueId.put(queue.queueId(), updated);
                continue;
            }

            List<QueueMemberState> unlaunchedReady = new ArrayList<>();
            for (int index = dispatch.launched().size(); index < launchCandidates.size(); index++) {
                unlaunchedReady.add(launchCandidates.get(index).member());
            }

            List<QueueMemberState> mergedWaiting = new ArrayList<>(readyState.waitingMembers());
            mergedWaiting.addAll(unlaunchedReady);
            QueueRuntimeState updated = new QueueRuntimeState(
                readyState.queueId(),
                QueuePhase.WAITING,
                List.copyOf(mergedWaiting),
                List.of(),
                0L,
                0L,
                nowEpochMs,
                0L,
                ""
            ).normalized();
            updated = maybeStartCountdown(updated, queue, nowEpochMs);
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
        long nowEpochMs = System.currentTimeMillis();
        if (assignmentId == null || assignmentId.isBlank()) {
            return AssignmentLaunchResult.rejected("", "Assignment id cannot be blank.");
        }
        if (playerUuids == null || playerUuids.isEmpty()) {
            return AssignmentLaunchResult.rejected("", "Assignment must include at least one player.");
        }
        Set<UUID> uniquePlayerUuids = new LinkedHashSet<>(playerUuids);
        if (uniquePlayerUuids.size() != playerUuids.size()) {
            return AssignmentLaunchResult.rejected("", "Assignment includes duplicate players.");
        }
        String normalizedMatchId;
        try {
            normalizedMatchId = NexoriMatchIds.normalizeBackendOwnedMatchId(matchId);
        } catch (IllegalArgumentException exception) {
            return AssignmentLaunchResult.rejected("", exception.getMessage());
        }
        List<UUID> canonicalExpectedPlayerUuids = PlayerUuidLists.canonicalize(expectedPlayerUuids);
        if (!canonicalExpectedPlayerUuids.isEmpty()
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

        QueueRuntimeState currentState = state(queue.queueId(), nowEpochMs);
        List<QueueMemberState> assignmentMembers = new ArrayList<>();
        for (UUID playerUuid : playerUuids) {
            if (playerUuid == null) {
                return AssignmentLaunchResult.rejected("", "Assignment includes a null player UUID.");
            }
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

        QueueRuntimeState launchingState = new QueueRuntimeState(
            currentState.queueId(),
            QueuePhase.READY,
            removePlayers(currentState.waitingMembers(), playerUuids),
            List.copyOf(assignmentMembers),
            0L,
            nowEpochMs,
            nowEpochMs,
            nowEpochMs,
            ""
        ).normalized();
        stateByQueueId.put(queue.queueId(), launchingState);

        List<LaunchCandidate> launchCandidates = new ArrayList<>();
        for (QueueMemberState member : assignmentMembers) {
            PlayerRef playerRef = Universe.get().getPlayer(member.playerUuid());
            if (playerRef == null || !playerRef.isValid()) {
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(launchingState, nowEpochMs, "Assigned player went offline before launch."));
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
            normalizedMatchId,
            externalMatchId,
            canonicalExpectedPlayerUuids
        );
        for (LaunchCandidate candidate : dispatch.launched()) {
            queueIdByPlayerUuid.remove(candidate.member().playerUuid());
        }

        List<QueueMemberState> unlaunchedReady = new ArrayList<>();
        for (int index = dispatch.launched().size(); index < launchCandidates.size(); index++) {
            unlaunchedReady.add(launchCandidates.get(index).member());
        }
        List<QueueMemberState> mergedWaiting = new ArrayList<>(launchingState.waitingMembers());
        mergedWaiting.addAll(unlaunchedReady);
        QueueRuntimeState updated = new QueueRuntimeState(
            queue.queueId(),
            QueuePhase.WAITING,
            List.copyOf(mergedWaiting),
            List.of(),
            0L,
            0L,
            nowEpochMs,
            0L,
            dispatch.succeeded() ? "" : dispatch.errorMessage()
        ).normalized();
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
    private QueueRuntimeState maybeStartCountdown(@Nonnull QueueRuntimeState state, @Nonnull QueueDefinition queue, long nowEpochMs) {
        if (queue.effectiveMatchmakingMode() == QueueMatchmakingMode.BACKEND_DRIVEN) {
            return new QueueRuntimeState(
                state.queueId(),
                QueuePhase.WAITING,
                state.waitingMembers(),
                state.readyMembers(),
                0L,
                state.readyAtEpochMs(),
                nowEpochMs,
                state.lastLaunchAttemptAtEpochMs(),
                state.lastLaunchError()
            ).normalized();
        }
        if (state.phase() == QueuePhase.READY || state.hasReadyBatch()) {
            return state;
        }
        if (state.waitingMembers().size() < queue.minPlayers()) {
            if (state.phase() == QueuePhase.WAITING && state.countdownEndsAtEpochMs() == 0L) {
                return state;
            }
            return new QueueRuntimeState(
                state.queueId(),
                QueuePhase.WAITING,
                state.waitingMembers(),
                state.readyMembers(),
                0L,
                state.readyAtEpochMs(),
                nowEpochMs,
                state.lastLaunchAttemptAtEpochMs(),
                state.lastLaunchError()
            ).normalized();
        }
        if (state.phase() == QueuePhase.COUNTDOWN && state.countdownEndsAtEpochMs() > 0L) {
            return state;
        }
        long countdownEndsAt = nowEpochMs + (queue.countdownSeconds() * 1000L);
        return new QueueRuntimeState(
            state.queueId(),
            QueuePhase.COUNTDOWN,
            state.waitingMembers(),
            state.readyMembers(),
            countdownEndsAt,
            state.readyAtEpochMs(),
            nowEpochMs,
            state.lastLaunchAttemptAtEpochMs(),
            state.lastLaunchError()
        ).normalized();
    }

    @Nonnull
    private QueueRuntimeState rememberLaunchFailure(
        @Nonnull QueueRuntimeState state,
        long nowEpochMs,
        @Nonnull String rawError
    ) {
        String launchError = rawError == null || rawError.isBlank() ? "Unknown queue launch failure." : rawError.trim();
        return new QueueRuntimeState(
            state.queueId(),
            QueuePhase.READY,
            state.waitingMembers(),
            state.readyMembers(),
            0L,
            state.readyAtEpochMs() <= 0L ? nowEpochMs : state.readyAtEpochMs(),
            nowEpochMs,
            nowEpochMs,
            launchError
        ).normalized();
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
        @Nonnull String matchIdOverride,
        @Nonnull String externalMatchId,
        @Nonnull List<UUID> expectedPlayerUuidsOverride
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
                matchIdOverride,
                externalMatchId,
                expectedPlayerUuidsOverride
            );
        } catch (IllegalStateException exception) {
            return DispatchLaunchResult.failedBeforeLaunch("", exception.getMessage());
        }
        try {
            matchSessionService.upsert(preparedLaunch.matchSessionState());
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist Nexori match session before launch.");
            return DispatchLaunchResult.failedBeforeLaunch(
                preparedLaunch.matchId(),
                "Failed to persist match session state before launch."
            );
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
                            preparedLaunch.playerReturnTargetsByUuid()
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
                            preparedLaunch.playerReturnTargetsByUuid()
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
                matchSessionService.upsert(preparedLaunch.matchSessionState().withHandoffCompleted(
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
            try {
                matchSessionService.remove(preparedLaunch.matchId());
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to remove unused Nexori match session after launch failure.");
            }
            return DispatchLaunchResult.failedBeforeLaunch(preparedLaunch.matchId(), launchError);
        }

        try {
            matchSessionService.upsert(preparedLaunch.matchSessionState().withHandoffCompleted(
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
    private PreparedLaunch prepareLaunch(
        @Nonnull QueueDefinition queue,
        @Nonnull ArenaDefinition arena,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs,
        @Nonnull String assignmentId,
        @Nonnull String matchIdOverride,
        @Nonnull String externalMatchId,
        @Nonnull List<UUID> expectedPlayerUuidsOverride
    ) {
        if (readyMembers.isEmpty()) {
            throw new IllegalStateException("Cannot build a launch context for an empty ready batch.");
        }
        String originLobbyId = normalizeSourceContextId(readyMembers.get(0).sourceLobbyId());
        String returnConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        if (returnConnectionAddress.isBlank()) {
            throw new IllegalStateException("This server does not have a local connection address configured for minigame return.");
        }
        String originReturnTargetId = defaultReturnTargetId(originLobbyId);
        String matchId = matchIdOverride != null && !matchIdOverride.isBlank()
            ? NexoriMatchIds.normalizeBackendOwnedMatchId(matchIdOverride)
            : NexoriMatchIds.normalizeGeneratedMatchId(UUID.randomUUID().toString().toLowerCase());
        List<UUID> expectedPlayerUuids = expectedPlayerUuidsOverride != null && !expectedPlayerUuidsOverride.isEmpty()
            ? PlayerUuidLists.canonicalize(expectedPlayerUuidsOverride)
            : PlayerUuidLists.canonicalize(readyMembers.stream().map(QueueMemberState::playerUuid).toList());
        LinkedHashMap<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid = new LinkedHashMap<>();
        for (QueueMemberState member : readyMembers) {
            String memberSourceContextId = normalizeSourceContextId(member.sourceLobbyId());
            playerReturnTargetsByUuid.put(
                member.playerUuid(),
                new ArenaPlayerReturnTarget(
                    memberSourceContextId,
                    returnConnectionAddress,
                    defaultReturnTargetId(memberSourceContextId),
                    queue.launchTravelProfileId()
                ).normalized()
            );
        }
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.launch");
        root.addProperty("matchId", matchId);
        root.addProperty("queueId", queue.queueId());
        root.addProperty("arenaId", arena.arenaId());
        root.addProperty("originLobbyId", originLobbyId);
        root.addProperty("returnConnectionAddress", returnConnectionAddress);
        root.addProperty("returnFallbackTargetId", originReturnTargetId);
        root.addProperty("launchTravelProfileId", queue.launchTravelProfileId());
        root.addProperty("instanceTemplateId", arena.instanceTemplateId());
        root.addProperty("matchResolutionTriggerId", arena.matchResolutionTriggerId());
        root.addProperty("rulesEngineId", arena.rulesEngineId());
        root.addProperty("expectedPlayerCount", expectedPlayerUuids.size());
        JsonArray expectedPlayerUuidsJson = new JsonArray();
        for (UUID expectedPlayerUuid : expectedPlayerUuids) {
            expectedPlayerUuidsJson.add(expectedPlayerUuid.toString());
        }
        root.add("expectedPlayerUuids", expectedPlayerUuidsJson);
        root.addProperty("launchedAtEpochMs", nowEpochMs);
        if (assignmentId != null && !assignmentId.isBlank()) {
            root.addProperty("assignmentId", assignmentId);
        }
        if (externalMatchId != null && !externalMatchId.isBlank()) {
            root.addProperty("externalMatchId", externalMatchId);
        }
        if (arena.usesInstanceTemplate()) {
            root.addProperty("serverEntryMode", "default_world_natural_spawn");
        }
        MatchSessionState matchSessionState = new MatchSessionState(
            matchId,
            queue.queueId(),
            arena.arenaId(),
            originLobbyId,
            returnConnectionAddress,
            originReturnTargetId,
            queue.launchTravelProfileId(),
            PlayerUuidLists.canonicalize(readyMembers.stream().map(QueueMemberState::playerUuid).toList()),
            List.of(),
            nowEpochMs,
            nowEpochMs,
            0L,
            nowEpochMs + MatchSessionService.PREPARED_SESSION_GRACE_MS,
            ""
        ).normalized();
        return new PreparedLaunch(matchId, GSON.toJson(root), matchSessionState, Map.copyOf(playerReturnTargetsByUuid));
    }

    @Nonnull
    private static String normalizeSourceContextId(@Nonnull String rawSourceContextId) {
        return SourceContextId.normalizeId(rawSourceContextId);
    }

    @Nonnull
    private static String defaultReturnTargetId(@Nonnull String sourceContextId) {
        return normalizeSourceContextId(sourceContextId) + ".natural_spawn";
    }

    @Nonnull
    private String contextJsonWithLaunchIndex(
        @Nonnull String baseContextJson,
        int launchIndex,
        @Nonnull QueueMemberState member,
        @Nonnull Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid
    ) {
        JsonObject root = GSON.fromJson(baseContextJson, JsonObject.class);
        if (root == null) {
            root = new JsonObject();
        }
        root.addProperty("launchIndex", Math.max(launchIndex, 0));
        ArenaPlayerReturnTarget returnTarget = playerReturnTargetsByUuid.get(member.playerUuid());
        if (returnTarget == null) {
            throw new IllegalStateException("Missing per-player return target for launched player " + member.playerUuid() + ".");
        }
        root.addProperty("originLobbyId", returnTarget.originLobbyId());
        root.addProperty("returnConnectionAddress", returnTarget.returnConnectionAddress());
        root.addProperty("returnFallbackTargetId", returnTarget.returnFallbackTargetId());
        root.addProperty("launchTravelProfileId", returnTarget.launchTravelProfileId());
        return GSON.toJson(root);
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
        List<QueueMemberState> waitingMembers = removePlayer(currentState.waitingMembers(), playerUuid);
        List<QueueMemberState> readyMembers = removePlayer(currentState.readyMembers(), playerUuid);
        QueueRuntimeState updated = new QueueRuntimeState(
            currentState.queueId(),
            currentState.phase(),
            waitingMembers,
            readyMembers,
            currentState.countdownEndsAtEpochMs(),
            currentState.readyAtEpochMs(),
            now,
            currentState.lastLaunchAttemptAtEpochMs(),
            currentState.lastLaunchError()
        ).normalized();

        if (updated.hasReadyBatch() && updated.readyMembers().size() < queue.minPlayers()) {
            List<QueueMemberState> mergedWaiting = new ArrayList<>(updated.readyMembers());
            mergedWaiting.addAll(updated.waitingMembers());
            updated = new QueueRuntimeState(
                updated.queueId(),
                QueuePhase.WAITING,
                List.copyOf(mergedWaiting),
                List.of(),
                0L,
                0L,
                now,
                0L,
                ""
            ).normalized();
        }

        if (updated.phase() == QueuePhase.COUNTDOWN && updated.waitingMembers().size() < queue.minPlayers()) {
            updated = new QueueRuntimeState(
                updated.queueId(),
                QueuePhase.WAITING,
                updated.waitingMembers(),
                updated.readyMembers(),
                0L,
                updated.readyAtEpochMs(),
                now,
                updated.lastLaunchAttemptAtEpochMs(),
                updated.lastLaunchError()
            ).normalized();
        }

        updated = maybeStartCountdown(updated, queue, now);
        stateByQueueId.put(queueId, updated);
        return RemovedPlayerResult.removed(queueId, updated);
    }

    @Nonnull
    private static List<QueueMemberState> removePlayer(@Nonnull List<QueueMemberState> members, @Nonnull UUID playerUuid) {
        List<QueueMemberState> filtered = new ArrayList<>();
        for (QueueMemberState member : members) {
            if (!member.playerUuid().equals(playerUuid)) {
                filtered.add(member);
            }
        }
        return List.copyOf(filtered);
    }

    @Nonnull
    private static List<QueueMemberState> removePlayers(@Nonnull List<QueueMemberState> members, @Nonnull List<UUID> playerUuids) {
        List<QueueMemberState> filtered = new ArrayList<>();
        for (QueueMemberState member : members) {
            if (!playerUuids.contains(member.playerUuid())) {
                filtered.add(member);
            }
        }
        return List.copyOf(filtered);
    }

    private record LaunchCandidate(
        QueueMemberState member,
        PlayerRef playerRef
    ) {
    }

    private record PreparedLaunch(
        String matchId,
        String contextJson,
        MatchSessionState matchSessionState,
        Map<UUID, ArenaPlayerReturnTarget> playerReturnTargetsByUuid
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
