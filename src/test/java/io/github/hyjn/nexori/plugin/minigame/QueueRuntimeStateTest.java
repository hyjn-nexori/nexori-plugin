package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueueRuntimeStateTest {

    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static QueueMemberState member() {
        return new QueueMemberState(UUID_A, "Player", "lobby-1", "", 1000L);
    }

    private static QueueRuntimeState state(
        String queueId, QueuePhase phase,
        List<QueueMemberState> waiting, List<QueueMemberState> ready
    ) {
        return new QueueRuntimeState(
            queueId, phase, waiting, ready,
            5000L, 3000L, 1000L, 0L, ""
        );
    }

    // ── normalized: queueId ───────────────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesQueueId() {
        QueueRuntimeState s = state("  SkyWarsQueue  ", QueuePhase.WAITING, List.of(), List.of()).normalized();
        assertEquals("skywarsqueue", s.queueId());
    }

    // ── normalized: phase ─────────────────────────────────────────────────────

    @Test
    void normalizedNullPhaseDefaultsToWaiting() {
        QueueRuntimeState s = state("queue-1", null, List.of(), List.of()).normalized();
        assertEquals(QueuePhase.WAITING, s.phase());
    }

    @Test
    void normalizedPreservesExplicitPhase() {
        QueueRuntimeState s = state("queue-1", QueuePhase.COUNTDOWN, List.of(), List.of()).normalized();
        assertEquals(QueuePhase.COUNTDOWN, s.phase());
    }

    // ── normalized: members ───────────────────────────────────────────────────

    @Test
    void normalizedNullWaitingMembersBecomesEmptyList() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, null, List.of(),
            0L, 0L, 1000L, 0L, "").normalized();
        assertEquals(0, s.waitingMembers().size());
    }

    @Test
    void normalizedSkipsNullMembersAccordingToCurrentBehavior() {
        List<QueueMemberState> withNull = new java.util.ArrayList<>();
        withNull.add(null);
        withNull.add(member());
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, withNull, List.of(),
            0L, 0L, 1000L, 0L, "").normalized();
        assertEquals(1, s.waitingMembers().size());
    }

    @Test
    void normalizedNormalizesEachMember() {
        QueueMemberState raw = new QueueMemberState(UUID_A, "  Steve  ", "  lobby-1  ", "", 1000L);
        QueueRuntimeState s = state("queue-1", QueuePhase.WAITING, List.of(raw), List.of()).normalized();
        assertEquals("Steve", s.waitingMembers().get(0).playerNameSnapshot());
        assertEquals("lobby-1", s.waitingMembers().get(0).sourceLobbyId());
    }

    // ── normalized: timestamps ────────────────────────────────────────────────

    @Test
    void normalizedClampsNegativeCountdownEndsAtToZero() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, List.of(), List.of(),
            -100L, 0L, 1000L, 0L, "").normalized();
        assertEquals(0L, s.countdownEndsAtEpochMs());
    }

    @Test
    void normalizedClampsNegativeLastLaunchAttemptToZero() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, List.of(), List.of(),
            0L, 0L, 1000L, -500L, "").normalized();
        assertEquals(0L, s.lastLaunchAttemptAtEpochMs());
    }

    @Test
    void normalizedZeroLastStateChangeUsesCurrentTimeAccordingToCurrentBehavior() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, List.of(), List.of(),
            0L, 0L, 0L, 0L, "").normalized();
        assertTrue(s.lastStateChangeEpochMs() > 0L,
            "lastStateChangeEpochMs <= 0 replaced by System.currentTimeMillis()");
    }

    @Test
    void normalizedPositiveLastStateChangePreserved() {
        QueueRuntimeState s = state("queue-1", QueuePhase.WAITING, List.of(), List.of()).normalized();
        assertEquals(1000L, s.lastStateChangeEpochMs());
    }

    // ── normalized: lastLaunchError ───────────────────────────────────────────

    @Test
    void normalizedNullLastLaunchErrorBecomesEmpty() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, List.of(), List.of(),
            0L, 0L, 1000L, 0L, null).normalized();
        assertEquals("", s.lastLaunchError());
    }

    @Test
    void normalizedTrimsLastLaunchError() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, List.of(), List.of(),
            0L, 0L, 1000L, 0L, "  some error  ").normalized();
        assertEquals("some error", s.lastLaunchError());
    }

    // ── empty ─────────────────────────────────────────────────────────────────

    @Test
    void emptyCreatesWaitingStateWithNowTimestamp() {
        long before = System.currentTimeMillis();
        QueueRuntimeState s = QueueRuntimeState.empty("queue-1", before);
        assertEquals("queue-1", s.queueId());
        assertEquals(QueuePhase.WAITING, s.phase());
        assertTrue(s.waitingMembers().isEmpty());
        assertTrue(s.readyMembers().isEmpty());
        assertEquals(before, s.lastStateChangeEpochMs());
        assertEquals(0L, s.countdownEndsAtEpochMs());
    }

    // ── queuedPlayerCount ─────────────────────────────────────────────────────

    @Test
    void queuedPlayerCountSumsWaitingAndReady() {
        QueueMemberState m = member();
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.WAITING, List.of(m, m), List.of(m),
            0L, 0L, 1000L, 0L, "").normalized();
        assertEquals(3, s.queuedPlayerCount());
    }

    @Test
    void queuedPlayerCountZeroWhenBothListsEmpty() {
        QueueRuntimeState s = state("queue-1", QueuePhase.WAITING, List.of(), List.of()).normalized();
        assertEquals(0, s.queuedPlayerCount());
    }

    // ── hasReadyBatch ─────────────────────────────────────────────────────────

    @Test
    void hasReadyBatchFalseWhenReadyMembersEmpty() {
        QueueRuntimeState s = state("queue-1", QueuePhase.WAITING, List.of(), List.of()).normalized();
        assertFalse(s.hasReadyBatch());
    }

    @Test
    void hasReadyBatchTrueWhenReadyMembersNotEmpty() {
        QueueRuntimeState s = new QueueRuntimeState(
            "queue-1", QueuePhase.READY, List.of(), List.of(member()),
            0L, 1000L, 1000L, 0L, "").normalized();
        assertTrue(s.hasReadyBatch());
    }
}
