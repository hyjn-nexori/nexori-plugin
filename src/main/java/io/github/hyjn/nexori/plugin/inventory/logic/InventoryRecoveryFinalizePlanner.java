package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferQueryResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public final class InventoryRecoveryFinalizePlanner {

    public static final String APPLIED_MESSAGE = "Nexori recovery confirmed that the destination already applied your inventory transfer.";
    public static final String MISSING_BACKUP_MESSAGE = "Nexori recovery could not restore that backup because it no longer exists on this origin server.";
    public static final String RESTORED_BACKUP_MESSAGE = "Nexori recovery restored your origin inventory because the destination did not report an applied transfer.";

    @Nonnull
    public InventoryRecoveryFinalizePlan planPending(
        @Nonnull UUID playerUuid,
        boolean pendingExists,
        boolean pendingExpired,
        @Nullable UUID pendingPlayerUuid
    ) {
        if (!pendingExists || pendingExpired || !playerUuid.equals(pendingPlayerUuid)) {
            return InventoryRecoveryFinalizePlan.ignore();
        }
        return InventoryRecoveryFinalizePlan.pendingValid();
    }

    @Nonnull
    public InventoryRecoveryFinalizePlan planFinalization(
        @Nonnull String transferId,
        @Nullable InventoryTransferBackupRecord backup,
        @Nullable String rawResult
    ) {
        InventoryTransferQueryResult result = InventoryTransferQueryResult.parse(rawResult);
        if (result == InventoryTransferQueryResult.APPLIED) {
            return new InventoryRecoveryFinalizePlan(
                InventoryRecoveryFinalizePlan.Action.FINALIZE,
                result,
                backup != null,
                false,
                true,
                "",
                APPLIED_MESSAGE
            );
        }
        if (backup == null) {
            return new InventoryRecoveryFinalizePlan(
                InventoryRecoveryFinalizePlan.Action.FINALIZE,
                result,
                false,
                false,
                true,
                "",
                MISSING_BACKUP_MESSAGE
            );
        }
        return new InventoryRecoveryFinalizePlan(
            InventoryRecoveryFinalizePlan.Action.FINALIZE,
            result,
            true,
            true,
            true,
            backup.transferId(),
            RESTORED_BACKUP_MESSAGE
        );
    }
}
