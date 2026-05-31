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

final class ArenaStoreTest {

    private static ArenaDefinition arena(String id) {
        return new ArenaDefinition(id, "Arena " + id, "srv.example.com:25565", "hub-1",
            "none", "", 16, true);
    }

    @Test
    void loadOrCreateMissingFileCreatesEmptyFileAndReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        ArenaStore store = new ArenaStore(file);

        List<ArenaDefinition> result = store.loadOrCreate();

        assertTrue(result.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateBlankFileRewritesAndReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        ArenaStore store = new ArenaStore(file);

        List<ArenaDefinition> result = store.loadOrCreate();

        assertTrue(result.isEmpty());
    }

    @Test
    void loadOrCreateNullDocumentReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        Files.writeString(file, "null", StandardCharsets.UTF_8);
        ArenaStore store = new ArenaStore(file);

        List<ArenaDefinition> result = store.loadOrCreate();

        assertTrue(result.isEmpty());
    }

    @Test
    void loadOrCreateNullArenaInListThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"arenas\":[null]}", StandardCharsets.UTF_8);
        ArenaStore store = new ArenaStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "List.copyOf() in ArenaConfigDocument compact constructor throws NullPointerException on null elements");
    }

    @Test
    void saveAndLoadOrCreateRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        ArenaStore store = new ArenaStore(file);

        store.save(List.of(arena("arena-1"), arena("arena-2")));
        List<ArenaDefinition> loaded = store.loadOrCreate();

        assertEquals(2, loaded.size());
        assertEquals("arena-1", loaded.get(0).arenaId());
        assertEquals("arena-2", loaded.get(1).arenaId());
    }

    @Test
    void loadOrCreateNormalizesArenas(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        ArenaStore store = new ArenaStore(file);

        store.save(List.of(arena("  MY-ARENA  ")));
        List<ArenaDefinition> loaded = store.loadOrCreate();

        assertEquals("my-arena", loaded.get(0).arenaId());
    }

    @Test
    void storeCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/dir/arenas.json");
        ArenaStore store = new ArenaStore(file);

        store.loadOrCreate();

        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("arenas.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        ArenaStore store = new ArenaStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "Corrupt JSON propagates as JsonSyntaxException (RuntimeException)");
    }
}

