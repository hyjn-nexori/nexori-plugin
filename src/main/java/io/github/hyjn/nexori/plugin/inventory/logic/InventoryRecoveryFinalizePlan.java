package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferQueryResult;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record InventoryRecoveryFinalizePlan(
    @Nonnull Action action,
    @Nullable InventoryTransferQueryResult result,
    boolean shouldRemoveBackup,
    boolean shouldRestoreBackup,
    boolean shouldCreatePendingReturn,
    @Nonnull String pendingReturnTransferId,
    @Nonnull String message
) {

    public enum Action {
        IGNORE,
        PENDING_VALID,
        FINALIZE
    }

    @Nonnull
    public static InventoryRecoveryFinalizePlan ignore() {
        return new InventoryRecoveryFinalizePlan(Action.IGNORE, null, false, false, false, "", "");
    }

    @Nonnull
    public static InventoryRecoveryFinalizePlan pendingValid() {
        return new InventoryRecoveryFinalizePlan(Action.PENDING_VALID, null, false, false, false, "", "");
    }
}
