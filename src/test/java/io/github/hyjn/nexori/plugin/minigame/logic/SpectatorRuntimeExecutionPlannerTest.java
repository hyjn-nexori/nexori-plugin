package io.github.hyjn.nexori.plugin.minigame.logic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class SpectatorRuntimeExecutionPlannerTest {

    private final SpectatorRuntimeExecutionPlanner planner = new SpectatorRuntimeExecutionPlanner();

    @Test
    void runNowWhenStoreMissing() {
        SpectatorRuntimeExecutionDecision result = planner.plan(false, false, false, true);

        assertEquals(SpectatorRuntimeExecutionDecision.RUN_NOW, result);
    }

    @Test
    void runNowWhenWorldMissingEvenIfStoreProcessing() {
        SpectatorRuntimeExecutionDecision result = planner.plan(true, true, true, false);

        assertEquals(SpectatorRuntimeExecutionDecision.RUN_NOW, result);
    }

    @Test
    void runNowWhenStoreIsInThreadAndNotProcessing() {
        SpectatorRuntimeExecutionDecision result = planner.plan(true, true, false, true);

        assertEquals(SpectatorRuntimeExecutionDecision.RUN_NOW, result);
    }

    @Test
    void scheduleWhenStoreProcessingAndWorldAvailable() {
        SpectatorRuntimeExecutionDecision result = planner.plan(true, true, true, true);

        assertEquals(SpectatorRuntimeExecutionDecision.SCHEDULE_ON_WORLD, result);
    }

    @Test
    void scheduleWhenStoreIsOffThreadAndWorldAvailable() {
        SpectatorRuntimeExecutionDecision result = planner.plan(true, false, false, true);

        assertEquals(SpectatorRuntimeExecutionDecision.SCHEDULE_ON_WORLD, result);
    }

    @Test
    void scheduleWhenStoreIsOffThreadAndProcessingWithWorldAvailable() {
        SpectatorRuntimeExecutionDecision result = planner.plan(true, false, true, true);

        assertEquals(SpectatorRuntimeExecutionDecision.SCHEDULE_ON_WORLD, result);
    }
}
