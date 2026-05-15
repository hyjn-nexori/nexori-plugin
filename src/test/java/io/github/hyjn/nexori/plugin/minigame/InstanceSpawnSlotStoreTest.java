package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InstanceSpawnSlotStoreTest {

    private static InstanceSpawnSlotDefinition slot(String slotId) {
        return new InstanceSpawnSlotDefinition(
            slotId, "template-1",
            1.0, 64.0, 2.0, 0f, 90f, 0f, 1000L
        );
    }

    @Test
    void loadOrCreateMissingFileCreatesEmptyFileAndReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        List<InstanceSpawnSlotDefinition> result = store.loadOrCreate();

        assertTrue(result.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateBlankFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        assertTrue(store.loadOrCreate().isEmpty());
    }

    @Test
    void loadOrCreateNullDocumentReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        Files.writeString(file, "null", StandardCharsets.UTF_8);
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        assertTrue(store.loadOrCreate().isEmpty());
    }

    @Test
    void loadOrCreateNullSlotInListThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"spawnSlots\":[null]}", StandardCharsets.UTF_8);
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "List.copyOf() in SpawnSlotConfigDocument compact constructor throws NullPointerException on null elements");
    }

    @Test
    void saveAndLoadOrCreateRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        store.save(List.of(slot("slot-1"), slot("slot-2")));
        List<InstanceSpawnSlotDefinition> loaded = store.loadOrCreate();

        assertEquals(2, loaded.size());
        assertEquals("slot-1", loaded.get(0).slotId());
        assertEquals("slot-2", loaded.get(1).slotId());
    }

    @Test
    void loadOrCreateNormalizesSlots(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        store.save(List.of(slot("  SLOT-A  ")));
        List<InstanceSpawnSlotDefinition> loaded = store.loadOrCreate();

        assertEquals("slot-a", loaded.get(0).slotId());
    }

    @Test
    void storeCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/dir/spawn-slots.json");
        new InstanceSpawnSlotStore(file).loadOrCreate();
        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("spawn-slots.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        InstanceSpawnSlotStore store = new InstanceSpawnSlotStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate);
    }
}
