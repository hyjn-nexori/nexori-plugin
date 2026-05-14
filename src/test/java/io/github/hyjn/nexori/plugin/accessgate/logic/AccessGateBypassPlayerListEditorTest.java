package io.github.hyjn.nexori.plugin.accessgate.logic;

import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateBypassPlayer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class AccessGateBypassPlayerListEditorTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final AccessGateBypassPlayerListEditor editor = new AccessGateBypassPlayerListEditor();

    @Test
    void addNewBypassPlayerAppendsToEnd() {
        List<NexoriAccessGateBypassPlayer> updated = editor.addBypassPlayerUuid(
            List.of(player(PLAYER_ONE, "One")),
            PLAYER_TWO,
            "Two"
        );

        assertEquals(List.of(
            player(PLAYER_ONE, "One"),
            player(PLAYER_TWO, "Two")
        ), updated);
    }

    @Test
    void addExistingBypassPlayerWithBlankUsernamePreservesExistingUsername() {
        List<NexoriAccessGateBypassPlayer> updated = editor.addBypassPlayerUuid(
            List.of(player(PLAYER_ONE, "One")),
            PLAYER_ONE,
            "   "
        );

        assertEquals(List.of(player(PLAYER_ONE, "One")), updated);
    }

    @Test
    void addExistingBypassPlayerWithNonBlankUsernameUpdatesUsername() {
        List<NexoriAccessGateBypassPlayer> updated = editor.addBypassPlayerUuid(
            List.of(player(PLAYER_ONE, "Old")),
            PLAYER_ONE,
            " New "
        );

        assertEquals(List.of(player(PLAYER_ONE, "New")), updated);
    }

    @Test
    void addBypassPlayerFiltersNullAndBlankExistingEntries() {
        List<NexoriAccessGateBypassPlayer> updated = editor.addBypassPlayerUuid(
            Arrays.asList(
                null,
                new NexoriAccessGateBypassPlayer(null, "Null"),
                new NexoriAccessGateBypassPlayer("   ", "Blank"),
                player(PLAYER_ONE, "One")
            ),
            PLAYER_TWO,
            "Two"
        );

        assertEquals(List.of(
            player(PLAYER_ONE, "One"),
            player(PLAYER_TWO, "Two")
        ), updated);
    }

    @Test
    void addBypassPlayerPreservesExistingOrder() {
        List<NexoriAccessGateBypassPlayer> updated = editor.addBypassPlayerUuid(
            List.of(
                player(PLAYER_TWO, "Two"),
                player(PLAYER_ONE, "One")
            ),
            PLAYER_THREE,
            "Three"
        );

        assertEquals(List.of(
            player(PLAYER_TWO, "Two"),
            player(PLAYER_ONE, "One"),
            player(PLAYER_THREE, "Three")
        ), updated);
    }

    @Test
    void removeBypassPlayerTokenTrimsAndLowercasesToken() {
        List<NexoriAccessGateBypassPlayer> updated = editor.removeBypassPlayerToken(
            List.of(
                player(PLAYER_ONE, "One"),
                player(PLAYER_TWO, "Two")
            ),
            " " + PLAYER_ONE.toString().toUpperCase() + " "
        );

        assertEquals(List.of(player(PLAYER_TWO, "Two")), updated);
    }

    @Test
    void removeBypassPlayerTokenRemovesMatchingPlayer() {
        List<NexoriAccessGateBypassPlayer> updated = editor.removeBypassPlayerToken(
            List.of(
                player(PLAYER_ONE, "One"),
                player(PLAYER_TWO, "Two")
            ),
            PLAYER_TWO.toString()
        );

        assertEquals(List.of(player(PLAYER_ONE, "One")), updated);
    }

    @Test
    void removeBypassPlayerTokenPreservesOtherPlayersOrder() {
        List<NexoriAccessGateBypassPlayer> updated = editor.removeBypassPlayerToken(
            List.of(
                player(PLAYER_ONE, "One"),
                player(PLAYER_TWO, "Two"),
                player(PLAYER_THREE, "Three")
            ),
            PLAYER_TWO.toString()
        );

        assertEquals(List.of(
            player(PLAYER_ONE, "One"),
            player(PLAYER_THREE, "Three")
        ), updated);
    }

    @Test
    void removeMissingBypassPlayerTokenReturnsNormalizedExistingList() {
        List<NexoriAccessGateBypassPlayer> updated = editor.removeBypassPlayerToken(
            List.of(
                new NexoriAccessGateBypassPlayer(" " + PLAYER_ONE.toString().toUpperCase() + " ", " One "),
                player(PLAYER_TWO, "Two")
            ),
            PLAYER_THREE.toString()
        );

        assertEquals(List.of(
            player(PLAYER_ONE, "One"),
            player(PLAYER_TWO, "Two")
        ), updated);
    }

    @Test
    void removeFiltersNullAndBlankExistingEntriesAccordingToCurrentBehavior() {
        List<NexoriAccessGateBypassPlayer> updated = editor.removeBypassPlayerToken(
            Arrays.asList(
                null,
                new NexoriAccessGateBypassPlayer(null, "Null"),
                new NexoriAccessGateBypassPlayer("   ", "Blank"),
                player(PLAYER_ONE, "One"),
                player(PLAYER_TWO, "Two")
            ),
            PLAYER_THREE.toString()
        );

        assertEquals(List.of(
            player(PLAYER_ONE, "One"),
            player(PLAYER_TWO, "Two")
        ), updated);
    }

    private static NexoriAccessGateBypassPlayer player(UUID uuid, String username) {
        return new NexoriAccessGateBypassPlayer(uuid.toString(), username);
    }
}
