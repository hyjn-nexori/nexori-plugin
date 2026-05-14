package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupMode;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class InventoryRecoveryStartPlanner {

    public static final String DISABLED_MESSAGE = "Nexori inventory recovery is currently disabled by this server's admin.";
    public static final String BACKUP_MISSING_MESSAGE = "That Nexori inventory backup does not exist for your player.";
    public static final String LOCAL_RESTORE_MESSAGE = "Nexori claim restored your saved local inventory backup on this server.";
    public static final String REMOTE_QUERY_MESSAGE = "Started Nexori inventory recovery query. If the destination is reachable, you will be sent there and back to resolve it.";

    @Nonnull
    public InventoryRecoveryStartPlan plan(boolean recoveryEnabled, @Nullable InventoryTransferBackupRecord backup) {
        if (!recoveryEnabled) {
            return new InventoryRecoveryStartPlan(
                InventoryRecoveryStartPlan.Action.DISABLED,
                null,
                false,
                DISABLED_MESSAGE
            );
        }
        if (backup == null) {
            return new InventoryRecoveryStartPlan(
                InventoryRecoveryStartPlan.Action.BACKUP_MISSING,
                null,
                false,
                BACKUP_MISSING_MESSAGE
            );
        }

        InventoryTransferBackupMode backupMode = InventoryTransferBackupMode.parse(backup.backupModeId());
        if (backupMode == InventoryTransferBackupMode.LOCAL_RESTORE) {
            return new InventoryRecoveryStartPlan(
                InventoryRecoveryStartPlan.Action.LOCAL_RESTORE,
                backupMode,
                false,
                LOCAL_RESTORE_MESSAGE
            );
        }
        return new InventoryRecoveryStartPlan(
            InventoryRecoveryStartPlan.Action.REMOTE_QUERY,
            backupMode,
            true,
            REMOTE_QUERY_MESSAGE
        );
    }
}
