package io.github.hyjn.nexori.plugin.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryTransferBackupStoreTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path tempDir;

    @Test
    void constructorCreatesMissingFile() throws IOException {
        Path file = tempDir.resolve("nested/backups.json");

        new InventoryTransferBackupStore(file);

        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("\"backups\""));
    }

    @Test
    void saveNormalizesAndPersistsRecord() throws IOException {
        Path file = tempDir.resolve("backups.json");
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(file);

        InventoryTransferBackupRecord saved = store.save(record(" Transfer-1 ", 100L, PLAYER_ONE));
        InventoryTransferBackupStore reloaded = new InventoryTransferBackupStore(file);

        assertEquals("transfer-1", saved.transferId());
        assertEquals("manual", saved.backupModeId());
        assertEquals("server-one", saved.destinationConnectionAddress());
        assertEquals("target-one", saved.destinationTargetId());
        assertEquals("clear_inventory", saved.travelProfileId());
        assertEquals(saved, reloaded.find("transfer-1").orElseThrow());
    }

    @Test
    void saveRejectsBlankTransferId() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.save(record("   ", 100L, PLAYER_ONE))
        );

        assertEquals("Inventory transfer id cannot be blank.", exception.getMessage());
    }

    @Test
    void saveRejectsNullPlayerUuid() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.save(new InventoryTransferBackupRecord(
                "transfer-1",
                100L,
                null,
                "manual",
                "server-one",
                "target-one",
                "keep_inventory",
                inventory()
            ))
        );

        assertEquals("Inventory transfer backup player UUID cannot be null.", exception.getMessage());
    }

    @Test
    void saveRejectsNullInventoryState() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.save(new InventoryTransferBackupRecord(
                "transfer-1",
                100L,
                PLAYER_ONE,
                "manual",
                "server-one",
                "target-one",
                "keep_inventory",
                null
            ))
        );

        assertEquals("Inventory transfer backup inventory state cannot be null.", exception.getMessage());
    }

    @Test
    void findTrimsAndLowercasesTransferId() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));
        store.save(record("transfer-1", 100L, PLAYER_ONE));

        assertTrue(store.find(" TRANSFER-1 ").isPresent());
    }

    @Test
    void saveReplacesExistingTransferId() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));

        store.save(record("transfer-1", 100L, PLAYER_ONE));
        store.save(record(" TRANSFER-1 ", 200L, PLAYER_TWO));

        assertEquals(1, store.listAll().size());
        assertEquals(PLAYER_TWO, store.find("transfer-1").orElseThrow().playerUuid());
    }

    @Test
    void listByPlayerReturnsNewestFirst() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));
        store.save(record("old", 100L, PLAYER_ONE));
        store.save(record("other-player", 300L, PLAYER_TWO));
        store.save(record("new", 200L, PLAYER_ONE));

        List<InventoryTransferBackupRecord> records = store.listByPlayer(PLAYER_ONE);

        assertEquals(List.of("new", "old"), records.stream().map(InventoryTransferBackupRecord::transferId).toList());
    }

    @Test
    void listAllPreservesInsertionOrder() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));
        store.save(record("one", 100L, PLAYER_ONE));
        store.save(record("two", 200L, PLAYER_ONE));
        store.save(record("three", 300L, PLAYER_ONE));

        assertEquals(List.of("one", "two", "three"), store.listAll().stream().map(InventoryTransferBackupRecord::transferId).toList());
    }

    @Test
    void removeDeletesExistingRecordAndPersists() throws IOException {
        Path file = tempDir.resolve("backups.json");
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(file);
        store.save(record("one", 100L, PLAYER_ONE));
        store.save(record("two", 200L, PLAYER_ONE));

        assertTrue(store.remove(" ONE "));

        InventoryTransferBackupStore reloaded = new InventoryTransferBackupStore(file);
        assertTrue(reloaded.find("one").isEmpty());
        assertTrue(reloaded.find("two").isPresent());
    }

    @Test
    void removeMissingReturnsFalse() throws IOException {
        InventoryTransferBackupStore store = new InventoryTransferBackupStore(tempDir.resolve("backups.json"));

        assertFalse(store.remove("missing"));
    }

    @Test
    void blankFileIsRewrittenAsEmptyDocument() throws IOException {
        Path file = tempDir.resolve("backups.json");
        Files.writeString(file, "   ");

        InventoryTransferBackupStore store = new InventoryTransferBackupStore(file);

        assertTrue(store.listAll().isEmpty());
        assertTrue(Files.readString(file).contains("\"backups\""));
    }

    @Test
    void nullBackupsDocumentIsRewrittenAsEmptyDocument() throws IOException {
        Path file = tempDir.resolve("backups.json");
        Files.writeString(file, "{\"schemaVersion\":2,\"backups\":null}");

        InventoryTransferBackupStore store = new InventoryTransferBackupStore(file);

        assertTrue(store.listAll().isEmpty());
        assertTrue(Files.readString(file).contains("\"backups\""));
    }

    @Test
    void loadSkipsNullRecords() throws IOException {
        Path file = tempDir.resolve("backups.json");
        Files.writeString(file, """
            {
              "schemaVersion": 2,
              "backups": [
                null,
                {
                  "transferId": " Transfer-1 ",
                  "createdAtEpochMs": 100,
                  "playerUuid": "11111111-1111-1111-1111-111111111111",
                  "backupModeId": " MANUAL ",
                  "destinationConnectionAddress": " Server-One ",
                  "destinationTargetId": " Target-One ",
                  "travelProfileId": " Clear_Inventory ",
                  "inventoryState": %s
                }
              ]
            }
            """.formatted(inventoryJson()));

        InventoryTransferBackupStore store = new InventoryTransferBackupStore(file);

        assertEquals(1, store.listAll().size());
        assertTrue(store.find("transfer-1").isPresent());
    }

    private static InventoryTransferBackupRecord record(String transferId, long createdAtEpochMs, UUID playerUuid) {
        return new InventoryTransferBackupRecord(
            transferId,
            createdAtEpochMs,
            playerUuid,
            " MANUAL ",
            " Server-One ",
            " Target-One ",
            " Clear_Inventory ",
            inventory()
        );
    }

    private static InventoryTransferState inventory() {
        return new InventoryTransferState(
            1,
            container("storage", 10),
            container("armor", 4),
            container("hotbar", 9),
            container("utility", 3),
            container("backpack", 20),
            container("tool", 1),
            0,
            0,
            0
        );
    }

    private static ContainerTransferState container(String id, int capacity) {
        return new ContainerTransferState(id, capacity, Map.of());
    }

    private static String inventoryJson() {
        return """
            {
              "version": 1,
              "storage": {"id": "storage", "capacity": 10, "items": {}},
              "armor": {"id": "armor", "capacity": 4, "items": {}},
              "hotBar": {"id": "hotbar", "capacity": 9, "items": {}},
              "utility": {"id": "utility", "capacity": 3, "items": {}},
              "backpack": {"id": "backpack", "capacity": 20, "items": {}},
              "tool": {"id": "tool", "capacity": 1, "items": {}},
              "activeHotbarSlot": 0,
              "activeToolsSlot": 0,
              "activeUtilitySlot": 0
            }
            """;
    }
}
