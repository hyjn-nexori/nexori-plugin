package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum InventoryTransferBackupMode {
    ORIGIN_QUERY("origin_query"),
    LOCAL_RESTORE("local_restore");

    private final String id;

    InventoryTransferBackupMode(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public static InventoryTransferBackupMode parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return ORIGIN_QUERY;
        }

        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        for (InventoryTransferBackupMode value : values()) {
            if (value.id.equals(normalized)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Unknown Nexori inventory backup mode: " + rawValue);
    }
}
