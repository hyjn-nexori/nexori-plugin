package io.github.hyjn.nexori.plugin.peers;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfiguredPeerMigrationStoreTest {

    @Test
    void loadOnMissingFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);

        List<ConfiguredPeerMigrationEntry> loaded = store.load();

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveAndLoadRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);
        List<ConfiguredPeerMigrationEntry> entries = List.of(
            new ConfiguredPeerMigrationEntry("old.host:5520", "new.host:5520"),
            new ConfiguredPeerMigrationEntry("another.old:7001", "another.new:7001")
        );

        store.save(entries);
        List<ConfiguredPeerMigrationEntry> loaded = store.load();

        assertEquals(2, loaded.size());
        assertEquals("old.host:5520", loaded.get(0).oldConnectionAddress());
        assertEquals("new.host:5520", loaded.get(0).newConnectionAddress());
    }

    @Test
    void loadOnBlankFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);

        List<ConfiguredPeerMigrationEntry> loaded = store.load();

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/dir/migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);

        store.save(List.of());

        assertTrue(Files.exists(file));
    }

    @Test
    void overwriteSaveReplacesPreviousEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("migrations.json");
        ConfiguredPeerMigrationStore store = new ConfiguredPeerMigrationStore(file);

        store.save(List.of(new ConfiguredPeerMigrationEntry("old", "new")));
        store.save(List.of());

        List<ConfiguredPeerMigrationEntry> loaded = store.load();
        assertTrue(loaded.isEmpty());
    }
}
