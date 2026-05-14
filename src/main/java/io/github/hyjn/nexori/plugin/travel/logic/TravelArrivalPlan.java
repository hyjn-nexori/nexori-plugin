package io.github.hyjn.nexori.plugin.travel.logic;

import io.github.hyjn.nexori.plugin.travel.PendingArrival;

import javax.annotation.Nonnull;
import java.util.Optional;

/**
 * Pure routing/build result for a secure travel arrival before runtime side effects are applied.
 */
public record TravelArrivalPlan(
    @Nonnull Route route,
    @Nonnull String effectiveTargetIdForErrors,
    PendingArrival pendingArrival
) {

    public enum Route {
        SERVER_HOP_WITHOUT_TARGET,
        RESOLVE_DEFAULT_WORLD_NATURAL_SPAWN,
        RESOLVE_CONFIGURED_TARGET,
        ACCEPT_RESOLVED_TARGET
    }

    @Nonnull
    public Optional<PendingArrival> pendingArrivalOptional() {
        return Optional.ofNullable(pendingArrival);
    }
}
