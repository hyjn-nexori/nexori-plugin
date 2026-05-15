package io.github.hyjn.nexori.plugin.discovery.logic;

import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DestinationTargetSummaryBuilderTest {

    private static DestinationTargetDefinition target(String id, DestinationTargetKind kind) {
        return new DestinationTargetDefinition(id, "Display " + id, kind, "world", "", "", "{}");
    }

    @Test
    void nonPortalTargetSummaryDoesNotIncludePortalId() {
        DestinationTargetDefinition target = target("lobby", DestinationTargetKind.NATURAL_SPAWN);

        DiscoveredDestinationTargetSummary summary = DestinationTargetSummaryBuilder.summarize(
            target, Map.of("lobby", "portal-99")
        );

        assertEquals("", summary.portalId(),
            "Non-PORTAL targets call from(target) which sets portalId to empty string");
    }

    @Test
    void portalTargetSummaryIncludesPortalIdWhenAutoTargetMatches() {
        DestinationTargetDefinition target = target("portal-target", DestinationTargetKind.PORTAL);

        DiscoveredDestinationTargetSummary summary = DestinationTargetSummaryBuilder.summarize(
            target, Map.of("portal-target", "portal-abc")
        );

        assertEquals("portal-abc", summary.portalId());
    }

    @Test
    void portalTargetSummaryUsesBlankPortalIdWhenNoMatch() {
        DestinationTargetDefinition target = target("portal-target", DestinationTargetKind.PORTAL);

        DiscoveredDestinationTargetSummary summary = DestinationTargetSummaryBuilder.summarize(
            target, Map.of()
        );

        assertEquals("", summary.portalId(),
            "getOrDefault returns empty string; from(target, '') normalizes to ''");
    }

    @Test
    void summaryBuilderPreservesDisplayKindWorldAndArrivalPoint() {
        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "spawn", "Main Spawn", DestinationTargetKind.NATURAL_SPAWN, "overworld", "arrival-pt", "", "{}"
        );

        DiscoveredDestinationTargetSummary summary = DestinationTargetSummaryBuilder.summarize(target, Map.of());

        assertEquals("spawn", summary.id());
        assertEquals("Main Spawn", summary.displayName());
        assertEquals("NATURAL_SPAWN", summary.kind());
        assertEquals("overworld", summary.worldName());
        assertEquals("arrival-pt", summary.arrivalPointId());
    }
}
