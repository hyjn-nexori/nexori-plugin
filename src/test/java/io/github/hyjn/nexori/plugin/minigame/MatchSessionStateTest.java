package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchSessionStateTest {

    private static final long CREATED_AT = 500L;
    private static final long NOW = 1_000L;
    private static final long EXPIRES_AT = 2_000L;

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BACKFILL_PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void normalizedCanonicalizesIdsAndPlayers() {
        MatchSessionState state = new MatchSessionState(
            " Match-ABC ",
            " Queue-One ",
            " Arena-One ",
            " Lobby-One ",
            " lobby.example:19132 ",
            " Lobby-One.Spawn ",
            " Keep_Inventory ",
            List.of(PLAYER_TWO, PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_TWO, PLAYER_ONE, PLAYER_ONE),
            CREATED_AT,
            NOW,
            0L,
            EXPIRES_AT,
            " test error "
        ).normalized();

        assertEquals("match-abc", state.matchId());
        assertEquals("queue-one", state.queueId());
        assertEquals("arena-one", state.arenaId());
        assertEquals("lobby-one", state.originLobbyId());
        assertEquals("lobby.example:19132", state.returnConnectionAddress());
        assertEquals("lobby-one.spawn", state.returnFallbackTargetId());
        assertEquals("keep_inventory", state.launchTravelProfileId());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), state.expectedPlayerUuids());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), state.returnedPlayerUuids());
        assertEquals("test error", state.lastError());
    }

    @Test
    void withMergedExpectedPlayersPreservesObservedPlayersThatStillBelongToRoster() {
        MatchSessionState state = baseState()
            .withObservedPlayer(PLAYER_ONE, NOW)
            .withObservedPlayer(BACKFILL_PLAYER, NOW + 1);

        MatchSessionState updated = state.withMergedExpectedPlayers(List.of(PLAYER_TWO), NOW + 2, " merged ");

        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), updated.expectedPlayerUuids());
        assertEquals(List.of(PLAYER_ONE), updated.returnedPlayerUuids());
        assertEquals("merged", updated.lastError());
        assertEquals(NOW + 2, updated.updatedAtEpochMs());
    }

    @Test
    void withLaunchedPlayersFiltersObservedPlayersOutsideNewLaunchSet() {
        MatchSessionState state = baseState()
            .withMergedExpectedPlayers(List.of(PLAYER_TWO), NOW, "")
            .withObservedPlayer(PLAYER_ONE, NOW + 1)
            .withObservedPlayer(PLAYER_TWO, NOW + 2);

        MatchSessionState updated = state.withLaunchedPlayers(List.of(PLAYER_TWO), NOW + 3, "");

        assertEquals(List.of(PLAYER_TWO), updated.expectedPlayerUuids());
        assertEquals(List.of(PLAYER_TWO), updated.returnedPlayerUuids());
        assertFalse(updated.expectsPlayer(PLAYER_ONE));
        assertTrue(updated.hasObservedPlayer(PLAYER_TWO));
    }

    @Test
    void withMergedHandoffCompletedMergesPlayersAndMarksHandoff() {
        MatchSessionState updated = baseState()
            .withMergedHandoffCompleted(List.of(BACKFILL_PLAYER), EXPIRES_AT, NOW, "");

        assertEquals(List.of(PLAYER_ONE, BACKFILL_PLAYER), updated.launchedPlayerUuids());
        assertEquals(NOW, updated.handoffCompletedAtEpochMs());
        assertEquals(EXPIRES_AT, updated.expiresAtEpochMs());
        assertTrue(updated.hasHandoffCompleted());
    }

    @Test
    void allLaunchedPlayersObservedRequiresNonEmptyLaunchSetAndAllPlayers() {
        MatchSessionState partial = baseState().withObservedPlayer(PLAYER_ONE, NOW);
        MatchSessionState complete = baseState()
            .withMergedExpectedPlayers(List.of(PLAYER_TWO), NOW, "")
            .withObservedPlayer(PLAYER_ONE, NOW + 1)
            .withObservedPlayer(PLAYER_TWO, NOW + 2);

        assertTrue(partial.allLaunchedPlayersObserved());
        assertTrue(complete.allLaunchedPlayersObserved());
        assertFalse(baseState().withLaunchedPlayers(List.of(), NOW, "").allLaunchedPlayersObserved());
    }

    @Test
    void isExpiredOnlyWhenDeadlineExistsAndHasPassed() {
        MatchSessionState state = baseState()
            .withHandoffCompleted(List.of(PLAYER_ONE), EXPIRES_AT, NOW, "");

        assertFalse(state.isExpired(EXPIRES_AT - 1));
        assertTrue(state.isExpired(EXPIRES_AT));
        assertFalse(baseState().withHandoffCompleted(List.of(PLAYER_ONE), 0L, NOW, "").isExpired(999_999L));
    }

    @Test
    void normalizedRejectsMissingRequiredFields() {
        MatchSessionState invalid = new MatchSessionState(
            "",
            "queue-one",
            "arena-one",
            "lobby-one",
            "lobby.example:19132",
            "lobby-one.spawn",
            "keep_inventory",
            List.of(PLAYER_ONE),
            List.of(),
            CREATED_AT,
            NOW,
            0L,
            EXPIRES_AT,
            ""
        );

        assertThrows(IllegalArgumentException.class, invalid::normalized);
    }

    private static MatchSessionState baseState() {
        return new MatchSessionState(
            "match-one",
            "queue-one",
            "arena-one",
            "lobby-one",
            "lobby.example:19132",
            "lobby-one.spawn",
            "keep_inventory",
            List.of(PLAYER_ONE),
            List.of(),
            CREATED_AT,
            CREATED_AT,
            0L,
            0L,
            ""
        ).normalized();
    }
}
