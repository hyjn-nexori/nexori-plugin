package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class InventoryInboundApplyPlanner {

    @Nonnull
    public InventoryInboundApplyPlan plan(
        @Nonnull TravelProfileType profileType,
        @Nullable String transferId,
        @Nullable InventoryTransferState inventoryState,
        @Nullable String sourceServerId,
        @Nullable String sourceConnectionAddress
    ) {
        return switch (profileType) {
            case KEEP_INVENTORY -> ignore(transferId, inventoryState);
            case CLEAR_INVENTORY -> new InventoryInboundApplyPlan(
                InventoryInboundApplyPlan.Action.CLEAR_INVENTORY,
                optional(transferId),
                inventoryState,
                optional(sourceServerId),
                optional(sourceConnectionAddress)
            );
            case APPLY_INVENTORY -> planApplyInventory(
                transferId,
                inventoryState,
                sourceServerId,
                sourceConnectionAddress
            );
        };
    }

    @Nonnull
    private InventoryInboundApplyPlan planApplyInventory(
        @Nullable String transferId,
        @Nullable InventoryTransferState inventoryState,
        @Nullable String sourceServerId,
        @Nullable String sourceConnectionAddress
    ) {
        boolean hasTransferId = transferId != null && !transferId.isBlank();
        boolean hasInventorySnapshot = inventoryState != null;
        if (!hasTransferId && !hasInventorySnapshot) {
            return ignore(transferId, inventoryState);
        }
        if (!hasTransferId) {
            throw new IllegalArgumentException("APPLY_INVENTORY travel requires a transferId.");
        }
        if (!hasInventorySnapshot) {
            throw new IllegalArgumentException("APPLY_INVENTORY travel requires an inventory snapshot.");
        }

        String normalizedSourceServerId = optional(sourceServerId);
        String normalizedSourceConnectionAddress = optional(sourceConnectionAddress);
        return new InventoryInboundApplyPlan(
            InventoryInboundApplyPlan.Action.APPLY_AND_CREATE_RECEIPT,
            transferId,
            inventoryState,
            normalizedSourceServerId,
            normalizedSourceConnectionAddress
        );
    }

    @Nonnull
    private static InventoryInboundApplyPlan ignore(
        @Nullable String transferId,
        @Nullable InventoryTransferState inventoryState
    ) {
        return new InventoryInboundApplyPlan(
            InventoryInboundApplyPlan.Action.IGNORE,
            optional(transferId),
            inventoryState,
            "",
            ""
        );
    }

    @Nonnull
    private static String optional(@Nullable String rawValue) {
        return rawValue == null ? "" : rawValue;
    }
}
