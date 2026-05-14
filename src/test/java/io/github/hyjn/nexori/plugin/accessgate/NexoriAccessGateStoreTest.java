package io.github.hyjn.nexori.plugin.accessgate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class NexoriAccessGateStoreTest {

    @Test
    void loadOrCreateOnMissingFileReturnsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("access-gate.json");
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);

        NexoriAccessGateConfigDocument loaded = store.loadOrCreate();

        assertNotNull(loaded);
        assertEquals(NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void loadOrCreateOnMissingFileCreatesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("access-gate.json");
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);

        store.loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateDefaultsHasExpectedMaxPlayers(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("access-gate.json");
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);

        NexoriAccessGateConfigDocument loaded = store.loadOrCreate();

        assertEquals(NexoriAccessGateConfigDocument.DEFAULT_MAX_PLAYERS, loaded.maxPlayers());
    }

    @Test
    void saveAndLoadOrCreateRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("access-gate.json");
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);
        UUID playerUuid = UUID.randomUUID();
        NexoriAccessGateConfigDocument doc = new NexoriAccessGateConfigDocument(
            NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION,
            true,
            100,
            5,
            "Server is full.",
            true,
            false,
            "",
            List.of(new NexoriAccessGateBypassPlayer(playerUuid.toString(), "TestPlayer"))
        );

        store.save(doc);
        NexoriAccessGateConfigDocument loaded = store.loadOrCreate();

        assertTrue(loaded.enabled());
        assertEquals(100, loaded.maxPlayers());
        assertEquals(5, loaded.reservedPrioritySlots());
        assertTrue(loaded.containsBypassUuid(playerUuid));
    }

    @Test
    void loadOrCreateOnBlankFileReturnsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("access-gate.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);

        NexoriAccessGateConfigDocument loaded = store.loadOrCreate();

        assertNotNull(loaded);
        assertEquals(NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/config/access-gate.json");
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);

        store.save(NexoriAccessGateConfigDocument.defaults());

        assertTrue(Files.exists(file));
    }

    @Test
    void savedDocumentNormalizesMaxPlayersMinimumToOne(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("access-gate.json");
        NexoriAccessGateStore store = new NexoriAccessGateStore(file);
        NexoriAccessGateConfigDocument doc = new NexoriAccessGateConfigDocument(
            NexoriAccessGateConfigDocument.CURRENT_SCHEMA_VERSION,
            false, 0, 0,
            NexoriAccessGateConfigDocument.DEFAULT_FULL_MESSAGE,
            false, false, "", List.of()
        );

        store.save(doc);
        NexoriAccessGateConfigDocument loaded = store.loadOrCreate();

        assertTrue(loaded.maxPlayers() >= 1, "maxPlayers should be clamped to at least 1: " + loaded.maxPlayers());
    }
}
