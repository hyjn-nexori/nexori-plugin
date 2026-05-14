package io.github.hyjn.nexori.plugin.discovery;

import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DiscoveredDestinationTargetSummaryTest {

    @Test
    void fromTargetPreservesId() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target());

        assertEquals("lobby", summary.id());
    }

    @Test
    void fromTargetPreservesDisplayName() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target());

        assertEquals("Lobby Display", summary.displayName());
    }

    @Test
    void fromTargetPreservesKindName() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target());

        assertEquals("NATURAL_SPAWN", summary.kind());
    }

    @Test
    void fromTargetPreservesWorldName() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target());

        assertEquals("overworld", summary.worldName());
    }

    @Test
    void fromTargetPreservesArrivalPointId() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target());

        assertEquals("natural_spawn", summary.arrivalPointId());
    }

    @Test
    void fromTargetWithoutPortalIdHasBlankPortalId() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target());

        assertEquals("", summary.portalId());
    }

    @Test
    void fromTargetWithPortalIdPreservesPortalId() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target(), "portal-abc");

        assertEquals("portal-abc", summary.portalId());
    }

    @Test
    void fromTargetWithPortalIdNormalizesPortalIdToLowercase() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target(), "Portal-ABC");

        assertEquals("portal-abc", summary.portalId());
    }

    @Test
    void fromTargetWithNullPortalIdBecomesBlank() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target(), null);

        assertEquals("", summary.portalId());
    }

    @Test
    void fromTargetWithBlankPortalIdTrimsToBlank() {
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(target(), "   ");

        assertEquals("", summary.portalId());
    }

    @Test
    void fromPortalTargetPreservesKindNameAsPortal() {
        DestinationTargetDefinition portalTarget = new DestinationTargetDefinition(
            "portal.target", "Portal Target", DestinationTargetKind.PORTAL, "overworld", "portal_entry", "", "{}"
        );
        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(portalTarget, "portal-xyz");

        assertEquals("PORTAL", summary.kind());
        assertEquals("portal-xyz", summary.portalId());
    }

    private DestinationTargetDefinition target() {
        return new DestinationTargetDefinition(
            "lobby", "Lobby Display", DestinationTargetKind.NATURAL_SPAWN,
            "overworld", "natural_spawn", "Welcome!", "{}"
        );
    }
}
