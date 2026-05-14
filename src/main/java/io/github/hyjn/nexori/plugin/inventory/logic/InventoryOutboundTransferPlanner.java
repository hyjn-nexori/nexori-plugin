package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class InventoryOutboundTransferPlanner {

    @Nonnull
    public InventoryOutboundTransferPlan plan(
        @Nonnull TravelProfileType profileType,
        @Nullable String transferId,
        @Nonnull UUID playerUuid,
        @Nullable InventoryTransferState capturedInventory,
        boolean hasTransferableInventory,
        @Nullable String destinationConnectionAddress,
        @Nullable String destinationTargetId
    ) {
        if (profileType != TravelProfileType.APPLY_INVENTORY || capturedInventory == null) {
            return ignore(profileType, transferId, playerUuid, capturedInventory, destinationConnectionAddress, destinationTargetId);
        }
        if (transferId == null || transferId.isBlank()) {
            return ignore(profileType, transferId, playerUuid, capturedInventory, destinationConnectionAddress, destinationTargetId);
        }
        if (!hasTransferableInventory) {
            return new InventoryOutboundTransferPlan(
                InventoryOutboundTransferPlan.Action.TRANSFER_WITHOUT_BACKUP,
                transferId,
                playerUuid,
                capturedInventory,
                optional(destinationConnectionAddress),
                optional(destinationTargetId),
                profileType.id()
            );
        }
        return new InventoryOutboundTransferPlan(
            InventoryOutboundTransferPlan.Action.BACKUP_CLEAR_AND_TRANSFER,
            transferId,
            playerUuid,
            capturedInventory,
            optional(destinationConnectionAddress),
            optional(destinationTargetId),
            profileType.id()
        );
    }

    @Nonnull
    private static InventoryOutboundTransferPlan ignore(
        @Nonnull TravelProfileType profileType,
        @Nullable String transferId,
        @Nonnull UUID playerUuid,
        @Nullable InventoryTransferState capturedInventory,
        @Nullable String destinationConnectionAddress,
        @Nullable String destinationTargetId
    ) {
        return new InventoryOutboundTransferPlan(
            InventoryOutboundTransferPlan.Action.IGNORE,
            optional(transferId),
            playerUuid,
            capturedInventory,
            optional(destinationConnectionAddress),
            optional(destinationTargetId),
            profileType.id()
        );
    }

    @Nonnull
    private static String optional(@Nullable String rawValue) {
        return rawValue == null ? "" : rawValue;
    }
}
