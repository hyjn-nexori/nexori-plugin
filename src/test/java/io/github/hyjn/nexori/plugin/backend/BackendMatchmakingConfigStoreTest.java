package io.github.hyjn.nexori.plugin.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendMatchmakingConfigStoreTest {

    @Test
    void loadOrCreateOnMissingFileReturnsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);

        BackendMatchmakingConfig loaded = store.loadOrCreate();

        assertNotNull(loaded);
        assertEquals(BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void loadOrCreateCreatesFileWhenMissing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);

        store.loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void saveAndLoadOrCreateRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            true,
            "http://backend.example.com",
            "my-secret-token",
            2000L,
            "us-east",
            5000L,
            true,
            10000L
        );

        store.save(config);
        BackendMatchmakingConfig loaded = store.loadOrCreate();

        assertTrue(loaded.syncEnabled());
        assertTrue(loaded.baseUrl().contains("backend.example.com"));
        assertTrue(loaded.resultReportingEnabled());
        assertEquals(2000L, loaded.syncIntervalMs());
    }

    @Test
    void loadOrCreateOnBlankFileReturnsDefaults(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);

        BackendMatchmakingConfig loaded = store.loadOrCreate();

        assertNotNull(loaded);
        assertEquals(BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION, loaded.schemaVersion());
    }

    @Test
    void saveWritesFileContainingBaseUrl(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            true,
            "http://uniquehost.internal",
            "token",
            1000L,
            "eu",
            3000L,
            false,
            5000L
        );

        store.save(config);

        String written = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(written.contains("uniquehost.internal"), "Saved file should contain baseUrl: " + written);
    }

    @Test
    void loadOrCreateCreatesParentDirectoriesIfMissing(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("nested/sub/config.json");
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);

        store.loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void savePreservesRegionField(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        BackendMatchmakingConfigStore store = new BackendMatchmakingConfigStore(file);
        BackendMatchmakingConfig config = new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            "",
            "",
            1000L,
            "ap-southeast",
            3000L,
            false,
            5000L
        );

        store.save(config);
        BackendMatchmakingConfig loaded = store.loadOrCreate();

        assertEquals("ap-southeast", loaded.region());
    }
}
