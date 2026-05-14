package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrustBundleStoreTest {

    @Test
    void loadOrCreateOnMissingFileReturnsInitial(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trust-bundle.json");
        TrustBundleStore store = new TrustBundleStore(file);

        TrustBundle loaded = store.loadOrCreate();

        assertNotNull(loaded);
        assertEquals(0L, loaded.bundleVersion());
        assertTrue(loaded.members().isEmpty());
    }

    @Test
    void loadOrCreateOnMissingFileCreatesFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trust-bundle.json");
        TrustBundleStore store = new TrustBundleStore(file);

        store.loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void getCurrentBundleBeforeLoadReturnsInitial(@TempDir Path dir) {
        Path file = dir.resolve("trust-bundle.json");
        TrustBundleStore store = new TrustBundleStore(file);

        TrustBundle current = store.getCurrentBundle();

        assertNotNull(current);
        assertEquals(TrustBundle.initial().bundleVersion(), current.bundleVersion());
    }

    @Test
    void loadOrCreateOnBlankFileReturnsInitial(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trust-bundle.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        TrustBundleStore store = new TrustBundleStore(file);

        TrustBundle loaded = store.loadOrCreate();

        assertNotNull(loaded);
        assertEquals(0L, loaded.bundleVersion());
    }

    @Test
    void loadOrCreateWithSavedBundleRestoresIt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trust-bundle.json");
        Files.writeString(file,
            "{\"bundleVersion\":42,\"bundleHash\":\"hash-abc\",\"updatedAtEpochMillis\":1000,\"members\":[]}",
            StandardCharsets.UTF_8);
        TrustBundleStore store = new TrustBundleStore(file);

        TrustBundle loaded = store.loadOrCreate();

        assertEquals(42L, loaded.bundleVersion());
        assertEquals("hash-abc", loaded.bundleHash());
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("trust-bundle.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        TrustBundleStore store = new TrustBundleStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "Corrupt JSON should throw RuntimeException (JsonSyntaxException)");
    }
}
