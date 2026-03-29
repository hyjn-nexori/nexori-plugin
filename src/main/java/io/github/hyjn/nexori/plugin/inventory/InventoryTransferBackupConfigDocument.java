package io.github.hyjn.nexori.plugin.inventory;

import java.util.List;

public record InventoryTransferBackupConfigDocument(
    int schemaVersion,
    List<InventoryTransferBackupRecord> backups
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
