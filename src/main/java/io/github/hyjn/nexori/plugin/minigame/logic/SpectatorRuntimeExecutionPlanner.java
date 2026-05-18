package io.github.hyjn.nexori.plugin.minigame.logic;

import javax.annotation.Nonnull;

public final class SpectatorRuntimeExecutionPlanner {

    @Nonnull
    public SpectatorRuntimeExecutionDecision plan(boolean storeAvailable, boolean storeInThread, boolean storeProcessing, boolean worldAvailable) {
        if (storeAvailable && worldAvailable && (!storeInThread || storeProcessing)) {
            return SpectatorRuntimeExecutionDecision.SCHEDULE_ON_WORLD;
        }
        return SpectatorRuntimeExecutionDecision.RUN_NOW;
    }
}
