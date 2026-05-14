package io.github.hyjn.nexori.plugin.travel.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.travel.SecureTravelPayload;

import javax.annotation.Nonnull;

/**
 * Builds the pure secure travel dispatch payload while callers keep runtime, signing and inventory side effects.
 */
public final class SecureTravelDispatchPlanner {

    @Nonnull
    public TravelProfileType travelProfileType(String rawTravelProfileId) {
        return TravelProfileType.parse(rawTravelProfileId);
    }

    public boolean requiresInventoryCapture(String rawTravelProfileId) {
        return travelProfileType(rawTravelProfileId) == TravelProfileType.APPLY_INVENTORY;
    }

    @Nonnull
    public SecureTravelDispatchPlan plan(
        @Nonnull String operationId,
        @Nonnull String sourceServerId,
        @Nonnull String destinationTargetId,
        @Nonnull String arrivalPointId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson,
        @Nonnull String inventoryTransferId,
        InventoryTransferState inventoryState
    ) {
        TravelProfileType profileType = travelProfileType(travelProfileId);
        String normalizedContextJson = contextJson == null || contextJson.isBlank() ? "{}" : contextJson;
        String normalizedInventoryTransferId = inventoryTransferId == null ? "" : inventoryTransferId;
        SecureTravelPayload payload = new SecureTravelPayload(
            operationId,
            sourceServerId,
            "",
            destinationTargetId,
            arrivalPointId,
            profileType.id(),
            "Secure travel accepted from " + sourceServerId + ".",
            normalizedContextJson,
            normalizedInventoryTransferId,
            inventoryState
        );
        return new SecureTravelDispatchPlan(
            profileType,
            payload,
            profileType == TravelProfileType.APPLY_INVENTORY
                && inventoryState != null
                && !normalizedInventoryTransferId.isBlank()
        );
    }
}
