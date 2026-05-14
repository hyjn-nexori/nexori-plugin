package io.github.hyjn.nexori.plugin.target.logic;

import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;

import javax.annotation.Nonnull;

public final class DestinationTargetResolutionPlanner {

    @Nonnull
    public ResolvedDestinationTarget plan(@Nonnull DestinationTargetDefinition target, String rawArrivalPointId) {
        String effectiveArrivalPointId = rawArrivalPointId == null ? "" : rawArrivalPointId.trim();
        if (effectiveArrivalPointId.isBlank()) {
            effectiveArrivalPointId = target.arrivalPointId();
        }
        return new ResolvedDestinationTarget(target, target.worldName(), effectiveArrivalPointId);
    }
}
