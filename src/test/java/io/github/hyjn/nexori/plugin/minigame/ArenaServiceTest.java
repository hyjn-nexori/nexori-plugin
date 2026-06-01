package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for the Games editor persistence bug: {@code ArenaService.upsert} used to
 * rebuild the {@link ArenaDefinition} through a constructor that dropped
 * {@code initialPlacementWindowSeconds}, so any saved value reverted to the default (60) on reload.
 */
final class ArenaServiceTest {

    private static ArenaService newService(Path dir, String connectionFileName, String arenasFileName) throws IOException {
        ArenaStore store = new ArenaStore(dir.resolve(arenasFileName));
        LocalConnectionAddressService local = new LocalConnectionAddressService(dir.resolve(connectionFileName));
        return new ArenaService(store, local);
    }

    private static ArenaDefinition game(String id, int initialPlacementWindowSeconds) {
        return new ArenaDefinition(
            id,
            "Game " + id,
            "srv.example.com:25565",
            "hub-1",
            "none",
            "skywars",
            initialPlacementWindowSeconds,
            16,
            true,
            AfkDetectionPolicy.defaults()
        );
    }

    @Test
    void upsertPreservesInitialPlacementWindowSeconds(@TempDir Path dir) throws IOException {
        ArenaService service = newService(dir, "connection.txt", "arenas.json");

        ArenaDefinition saved = service.upsert(game("g1", 10));

        assertEquals(10, saved.initialPlacementWindowSeconds());
        assertEquals(10, service.find("g1").orElseThrow().initialPlacementWindowSeconds());
    }

    @Test
    void updatingExistingGameKeepsNewInitialPlacementWindowSeconds(@TempDir Path dir) throws IOException {
        ArenaService service = newService(dir, "connection.txt", "arenas.json");

        service.upsert(game("g1", 60));
        // Edit the saved game: change 60 -> 10 and update.
        service.upsert(game("g1", 10));

        // Reopening the editor must show 10, not the default 60.
        assertEquals(10, service.find("g1").orElseThrow().initialPlacementWindowSeconds());
    }

    @Test
    void reloadFromDiskPreservesInitialPlacementWindowSeconds(@TempDir Path dir) throws IOException {
        ArenaService service = newService(dir, "connection.txt", "arenas.json");
        service.upsert(game("g1", 10));

        // Fresh service instance reading the same persisted file (simulates server restart / reload).
        ArenaService reloaded = newService(dir, "connection.txt", "arenas.json");

        assertEquals(10, reloaded.find("g1").orElseThrow().initialPlacementWindowSeconds());
    }

    @Test
    void blankOrInvalidValueFallsBackToDefault(@TempDir Path dir) throws IOException {
        ArenaService service = newService(dir, "connection.txt", "arenas.json");

        // 0 / negative is normalized to the default by ArenaDefinition.normalized().
        ArenaDefinition saved = service.upsert(game("g1", 0));

        assertEquals(ArenaDefinition.DEFAULT_INITIAL_PLACEMENT_WINDOW_SECONDS, saved.initialPlacementWindowSeconds());
    }
}
