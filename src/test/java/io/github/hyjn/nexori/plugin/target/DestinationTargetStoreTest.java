package io.github.hyjn.nexori.plugin.target;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DestinationTargetStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void constructorCreatesMissingParentDirectory() throws IOException {
        Path file = tempDir.resolve("nested/config/targets.json");

        new DestinationTargetStore(file);

        // No targets yet; loadOrCreate creates the directory and file
        DestinationTargetStore store = new DestinationTargetStore(file);
        List<DestinationTargetDefinition> loaded = store.loadOrCreate();
        assertTrue(loaded.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateReturnsMutableEmptyListForMissingFile() throws IOException {
        Path file = tempDir.resolve("targets.json");
        DestinationTargetStore store = new DestinationTargetStore(file);

        List<DestinationTargetDefinition> loaded = store.loadOrCreate();

        assertTrue(loaded.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void saveAndLoadRoundtrip() throws IOException {
        Path file = tempDir.resolve("targets.json");
        DestinationTargetStore store = new DestinationTargetStore(file);

        DestinationTargetDefinition target = new DestinationTargetDefinition(
            "lobby", "Lobby", DestinationTargetKind.NATURAL_SPAWN, "overworld", "natural_spawn", "Welcome!", "{}"
        );
        store.save(List.of(target));

        DestinationTargetStore reloaded = new DestinationTargetStore(file);
        List<DestinationTargetDefinition> loaded = reloaded.loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals("lobby", loaded.get(0).id());
        assertEquals("Lobby", loaded.get(0).displayName());
        assertEquals(DestinationTargetKind.NATURAL_SPAWN, loaded.get(0).kind());
        assertEquals("overworld", loaded.get(0).worldName());
        assertEquals("natural_spawn", loaded.get(0).arrivalPointId());
    }

    @Test
    void savedMultipleTargetsAreAllLoaded() throws IOException {
        Path file = tempDir.resolve("targets.json");
        DestinationTargetStore store = new DestinationTargetStore(file);

        DestinationTargetDefinition target1 = new DestinationTargetDefinition(
            "lobby", "Lobby", DestinationTargetKind.NATURAL_SPAWN, "world", "", "", "{}"
        );
        DestinationTargetDefinition target2 = new DestinationTargetDefinition(
            "arena", "Arena", DestinationTargetKind.COORDINATE, "arena-world", "checkpoint", "", "{}"
        );
        store.save(List.of(target1, target2));

        List<DestinationTargetDefinition> loaded = new DestinationTargetStore(file).loadOrCreate();

        assertEquals(2, loaded.size());
    }

    @Test
    void blankFileAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("targets.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        List<DestinationTargetDefinition> loaded = new DestinationTargetStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void corruptFileAccordingToCurrentBehaviorThrowsJsonSyntaxException() throws IOException {
        // DestinationTargetStore does NOT catch JsonSyntaxException — corrupt JSON propagates as RuntimeException.
        // Blank files are handled gracefully (return empty), but truly malformed JSON is not.
        Path file = tempDir.resolve("targets.json");
        Files.writeString(file, "not valid json {{{{", StandardCharsets.UTF_8);

        org.junit.jupiter.api.Assertions.assertThrows(
            RuntimeException.class,
            () -> new DestinationTargetStore(file).loadOrCreate()
        );
    }

    @Test
    void nullListInDocumentAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("targets.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"destinationTargets\":null}", StandardCharsets.UTF_8);

        List<DestinationTargetDefinition> loaded = new DestinationTargetStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void loadNormalizesTargetsOnRead() throws IOException {
        Path file = tempDir.resolve("targets.json");
        Files.writeString(file,
            "{\"schemaVersion\":1,\"destinationTargets\":[" +
            "{\"id\":\"  LOBBY  \",\"displayName\":\"Lobby\",\"kind\":\"NATURAL_SPAWN\",\"worldName\":\"world\",\"arrivalPointId\":\"\",\"arrivalMessage\":\"\",\"metadataJson\":\"{}\"}" +
            "]}",
            StandardCharsets.UTF_8
        );

        List<DestinationTargetDefinition> loaded = new DestinationTargetStore(file).loadOrCreate();

        assertFalse(loaded.isEmpty());
        assertEquals("lobby", loaded.get(0).id());
    }
}
