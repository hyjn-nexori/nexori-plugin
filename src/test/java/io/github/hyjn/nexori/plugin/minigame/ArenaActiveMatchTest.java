package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArenaActiveMatchTest {

    private static final long CREATED_AT = 500L;
    private static final long NOW = 1_000L;
    private static final long CLOSED_AT = 1_500L;

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BACKFILL_PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void withPlayerArrivalAddsPlayerToArrivedAndActive() {
        ArenaActiveMatch match = baseMatch();

        ArenaActiveMatch updated = match.withPlayerArrival(PLAYER_ONE, NOW);

        assertTrue(updated.arrivedPlayerUuids().contains(PLAYER_ONE));
        assertTrue(updated.activePlayerUuids().contains(PLAYER_ONE));
        assertEquals(NOW, updated.lastUpdatedAtEpochMs());
    }

    @Test
    void arrivedInitialPlayerCountIgnoresBackfillPlayers() {
        ArenaActiveMatch updated = baseMatch()
            .withPlayerArrival(PLAYER_ONE, NOW)
            .withPlayerArrival(BACKFILL_PLAYER, NOW);

        assertEquals(1, updated.arrivedInitialPlayerCount());
        assertFalse(updated.allExpectedPlayersArrived());
    }

    @Test
    void allExpectedPlayersArrivedRequiresExpectedCount() {
        ArenaActiveMatch updated = baseMatch()
            .withPlayerArrival(PLAYER_ONE, NOW)
            .withPlayerArrival(PLAYER_TWO, NOW);

        assertTrue(updated.allExpectedPlayersArrived());
    }

    @Test
    void acceptedBackfillReservationIsNormalizedAndNotDuplicated() {
        ArenaActiveMatch updated = baseMatch()
            .withAcceptedBackfillReservation(" reservation-1 ", NOW);

        assertTrue(updated.hasAcceptedBackfillReservation("reservation-1"));

        ArenaActiveMatch replayed = updated.withAcceptedBackfillReservation("reservation-1", NOW);

        assertEquals(1, replayed.acceptedBackfillReservationIds().size());
    }

    @Test
    void consumedBackfillAdmissionCountIncrements() {
        ArenaActiveMatch match = baseMatch();

        ArenaActiveMatch once = match.withConsumedBackfillAdmissionIncrement(NOW);
        ArenaActiveMatch twice = once.withConsumedBackfillAdmissionIncrement(NOW);

        assertEquals(1, once.consumedBackfillAdmissionCount());
        assertEquals(2, twice.consumedBackfillAdmissionCount());
    }

    @Test
    void explicitAdmissionClosedStoresReasonMessageAndTimestamp() {
        ArenaActiveMatch updated = baseMatch()
            .withExplicitAdmissionClosed("manual", "closed by test", CLOSED_AT, NOW);

        assertTrue(updated.explicitAdmissionClosed());
        assertEquals("manual", updated.explicitAdmissionCloseReason());
        assertEquals("closed by test", updated.explicitAdmissionCloseMessage());
        assertEquals(CLOSED_AT, updated.explicitAdmissionClosedAtEpochMs());
    }

    @Test
    void withoutReturnedPlayerPreservesAssignmentIdHistory() {
        ArenaActiveMatch updated = baseMatch()
            .withPlayerAssignmentId(PLAYER_ONE, "assignment-1", NOW)
            .withPlayerArrival(PLAYER_ONE, NOW)
            .withoutReturnedPlayer(PLAYER_ONE, NOW + 1);

        assertFalse(updated.activePlayerUuids().contains(PLAYER_ONE));
        assertEquals("assignment-1", updated.assignmentIdsByPlayerUuid().get(PLAYER_ONE));
    }

    @Test
    void alivePlayerUuidsExcludesEliminatedAndSpectators() {
        ArenaActiveMatch updated = baseMatch()
            .withPlayerArrival(PLAYER_ONE, NOW)
            .withPlayerArrival(PLAYER_TWO, NOW)
            .withPlayerArrival(BACKFILL_PLAYER, NOW)
            .withEliminatedPlayer(PLAYER_TWO, NOW + 500L, NOW)
            .withSpectatorPlayer(BACKFILL_PLAYER, true, NOW);

        assertEquals(List.of(PLAYER_ONE), updated.alivePlayerUuids());
    }

    private static ArenaActiveMatch baseMatch() {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby.example:19132",
            "lobby-1.natural_spawn",
            "keep_inventory",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "",
            LastPlayerAliveArenaMatchResolutionTrigger.ID,
            "rules-default",
            "assignment-1",
            "INITIAL_MATCH",
            "external-match-1",
            ArenaMatchSource.BACKEND_DRIVEN.id(),
            1,
            4,
            true,
            QueueBackfillMode.ACTIVE_WINDOW.id(),
            30,
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            0L,
            0L,
            0L,
            0L,
            "",
            CREATED_AT,
            CREATED_AT,
            ""
        ).normalized();
    }
}
