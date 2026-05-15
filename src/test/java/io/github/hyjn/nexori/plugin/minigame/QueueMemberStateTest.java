package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueueMemberStateTest {

    private static final UUID UUID_A = UUID.fromString("00000000-0000-0000-0000-000000000001");

    // ── normalized: playerUuid ────────────────────────────────────────────────

    @Test
    void normalizedNullUuidThrows() {
        QueueMemberState m = new QueueMemberState(null, "Player1", "lobby-1", "", 1000L);
        assertThrows(IllegalArgumentException.class, m::normalized);
    }

    @Test
    void normalizedPreservesUuid() {
        QueueMemberState m = new QueueMemberState(UUID_A, "Player1", "lobby-1", "", 1000L).normalized();
        assertEquals(UUID_A, m.playerUuid());
    }

    // ── normalized: playerNameSnapshot ───────────────────────────────────────

    @Test
    void normalizedNullPlayerNameDefaultsToUuidString() {
        QueueMemberState m = new QueueMemberState(UUID_A, null, "lobby-1", "", 1000L).normalized();
        assertEquals(UUID_A.toString(), m.playerNameSnapshot());
    }

    @Test
    void normalizedBlankPlayerNameDefaultsToUuidString() {
        QueueMemberState m = new QueueMemberState(UUID_A, "   ", "lobby-1", "", 1000L).normalized();
        assertEquals(UUID_A.toString(), m.playerNameSnapshot());
    }

    @Test
    void normalizedTrimsPlayerNameButPreservesCase() {
        QueueMemberState m = new QueueMemberState(UUID_A, "  Steve  ", "lobby-1", "", 1000L).normalized();
        assertEquals("Steve", m.playerNameSnapshot());
    }

    // ── normalized: sourceLobbyId ─────────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesSourceLobbyId() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "  Lobby-1  ", "", 1000L).normalized();
        assertEquals("lobby-1", m.sourceLobbyId());
    }

    @Test
    void normalizedBlankSourceLobbyIdThrows() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "  ", "", 1000L);
        assertThrows(IllegalArgumentException.class, m::normalized);
    }

    @Test
    void normalizedNullSourceLobbyIdThrows() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", null, "", 1000L);
        assertThrows(IllegalArgumentException.class, m::normalized);
    }

    // ── normalized: sourcePortalId ────────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesSourcePortalId() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "lobby-1", "  Portal-A  ", 1000L).normalized();
        assertEquals("portal-a", m.sourcePortalId());
    }

    @Test
    void normalizedNullSourcePortalIdBecomesEmpty() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "lobby-1", null, 1000L).normalized();
        assertEquals("", m.sourcePortalId());
    }

    // ── normalized: joinedAtEpochMs ───────────────────────────────────────────

    @Test
    void normalizedPreservesPositiveJoinedAt() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "lobby-1", "", 5000L).normalized();
        assertEquals(5000L, m.joinedAtEpochMs());
    }

    @Test
    void normalizedZeroJoinedAtUsesCurrentTimeAccordingToCurrentBehavior() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "lobby-1", "", 0L).normalized();
        assertTrue(m.joinedAtEpochMs() > 0L,
            "joinedAtEpochMs <= 0 is replaced by System.currentTimeMillis()");
    }

    @Test
    void normalizedNegativeJoinedAtUsesCurrentTimeAccordingToCurrentBehavior() {
        QueueMemberState m = new QueueMemberState(UUID_A, "P", "lobby-1", "", -1L).normalized();
        assertTrue(m.joinedAtEpochMs() > 0L,
            "Negative joinedAtEpochMs is replaced by System.currentTimeMillis()");
    }
}
