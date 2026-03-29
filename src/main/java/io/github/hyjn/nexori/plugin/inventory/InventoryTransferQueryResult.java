package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum InventoryTransferQueryResult {
    APPLIED,
    NOT_FOUND;

    @Nonnull
    public static InventoryTransferQueryResult parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return NOT_FOUND;
        }
        return switch (rawValue.trim().toUpperCase(Locale.ROOT)) {
            case "APPLIED" -> APPLIED;
            case "NOT_FOUND" -> NOT_FOUND;
            default -> throw new IllegalArgumentException("Unknown Nexori inventory transfer query result '" + rawValue + "'.");
        };
    }
}
