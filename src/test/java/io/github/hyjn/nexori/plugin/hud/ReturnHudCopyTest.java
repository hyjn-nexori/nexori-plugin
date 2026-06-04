package io.github.hyjn.nexori.plugin.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class ReturnHudCopyTest {

    @Test
    void initialPlacementWindowMissedShowsExpiredTitle() {
        ReturnHudCopy.Copy copy = ReturnHudCopy.resolve(
            "No Contest",
            ReturnHudCopy.REASON_INITIAL_PLACEMENT_WINDOW_MISSED
        );
        assertEquals("MATCH START WINDOW EXPIRED", copy.title());
        assertEquals("You did not join in time. Returning to lobby...", copy.subtitle());
    }

    @Test
    void shortfallShowsNotEnoughPlayersTitle() {
        ReturnHudCopy.Copy copy = ReturnHudCopy.resolve(
            "No Contest",
            ReturnHudCopy.REASON_NOT_ENOUGH_PLAYERS
        );
        assertEquals("NOT ENOUGH PLAYERS", copy.title());
        assertEquals("Returning to lobby...", copy.subtitle());
    }

    @Test
    void noContestWithoutKnownReasonShowsGenericCancelled() {
        assertEquals("MATCH CANCELLED", ReturnHudCopy.resolve("No Contest", "").title());
        assertEquals("MATCH CANCELLED", ReturnHudCopy.resolve("No Contest", null).title());
        assertEquals("MATCH CANCELLED", ReturnHudCopy.resolve("No Contest", "UNKNOWN_CANCEL").title());
        assertEquals("MATCH CANCELLED", ReturnHudCopy.resolve("No Contest", "SOME_OTHER_REASON").title());
    }

    @Test
    void victoryAndEliminatedAndCompleteUnaffectedByReason() {
        assertEquals("VICTORY", ReturnHudCopy.resolve("Victory",
            ReturnHudCopy.REASON_INITIAL_PLACEMENT_WINDOW_MISSED).title());
        assertEquals("ELIMINATED", ReturnHudCopy.resolve("Eliminated", "").title());
        assertEquals("MATCH COMPLETE", ReturnHudCopy.resolve("Match Complete", "").title());
        assertEquals("MATCH COMPLETE", ReturnHudCopy.resolve("", "").title());
    }
}
