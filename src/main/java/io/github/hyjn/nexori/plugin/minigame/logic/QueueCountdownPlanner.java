package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueuePhase;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure planner for local queue countdown state transitions.
 */
public final class QueueCountdownPlanner {

    @Nonnull
    public QueueRuntimeState maybeStartCountdown(
        @Nonnull QueueRuntimeState state,
        @Nonnull QueueDefinition queue,
        long nowEpochMs
    ) {
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
    public QueueRuntimeState advanceCountdown(
        @Nonnull QueueRuntimeState currentState,
        @Nonnull QueueDefinition queue,
        long nowEpochMs
    ) {
        if (queue.effectiveMatchmakingMode() == QueueMatchmakingMode.BACKEND_DRIVEN) {
            return currentState;
        }
        if (currentState.phase() != QueuePhase.COUNTDOWN) {
            return currentState;
        }
        if (currentState.waitingMembers().size() < queue.minPlayers()) {
            return new QueueRuntimeState(
                currentState.queueId(),
                QueuePhase.WAITING,
                currentState.waitingMembers(),
                currentState.readyMembers(),
                0L,
                currentState.readyAtEpochMs(),
                nowEpochMs,
                currentState.lastLaunchAttemptAtEpochMs(),
                currentState.lastLaunchError()
            ).normalized();
        }
        if (currentState.countdownEndsAtEpochMs() > nowEpochMs) {
            return currentState;
        }

        int readyCount = Math.min(queue.maxPlayers(), currentState.waitingMembers().size());
        List<QueueMemberState> readyMembers = new ArrayList<>(currentState.waitingMembers().subList(0, readyCount));
        List<QueueMemberState> remainingWaiting = new ArrayList<>(
            currentState.waitingMembers().subList(readyCount, currentState.waitingMembers().size())
        );
        return new QueueRuntimeState(
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
    }
}
