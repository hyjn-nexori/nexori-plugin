package io.github.hyjn.nexori.plugin.discovery;

import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DiscoveredDestinationTargetCacheStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadOrCreateReturnsMutableEmptyListForMissingFile() throws IOException {
        Path file = tempDir.resolve("cache.json");
        DiscoveredDestinationTargetCacheStore store = new DiscoveredDestinationTargetCacheStore(file);

        List<DiscoveredDestinationTargetSet> loaded = store.loadOrCreate();

        assertTrue(loaded.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void constructorCreatesMissingParentDirectory() throws IOException {
        Path file = tempDir.resolve("nested/dir/cache.json");

        new DiscoveredDestinationTargetCacheStore(file).loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void saveAndLoadRoundtripPreservesConnectionAddress() throws IOException {
        Path file = tempDir.resolve("cache.json");
        DiscoveredDestinationTargetCacheStore store = new DiscoveredDestinationTargetCacheStore(file);

        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(
            new DestinationTargetDefinition("lobby", "Lobby", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "{}")
        );
        DiscoveredDestinationTargetSet discovery = new DiscoveredDestinationTargetSet(
            "remote-server:5520", "srv-abc", 1_000_000L, List.of(summary)
        );
        store.save(List.of(discovery));

        List<DiscoveredDestinationTargetSet> loaded = new DiscoveredDestinationTargetCacheStore(file).loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals("remote-server:5520", loaded.get(0).connectionAddress());
        assertEquals("srv-abc", loaded.get(0).remoteServerId());
    }

    @Test
    void saveAndLoadRoundtripPreservesTargets() throws IOException {
        Path file = tempDir.resolve("cache.json");
        DiscoveredDestinationTargetCacheStore store = new DiscoveredDestinationTargetCacheStore(file);

        DiscoveredDestinationTargetSummary summary = DiscoveredDestinationTargetSummary.from(
            new DestinationTargetDefinition("lobby", "Lobby", DestinationTargetKind.NATURAL_SPAWN, "overworld", "natural_spawn", "", "{}")
        );
        DiscoveredDestinationTargetSet discovery = new DiscoveredDestinationTargetSet(
            "remote-server:5520", "srv-abc", 1_000_000L, List.of(summary)
        );
        store.save(List.of(discovery));

        List<DiscoveredDestinationTargetSet> loaded = new DiscoveredDestinationTargetCacheStore(file).loadOrCreate();

        assertEquals(1, loaded.get(0).targets().size());
        assertEquals("lobby", loaded.get(0).targets().get(0).id());
        assertEquals("Lobby", loaded.get(0).targets().get(0).displayName());
    }

    @Test
    void blankFileAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("cache.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        List<DiscoveredDestinationTargetSet> loaded = new DiscoveredDestinationTargetCacheStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void nullDocumentAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("cache.json");
        Files.writeString(file,
            "{\"schemaVersion\":1,\"discoveries\":null}",
            StandardCharsets.UTF_8
        );

        List<DiscoveredDestinationTargetSet> loaded = new DiscoveredDestinationTargetCacheStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveEmptyListPersistsEmptyDocument() throws IOException {
        Path file = tempDir.resolve("cache.json");
        DiscoveredDestinationTargetCacheStore store = new DiscoveredDestinationTargetCacheStore(file);
        store.save(List.of());

        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(content.contains("schemaVersion"));
        List<DiscoveredDestinationTargetSet> loaded = new DiscoveredDestinationTargetCacheStore(file).loadOrCreate();
        assertTrue(loaded.isEmpty());
    }
}
