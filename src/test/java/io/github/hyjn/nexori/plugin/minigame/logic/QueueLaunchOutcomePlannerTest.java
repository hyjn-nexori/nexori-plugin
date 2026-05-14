package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueuePhase;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class QueueLaunchOutcomePlannerTest {

    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_FOUR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final QueueLaunchOutcomePlanner planner = new QueueLaunchOutcomePlanner();

    @Test
    void successfulReadyBatchLaunchClearsReadyMembers() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            2,
            true,
            NOW
        ).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void successfulReadyBatchLaunchPreservesWaitingMembers() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            2,
            true,
            NOW
        ).state();

        assertEquals(List.of(member(PLAYER_THREE)), result.waitingMembers());
    }

    @Test
    void successfulReadyBatchLaunchClearsLaunchFailureFieldsIfCurrentBehaviorDoesThat() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            2,
            true,
            NOW
        ).state();

        assertEquals(0L, result.lastLaunchAttemptAtEpochMs());
        assertEquals("", result.lastLaunchError());
    }

    @Test
    void successfulReadyBatchLaunchAppliesMaybeStartCountdownForRemainingWaitingIfCurrentBehaviorDoesThat() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE), member(PLAYER_FOUR)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            2,
            true,
            NOW
        ).state();

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW + 10_000L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_FOUR)), result.waitingMembers());
    }

    @Test
    void failedReadyBatchLaunchRestoresUnlaunchedReadyMembersToWaitingAccordingToCurrentBehavior() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            0,
            false,
            NOW
        ).state();

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_ONE), member(PLAYER_TWO)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void partialReadyBatchLaunchReturnsOnlyUnlaunchedMembersToWaiting() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            1,
            false,
            NOW
        ).state();

        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_TWO)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void failedReadyBatchLaunchSetsLastLaunchAttemptAtEpochMs() {
        QueueRuntimeState result = planner.rememberLaunchFailure(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            NOW,
            " launch failed "
        ).state();

        assertEquals(NOW, result.lastLaunchAttemptAtEpochMs());
        assertEquals(NOW, result.lastStateChangeEpochMs());
    }

    @Test
    void failedReadyBatchLaunchStoresLastLaunchError() {
        QueueRuntimeState result = planner.rememberLaunchFailure(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            NOW,
            " launch failed "
        ).state();

        assertEquals("launch failed", result.lastLaunchError());
    }

    @Test
    void rememberLaunchFailureUsesDefaultMessageWhenBlank() {
        QueueRuntimeState state = readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO)));

        QueueRuntimeState result = planner.rememberLaunchFailure(state, NOW, "   ").state();

        assertEquals("Unknown queue launch failure.", result.lastLaunchError());
        assertEquals(QueuePhase.READY, result.phase());
        assertEquals(state.readyMembers(), result.readyMembers());
    }

    @Test
    void failedReadyBatchLaunchDoesNotDropWaitingMembers() {
        QueueRuntimeState result = planner.rememberLaunchFailure(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            NOW,
            "launch failed"
        ).state();

        assertEquals(List.of(member(PLAYER_THREE)), result.waitingMembers());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.readyMembers());
    }

    @Test
    void emptyReadyBatchNoOpsAccordingToCurrentBehavior() {
        QueueRuntimeState state = waitingState(member(PLAYER_ONE));

        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            state,
            queue(),
            List.of(),
            0,
            true,
            NOW
        ).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(List.of(member(PLAYER_ONE)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void backendDrivenQueueBehaviorIsPreservedAccordingToCurrentBehavior() {
        QueueRuntimeState result = planner.completeReadyBatchLaunch(
            readyState(List.of(member(PLAYER_THREE), member(PLAYER_FOUR)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(QueueMatchmakingMode.BACKEND_DRIVEN, 2, 4, 10),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            2,
            true,
            NOW
        ).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_FOUR)), result.waitingMembers());
    }

    @Test
    void collapseInsufficientLiveReadyMembersAppendsLiveReadyAfterExistingWaiting() {
        QueueRuntimeState result = planner.collapseInsufficientLiveReadyMembers(
            readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))),
            queue(QueueMatchmakingMode.LOCAL_FIFO, 2, 4, 10),
            List.of(member(PLAYER_ONE)),
            NOW
        ).state();

        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_ONE)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
        assertEquals(QueuePhase.COUNTDOWN, result.phase());
    }

    @Test
    void prepareAssignmentLaunchRemovesAssignmentMembersFromWaiting() {
        QueueRuntimeState result = planner.prepareAssignmentLaunchAttempt(
            waitingState(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE)),
            List.of(member(PLAYER_ONE), member(PLAYER_THREE)),
            NOW
        ).state();

        assertEquals(QueuePhase.READY, result.phase());
        assertEquals(List.of(member(PLAYER_TWO)), result.waitingMembers());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_THREE)), result.readyMembers());
    }

    @Test
    void assignmentLaunchSuccessDoesNotApplyMaybeStartCountdownAccordingToCurrentBehavior() {
        QueueRuntimeState launchingState = readyState(
            List.of(member(PLAYER_THREE), member(PLAYER_FOUR)),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO))
        );

        QueueRuntimeState result = planner.completeAssignmentLaunch(
            launchingState,
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            2,
            true,
            "",
            NOW
        ).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_FOUR)), result.waitingMembers());
    }

    @Test
    void partialAssignmentLaunchStoresErrorAndReturnsUnlaunchedMembersToWaiting() {
        QueueRuntimeState launchingState = readyState(
            List.of(member(PLAYER_THREE)),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO))
        );

        QueueRuntimeState result = planner.completeAssignmentLaunch(
            launchingState,
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            1,
            false,
            "partial failure",
            NOW
        ).state();

        assertEquals(List.of(member(PLAYER_THREE), member(PLAYER_TWO)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
        assertEquals("partial failure", result.lastLaunchError());
    }

    private static QueueDefinition queue() {
        return queue(QueueMatchmakingMode.LOCAL_FIFO, 2, 4, 10);
    }

    private static QueueDefinition queue(
        QueueMatchmakingMode matchmakingMode,
        int minPlayers,
        int maxPlayers,
        int countdownSeconds
    ) {
        return new QueueDefinition(
            "queue-one",
            "Queue One",
            List.of("arena-one"),
            minPlayers,
            maxPlayers,
            countdownSeconds,
            "keep_inventory",
            matchmakingMode.id(),
            true
        );
    }

    private static QueueRuntimeState waitingState(QueueMemberState... members) {
        return new QueueRuntimeState(
            "queue-one",
            QueuePhase.WAITING,
            List.of(members),
            List.of(),
            0L,
            0L,
            NOW - 500L,
            0L,
            ""
        ).normalized();
    }

    private static QueueRuntimeState readyState(List<QueueMemberState> waitingMembers, List<QueueMemberState> readyMembers) {
        return new QueueRuntimeState(
            "queue-one",
            QueuePhase.READY,
            waitingMembers,
            readyMembers,
            0L,
            NOW - 250L,
            NOW - 500L,
            NOW - 100L,
            "previous failure"
        ).normalized();
    }

    private static QueueMemberState member(UUID playerUuid) {
        return new QueueMemberState(
            playerUuid,
            "Player " + playerUuid.toString().substring(0, 4),
            "lobby-one",
            "portal-one",
            NOW
        ).normalized();
    }
}
