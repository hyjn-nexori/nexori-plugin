package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupMode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record InventoryRecoveryStartPlan(
    @Nonnull Action action,
    @Nullable InventoryTransferBackupMode backupMode,
    boolean remoteTravelStarted,
    @Nonnull String message
) {

    public enum Action {
        DISABLED,
        BACKUP_MISSING,
        LOCAL_RESTORE,
        REMOTE_QUERY
    }
}
