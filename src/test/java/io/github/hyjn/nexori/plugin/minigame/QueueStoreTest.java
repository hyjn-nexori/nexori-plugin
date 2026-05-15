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

final class QueueStoreTest {

    private static QueueDefinition queue(String id) {
        return new QueueDefinition(
            id, "Queue " + id, List.of("arena-1"),
            2, 8, 10, "default", "LOCAL_FIFO", true
        );
    }

    @Test
    void loadOrCreateMissingFileCreatesEmptyFileAndReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queues.json");
        QueueStore store = new QueueStore(file);

        List<QueueDefinition> result = store.loadOrCreate();

        assertTrue(result.isEmpty());
        assertTrue(Files.exists(file));
    }

    @Test
    void loadOrCreateBlankFileReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queues.json");
        Files.writeString(file, "   ", StandardCharsets.UTF_8);
        QueueStore store = new QueueStore(file);

        assertTrue(store.loadOrCreate().isEmpty());
    }

    @Test
    void loadOrCreateNullDocumentReturnsEmptyList(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queues.json");
        Files.writeString(file, "null", StandardCharsets.UTF_8);
        QueueStore store = new QueueStore(file);

        assertTrue(store.loadOrCreate().isEmpty());
    }

    @Test
    void loadOrCreateNullQueueInListThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queues.json");
        Files.writeString(file, "{\"schemaVersion\":1,\"queues\":[null]}", StandardCharsets.UTF_8);
        QueueStore store = new QueueStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate,
            "List.copyOf() in QueueConfigDocument compact constructor throws NullPointerException on null elements");
    }

    @Test
    void saveAndLoadOrCreateRoundTrips(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queues.json");
        QueueStore store = new QueueStore(file);

        store.save(List.of(queue("queue-1"), queue("queue-2")));
        List<QueueDefinition> loaded = store.loadOrCreate();

        assertEquals(2, loaded.size());
        assertEquals("queue-1", loaded.get(0).queueId());
        assertEquals("queue-2", loaded.get(1).queueId());
    }

    @Test
    void storeCreatesParentDirectory(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("sub/dir/queues.json");
        new QueueStore(file).loadOrCreate();
        assertTrue(Files.isDirectory(file.getParent()));
    }

    @Test
    void corruptJsonThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("queues.json");
        Files.writeString(file, "{not-json", StandardCharsets.UTF_8);
        QueueStore store = new QueueStore(file);

        assertThrows(RuntimeException.class, store::loadOrCreate);
    }
}
