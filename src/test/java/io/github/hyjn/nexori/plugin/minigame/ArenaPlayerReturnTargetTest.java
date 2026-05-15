package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class ArenaPlayerReturnTargetTest {

    private static ArenaPlayerReturnTarget target() {
        return new ArenaPlayerReturnTarget("lobby-1", "srv.example.com:25565", "hub-1", "default-travel");
    }

    // ── normalized: originLobbyId ─────────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesOriginLobbyId() {
        ArenaPlayerReturnTarget t = new ArenaPlayerReturnTarget(
            "  Lobby-1  ", "srv:1", "hub", "profile").normalized();
        assertEquals("lobby-1", t.originLobbyId());
    }

    @Test
    void normalizedBlankOriginLobbyIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaPlayerReturnTarget("  ", "srv:1", "hub", "profile").normalized());
    }

    @Test
    void normalizedNullOriginLobbyIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaPlayerReturnTarget(null, "srv:1", "hub", "profile").normalized());
    }

    // ── normalized: returnConnectionAddress ───────────────────────────────────

    @Test
    void normalizedTrimsReturnConnectionAddress() {
        ArenaPlayerReturnTarget t = new ArenaPlayerReturnTarget(
            "lobby-1", "  srv.example.com:25565  ", "hub", "profile").normalized();
        assertEquals("srv.example.com:25565", t.returnConnectionAddress());
    }

    @Test
    void normalizedBlankReturnConnectionAddressThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaPlayerReturnTarget("lobby-1", "  ", "hub", "profile").normalized());
    }

    @Test
    void normalizedNullReturnConnectionAddressThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaPlayerReturnTarget("lobby-1", null, "hub", "profile").normalized());
    }

    // ── normalized: returnFallbackTargetId ───────────────────────────────────

    @Test
    void normalizedTrimsReturnFallbackTargetId() {
        ArenaPlayerReturnTarget t = new ArenaPlayerReturnTarget(
            "lobby-1", "srv:1", "  hub-2  ", "profile").normalized();
        assertEquals("hub-2", t.returnFallbackTargetId());
    }

    @Test
    void normalizedBlankReturnFallbackTargetIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaPlayerReturnTarget("lobby-1", "srv:1", "  ", "profile").normalized());
    }

    // ── normalized: launchTravelProfileId ────────────────────────────────────

    @Test
    void normalizedTrimsLaunchTravelProfileId() {
        ArenaPlayerReturnTarget t = new ArenaPlayerReturnTarget(
            "lobby-1", "srv:1", "hub", "  default-travel  ").normalized();
        assertEquals("default-travel", t.launchTravelProfileId());
    }

    @Test
    void normalizedBlankLaunchTravelProfileIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaPlayerReturnTarget("lobby-1", "srv:1", "hub", "   ").normalized());
    }

    // ── round-trip ────────────────────────────────────────────────────────────

    @Test
    void normalizedPreservesAllFieldsWhenAlreadyClean() {
        ArenaPlayerReturnTarget t = target().normalized();
        assertEquals("lobby-1", t.originLobbyId());
        assertEquals("srv.example.com:25565", t.returnConnectionAddress());
        assertEquals("hub-1", t.returnFallbackTargetId());
        assertEquals("default-travel", t.launchTravelProfileId());
    }
}
