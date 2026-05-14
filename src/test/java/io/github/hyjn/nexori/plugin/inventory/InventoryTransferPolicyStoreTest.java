package io.github.hyjn.nexori.plugin.inventory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryTransferPolicyStoreTest {

    @Test
    void freshStoreHasDefaultsApplyEnabled(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        InventoryTransferPolicyStore store = new InventoryTransferPolicyStore(file);

        assertTrue(store.isApplyInventoryBackupsEnabled());
    }

    @Test
    void freshStoreHasDefaultMaxBackups(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        InventoryTransferPolicyStore store = new InventoryTransferPolicyStore(file);

        assertEquals(InventoryTransferPolicyConfigDocument.DEFAULT_MAX_BACKUPS_PER_PLAYER,
            store.getMaxBackupsPerPlayer());
    }

    @Test
    void freshStoreCreatesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        new InventoryTransferPolicyStore(file);

        assertTrue(Files.exists(file));
    }

    @Test
    void setApplyEnabledPersistsAcrossStoreInstances(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        InventoryTransferPolicyStore store = new InventoryTransferPolicyStore(file);
        store.setApplyInventoryBackupsEnabled(false);

        InventoryTransferPolicyStore reloaded = new InventoryTransferPolicyStore(file);
        assertFalse(reloaded.isApplyInventoryBackupsEnabled());
    }

    @Test
    void setMaxBackupsPersistsAcrossStoreInstances(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        InventoryTransferPolicyStore store = new InventoryTransferPolicyStore(file);
        store.setMaxBackupsPerPlayer(10);

        InventoryTransferPolicyStore reloaded = new InventoryTransferPolicyStore(file);
        assertEquals(10, reloaded.getMaxBackupsPerPlayer());
    }

    @Test
    void getMaxBackupsPerPlayerClampsZeroToOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        Files.writeString(file,
            "{\"schemaVersion\":2,\"applyInventoryBackupsEnabled\":true,\"maxBackupsPerPlayer\":0}",
            StandardCharsets.UTF_8);
        InventoryTransferPolicyStore store = new InventoryTransferPolicyStore(file);

        assertTrue(store.getMaxBackupsPerPlayer() >= 1,
            "maxBackupsPerPlayer should be clamped to >=1: " + store.getMaxBackupsPerPlayer());
    }

    @Test
    void setMaxBackupsOfZeroIsClampedToOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("policy.json");
        InventoryTransferPolicyStore store = new InventoryTransferPolicyStore(file);
        store.setMaxBackupsPerPlayer(0);

        assertEquals(1, store.getMaxBackupsPerPlayer());
    }

    @Test
    void storeCreatesParentDirectoriesIfMissing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/config/policy.json");
        new InventoryTransferPolicyStore(file);

        assertTrue(Files.exists(file));
    }
}
