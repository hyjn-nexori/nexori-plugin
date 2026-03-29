package io.github.hyjn.nexori.plugin.inventory;

public record InventoryTransferPolicyConfigDocument(
    int schemaVersion,
    boolean applyInventoryBackupsEnabled,
    int maxBackupsPerPlayer
) {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    public static final int DEFAULT_MAX_BACKUPS_PER_PLAYER = 5;

    public static InventoryTransferPolicyConfigDocument defaults() {
        return new InventoryTransferPolicyConfigDocument(
            CURRENT_SCHEMA_VERSION,
            true,
            DEFAULT_MAX_BACKUPS_PER_PLAYER
        );
    }
}
