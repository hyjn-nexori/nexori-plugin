package io.github.hyjn.nexori.plugin.portal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PortalInstanceStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadOrCreateReturnsMutableEmptyListForMissingFile() throws IOException {
        Path file = tempDir.resolve("portals.json");
        PortalInstanceStore store = new PortalInstanceStore(file);

        List<PortalInstanceDefinition> loaded = store.loadOrCreate();

        assertTrue(loaded.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void constructorCreatesMissingParentDirectory() throws IOException {
        Path file = tempDir.resolve("nested/dir/portals.json");

        new PortalInstanceStore(file).loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void saveAndLoadRoundtripPreservesKeyFields() throws IOException {
        Path file = tempDir.resolve("portals.json");
        PortalInstanceStore store = new PortalInstanceStore(file);

        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-abc", "My Portal", "overworld", 10, 64, -5,
            "overworld.portal.10_64_-5", true, 1_000L, 2_000L
        );
        store.save(List.of(portal));

        List<PortalInstanceDefinition> loaded = new PortalInstanceStore(file).loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals("portal-abc", loaded.get(0).portalId());
        assertEquals("My Portal", loaded.get(0).displayName());
        assertEquals("overworld", loaded.get(0).worldName());
        assertEquals(10, loaded.get(0).blockX());
        assertEquals(64, loaded.get(0).blockY());
        assertEquals(-5, loaded.get(0).blockZ());
    }

    @Test
    void saveAndLoadRoundtripPreservesTimestampsWhenValid() throws IOException {
        Path file = tempDir.resolve("portals.json");
        PortalInstanceStore store = new PortalInstanceStore(file);

        PortalInstanceDefinition portal = new PortalInstanceDefinition(
            "portal-1", "Display", "world", 0, 0, 0, "", true, 1_000_000L, 2_000_000L
        );
        store.save(List.of(portal));

        List<PortalInstanceDefinition> loaded = new PortalInstanceStore(file).loadOrCreate();

        assertEquals(1_000_000L, loaded.get(0).createdAtEpochMillis());
        assertEquals(2_000_000L, loaded.get(0).updatedAtEpochMillis());
    }

    @Test
    void blankFileAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("portals.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        List<PortalInstanceDefinition> loaded = new PortalInstanceStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void nullDocumentAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("portals.json");
        Files.writeString(file,
            "{\"schemaVersion\":1,\"portalInstances\":null}",
            StandardCharsets.UTF_8
        );

        List<PortalInstanceDefinition> loaded = new PortalInstanceStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void saveEmptyListPersistsEmptyDocument() throws IOException {
        Path file = tempDir.resolve("portals.json");
        PortalInstanceStore store = new PortalInstanceStore(file);
        store.save(List.of());

        String content = Files.readString(file, StandardCharsets.UTF_8);

        assertTrue(content.contains("schemaVersion"));
        List<PortalInstanceDefinition> loaded = new PortalInstanceStore(file).loadOrCreate();
        assertTrue(loaded.isEmpty());
    }
}
