package io.github.hyjn.nexori.plugin.travel.logic;

import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.SecureTravelPayload;

import javax.annotation.Nonnull;

/**
 * Plans pure secure travel arrival routing and pending-arrival construction.
 */
public final class TravelArrivalPlanner {

    @Nonnull
    public TravelArrivalPlan route(
        @Nonnull SecureTravelPayload payload,
        boolean shouldUseDefaultWorldNaturalSpawnEntry,
        boolean isMinigameLaunchContext
    ) {
        String effectiveTargetIdForErrors = normalizeOptional(payload.destinationTargetId());
        if (payload.destinationTargetId() == null || payload.destinationTargetId().isBlank()) {
            TravelArrivalPlan.Route route = shouldUseDefaultWorldNaturalSpawnEntry || isMinigameLaunchContext
                ? TravelArrivalPlan.Route.RESOLVE_DEFAULT_WORLD_NATURAL_SPAWN
                : TravelArrivalPlan.Route.SERVER_HOP_WITHOUT_TARGET;
            return new TravelArrivalPlan(route, effectiveTargetIdForErrors, null);
        }
        return new TravelArrivalPlan(
            TravelArrivalPlan.Route.RESOLVE_CONFIGURED_TARGET,
            effectiveTargetIdForErrors,
            null
        );
    }

    @Nonnull
    public TravelArrivalPlan acceptResolvedTarget(
        @Nonnull String operationId,
        @Nonnull SecureTravelPayload payload,
        @Nonnull TravelProfileType profileType,
        @Nonnull ResolvedDestinationTarget resolvedTarget
    ) {
        String arrivalMessage = resolvedTarget.definition().arrivalMessage().isBlank()
            ? payload.arrivalMessage()
            : resolvedTarget.definition().arrivalMessage();
        PendingArrival pendingArrival = new PendingArrival(
            operationId,
            payload.sourceServerId(),
            payload.sourceConnectionAddress(),
            resolvedTarget.definition().id(),
            resolvedTarget.definition().displayName(),
            resolvedTarget.definition().kind().name(),
            resolvedTarget.effectiveWorldName(),
            resolvedTarget.effectiveArrivalPointId(),
            profileType.id(),
            arrivalMessage,
            payload.contextJson(),
            resolvedTarget.definition().metadataJson()
        );
        return new TravelArrivalPlan(
            TravelArrivalPlan.Route.ACCEPT_RESOLVED_TARGET,
            resolvedTarget.definition().id(),
            pendingArrival
        );
    }

    @Nonnull
    public String buildArrivalMessage(@Nonnull PendingArrival arrival) {
        StringBuilder message = new StringBuilder();
        message.append(arrival.arrivalMessage().isBlank()
            ? "Secure Nexori travel accepted."
            : arrival.arrivalMessage());
        if (!arrival.destinationTargetId().isBlank()) {
            message.append(" target=").append(arrival.destinationTargetId());
        }
        if (!arrival.destinationTargetKind().isBlank()) {
            message.append(" kind=").append(arrival.destinationTargetKind());
        }
        if (!arrival.worldName().isBlank()) {
            message.append(" world=").append(arrival.worldName());
        }
        if (!arrival.arrivalPointId().isBlank()) {
            message.append(" arrivalPoint=").append(arrival.arrivalPointId());
        }
        if (!arrival.travelProfileId().isBlank()) {
            message.append(" travelProfile=").append(arrival.travelProfileId());
        }
        return message.toString();
    }

    @Nonnull
    private String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
