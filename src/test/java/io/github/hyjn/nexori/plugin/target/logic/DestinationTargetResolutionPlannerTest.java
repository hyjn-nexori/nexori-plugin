package io.github.hyjn.nexori.plugin.target.logic;

import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DestinationTargetResolutionPlannerTest {

    private final DestinationTargetResolutionPlanner planner = new DestinationTargetResolutionPlanner();

    private DestinationTargetDefinition target(String id, String worldName, String arrivalPointId) {
        return new DestinationTargetDefinition(id, id, DestinationTargetKind.NATURAL_SPAWN, worldName, arrivalPointId, "", "{}");
    }

    @Test
    void planWithNullRawArrivalUsesTargetDefault() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", "default_arrival"), null);

        assertEquals("default_arrival", result.effectiveArrivalPointId());
    }

    @Test
    void planWithBlankRawArrivalUsesTargetDefault() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", "default_arrival"), "   ");

        assertEquals("default_arrival", result.effectiveArrivalPointId());
    }

    @Test
    void planWithEmptyRawArrivalUsesTargetDefault() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", "default_arrival"), "");

        assertEquals("default_arrival", result.effectiveArrivalPointId());
    }

    @Test
    void planWithNonBlankRawArrivalOverridesTargetDefault() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", "default_arrival"), "  custom_arrival  ");

        assertEquals("custom_arrival", result.effectiveArrivalPointId());
    }

    @Test
    void planTrimsRawArrivalBeforeUse() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", "default"), "  my_arrival  ");

        assertEquals("my_arrival", result.effectiveArrivalPointId());
    }

    @Test
    void planPreservesWorldNameFromTarget() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "overworld", "default"), null);

        assertEquals("overworld", result.effectiveWorldName());
    }

    @Test
    void planWorldNameNotChangedByArrivalOverride() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "my-world", "default"), "custom");

        assertEquals("my-world", result.effectiveWorldName());
    }

    @Test
    void planPreservesDefinitionReference() {
        DestinationTargetDefinition t = target("lobby", "world", "arrival");
        ResolvedDestinationTarget result = planner.plan(t, null);

        assertEquals(t, result.definition());
    }

    @Test
    void planPreservesTargetIdInDefinition() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", "arrival"), null);

        assertEquals("lobby", result.definition().id());
    }

    @Test
    void planWithBlankTargetDefaultAndNullRawArrivalReturnsBlankArrival() {
        ResolvedDestinationTarget result = planner.plan(target("lobby", "world", ""), null);

        assertEquals("", result.effectiveArrivalPointId());
    }
}
