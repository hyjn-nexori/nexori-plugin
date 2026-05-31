package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.LastPlayerAliveArenaMatchResolutionTrigger;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchPlacementEvaluatorTest {

    private static final long CREATED_AT = 500L;
    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BACKFILL_PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final MatchPlacementEvaluator evaluator = new MatchPlacementEvaluator();

    @Test
    void emptyExpectedRosterIsNotPlacementComplete() {
        MatchPlacementEvaluation evaluation = evaluator.evaluate(
            match(List.of(), 0, List.of()),
            Set.of()
        );

        assertEquals(0, evaluation.expectedPlayers());
        assertFalse(evaluation.placementComplete());
    }

    @Test
    void arrivedInitialPlayersCountsOnlyExpectedPlayers() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO, BACKFILL_PLAYER)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(2, evaluation.arrivedInitialPlayers());
    }

    @Test
    void arrivedInitialPlayersIgnoresBackfillPlayers() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, BACKFILL_PLAYER)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(1, evaluation.arrivedInitialPlayers());
    }

    @Test
    void arrivedOnlyExpectedPlayerDoesNotCountAsPlaced() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of()
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(0, evaluation.placedInitialPlayers());
        assertFalse(evaluation.placementComplete());
    }

    @Test
    void activeExpectedPlayerCountsAsPlaced() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(2, evaluation.placedInitialPlayers());
    }

    @Test
    void pendingUnconfirmedSetDoesNotMakeArrivedOnlyPlayerPlaced() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of(PLAYER_TWO));

        assertEquals(1, evaluation.placedInitialPlayers());
    }

    @Test
    void pendingUnconfirmedBackfillPlayerDoesNotAffectPlacedInitialPlayers() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO, BACKFILL_PLAYER),
            List.of(PLAYER_ONE, PLAYER_TWO, BACKFILL_PLAYER)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of(BACKFILL_PLAYER));

        assertEquals(2, evaluation.placedInitialPlayers());
        assertTrue(evaluation.placementComplete());
    }

    @Test
    void placementCompleteRequiresExpectedArrivedAndPlacedCounts() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(2, evaluation.expectedPlayers());
        assertEquals(2, evaluation.arrivedInitialPlayers());
        assertEquals(2, evaluation.placedInitialPlayers());
        assertTrue(evaluation.placementComplete());
    }

    @Test
    void placementCompleteFalseWhenExpectedPlayersMissing() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertFalse(evaluation.placementComplete());
    }

    @Test
    void expectedPlayerCountGreaterThanExpectedUuidListPreventsPlacementComplete() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            3,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(3, evaluation.expectedPlayers());
        assertEquals(2, evaluation.arrivedInitialPlayers());
        assertEquals(2, evaluation.placedInitialPlayers());
        assertFalse(evaluation.placementComplete());
    }

    @Test
    void failedPlacementForInitialPlayerDoesNotMarkPlacementComplete() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertEquals(2, evaluation.arrivedInitialPlayers());
        assertEquals(1, evaluation.placedInitialPlayers());
        assertFalse(evaluation.placementComplete());
        assertFalse(evaluation.shouldMarkPlacementCompleted());
    }

    @Test
    void placementCompleteTrueWhenAllExpectedPlayersArrivedAndPlaced() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO, BACKFILL_PLAYER),
            List.of(PLAYER_ONE, PLAYER_TWO, BACKFILL_PLAYER)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertTrue(evaluation.placementComplete());
    }

    @Test
    void shouldMarkPlacementCompletedOnlyWhenNotAlreadyMarkedAndPlacementComplete() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertTrue(evaluation.shouldMarkPlacementCompleted());
    }

    @Test
    void shouldNotMarkPlacementCompletedWhenAlreadyMarked() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        ).withPlacementCompleted(NOW, NOW);

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertTrue(evaluation.placementComplete());
        assertFalse(evaluation.shouldMarkPlacementCompleted());
    }

    @Test
    void shouldMarkMatchCompletedWhenMatchHasWinner() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        ).withWinner(PLAYER_ONE, NOW + 1_000L, NOW);

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertTrue(evaluation.shouldMarkMatchCompleted());
    }

    @Test
    void shouldMarkMatchCompletedWhenMatchHasSubmittedResult() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        ).withSubmittedResult(NOW, NOW, "payload-hash");

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertTrue(evaluation.shouldMarkMatchCompleted());
    }

    @Test
    void shouldNotMarkMatchCompletedWhenNoWinnerAndNoSubmittedResult() {
        ArenaActiveMatch match = match(
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO)
        );

        MatchPlacementEvaluation evaluation = evaluator.evaluate(match, Set.of());

        assertFalse(evaluation.shouldMarkMatchCompleted());
    }

    private static ArenaActiveMatch match(
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        List<UUID> arrivedPlayerUuids
    ) {
        return match(expectedPlayerUuids, expectedPlayerCount, arrivedPlayerUuids, arrivedPlayerUuids);
    }

    private static ArenaActiveMatch match(
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids
    ) {
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
            expectedPlayerUuids,
            expectedPlayerCount,
            arrivedPlayerUuids,
            activePlayerUuids,
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
