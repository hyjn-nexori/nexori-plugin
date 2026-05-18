package io.github.hyjn.nexori.plugin.minigame.logic;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class SpectatorHiddenViewerPlannerTest {

    private static final UUID SPECTATOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID VIEWER_ONE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID VIEWER_TWO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID VIEWER_THREE = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final SpectatorHiddenViewerPlanner planner = new SpectatorHiddenViewerPlanner();

    @Test
    void normalizeDesiredViewersRemovesNullsAndSpectatorUuid() {
        Set<UUID> result = planner.normalizeDesiredViewers(SPECTATOR, Arrays.asList(VIEWER_ONE, null, SPECTATOR, VIEWER_TWO));

        assertEquals(List.of(VIEWER_ONE, VIEWER_TWO), new ArrayList<>(result));
    }

    @Test
    void normalizeDesiredViewersDeduplicatesViewersAndPreservesOrder() {
        Set<UUID> result = planner.normalizeDesiredViewers(SPECTATOR, List.of(VIEWER_TWO, VIEWER_ONE, VIEWER_TWO));

        assertEquals(List.of(VIEWER_TWO, VIEWER_ONE), new ArrayList<>(result));
    }

    @Test
    void planHidesNewDesiredViewers() {
        SpectatorHiddenViewerPlan result = planner.plan(SPECTATOR, Set.of(VIEWER_ONE), List.of(VIEWER_ONE, VIEWER_TWO));

        assertEquals(Set.of(VIEWER_TWO), result.viewersToHide());
    }

    @Test
    void planShowsViewersNoLongerDesired() {
        SpectatorHiddenViewerPlan result = planner.plan(SPECTATOR, Set.of(VIEWER_ONE, VIEWER_TWO), List.of(VIEWER_ONE));

        assertEquals(Set.of(VIEWER_TWO), result.viewersToShow());
    }

    @Test
    void planKeepsAlreadyHiddenDesiredViewersUnchanged() {
        SpectatorHiddenViewerPlan result = planner.plan(SPECTATOR, Set.of(VIEWER_ONE), List.of(VIEWER_ONE));

        assertEquals(Set.of(), result.viewersToHide());
        assertEquals(Set.of(), result.viewersToShow());
    }

    @Test
    void planWithEmptyDesiredShowsAllCurrentlyHiddenViewers() {
        SpectatorHiddenViewerPlan result = planner.plan(SPECTATOR, Set.of(VIEWER_ONE, VIEWER_TWO), List.of());

        assertEquals(Set.of(), result.desiredHiddenViewers());
        assertEquals(Set.of(), result.viewersToHide());
        assertEquals(Set.of(VIEWER_ONE, VIEWER_TWO), result.viewersToShow());
    }

    @Test
    void planPreservesDesiredHiddenViewerOrder() {
        SpectatorHiddenViewerPlan result = planner.plan(
            SPECTATOR,
            Set.of(),
            List.of(VIEWER_THREE, VIEWER_ONE, VIEWER_TWO)
        );

        assertEquals(List.of(VIEWER_THREE, VIEWER_ONE, VIEWER_TWO), new ArrayList<>(result.desiredHiddenViewers()));
    }

    @Test
    void planReturnsImmutableSets() {
        SpectatorHiddenViewerPlan result = planner.plan(SPECTATOR, Set.of(), List.of(VIEWER_ONE));

        assertThrows(UnsupportedOperationException.class, () -> result.desiredHiddenViewers().add(VIEWER_TWO));
        assertThrows(UnsupportedOperationException.class, () -> result.viewersToHide().add(VIEWER_TWO));
        assertThrows(UnsupportedOperationException.class, () -> result.viewersToShow().add(VIEWER_TWO));
    }
}
