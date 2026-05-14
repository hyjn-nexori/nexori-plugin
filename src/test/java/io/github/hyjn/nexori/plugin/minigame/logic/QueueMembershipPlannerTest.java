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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueueMembershipPlannerTest {

    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PLAYER_FOUR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final QueueMembershipPlanner planner = new QueueMembershipPlanner();

    @Test
    void joinAddsMemberToWaitingMembers() {
        QueueRuntimeState result = join(waitingState(member(PLAYER_ONE)), PLAYER_TWO).state();

        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.waitingMembers());
    }

    @Test
    void joinNormalizesMemberFields() {
        QueueMembershipPlan plan = planner.join(
            waitingState(),
            queue(QueueMatchmakingMode.LOCAL_FIFO, 3, 4, 10),
            PLAYER_ONE,
            " Player One ",
            " Lobby-One ",
            " Portal-One ",
            NOW
        );

        QueueMemberState member = plan.state().waitingMembers().get(0);
        assertEquals("Player One", member.playerNameSnapshot());
        assertEquals("lobby-one", member.sourceLobbyId());
        assertEquals("portal-one", member.sourcePortalId());
        assertEquals(NOW, member.joinedAtEpochMs());
    }

    @Test
    void joinPreservesExistingWaitingOrder() {
        QueueRuntimeState result = join(waitingState(member(PLAYER_ONE), member(PLAYER_TWO)), PLAYER_THREE).state();

        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE)), result.waitingMembers());
    }

    @Test
    void joinStartsCountdownWhenMinPlayersReached() {
        QueueRuntimeState result = join(waitingState(member(PLAYER_ONE)), PLAYER_TWO).state();

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW + 10_000L, result.countdownEndsAtEpochMs());
        assertEquals(NOW, result.lastStateChangeEpochMs());
    }

    @Test
    void joinBackendDrivenQueueDoesNotStartCountdown() {
        QueueRuntimeState result = planner.join(
            waitingState(member(PLAYER_ONE)),
            queue(QueueMatchmakingMode.BACKEND_DRIVEN, 2, 4, 10),
            PLAYER_TWO,
            "Player Two",
            "lobby-one",
            "portal-one",
            NOW
        ).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO, "Player Two")), result.waitingMembers());
    }

    @Test
    void joinWithExistingReadyBatchAddsToWaitingWithoutChangingReadyBatch() {
        QueueRuntimeState state = readyState(List.of(), List.of(member(PLAYER_ONE), member(PLAYER_TWO)));

        QueueRuntimeState result = join(state, PLAYER_THREE).state();

        assertEquals(QueuePhase.READY, result.phase());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.readyMembers());
        assertEquals(List.of(member(PLAYER_THREE)), result.waitingMembers());
        assertEquals(state.readyAtEpochMs(), result.readyAtEpochMs());
    }

    @Test
    void joinPreservesLastLaunchFailureFieldsWhenCountdownDoesNotStart() {
        QueueRuntimeState state = new QueueRuntimeState(
            "queue-one",
            QueuePhase.WAITING,
            List.of(member(PLAYER_ONE)),
            List.of(),
            0L,
            0L,
            NOW - 500L,
            NOW - 250L,
            "Previous launch failed"
        ).normalized();

        QueueRuntimeState result = planner.join(
            state,
            queue(QueueMatchmakingMode.LOCAL_FIFO, 3, 4, 10),
            PLAYER_TWO,
            "Player Two",
            "lobby-one",
            "portal-one",
            NOW
        ).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(NOW - 250L, result.lastLaunchAttemptAtEpochMs());
        assertEquals("Previous launch failed", result.lastLaunchError());
    }

    @Test
    void removeFromWaitingRemovesOnlyThatPlayer() {
        QueueRuntimeState result = remove(waitingState(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE)), PLAYER_TWO).state();

        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_THREE)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void removeFromReadyRemovesOnlyThatPlayer() {
        QueueRuntimeState result = remove(
            readyState(List.of(member(PLAYER_FOUR)), List.of(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE))),
            PLAYER_THREE
        ).state();

        assertEquals(List.of(member(PLAYER_FOUR)), result.waitingMembers());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.readyMembers());
    }

    @Test
    void removeReadyBatchBelowMinPlayersMergesReadyBeforeWaiting() {
        QueueRuntimeState result = remove(readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))), PLAYER_TWO).state();

        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_THREE)), result.waitingMembers());
        assertEquals(List.of(), result.readyMembers());
    }

    @Test
    void removeReadyBatchBelowMinPlayersResetsReadyState() {
        QueueRuntimeState result = remove(readyState(List.of(member(PLAYER_THREE)), List.of(member(PLAYER_ONE), member(PLAYER_TWO))), PLAYER_TWO).state();

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW + 10_000L, result.countdownEndsAtEpochMs());
        assertEquals(0L, result.readyAtEpochMs());
        assertEquals(0L, result.lastLaunchAttemptAtEpochMs());
        assertEquals("", result.lastLaunchError());
    }

    @Test
    void removeReadyBatchStillAtMinPlayersKeepsReadyState() {
        QueueRuntimeState state = readyState(
            List.of(member(PLAYER_FOUR)),
            List.of(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE))
        );

        QueueRuntimeState result = remove(state, PLAYER_THREE).state();

        assertEquals(QueuePhase.READY, result.phase());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.readyMembers());
        assertEquals(List.of(member(PLAYER_FOUR)), result.waitingMembers());
        assertEquals(state.readyAtEpochMs(), result.readyAtEpochMs());
        assertEquals(0L, result.countdownEndsAtEpochMs());
    }

    @Test
    void removeCountdownBelowMinPlayersResetsToWaiting() {
        QueueRuntimeState result = remove(countdownState(member(PLAYER_ONE), member(PLAYER_TWO)), PLAYER_TWO).state();

        assertEquals(QueuePhase.WAITING, result.phase());
        assertEquals(0L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_ONE)), result.waitingMembers());
    }

    @Test
    void removeCountdownStillAtMinPlayersKeepsCountdown() {
        QueueRuntimeState result = remove(countdownState(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE)), PLAYER_THREE).state();

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW + 5_000L, result.countdownEndsAtEpochMs());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), result.waitingMembers());
    }

    @Test
    void removeAppliesMaybeStartCountdownAfterRemoval() {
        QueueRuntimeState result = remove(waitingState(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE)), PLAYER_THREE).state();

        assertEquals(QueuePhase.COUNTDOWN, result.phase());
        assertEquals(NOW + 10_000L, result.countdownEndsAtEpochMs());
    }

    @Test
    void removePreservesWaitingOrder() {
        QueueRuntimeState result = remove(waitingState(member(PLAYER_ONE), member(PLAYER_TWO), member(PLAYER_THREE), member(PLAYER_FOUR)), PLAYER_TWO).state();

        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_THREE), member(PLAYER_FOUR)), result.waitingMembers());
    }

    @Test
    void removeAbsentPlayerPreservesMembersAndReportsNotRemoved() {
        QueueRuntimeState state = waitingState(member(PLAYER_ONE), member(PLAYER_TWO));

        QueueMembershipPlan plan = remove(state, PLAYER_THREE);

        assertFalse(plan.removed());
        assertEquals(List.of(member(PLAYER_ONE), member(PLAYER_TWO)), plan.state().waitingMembers());
    }

    @Test
    void removeReportsRemovedWhenPlayerWasPresent() {
        QueueMembershipPlan plan = remove(waitingState(member(PLAYER_ONE), member(PLAYER_TWO)), PLAYER_TWO);

        assertTrue(plan.removed());
    }

    private QueueMembershipPlan join(QueueRuntimeState state, UUID playerUuid) {
        return planner.join(
            state,
            queue(),
            playerUuid,
            "Player " + playerUuid.toString().substring(0, 4),
            "lobby-one",
            "portal-one",
            NOW
        );
    }

    private QueueMembershipPlan remove(QueueRuntimeState state, UUID playerUuid) {
        return planner.remove(state, queue(), playerUuid, NOW);
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

    private static QueueRuntimeState countdownState(QueueMemberState... members) {
        return new QueueRuntimeState(
            "queue-one",
            QueuePhase.COUNTDOWN,
            List.of(members),
            List.of(),
            NOW + 5_000L,
            0L,
            NOW,
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
            NOW - 500L,
            NOW - 500L,
            NOW - 250L,
            "Previous launch failed"
        ).normalized();
    }

    private static QueueMemberState member(UUID playerUuid) {
        return member(playerUuid, "Player " + playerUuid.toString().substring(0, 4));
    }

    private static QueueMemberState member(UUID playerUuid, String playerName) {
        return new QueueMemberState(
            playerUuid,
            playerName,
            "lobby-one",
            "portal-one",
            NOW
        ).normalized();
    }
}
