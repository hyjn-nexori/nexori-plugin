package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueuePhase;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pure planner for queue join/leave membership state transitions.
 */
public final class QueueMembershipPlanner {

    private final QueueCountdownPlanner countdownPlanner = new QueueCountdownPlanner();

    @Nonnull
    public QueueMembershipPlan join(
        @Nonnull QueueRuntimeState currentState,
        @Nonnull QueueDefinition queue,
        @Nonnull UUID playerUuid,
        @Nonnull String playerName,
        @Nonnull String sourceLobbyId,
        @Nonnull String sourcePortalId,
        long nowEpochMs
    ) {
        QueueMemberState member = new QueueMemberState(playerUuid, playerName, sourceLobbyId, sourcePortalId, nowEpochMs).normalized();
        List<QueueMemberState> waitingMembers = new ArrayList<>(currentState.waitingMembers());
        waitingMembers.add(member);
        QueueRuntimeState updated = new QueueRuntimeState(
            currentState.queueId(),
            currentState.phase(),
            List.copyOf(waitingMembers),
            currentState.readyMembers(),
            currentState.countdownEndsAtEpochMs(),
            currentState.readyAtEpochMs(),
            nowEpochMs,
            currentState.lastLaunchAttemptAtEpochMs(),
            currentState.lastLaunchError()
        ).normalized();
        return new QueueMembershipPlan(true, countdownPlanner.maybeStartCountdown(updated, queue, nowEpochMs));
    }

    @Nonnull
    public QueueMembershipPlan remove(
        @Nonnull QueueRuntimeState currentState,
        @Nonnull QueueDefinition queue,
        @Nonnull UUID playerUuid,
        long nowEpochMs
    ) {
        List<QueueMemberState> waitingMembers = removePlayer(currentState.waitingMembers(), playerUuid);
        List<QueueMemberState> readyMembers = removePlayer(currentState.readyMembers(), playerUuid);
        boolean removed = waitingMembers.size() != currentState.waitingMembers().size()
            || readyMembers.size() != currentState.readyMembers().size();
        QueueRuntimeState updated = new QueueRuntimeState(
            currentState.queueId(),
            currentState.phase(),
            waitingMembers,
            readyMembers,
            currentState.countdownEndsAtEpochMs(),
            currentState.readyAtEpochMs(),
            nowEpochMs,
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
                nowEpochMs,
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
                nowEpochMs,
                updated.lastLaunchAttemptAtEpochMs(),
                updated.lastLaunchError()
            ).normalized();
        }

        return new QueueMembershipPlan(removed, countdownPlanner.maybeStartCountdown(updated, queue, nowEpochMs));
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
}
