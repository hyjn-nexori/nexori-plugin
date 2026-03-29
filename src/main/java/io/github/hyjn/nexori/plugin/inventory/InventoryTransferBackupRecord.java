package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.UUID;

public record InventoryTransferBackupRecord(
    String transferId,
    long createdAtEpochMs,
    UUID playerUuid,
    String destinationConnectionAddress,
    String destinationTargetId,
    String travelProfileId,
    InventoryTransferState inventoryState
) {

    @Nonnull
    public InventoryTransferBackupRecord normalized() {
        if (playerUuid == null) {
            throw new IllegalArgumentException("Inventory transfer backup player UUID cannot be null.");
        }
        if (inventoryState == null) {
            throw new IllegalArgumentException("Inventory transfer backup inventory state cannot be null.");
        }
        String normalizedTransferId = normalizeRequired(transferId, "Inventory transfer id cannot be blank.");
        return new InventoryTransferBackupRecord(
            normalizedTransferId,
            createdAtEpochMs,
            playerUuid,
            normalizeOptional(destinationConnectionAddress, ""),
            normalizeOptional(destinationTargetId, ""),
            normalizeOptional(travelProfileId, "keep_inventory"),
            inventoryState
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
