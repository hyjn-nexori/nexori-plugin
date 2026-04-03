package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QueueCoordinatorService {

    private static final Gson GSON = new Gson();
    private static final long LAUNCH_RETRY_INTERVAL_MS = 3000L;

    private final QueueService queueService;
    private final ArenaService arenaService;
    private final LobbyService lobbyService;
    private final MatchSessionService matchSessionService;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final SecureTravelService secureTravelService;
    private final HytaleLogger logger;
    private final Map<String, QueueRuntimeState> stateByQueueId = new LinkedHashMap<>();
    private final Map<UUID, String> queueIdByPlayerUuid = new LinkedHashMap<>();

    public QueueCoordinatorService(
        @Nonnull QueueService queueService,
        @Nonnull ArenaService arenaService,
        @Nonnull LobbyService lobbyService,
        @Nonnull MatchSessionService matchSessionService,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull SecureTravelService secureTravelService,
        @Nonnull HytaleLogger logger
    ) {
        this.queueService = queueService;
        this.arenaService = arenaService;
        this.lobbyService = lobbyService;
        this.matchSessionService = matchSessionService;
        this.localConnectionAddressService = localConnectionAddressService;
        this.secureTravelService = secureTravelService;
        this.logger = logger;
    }

    @Nonnull
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
    public synchronized LeaveResult leaveCurrentQueue(@Nonnull UUID playerUuid) {
        String queueId = queueIdByPlayerUuid.remove(playerUuid);
        if (queueId == null) {
            return LeaveResult.notQueued();
        }

        QueueDefinition queue = queueService.find(queueId)
            .orElseThrow(() -> new IllegalStateException("Queue runtime references missing queue '" + queueId + "'."));

        long now = System.currentTimeMillis();
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
        return LeaveResult.left(queueId, updated);
    }

    public synchronized boolean isQueued(@Nonnull UUID playerUuid) {
        return queueIdByPlayerUuid.containsKey(playerUuid);
    }

    @Nonnull
    public synchronized Optional<String> findQueuedQueueId(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(queueIdByPlayerUuid.get(playerUuid));
    }

    @Nonnull
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
    public synchronized List<QueueRuntimeState> listQueueStates() {
        long now = System.currentTimeMillis();
        for (QueueDefinition queue : queueService.list()) {
            state(queue.queueId(), now);
        }
        return stateByQueueId.values().stream()
            .sorted(Comparator.comparing(QueueRuntimeState::queueId))
            .toList();
    }

    public synchronized void advanceCountdowns(long nowEpochMs) {
        for (QueueDefinition queue : queueService.list()) {
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

    public synchronized void launchReadyBatches(long nowEpochMs) {
        for (QueueDefinition queue : queueService.list()) {
            if (!queue.enabled()) {
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

            ConfiguredPeer destination;
            try {
                destination = ConfiguredPeer.parse(arena.get().destinationConnectionAddress());
            } catch (IllegalArgumentException exception) {
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(readyState, nowEpochMs, exception.getMessage()));
                continue;
            }

            PreparedLaunch preparedLaunch;
            try {
                preparedLaunch = prepareLaunch(queue, arena.get(), liveReadyMembers, nowEpochMs);
            } catch (IllegalStateException exception) {
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(readyState, nowEpochMs, exception.getMessage()));
                continue;
            }
            try {
                matchSessionService.upsert(preparedLaunch.matchSessionState());
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to persist Nexori match session before launch.");
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(readyState, nowEpochMs, "Failed to persist match session state before launch."));
                continue;
            }
            List<LaunchCandidate> launched = new ArrayList<>();
            String launchError = "";
            for (LaunchCandidate candidate : launchCandidates) {
                try {
                    secureTravelService.travel(
                        candidate.playerRef(),
                        destination,
                        arena.get().destinationTargetId(),
                        "",
                        queue.launchTravelProfileId(),
                        preparedLaunch.contextJson()
                    );
                    launched.add(candidate);
                } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                    launchError = exception.getMessage();
                    logger.atWarning().withCause(exception).log(
                        "Failed to launch Nexori queue "
                            + queue.queueId()
                            + " to arena "
                            + arena.get().arenaId()
                            + "."
                    );
                    break;
                }
            }

            if (launchError.isBlank()) {
                for (LaunchCandidate candidate : launched) {
                    queueIdByPlayerUuid.remove(candidate.member().playerUuid());
                }
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

            if (launched.isEmpty()) {
                try {
                    matchSessionService.remove(preparedLaunch.matchId());
                } catch (IOException exception) {
                    logger.atWarning().withCause(exception).log("Failed to remove unused Nexori match session after launch failure.");
                }
                stateByQueueId.put(queue.queueId(), rememberLaunchFailure(readyState, nowEpochMs, launchError));
                continue;
            }

            for (LaunchCandidate candidate : launched) {
                queueIdByPlayerUuid.remove(candidate.member().playerUuid());
            }
            try {
                matchSessionService.upsert(preparedLaunch.matchSessionState().withExpectedPlayers(
                    launched.stream().map(candidate -> candidate.member().playerUuid()).toList(),
                    nowEpochMs,
                    launchError
                ));
            } catch (IOException exception) {
                logger.atWarning().withCause(exception).log("Failed to shrink Nexori match session roster after partial launch.");
            }

            List<QueueMemberState> unlaunchedReady = new ArrayList<>();
            for (int index = launched.size(); index < launchCandidates.size(); index++) {
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
    private QueueRuntimeState maybeStartCountdown(@Nonnull QueueRuntimeState state, @Nonnull QueueDefinition queue, long nowEpochMs) {
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
    private PreparedLaunch prepareLaunch(
        @Nonnull QueueDefinition queue,
        @Nonnull ArenaDefinition arena,
        @Nonnull List<QueueMemberState> readyMembers,
        long nowEpochMs
    ) {
        if (readyMembers.isEmpty()) {
            throw new IllegalStateException("Cannot build a launch context for an empty ready batch.");
        }
        String originLobbyId = readyMembers.get(0).sourceLobbyId();
        LobbyDefinition originLobby = lobbyService.find(originLobbyId)
            .orElseThrow(() -> new IllegalStateException(
                "Queue ready batch references missing lobby '" + originLobbyId + "'."
            ));
        String returnConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        if (returnConnectionAddress.isBlank()) {
            throw new IllegalStateException("This server does not have a local connection address configured for minigame return.");
        }
        String matchId = UUID.randomUUID().toString().toLowerCase();
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.launch");
        root.addProperty("matchId", matchId);
        root.addProperty("queueId", queue.queueId());
        root.addProperty("arenaId", arena.arenaId());
        root.addProperty("originLobbyId", originLobby.lobbyId());
        root.addProperty("returnConnectionAddress", returnConnectionAddress);
        root.addProperty("returnFallbackTargetId", originLobby.returnTargetId());
        root.addProperty("launchTravelProfileId", queue.launchTravelProfileId());
        root.addProperty("launchedAtEpochMs", nowEpochMs);
        MatchSessionState matchSessionState = new MatchSessionState(
            matchId,
            queue.queueId(),
            arena.arenaId(),
            originLobby.lobbyId(),
            returnConnectionAddress,
            originLobby.returnTargetId(),
            queue.launchTravelProfileId(),
            readyMembers.stream().map(QueueMemberState::playerUuid).toList(),
            List.of(),
            nowEpochMs,
            nowEpochMs,
            ""
        ).normalized();
        return new PreparedLaunch(matchId, GSON.toJson(root), matchSessionState);
    }

    @Nonnull
    private QueueRuntimeState state(@Nonnull String queueId, long nowEpochMs) {
        return stateByQueueId.computeIfAbsent(QueueDefinition.normalizeId(queueId), ignored -> QueueRuntimeState.empty(queueId, nowEpochMs));
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

    private record LaunchCandidate(
        QueueMemberState member,
        PlayerRef playerRef
    ) {
    }

    private record PreparedLaunch(
        String matchId,
        String contextJson,
        MatchSessionState matchSessionState
    ) {
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
}
