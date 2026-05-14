package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapStateStoreTest {

    @Test
    void loadOrCreateOnMissingDirReturnsInitialState(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);

        BootstrapState state = store.loadOrCreate();

        assertNotNull(state);
        assertFalse(state.bootstrapOpen());
        assertEquals(0L, state.bundleVersion());
    }

    @Test
    void loadOrCreateCreatesStateFile(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);

        store.loadOrCreate();

        assertTrue(Files.exists(stateDir.resolve("bootstrap-state.properties")));
    }

    @Test
    void openSessionSetsBootstrapOpenTrue(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);
        store.loadOrCreate();

        BootstrapState state = store.openSession(Duration.ofMinutes(5));

        assertTrue(state.bootstrapOpen());
        assertFalse(state.sessionId().isBlank());
        assertTrue(state.hasActiveSession());
    }

    @Test
    void closeSessionSetsBootstrapOpenFalse(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);
        store.loadOrCreate();
        store.openSession(Duration.ofMinutes(5));

        BootstrapState state = store.closeSession();

        assertFalse(state.bootstrapOpen());
        assertFalse(state.hasActiveSession());
    }

    @Test
    void recordFailureSetsLastRunFailedTrue(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);
        store.loadOrCreate();

        BootstrapState state = store.recordFailure("Something went wrong");

        assertTrue(state.lastRunFailed());
        assertEquals("Something went wrong", state.lastRunMessage());
    }

    @Test
    void markBundleInstalledIncrementsBundleVersion(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);
        store.loadOrCreate();

        store.markBundleInstalled("hash-1");
        BootstrapState state = store.markBundleInstalled("hash-2");

        assertEquals(2L, state.bundleVersion());
        assertEquals("hash-2", state.bundleHash());
    }

    @Test
    void stateRoundTripsSurvivesReinstantiation(@TempDir Path dir) throws IOException {
        Path stateDir = dir.resolve("bootstrap");
        BootstrapStateStore store = new BootstrapStateStore(stateDir);
        store.loadOrCreate();
        store.markBundleInstalled("hash-abc");

        BootstrapStateStore reloaded = new BootstrapStateStore(stateDir);
        BootstrapState state = reloaded.loadOrCreate();

        assertEquals(1L, state.bundleVersion());
        assertEquals("hash-abc", state.bundleHash());
    }
}
