package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PlayerUuidListsTest {

    private static final UUID PLAYER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PLAYER_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID PLAYER_C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Test
    void canonicalizeReturnsEmptyListForNullOrEmptyInput() {
        assertEquals(List.of(), PlayerUuidLists.canonicalize(null));
        assertEquals(List.of(), PlayerUuidLists.canonicalize(List.of()));
    }

    @Test
    void canonicalizeRemovesNullsDeduplicatesAndSortsByUuidString() {
        List<UUID> result = PlayerUuidLists.canonicalize(Arrays.asList(PLAYER_C, null, PLAYER_A, PLAYER_C, PLAYER_B));

        assertEquals(List.of(PLAYER_A, PLAYER_B, PLAYER_C), result);
    }

    @Test
    void sameCanonicalPlayersIgnoresOrderAndDuplicates() {
        assertTrue(PlayerUuidLists.sameCanonicalPlayers(
            List.of(PLAYER_B, PLAYER_A, PLAYER_A),
            List.of(PLAYER_A, PLAYER_B)
        ));
        assertFalse(PlayerUuidLists.sameCanonicalPlayers(
            List.of(PLAYER_A, PLAYER_C),
            List.of(PLAYER_A, PLAYER_B)
        ));
    }

    @Test
    void isSubsetUsesCanonicalPlayers() {
        assertTrue(PlayerUuidLists.isSubset(
            List.of(PLAYER_B, PLAYER_A, PLAYER_A),
            List.of(PLAYER_C, PLAYER_B, PLAYER_A)
        ));
        assertFalse(PlayerUuidLists.isSubset(
            List.of(PLAYER_A, PLAYER_C),
            List.of(PLAYER_A, PLAYER_B)
        ));
    }
}
