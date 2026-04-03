package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class QueueCoordinatorService {

    private final QueueService queueService;
    private final Map<String, QueueRuntimeState> stateByQueueId = new LinkedHashMap<>();
    private final Map<UUID, String> queueIdByPlayerUuid = new LinkedHashMap<>();

    public QueueCoordinatorService(@Nonnull QueueService queueService) {
        this.queueService = queueService;
    }

    @Nonnull
    public synchronized JoinResult joinQueue(
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String rawQueueId,
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
        QueueMemberState member = new QueueMemberState(playerUuid, playerName, sourcePortalId, now).normalized();
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
            now
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
            now
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
                now
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
                now
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
                    nowEpochMs
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
                nowEpochMs
            ).normalized();
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
                nowEpochMs
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
            nowEpochMs
        ).normalized();
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
