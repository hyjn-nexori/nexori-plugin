package io.github.hyjn.nexori.plugin.inventory;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class InventoryTransferConfigDocumentsTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void defaultsUseCurrentSchemaVersion() {
        assertEquals(
            InventoryTransferPolicyConfigDocument.CURRENT_SCHEMA_VERSION,
            InventoryTransferPolicyConfigDocument.defaults().schemaVersion()
        );
    }

    @Test
    void defaultsEnableApplyInventoryBackups() {
        assertEquals(true, InventoryTransferPolicyConfigDocument.defaults().applyInventoryBackupsEnabled());
    }

    @Test
    void defaultsUseExpectedMaxBackupsPerPlayer() {
        assertEquals(
            InventoryTransferPolicyConfigDocument.DEFAULT_MAX_BACKUPS_PER_PLAYER,
            InventoryTransferPolicyConfigDocument.defaults().maxBackupsPerPlayer()
        );
    }

    @Test
    void backupCurrentSchemaVersionIsStable() {
        assertEquals(2, InventoryTransferBackupConfigDocument.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void backupDocumentPreservesProvidedBackupsList() {
        List<InventoryTransferBackupRecord> backups = List.of(backupRecord("transfer-1"));
        InventoryTransferBackupConfigDocument document = new InventoryTransferBackupConfigDocument(2, backups);

        assertSame(backups, document.backups());
    }

    @Test
    void receiptCurrentSchemaVersionIsStable() {
        assertEquals(1, InventoryTransferReceiptConfigDocument.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void receiptDocumentPreservesProvidedReceiptsList() {
        List<InventoryTransferReceiptRecord> receipts = List.of(receiptRecord("transfer-1"));
        InventoryTransferReceiptConfigDocument document = new InventoryTransferReceiptConfigDocument(1, receipts);

        assertSame(receipts, document.receipts());
    }

    @Test
    void backupModeParseKnownModesAccordingToCurrentBehavior() {
        assertEquals(InventoryTransferBackupMode.ORIGIN_QUERY, InventoryTransferBackupMode.parse(" origin_query "));
        assertEquals(InventoryTransferBackupMode.LOCAL_RESTORE, InventoryTransferBackupMode.parse("LOCAL_RESTORE"));
    }

    @Test
    void backupModeParseBlankModeAccordingToCurrentBehavior() {
        assertEquals(InventoryTransferBackupMode.ORIGIN_QUERY, InventoryTransferBackupMode.parse(null));
        assertEquals(InventoryTransferBackupMode.ORIGIN_QUERY, InventoryTransferBackupMode.parse("   "));
    }

    @Test
    void backupModeParseUnknownModeAccordingToCurrentBehavior() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> InventoryTransferBackupMode.parse("mystery")
        );

        assertEquals("Unknown Nexori inventory backup mode: mystery", exception.getMessage());
    }

    @Test
    void backupModeIdsAreStable() {
        assertEquals("origin_query", InventoryTransferBackupMode.ORIGIN_QUERY.id());
        assertEquals("local_restore", InventoryTransferBackupMode.LOCAL_RESTORE.id());
    }

    private static InventoryTransferBackupRecord backupRecord(String transferId) {
        return new InventoryTransferBackupRecord(
            transferId,
            100L,
            PLAYER_ONE,
            "origin_query",
            "server-one",
            "target-one",
            "keep_inventory",
            emptyInventory()
        );
    }

    private static InventoryTransferReceiptRecord receiptRecord(String transferId) {
        return new InventoryTransferReceiptRecord(
            transferId,
            100L,
            PLAYER_ONE,
            "origin-one",
            "server-one"
        );
    }

    private static InventoryTransferState emptyInventory() {
        return new InventoryTransferState(
            1,
            emptyContainer("storage", 10),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", 9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", 20),
            emptyContainer("tool", 1),
            0,
            0,
            0
        );
    }

    private static ContainerTransferState emptyContainer(String id, int capacity) {
        return new ContainerTransferState(id, capacity, java.util.Map.of());
    }
}
