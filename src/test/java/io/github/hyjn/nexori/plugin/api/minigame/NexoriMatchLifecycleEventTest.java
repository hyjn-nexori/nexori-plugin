package io.github.hyjn.nexori.plugin.api.minigame;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NexoriMatchLifecycleEventTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void matchEventCopiesMutableLists() {
        List<UUID> expected = new ArrayList<>();
        expected.add(PLAYER_ONE);
        NexoriMatchLifecycleEvent event = new NexoriMatchLifecycleEvent(
            " match-1 ",
            " queue-1 ",
            " arena-1 ",
            " assignment-1 ",
            " external-1 ",
            " capture_the_zone ",
            " none ",
            expected,
            List.of(),
            List.of(),
            List.of(),
            List.of(PLAYER_ONE),
            new NexoriMatchPlacementState(1, 0, 0, false),
            " test ",
            1_000L,
            2_000L
        );

        expected.add(PLAYER_TWO);

        assertEquals(List.of(PLAYER_ONE), event.expectedPlayerUuids());
        assertEquals("match-1", event.matchId());
        assertEquals("capture_the_zone", event.rulesEngineId());
        assertThrows(UnsupportedOperationException.class, () -> event.expectedPlayerUuids().add(PLAYER_TWO));
    }

    @Test
    void playerEventRequiresMatchSnapshot() {
        assertThrows(IllegalArgumentException.class, () -> new NexoriPlayerMatchLifecycleEvent(
            null,
            PLAYER_ONE,
            "PlayerOne",
            "assignment-player-1",
            "PLAYER_ARRIVED",
            1_000L
        ));
    }

    @Test
    void playerEventRequiresPlayerUuid() {
        assertThrows(IllegalArgumentException.class, () -> new NexoriPlayerMatchLifecycleEvent(
            matchEvent(),
            null,
            "PlayerOne",
            "assignment-player-1",
            "PLAYER_ARRIVED",
            1_000L
        ));
    }

    @Test
    void placementEventRequiresPlayerSnapshotAndOutcome() {
        NexoriPlayerMatchLifecycleEvent playerEvent = new NexoriPlayerMatchLifecycleEvent(
            matchEvent(),
            PLAYER_ONE,
            "PlayerOne",
            "assignment-player-1",
            "PLAYER_ARRIVED",
            2_000L
        );

        assertThrows(IllegalArgumentException.class, () -> new NexoriPlayerPlacementLifecycleEvent(
            null,
            NexoriPlayerPlacementOutcome.CONFIRMED,
            new NexoriMatchPlacementState(1, 1, 1, true),
            "world",
            "template",
            2_000L
        ));
        assertThrows(IllegalArgumentException.class, () -> new NexoriPlayerPlacementLifecycleEvent(
            playerEvent,
            null,
            new NexoriMatchPlacementState(1, 1, 1, true),
            "world",
            "template",
            2_000L
        ));
    }

    @Test
    void placementEventRequiresPlacementState() {
        NexoriPlayerMatchLifecycleEvent playerEvent = new NexoriPlayerMatchLifecycleEvent(
            matchEvent(),
            PLAYER_ONE,
            "PlayerOne",
            "assignment-player-1",
            "PLAYER_ARRIVED",
            2_000L
        );

        assertThrows(IllegalArgumentException.class, () -> new NexoriPlayerPlacementLifecycleEvent(
            playerEvent,
            NexoriPlayerPlacementOutcome.CONFIRMED,
            null,
            "world",
            "template",
            2_000L
        ));
    }

    private static NexoriMatchLifecycleEvent matchEvent() {
        return new NexoriMatchLifecycleEvent(
            "match-1",
            "queue-1",
            "arena-1",
            "assignment-1",
            "external-1",
            "capture_the_zone",
            "none",
            List.of(PLAYER_ONE),
            List.of(),
            List.of(),
            List.of(),
            List.of(PLAYER_ONE),
            new NexoriMatchPlacementState(1, 0, 0, false),
            "test",
            1_000L,
            2_000L
        );
    }
}
