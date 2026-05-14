package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupMode;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public record InventoryOutboundTransferPlan(
    @Nonnull Action action,
    @Nonnull String transferId,
    @Nonnull UUID playerUuid,
    @Nullable InventoryTransferState inventoryState,
    @Nonnull String destinationConnectionAddress,
    @Nonnull String destinationTargetId,
    @Nonnull String travelProfileId
) {

    public enum Action {
        IGNORE,
        TRANSFER_WITHOUT_BACKUP,
        BACKUP_CLEAR_AND_TRANSFER
    }

    public boolean shouldSaveBackup() {
        return action == Action.BACKUP_CLEAR_AND_TRANSFER;
    }

    public boolean shouldClearOriginInventory() {
        return action == Action.BACKUP_CLEAR_AND_TRANSFER;
    }

    public boolean shouldIncludeInventoryState() {
        return action == Action.TRANSFER_WITHOUT_BACKUP
            || action == Action.BACKUP_CLEAR_AND_TRANSFER;
    }

    @Nonnull
    public InventoryTransferBackupRecord toBackupRecord(long createdAtEpochMs) {
        return new InventoryTransferBackupRecord(
            transferId,
            createdAtEpochMs,
            playerUuid,
            InventoryTransferBackupMode.ORIGIN_QUERY.id(),
            destinationConnectionAddress,
            destinationTargetId,
            travelProfileId,
            inventoryState
        );
    }
}
