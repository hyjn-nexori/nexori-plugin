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

final class ConfiguredPeerStoreTest {

    @Test
    void loadOnMissingFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("peers.json");
        ConfiguredPeerStore store = new ConfiguredPeerStore(file);

        List<ConfiguredPeer> loaded = store.load();

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadOnMissingFileDoesNotCreateFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("peers.json");
        ConfiguredPeerStore store = new ConfiguredPeerStore(file);

        store.load();

        assertTrue(Files.notExists(file), "load() must not create file when missing");
    }

    @Test
    void saveAndLoadRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("peers.json");
        ConfiguredPeerStore store = new ConfiguredPeerStore(file);
        List<ConfiguredPeer> peers = List.of(
            new ConfiguredPeer("Server A", "host-a.internal", 5520),
            new ConfiguredPeer("Server B", "host-b.internal", 7001)
        );

        store.save(peers);
        List<ConfiguredPeer> loaded = store.load();

        assertEquals(2, loaded.size());
        assertEquals("host-a.internal", loaded.get(0).host());
        assertEquals("host-b.internal", loaded.get(1).host());
    }

    @Test
    void loadOnBlankFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("peers.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        ConfiguredPeerStore store = new ConfiguredPeerStore(file);

        List<ConfiguredPeer> loaded = store.load();

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/config/peers.json");
        ConfiguredPeerStore store = new ConfiguredPeerStore(file);

        store.save(List.of());

        assertTrue(Files.exists(file));
    }

    @Test
    void saveEmptyListAndLoadReturnsEmpty(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("peers.json");
        ConfiguredPeerStore store = new ConfiguredPeerStore(file);

        store.save(List.of());
        List<ConfiguredPeer> loaded = store.load();

        assertNotNull(loaded);
        assertTrue(loaded.isEmpty());
    }
}
