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
import static org.junit.jupiter.api.Assertions.assertSame;

final class QueueCountdownPlannerTest {

    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_FOUR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final QueueCountdownPlanner planner = new QueueCountdownPlanner();

    @Test
    void backendDrivenQueueDoesNotStartLocalCountdown() {
        QueueRuntimeState state = waitingState(member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.maybeStartCountdown(
            state,
            queue(QueueMatchmakingMode.BACKEND_DRIVEN, 2, 4, 10),
            NOW
        );

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(NOW, result.lastStateChangeEpochMs());
        assertEquals(state.waitingMembers(), result.waitingMembers());
    }

    @Test
    void doesNotChangeReadyState() {
        QueueRuntimeState state = new QueueRuntimeState(
            "queue-one",
            QueuePhase.READY,
            List.of(member(PLAYER_THREE)),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            0L,
            NOW,
            NOW,
            0L,
            ""
        ).normalized();

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW + 1);

        assertSame(state, result);
    }

    @Test
    void doesNotChangeStateWithReadyBatch() {
        QueueRuntimeState state = new QueueRuntimeState(
            "queue-one",
            QueuePhase.WAITING,
            List.of(member(PLAYER_THREE)),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO)),
            0L,
            NOW,
            NOW,
            0L,
            ""
        ).normalized();

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW + 1);

        assertSame(state, result);
    }

    @Test
    void waitingBelowMinPlayersStaysWaitingWithoutCountdown() {
        QueueRuntimeState state = waitingState(member(PLAYER_ONE));

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW + 1);

        assertSame(state, result);
    }

    @Test
    void countdownBelowMinPlayersResetsToWaiting() {
        QueueRuntimeState state = countdownState(NOW + 10_000L, member(PLAYER_ONE));

        QueueRuntimeState result = planner.advanceCountdown(state, queue(), NOW);

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_ONE)), result.waitingMembers());
        assertEquals(NOW, result.lastStateChangeEpochMs());
    }

    @Test
    void maybeStartCountdownResetsCountdownBelowMinPlayers() {
        QueueRuntimeState state = countdownState(NOW + 10_000L, member(PLAYER_ONE));

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW);

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_ONE)), result.waitingMembers());
        assertEquals(NOW, result.lastStateChangeEpochMs());
    }

    @Test
    void startsCountdownWhenMinPlayersReached() {
        QueueRuntimeState state = waitingState(member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW);

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW + 10_000L, result.countdownEndsAtEpochMs());
        assertEquals(NOW, result.lastStateChangeEpochMs());
        assertEquals(state.waitingMembers(), result.waitingMembers());
    }

    @Test
    void doesNotRestartExistingCountdown() {
        QueueRuntimeState state = countdownState(NOW + 10_000L, member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW + 500L);

        assertSame(state, result);
    }

    @Test
    void countdownNotExpiredDoesNotAdvance() {
        QueueRuntimeState state = countdownState(NOW + 10_000L, member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.advanceCountdown(state, queue(), NOW + 999L);

        assertSame(state, result);
    }

    @Test
    void advanceCountdownDoesNotAdvanceBackendDrivenQueue() {
        QueueRuntimeState state = countdownState(NOW, member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.advanceCountdown(
            state,
            queue(QueueMatchmakingMode.BACKEND_DRIVEN, 2, 4, 10),
            NOW
        );

        assertSame(state, result);
        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void countdownExpiredMovesFirstMaxPlayersToReady() {
        QueueRuntimeState state = countdownState(NOW, member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE));

        QueueRuntimeState result = planner.advanceCountdown(state, queue(QueueMatchmakingMode.LOCAL_FIFO, 2, 2, 10), NOW);

        assertEquals(QueuePhase.READY, result.phase());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.readyMembers());
    }

    @Test
    void countdownExpiredLeavesRemainingPlayersWaiting() {
        QueueRuntimeState state = countdownState(NOW, member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE));

        QueueRuntimeState result = planner.advanceCountdown(state, queue(QueueMatchmakingMode.LOCAL_FIFO, 2, 2, 10), NOW);

        assertEquals(List.of(member(PLAYER_THREE)), result.waitingMembers());
    }

    @Test
    void countdownExpiredSetsReadyAtAndClearsCountdown() {
        QueueRuntimeState state = countdownState(NOW, member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.advanceCountdown(state, queue(), NOW);

        assertEquals(QueuePhase.READY, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(NOW, result.readyAtEpochMs());
        assertEquals(NOW, result.lastStateChangeEpochMs());
        assertEquals(0L, result.lastLaunchAttemptAtEpochMs());
        assertEquals("", result.lastLaunchError());
    }

    @Test
    void countdownExpiredWithMaxPlayersGreaterThanWaitingUsesAllWaiting() {
        QueueRuntimeState state = countdownState(NOW, member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.advanceCountdown(state, queue(QueueMatchmakingMode.LOCAL_FIFO, 2, 4, 10), NOW);

        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.readyMembers());
        assertEquals(List.of(), result.waitingMembers());
    }

    @Test
    void zeroSecondCountdownStartsCountdownEndingNow() {
        QueueRuntimeState state = waitingState(member(PLAYER_ONE), member(PLAYER_TWO));

        QueueRuntimeState result = planner.maybeStartCountdown(
            state,
            queue(QueueMatchmakingMode.LOCAL_FIFO, 2, 4, 0),
            NOW
        );

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW, result.countdownEndsAtEpochMs());
    }

    @Test
    void preservesLastLaunchFailureFieldsWhenStartingCountdownAccordingToCurrentBehavior() {
        QueueRuntimeState state = new QueueRuntimeState(
            "queue-one",
            QueuePhase.WAITING,
            List.of(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE), member(PLAYER_FOUR)),
            List.of(),
            0L,
            0L,
            NOW - 500L,
            NOW - 250L,
            "Previous launch failed"
        ).normalized();

        QueueRuntimeState result = planner.maybeStartCountdown(state, queue(), NOW);

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW - 250L, result.lastLaunchAttemptAtEpochMs());
        assertEquals("Previous launch failed", result.lastLaunchError());
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
            NOW,
            0L,
            ""
        ).normalized();
    }

    private static QueueRuntimeState countdownState(long countdownEndsAtEpochMs, QueueMemberState... members) {
        return new QueueRuntimeState(
            "queue-one",
            QueuePhase.COUNTDOWN,
            List.of(members),
            List.of(),
            countdownEndsAtEpochMs,
            0L,
            NOW,
            0L,
            ""
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
