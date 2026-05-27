package io.github.hyjn.nexori.plugin.portal;

import io.github.hyjn.nexori.plugin.portal.logic.PortalAutoTargetPlanner;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalInstanceDefinitionTest {

    // ── locationKey (static) ──────────────────────────────────────────────────

    @Test
    void locationKeyStaticFormatIsWorldColonXColonYColonZ() {
        assertEquals("overworld:10:64:-5", PortalInstanceDefinition.locationKey("overworld", 10, 64, -5));
    }

    @Test
    void locationKeyStaticNormalizesWorldNameLowercase() {
        assertEquals("overworld:0:0:0", PortalInstanceDefinition.locationKey("OverWorld", 0, 0, 0));
    }

    @Test
    void locationKeyStaticBlankWorldNameBecomesDefault() {
        assertEquals("default:0:0:0", PortalInstanceDefinition.locationKey("   ", 0, 0, 0));
    }

    @Test
    void locationKeyStaticNullWorldNameBecomesDefault() {
        assertEquals("default:5:10:15", PortalInstanceDefinition.locationKey(null, 5, 10, 15));
    }

    @Test
    void locationKeyInstanceMatchesStaticMethod() {
        PortalInstanceDefinition portal = portal("my-portal", "overworld", 10, 64, -5);
        assertEquals(PortalInstanceDefinition.locationKey("overworld", 10, 64, -5), portal.locationKey());
    }

    // ── buildAutoDestinationTargetId ─────────────────────────────────────────

    @Test
    void buildAutoDestinationTargetIdFormat() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("overworld", 10, 64, -5);

        assertEquals("overworld.portal.10_64_-5", id);
    }

    @Test
    void buildAutoDestinationTargetIdLowercasesWorldName() {
        String id = PortalAutoTargetPlanner.buildAutoDestinationTargetId("OverWorld", 0, 0, 0);

        assertTrue(id.startsWith("overworld."), "Should start with lowercased world name: " + id);
    }

    // ── normalized() ─────────────────────────────────────────────────────────

    @Test
    void normalizedWorldNameIsLowercasedAndTrimmed() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "  OverWorld  ", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        assertEquals("overworld", portal.normalized().worldName());
    }

    @Test
    void normalizedWorldNameNullBecomesDefault() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", null, 10, 64, 5, "", true, 1_000L, 2_000L
        );
        assertEquals("default", portal.normalized().worldName());
    }

    @Test
    void normalizedWorldNameBlankBecomesDefault() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "   ", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        assertEquals("default", portal.normalized().worldName());
    }

    @Test
    void normalizedPortalIdIsPreservedWhenNonBlank() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "  My-Portal  ", "Display", "world", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        assertEquals("my-portal", portal.normalized().portalId());
    }

    @Test
    void normalizedPortalIdNullBecomesLocationKey() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            null, "Display", "overworld", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        String expected = PortalInstanceDefinition.locationKey("overworld", 10, 64, 5);
        assertEquals(expected, portal.normalized().portalId());
    }

    @Test
    void normalizedPortalIdBlankBecomesLocationKey() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "   ", "Display", "overworld", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        String expected = PortalInstanceDefinition.locationKey("overworld", 10, 64, 5);
        assertEquals(expected, portal.normalized().portalId());
    }

    @Test
    void normalizedDisplayNameIsPreservedWhenNonBlank() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "  My Portal  ", "world", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        assertEquals("My Portal", portal.normalized().displayName());
    }

    @Test
    void normalizedDisplayNameNullBecomesCoordinateDefault() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", null, "overworld", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        String normalizedDisplayName = portal.normalized().displayName();
        assertTrue(normalizedDisplayName.contains("overworld"), "Display name should contain world name: " + normalizedDisplayName);
        assertTrue(normalizedDisplayName.contains("10"), "Display name should contain blockX: " + normalizedDisplayName);
    }

    @Test
    void normalizedAutoDestinationTargetIdNullBecomesEmpty() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", 10, 64, 5, null, true, 1_000L, 2_000L
        );
        assertEquals("", portal.normalized().autoDestinationTargetId());
    }

    @Test
    void normalizedAutoDestinationTargetIdIsLowercasedAndTrimmed() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", 10, 64, 5, "  Target-ABC  ", true, 1_000L, 2_000L
        );
        assertEquals("target-abc", portal.normalized().autoDestinationTargetId());
    }

    @Test
    void normalizedPreservesValidTimestamps() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", 10, 64, 5, "", true, 1_000L, 2_000L
        );
        PortalInstanceDefinition normalized = portal.normalized();

        assertEquals(1_000L, normalized.createdAtEpochMillis());
        assertEquals(2_000L, normalized.updatedAtEpochMillis());
    }

    @Test
    void normalizedZeroCreatedAtBecomesCurrentTimeAccordingToCurrentBehavior() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", 10, 64, 5, "", true, 0L, 0L
        );
        PortalInstanceDefinition normalized = portal.normalized();

        assertTrue(normalized.createdAtEpochMillis() > 0,
            "Zero createdAt should be replaced with current time: " + normalized.createdAtEpochMillis());
        assertTrue(normalized.updatedAtEpochMillis() >= normalized.createdAtEpochMillis(),
            "updatedAt should be >= createdAt");
    }

    @Test
    void normalizedPreservesEnabledState() {
        PortalInstanceDefinition enabledPortal = portal("portal-1", "world", 10, 64, 5);
        PortalInstanceDefinition disabledPortal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", 10, 64, 5, "", false, 1_000L, 2_000L
        );

        assertTrue(enabledPortal.normalized().enabled());
        assertEquals(false, disabledPortal.normalized().enabled());
    }

    @Test
    void normalizedPreservesBlockCoordinates() {
        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", -10, 200, 999, "", true, 1_000L, 2_000L
        );
        PortalInstanceDefinition normalized = portal.normalized();

        assertEquals(-10, normalized.blockX());
        assertEquals(200, normalized.blockY());
        assertEquals(999, normalized.blockZ());
    }

    @Test
    void blockPositionReturnsCoordsAsVector() {
        PortalInstanceDefinition portal = portal("portal-1", "world", 10, 64, 5);
        assertNotNull(portal.blockPosition());
        assertEquals(10, portal.blockPosition().x);
        assertEquals(64, portal.blockPosition().y);
        assertEquals(5, portal.blockPosition().z);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private PortalInstanceDefinition portal(String portalId, String worldName, int x, int y, int z) {
        return new PortalInstanceDefinition(portalId, "Display", worldName, x, y, z, "", true, 1_000L, 2_000L);
    }
}
