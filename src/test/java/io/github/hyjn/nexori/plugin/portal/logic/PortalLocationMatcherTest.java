package io.github.hyjn.nexori.plugin.portal.logic;

import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalLocationMatcherTest {

    private PortalInstanceDefinition portal(String id, String world, int x, int y, int z) {
        return new PortalInstanceDefinition(id, id, world, x, y, z, "", true, 0L, 0L);
    }

    @Test
    void emptyCollectionReturnsEmpty() {
        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            List.of(), "overworld", 0, 64, 0, 10, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void singlePortalAtExactPositionIsFound() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "overworld", 0, 64, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 10, 5);

        assertTrue(result.isPresent());
        assertEquals("p1", result.get().portalId());
    }

    @Test
    void portalInDifferentWorldIsIgnored() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "nether", 0, 64, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 100, 100);

        assertTrue(result.isEmpty());
    }

    @Test
    void portalOutsideHorizontalRadiusIsIgnored() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "overworld", 50, 64, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 10, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void portalOutsideVerticalRadiusIsIgnored() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "overworld", 0, 100, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 100, 5);

        assertTrue(result.isEmpty());
    }

    @Test
    void portalAtExactHorizontalBoundaryIsIncluded() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "overworld", 10, 64, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 10, 10);

        assertTrue(result.isPresent());
    }

    @Test
    void portalOneBeyondHorizontalBoundaryIsExcluded() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "overworld", 11, 64, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 10, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void closestPortalSelectedWhenMultipleInRadius() {
        List<PortalInstanceDefinition> portals = List.of(
            portal("far", "overworld", 10, 64, 0),
            portal("near", "overworld", 2, 64, 0)
        );

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 20, 10);

        assertEquals("near", result.get().portalId());
    }

    @Test
    void distanceIsManhattanSumAccordingToCurrentBehavior() {
        // portal-a: dx=5, dy=0, dz=0 → manhattan=5
        // portal-b: dx=3, dy=3, dz=0 → manhattan=6  (further despite closer X)
        List<PortalInstanceDefinition> portals = List.of(
            portal("a", "overworld", 5, 64, 0),
            portal("b", "overworld", 3, 67, 0)
        );

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 20, 20);

        assertEquals("a", result.get().portalId());
    }

    @Test
    void worldNameSearchIsCaseInsensitiveAccordingToCurrentBehavior() {
        List<PortalInstanceDefinition> portals = List.of(portal("p1", "overworld", 0, 64, 0));

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "OVERWORLD", 0, 64, 0, 10, 10);

        assertTrue(result.isPresent());
    }

    @Test
    void tieBreakFavorsFirstInCollectionAccordingToCurrentBehavior() {
        // Equal Manhattan distance (5 each) — first in list wins because distance < bestDistance is strict
        List<PortalInstanceDefinition> portals = List.of(
            portal("first", "overworld", 5, 64, 0),
            portal("second", "overworld", 0, 64, 5)
        );

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            portals, "overworld", 0, 64, 0, 10, 10);

        assertEquals("first", result.get().portalId());
    }

    @Test
    void disabledPortalIsIncludedAccordingToCurrentBehavior() {
        // findNearest does NOT filter by enabled — disabled portals are returned
        PortalInstanceDefinition disabled = new PortalInstanceDefinition("p1", "p1", "overworld", 0, 64, 0, "", false, 0L, 0L);

        Optional<PortalInstanceDefinition> result = PortalLocationMatcher.findNearest(
            List.of(disabled), "overworld", 0, 64, 0, 10, 10);

        assertTrue(result.isPresent());
    }
}
