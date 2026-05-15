package io.github.hyjn.nexori.plugin.target;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ResolvedDestinationTargetTest {

    @Test
    void preservesDefinitionWorldAndArrivalPoint() {
        DestinationTargetDefinition definition = new DestinationTargetDefinition(
            "lobby", "Lobby", DestinationTargetKind.NATURAL_SPAWN, "overworld", "arrival-pt", "", "{}"
        );
        ResolvedDestinationTarget resolved = new ResolvedDestinationTarget(
            definition, "overworld", "arrival-pt"
        );
        assertEquals(definition, resolved.definition());
        assertEquals("overworld", resolved.effectiveWorldName());
        assertEquals("arrival-pt", resolved.effectiveArrivalPointId());
    }

    @Test
    void allowsNullFieldsAccordingToCurrentBehavior() {
        ResolvedDestinationTarget resolved = new ResolvedDestinationTarget(null, null, null);
        assertNull(resolved.definition(),
            "Record constructor does not validate fields; null is preserved as-is");
        assertNull(resolved.effectiveWorldName());
        assertNull(resolved.effectiveArrivalPointId());
    }
}
