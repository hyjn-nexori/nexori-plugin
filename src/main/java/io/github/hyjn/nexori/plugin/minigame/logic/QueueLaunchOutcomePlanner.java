package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueuePhase;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure planner for queue state transitions after launch attempts.
 */
public final class QueueLaunchOutcomePlanner {

    private final QueueCountdownPlanner countdownPlanner = new QueueCountdownPlanner();

    @Nonnull
    public QueueLaunchOutcomePlan rememberLaunchFailure(
        @Nonnull QueueRuntimeState state,
        long nowEpochMs,
        @Nonnull String rawError
    ) {
        String launchError = rawError == null || rawError.isBlank() ? "Unknown queue launch failure." : rawError.trim();
        return new QueueLaunchOutcomePlan(new QueueRuntimeState(
            state.queueId(),
            QueuePhase.READY,
            state.waitingMembers(),
            state.readyMembers(),
            0L,
            state.readyAtEpochMs() <= 0L ? nowEpochMs : state.readyAtEpochMs(),
            nowEpochMs,
            nowEpochMs,
            launchError
        ).normalized());
    }

    @Nonnull
    public QueueLaunchOutcomePlan collapseInsufficientLiveReadyMembers(
        @Nonnull QueueRuntimeState currentState,
        @Nonnull QueueDefinition queue,
        @Nonnull List<QueueMemberState> liveReadyMembers,
        long nowEpochMs
    ) {
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
        return new QueueLaunchOutcomePlan(countdownPlanner.maybeStartCountdown(updated, queue, nowEpochMs));
    }

    @Nonnull
    public QueueLaunchOutcomePlan prepareReadyBatchLaunchAttempt(
        @Nonnull QueueRuntimeState currentState,
        @Nonnull List<QueueMemberState> liveReadyMembers,
        long nowEpochMs
    ) {
        return new QueueLaunchOutcomePlan(new QueueRuntimeState(
            currentState.queueId(),
            QueuePhase.READY,
            currentState.waitingMembers(),
            List.copyOf(liveReadyMembers),
            0L,
            currentState.readyAtEpochMs(),
            nowEpochMs,
            nowEpochMs,
            ""
        ).normalized());
    }

    @Nonnull
    public QueueLaunchOutcomePlan completeReadyBatchLaunch(
        @Nonnull QueueRuntimeState readyState,
        @Nonnull QueueDefinition queue,
        @Nonnull List<QueueMemberState> launchMembers,
        int launchedCount,
        boolean succeeded,
        long nowEpochMs
    ) {
        List<QueueMemberState> waitingMembers = new ArrayList<>(readyState.waitingMembers());
        if (!succeeded) {
            waitingMembers.addAll(unlaunchedMembers(launchMembers, launchedCount));
        }
        QueueRuntimeState updated = new QueueRuntimeState(
            readyState.queueId(),
            QueuePhase.WAITING,
            List.copyOf(waitingMembers),
            List.of(),
            0L,
            0L,
            nowEpochMs,
            0L,
            ""
        ).normalized();
        return new QueueLaunchOutcomePlan(countdownPlanner.maybeStartCountdown(updated, queue, nowEpochMs));
    }

    @Nonnull
    public QueueLaunchOutcomePlan prepareAssignmentLaunchAttempt(
        @Nonnull QueueRuntimeState currentState,
        @Nonnull List<QueueMemberState> assignmentMembers,
        long nowEpochMs
    ) {
        return new QueueLaunchOutcomePlan(new QueueRuntimeState(
            currentState.queueId(),
            QueuePhase.READY,
            removePlayers(currentState.waitingMembers(), assignmentMembers),
            List.copyOf(assignmentMembers),
            0L,
            nowEpochMs,
            nowEpochMs,
            nowEpochMs,
            ""
        ).normalized());
    }

    @Nonnull
    public QueueLaunchOutcomePlan completeAssignmentLaunch(
        @Nonnull QueueRuntimeState launchingState,
        @Nonnull List<QueueMemberState> assignmentMembers,
        int launchedCount,
        boolean succeeded,
        @Nonnull String rawError,
        long nowEpochMs
    ) {
        List<QueueMemberState> mergedWaiting = new ArrayList<>(launchingState.waitingMembers());
        mergedWaiting.addAll(unlaunchedMembers(assignmentMembers, launchedCount));
        QueueRuntimeState updated = new QueueRuntimeState(
            launchingState.queueId(),
            QueuePhase.WAITING,
            List.copyOf(mergedWaiting),
            List.of(),
            0L,
            0L,
            nowEpochMs,
            0L,
            succeeded ? "" : rawError
        ).normalized();
        return new QueueLaunchOutcomePlan(updated);
    }

    @Nonnull
    private static List<QueueMemberState> unlaunchedMembers(
        @Nonnull List<QueueMemberState> launchMembers,
        int launchedCount
    ) {
        int firstUnlaunchedIndex = Math.max(0, Math.min(launchedCount, launchMembers.size()));
        return List.copyOf(launchMembers.subList(firstUnlaunchedIndex, launchMembers.size()));
    }

    @Nonnull
    private static List<QueueMemberState> removePlayers(
        @Nonnull List<QueueMemberState> members,
        @Nonnull List<QueueMemberState> assignmentMembers
    ) {
        Set<UUID> assignedPlayerUuids = new HashSet<>();
        for (QueueMemberState member : assignmentMembers) {
            assignedPlayerUuids.add(member.playerUuid());
        }
        List<QueueMemberState> filtered = new ArrayList<>();
        for (QueueMemberState member : members) {
            if (!assignedPlayerUuids.contains(member.playerUuid())) {
                filtered.add(member);
            }
        }
        return List.copyOf(filtered);
    }
}
