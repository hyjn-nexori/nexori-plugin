package io.github.hyjn.nexori.plugin.target;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DestinationTargetKindTest {

    // ── displayName ───────────────────────────────────────────────────────────

    @Test
    void naturalSpawnHasDisplayName() {
        assertEquals("Natural Spawn", DestinationTargetKind.NATURAL_SPAWN.displayName());
    }

    @Test
    void coordinateHasDisplayName() {
        assertEquals("Coordinate", DestinationTargetKind.COORDINATE.displayName());
    }

    @Test
    void portalHasDisplayName() {
        assertEquals("Portal", DestinationTargetKind.PORTAL.displayName());
    }

    // ── parse ─────────────────────────────────────────────────────────────────

    @Test
    void parseNaturalSpawn() {
        assertEquals(DestinationTargetKind.NATURAL_SPAWN, DestinationTargetKind.parse("NATURAL_SPAWN"));
    }

    @Test
    void parseSpawnAlias() {
        assertEquals(DestinationTargetKind.NATURAL_SPAWN, DestinationTargetKind.parse("SPAWN"));
    }

    @Test
    void parseNaturalSpawnLowercaseInput() {
        assertEquals(DestinationTargetKind.NATURAL_SPAWN, DestinationTargetKind.parse("natural_spawn"));
    }

    @Test
    void parseCoordinate() {
        assertEquals(DestinationTargetKind.COORDINATE, DestinationTargetKind.parse("COORDINATE"));
    }

    @Test
    void parseCoordAlias() {
        assertEquals(DestinationTargetKind.COORDINATE, DestinationTargetKind.parse("COORD"));
    }

    @Test
    void parsePortal() {
        assertEquals(DestinationTargetKind.PORTAL, DestinationTargetKind.parse("PORTAL"));
    }

    @Test
    void parseTrimsWhitespace() {
        assertEquals(DestinationTargetKind.PORTAL, DestinationTargetKind.parse("  portal  "));
    }

    @Test
    void parseUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> DestinationTargetKind.parse("WARP"));
    }

    @Test
    void parseBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> DestinationTargetKind.parse("   "));
    }

    // ── enum completeness ─────────────────────────────────────────────────────

    @Test
    void allKindsHaveNonNullDisplayName() {
        for (DestinationTargetKind kind : DestinationTargetKind.values()) {
            assertNotNull(kind.displayName(), "Kind " + kind.name() + " has null displayName");
        }
    }
}
