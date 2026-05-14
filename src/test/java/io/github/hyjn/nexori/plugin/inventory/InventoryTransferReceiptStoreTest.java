package io.github.hyjn.nexori.plugin.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryTransferReceiptStoreTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path tempDir;

    @Test
    void constructorCreatesMissingFile() throws IOException {
        Path file = tempDir.resolve("nested/receipts.json");

        new InventoryTransferReceiptStore(file);

        assertTrue(Files.exists(file));
        assertTrue(Files.readString(file).contains("\"receipts\""));
    }

    @Test
    void saveNormalizesAndPersistsRecord() throws IOException {
        Path file = tempDir.resolve("receipts.json");
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(file);

        InventoryTransferReceiptRecord saved = store.save(record(" Transfer-1 ", 100L, PLAYER_ONE));
        InventoryTransferReceiptStore reloaded = new InventoryTransferReceiptStore(file);

        assertEquals("transfer-1", saved.transferId());
        assertEquals("origin-one", saved.originServerId());
        assertEquals("server-one", saved.originConnectionAddress());
        assertEquals(saved, reloaded.find("transfer-1").orElseThrow());
    }

    @Test
    void saveRejectsBlankTransferId() throws IOException {
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(tempDir.resolve("receipts.json"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.save(record("   ", 100L, PLAYER_ONE))
        );

        assertEquals("Inventory transfer receipt id cannot be blank.", exception.getMessage());
    }

    @Test
    void saveRejectsNullPlayerUuid() throws IOException {
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(tempDir.resolve("receipts.json"));

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> store.save(new InventoryTransferReceiptRecord(
                "transfer-1",
                100L,
                null,
                "origin-one",
                "server-one"
            ))
        );

        assertEquals("Inventory transfer receipt player UUID cannot be null.", exception.getMessage());
    }

    @Test
    void findTrimsAndLowercasesTransferId() throws IOException {
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(tempDir.resolve("receipts.json"));
        store.save(record("transfer-1", 100L, PLAYER_ONE));

        assertTrue(store.find(" TRANSFER-1 ").isPresent());
    }

    @Test
    void saveReplacesExistingTransferId() throws IOException {
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(tempDir.resolve("receipts.json"));

        store.save(record("transfer-1", 100L, PLAYER_ONE));
        store.save(record(" TRANSFER-1 ", 200L, PLAYER_TWO));

        assertEquals(PLAYER_TWO, store.find("transfer-1").orElseThrow().playerUuid());
        assertEquals(200L, store.find("transfer-1").orElseThrow().createdAtEpochMs());
    }

    @Test
    void removeDeletesExistingRecordAndPersists() throws IOException {
        Path file = tempDir.resolve("receipts.json");
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(file);
        store.save(record("one", 100L, PLAYER_ONE));
        store.save(record("two", 200L, PLAYER_ONE));

        assertTrue(store.remove(" ONE "));

        InventoryTransferReceiptStore reloaded = new InventoryTransferReceiptStore(file);
        assertTrue(reloaded.find("one").isEmpty());
        assertTrue(reloaded.find("two").isPresent());
    }

    @Test
    void removeMissingReturnsFalse() throws IOException {
        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(tempDir.resolve("receipts.json"));

        assertFalse(store.remove("missing"));
    }

    @Test
    void blankFileIsRewrittenAsEmptyDocument() throws IOException {
        Path file = tempDir.resolve("receipts.json");
        Files.writeString(file, "   ");

        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(file);

        assertTrue(store.find("anything").isEmpty());
        assertTrue(Files.readString(file).contains("\"receipts\""));
    }

    @Test
    void nullReceiptsDocumentIsRewrittenAsEmptyDocument() throws IOException {
        Path file = tempDir.resolve("receipts.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"receipts\":null}");

        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(file);

        assertTrue(store.find("anything").isEmpty());
        assertTrue(Files.readString(file).contains("\"receipts\""));
    }

    @Test
    void loadSkipsNullRecords() throws IOException {
        Path file = tempDir.resolve("receipts.json");
        Files.writeString(file, """
            {
              "schemaVersion": 1,
              "receipts": [
                null,
                {
                  "transferId": " Transfer-1 ",
                  "createdAtEpochMs": 100,
                  "playerUuid": "11111111-1111-1111-1111-111111111111",
                  "originServerId": " Origin-One ",
                  "originConnectionAddress": " Server-One "
                }
              ]
            }
            """);

        InventoryTransferReceiptStore store = new InventoryTransferReceiptStore(file);

        assertTrue(store.find("transfer-1").isPresent());
        assertEquals("origin-one", store.find("transfer-1").orElseThrow().originServerId());
    }

    private static InventoryTransferReceiptRecord record(String transferId, long createdAtEpochMs, UUID playerUuid) {
        return new InventoryTransferReceiptRecord(
            transferId,
            createdAtEpochMs,
            playerUuid,
            " Origin-One ",
            " Server-One "
        );
    }
}
