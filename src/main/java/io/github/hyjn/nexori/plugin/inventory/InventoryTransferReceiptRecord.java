package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

public record InventoryTransferReceiptRecord(
    String transferId,
    long createdAtEpochMs,
    UUID playerUuid,
    String originServerId,
    String originConnectionAddress
) {

    @Nonnull
    public InventoryTransferReceiptRecord normalized() {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Inventory transfer receipt player UUID cannot be null.");
        }
        return new InventoryTransferReceiptRecord(
            normalizeRequired(transferId, "Inventory transfer receipt id cannot be blank."),
            createdAtEpochMs,
            playerUuid,
            normalizeOptional(originServerId, ""),
            normalizeOptional(originConnectionAddress, "")
        );
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? defaultValue : normalized;
    }
}
