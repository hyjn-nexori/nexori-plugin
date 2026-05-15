package io.github.hyjn.nexori.plugin.bootstrap;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapRunStoreTest {

    private static BootstrapRun makeRun(long expiresAtEpochMillis) {
        return new BootstrapRun(
            "session-1", "player-1", "origin-1",
            expiresAtEpochMillis,
            BootstrapPhase.COLLECT_PROOFS,
            0,
            List.of(new ConfiguredPeer("Peer A", "peer-a.host", 5520)),
            List.of(),
            BootstrapMigrationPlan.empty(),
            null
        );
    }

    @Test
    void loadMissingFileReturnsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        BootstrapRunStore store = new BootstrapRunStore(file);

        BootstrapRun run = store.load();

        assertNull(run);
    }

    @Test
    void loadBlankFileReturnsNull(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        BootstrapRunStore store = new BootstrapRunStore(file);

        BootstrapRun run = store.load();

        assertNull(run);
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        BootstrapRunStore store = new BootstrapRunStore(file);
        BootstrapRun original = makeRun(System.currentTimeMillis() + 60_000L);

        store.save(original);
        BootstrapRun loaded = store.load();

        assertNotNull(loaded);
        assertEquals(original.sessionId(), loaded.sessionId());
        assertEquals(original.startedByPlayerUuid(), loaded.startedByPlayerUuid());
        assertEquals(original.phase(), loaded.phase());
    }

    @Test
    void getCurrentRunClearsExpiredRunAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        BootstrapRunStore store = new BootstrapRunStore(file);
        BootstrapRun expired = makeRun(1L); // far past

        store.save(expired);
        BootstrapRun current = store.getCurrentRun();

        assertNull(current, "getCurrentRun() should clear and return null for expired run");
        assertTrue(Files.notExists(file), "File should be deleted after clearing expired run");
    }

    @Test
    void clearDeletesFileAndCurrentRun(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        BootstrapRunStore store = new BootstrapRunStore(file);
        store.save(makeRun(System.currentTimeMillis() + 60_000L));

        store.clear();

        assertNull(store.getCurrentRun());
        assertTrue(Files.notExists(file));
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        BootstrapRunStore store = new BootstrapRunStore(file);

        assertThrows(RuntimeException.class, store::load,
            "Corrupt JSON in run store should throw RuntimeException (JsonSyntaxException)");
    }

    @Test
    void saveCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/dir/run.json");
        BootstrapRunStore store = new BootstrapRunStore(file);

        store.save(makeRun(System.currentTimeMillis() + 60_000L));

        assertTrue(Files.exists(file));
    }

    @Test
    void saveOverwritesExistingRun(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("run.json");
        BootstrapRunStore store = new BootstrapRunStore(file);
        store.save(makeRun(System.currentTimeMillis() + 60_000L));

        BootstrapRun updated = new BootstrapRun(
            "session-NEW", "player-2", "origin-2",
            System.currentTimeMillis() + 120_000L,
            BootstrapPhase.INSTALL_BUNDLE,
            0, List.of(), List.of(), BootstrapMigrationPlan.empty(), null
        );
        store.save(updated);
        BootstrapRun loaded = store.load();

        assertEquals("session-NEW", loaded.sessionId());
        assertEquals(BootstrapPhase.INSTALL_BUNDLE, loaded.phase());
    }
}
