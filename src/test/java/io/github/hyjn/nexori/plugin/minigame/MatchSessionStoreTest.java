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

final class MatchSessionStoreTest {

    private static MatchSessionState session(String matchId) {
        return new MatchSessionState(
            matchId, "queue-1", "arena-1", "lobby-1",
            "srv.example.com:25565", "hub-1", "profile-1",
            List.of(), List.of(),
            1000L, 1000L, 0L, 0L, ""
        );
    }

    @Test
    void loadOrCreateMissingFileCreatesEmptyFileAndReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sessions.json");
        MatchSessionStore store = new MatchSessionStore(file);

        List<MatchSessionState> result = store.loadOrCreate();

        assertTrue(result.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateBlankFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sessions.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        MatchSessionStore store = new MatchSessionStore(file);

        assertTrue(store.loadOrCreate().isEmpty());
    }

    @Test
    void loadOrCreateNullDocumentReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sessions.json");
        Files.writeString(file, "null", StandardCharsets.UTF_8);
        MatchSessionStore store = new MatchSessionStore(file);

        assertTrue(store.loadOrCreate().isEmpty());
    }

    @Test
    void loadOrCreateNullSessionInListThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sessions.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"sessions\":[null]}", StandardCharsets.UTF_8);
        MatchSessionStore store = new MatchSessionStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "List.copyOf() in MatchSessionConfigDocument compact constructor throws NullPointerException on null elements");
    }

    @Test
    void saveAndLoadOrCreateRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sessions.json");
        MatchSessionStore store = new MatchSessionStore(file);

        store.save(List.of(session("match-1"), session("match-2")));
        List<MatchSessionState> loaded = store.loadOrCreate();

        assertEquals(2, loaded.size());
        assertEquals("match-1", loaded.get(0).matchId());
        assertEquals("match-2", loaded.get(1).matchId());
    }

    @Test
    void storeCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/dir/sessions.json");
        new MatchSessionStore(file).loadOrCreate();
        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sessions.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        MatchSessionStore store = new MatchSessionStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate);
    }
}
