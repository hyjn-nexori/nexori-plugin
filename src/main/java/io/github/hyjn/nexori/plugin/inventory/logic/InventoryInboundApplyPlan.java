package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public record InventoryInboundApplyPlan(
    @Nonnull Action action,
    @Nonnull String transferId,
    @Nullable InventoryTransferState inventoryState,
    @Nonnull String sourceServerId,
    @Nonnull String sourceConnectionAddress
) {

    public enum Action {
        IGNORE,
        CLEAR_INVENTORY,
        APPLY_AND_CREATE_RECEIPT
    }

    public boolean shouldApplyInventory() {
        return action == Action.APPLY_AND_CREATE_RECEIPT;
    }

    public boolean shouldSaveReceipt() {
        return action == Action.APPLY_AND_CREATE_RECEIPT;
    }

    @Nonnull
    public InventoryTransferReceiptRecord toReceiptRecord(@Nonnull UUID playerUuid, long createdAtEpochMs) {
        return new InventoryTransferReceiptRecord(
            transferId,
            createdAtEpochMs,
            playerUuid,
            sourceServerId,
            sourceConnectionAddress
        );
    }
}
