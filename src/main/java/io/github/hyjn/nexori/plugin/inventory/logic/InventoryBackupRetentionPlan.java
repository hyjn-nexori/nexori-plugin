package io.github.hyjn.nexori.plugin.inventory.logic;

import javax.annotation.Nonnull;
import java.util.List;

public record InventoryBackupRetentionPlan(
    int effectiveMaxBackupsPerPlayer,
    List<String> transferIdsToRemove
) {

    public InventoryBackupRetentionPlan {
        transferIdsToRemove = List.copyOf(transferIdsToRemove);
    }

    @Nonnull
    public static InventoryBackupRetentionPlan of(int effectiveMaxBackupsPerPlayer, @Nonnull List<String> transferIdsToRemove) {
        return new InventoryBackupRetentionPlan(effectiveMaxBackupsPerPlayer, transferIdsToRemove);
    }
}
