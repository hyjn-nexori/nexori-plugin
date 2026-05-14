package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;

import javax.annotation.Nonnull;
import java.util.List;

public final class InventoryBackupRetentionPlanner {

    @Nonnull
    public InventoryBackupRetentionPlan plan(
        @Nonnull List<InventoryTransferBackupRecord> backupsForPlayer,
        int maxBackupsPerPlayer
    ) {
        int effectiveMaxBackups = Math.max(1, maxBackupsPerPlayer);
        List<String> transferIdsToRemove = backupsForPlayer.stream()
            .skip(effectiveMaxBackups)
            .map(InventoryTransferBackupRecord::transferId)
            .toList();
        return InventoryBackupRetentionPlan.of(effectiveMaxBackups, transferIdsToRemove);
    }
}
