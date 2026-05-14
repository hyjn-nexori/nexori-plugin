package io.github.hyjn.nexori.plugin.binding;

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

final class TriggerBindingStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void loadOrCreateReturnsMutableEmptyListForMissingFile() throws IOException {
        Path file = tempDir.resolve("bindings.json");
        TriggerBindingStore store = new TriggerBindingStore(file);

        List<TriggerBindingDefinition> loaded = store.loadOrCreate();

        assertTrue(loaded.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void constructorCreatesMissingParentDirectory() throws IOException {
        Path file = tempDir.resolve("nested/dir/bindings.json");
        TriggerBindingStore store = new TriggerBindingStore(file);

        store.loadOrCreate();

        assertTrue(Files.exists(file));
    }

    @Test
    void saveAndLoadRoundtrip() throws IOException {
        Path file = tempDir.resolve("bindings.json");
        TriggerBindingStore store = new TriggerBindingStore(file);

        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "portal_collision_enter.portal-1.travel",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            "portal-1",
            TriggerBindingAction.TRAVEL,
            "",
            "server:5520",
            "lobby",
            "default_travel",
            "{}",
            true
        );
        store.save(List.of(binding));

        List<TriggerBindingDefinition> loaded = new TriggerBindingStore(file).loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals("portal_collision_enter.portal-1.travel", loaded.get(0).id());
        assertEquals(TriggerBindingKind.PORTAL_COLLISION_ENTER, loaded.get(0).triggerKind());
        assertEquals("portal-1", loaded.get(0).sourceId());
        assertEquals(TriggerBindingAction.TRAVEL, loaded.get(0).action());
    }

    @Test
    void savedQueueBindingRoundtrips() throws IOException {
        Path file = tempDir.resolve("bindings.json");
        TriggerBindingStore store = new TriggerBindingStore(file);

        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "portal_collision_enter.portal-1.join_queue",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            "portal-1",
            TriggerBindingAction.JOIN_QUEUE,
            "minigame-queue",
            "",
            "",
            "",
            "{}",
            true
        );
        store.save(List.of(binding));

        List<TriggerBindingDefinition> loaded = new TriggerBindingStore(file).loadOrCreate();

        assertEquals(1, loaded.size());
        assertEquals(TriggerBindingAction.JOIN_QUEUE, loaded.get(0).action());
        assertEquals("minigame-queue", loaded.get(0).queueId());
    }

    @Test
    void blankFileAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("bindings.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);

        List<TriggerBindingDefinition> loaded = new TriggerBindingStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void nullListInDocumentAccordingToCurrentBehaviorReturnsEmptyList() throws IOException {
        Path file = tempDir.resolve("bindings.json");
        Files.writeString(file,
            "{\"schemaVersion\":2,\"triggerBindings\":null}",
            StandardCharsets.UTF_8
        );

        List<TriggerBindingDefinition> loaded = new TriggerBindingStore(file).loadOrCreate();

        assertTrue(loaded.isEmpty());
    }

    @Test
    void olderSchemaVersionTriggersRewriteAccordingToCurrentBehavior() throws IOException {
        Path file = tempDir.resolve("bindings.json");
        // Write with schemaVersion=1 (older than CURRENT=2)
        Files.writeString(file,
            "{\"schemaVersion\":1,\"triggerBindings\":[" +
            "{\"id\":\"binding-1\",\"triggerKind\":\"PORTAL_COLLISION_ENTER\",\"sourceId\":\"portal-1\"," +
            "\"action\":\"JOIN_QUEUE\",\"queueId\":\"queue-abc\",\"destinationConnectionAddress\":\"\"," +
            "\"destinationTargetId\":\"\",\"travelProfileId\":\"\",\"contextJson\":\"{}\",\"enabled\":true}" +
            "]}",
            StandardCharsets.UTF_8
        );

        List<TriggerBindingDefinition> loaded = new TriggerBindingStore(file).loadOrCreate();

        assertFalse(loaded.isEmpty());
        // After rewrite, schema version in file should be current
        String rewritten = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(rewritten.contains("\"schemaVersion\": " + TriggerBindingConfigDocument.CURRENT_SCHEMA_VERSION));
    }
}
