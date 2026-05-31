package io.github.hyjn.nexori.plugin.minigame.transfer;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinigameTransferSessionTest {

    private static final UUID PLAYER_UUID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long NOW = 1_000L;

    @Test
    void newSessionStartsInWaitingForSafeReady() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);

        assertEquals(MinigameTransferPhase.WAITING_FOR_SAFE_READY, session.phase());
        assertEquals(PLAYER_UUID, session.playerUuid());
        assertEquals("alice", session.username());
        assertEquals(NOW, session.createdAtEpochMs());
    }

    @Test
    void newSessionIsNotTerminal() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);

        assertFalse(session.isTerminal());
    }

    @Test
    void confirmedSessionIsTerminal() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.CONFIRMED;

        assertTrue(session.isTerminal());
        assertTrue(session.isPlacementConfirmed());
    }

    @Test
    void failedSessionIsTerminal() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.FAILED;
        session.failureReason = MinigameTransferFailureReason.INVALID_LAUNCH_CONTEXT;

        assertTrue(session.isTerminal());
        assertFalse(session.isPlacementConfirmed());
        assertEquals(MinigameTransferFailureReason.INVALID_LAUNCH_CONTEXT, session.failureReason());
    }

    @Test
    void fallbackSessionIsTerminal() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.FALLBACK;

        assertTrue(session.isTerminal());
    }

    @Test
    void closedSessionIsTerminal() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.CLOSED;

        assertTrue(session.isTerminal());
    }

    @Test
    void acceptedByMinigamePhaseHasAcceptedLaunch() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.ACCEPTED_BY_MINIGAME;
        session.matchId = "match-1";

        assertTrue(session.hasAcceptedLaunch());
    }

    @Test
    void waitingForSafeReadyDoesNotHaveAcceptedLaunch() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);

        assertFalse(session.hasAcceptedLaunch());
    }

    @Test
    void failedPhaseDoesNotHaveAcceptedLaunch() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.FAILED;

        assertFalse(session.hasAcceptedLaunch());
    }

    @Test
    void teleportIssuedPhaseIsNotTerminalAndHasAcceptedLaunch() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.phase = MinigameTransferPhase.TELEPORT_ISSUED;

        assertFalse(session.isTerminal());
        assertTrue(session.hasAcceptedLaunch());
    }

    @Test
    void sessionMetadataIsReadableViaAccessors() {
        MinigameTransferSession session = new MinigameTransferSession(PLAYER_UUID, "alice", NOW);
        session.matchId = "match-abc";
        session.arenaId = "skywars";
        session.instanceTemplateId = "skywars_nexori_template";
        session.expectedWorldName = "match-abc_instance";

        assertEquals("match-abc", session.matchId());
        assertEquals("skywars", session.arenaId());
        assertEquals("skywars_nexori_template", session.instanceTemplateId());
        assertEquals("match-abc_instance", session.expectedWorldName());
        assertNull(session.failureReason());
    }
}
