package io.github.hyjn.nexori.plugin.inventory;

import java.util.List;

public record InventoryTransferReceiptConfigDocument(
    int schemaVersion,
    List<InventoryTransferReceiptRecord> receipts
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
}
