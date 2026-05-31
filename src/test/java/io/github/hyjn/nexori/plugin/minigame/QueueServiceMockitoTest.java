package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Mockito-based tests for {@link QueueService}.
 *
 * <p>Uses a real {@link QueueStore} (backed by @TempDir) and a mock {@link ArenaService}
 * so that arena validation can be controlled without file I/O for arenas.</p>
 *
 * <p>Tests cover upsert with missing/present arenas and queue removal.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
final class QueueServiceMockitoTest {

    private static final String ARENA_ID = "arena-alpha";
    private static final String QUEUE_ID = "queue-test-one";

    // HytaleLogger cannot be mocked (private constructor / Flogger internals)
    private HytaleLogger logger;

    @Mock ArenaService arenaService;

    @TempDir Path tempDir;

    private QueueStore queueStore;
    private QueueService queueService;

    // A minimal ArenaDefinition that the mock will return for the happy-path tests
    private ArenaDefinition validArena;

    @BeforeEach
    void setUp() throws IOException {
        logger = HytaleLogger.getLogger();

        validArena = new ArenaDefinition(
            ARENA_ID,
            "Arena Alpha",
            "127.0.0.2:25566",  // destination address — must be parseable by ConfiguredPeer
            "target-alpha",     // destination target id
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            10,
            true
        );

        queueStore = new QueueStore(tempDir.resolve("queues.json"));

        // No queues on disk initially — service starts empty
        when(arenaService.find(ARENA_ID)).thenReturn(Optional.of(validArena));
        queueService = new QueueService(queueStore, arenaService, logger);
    }

    // ── Test 1: upsert rejects queue when referenced arena is missing ─────────

    @Test
    void upsertRejectsQueueWhenReferencedArenaMissingAndDoesNotPersist() throws IOException {
        when(arenaService.find("arena-nonexistent")).thenReturn(Optional.empty());

        QueueDefinition queueWithMissingArena = new QueueDefinition(
            QUEUE_ID,
            "Test Queue",
            List.of("arena-nonexistent"),
            2,
            4,
            10,
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );

        assertThrows(IllegalArgumentException.class, () -> queueService.upsert(queueWithMissingArena));

        // Queue should NOT have been persisted
        assertTrue(queueService.find(QUEUE_ID).isEmpty(), "Queue must not be stored after rejected upsert");
    }

    // ── Test 2: upsert persists queue when all referenced arenas exist ────────

    @Test
    void upsertPersistsQueueWhenAllReferencedArenasExist() throws IOException {
        when(arenaService.find(ARENA_ID)).thenReturn(Optional.of(validArena));

        QueueDefinition validQueue = new QueueDefinition(
            QUEUE_ID,
            "Test Queue",
            List.of(ARENA_ID),
            2,
            4,
            10,
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );

        QueueDefinition persisted = queueService.upsert(validQueue);

        assertFalse(persisted.queueId().isBlank());
        assertEquals(QUEUE_ID, persisted.queueId());
        assertTrue(queueService.find(QUEUE_ID).isPresent(), "Queue must be retrievable after successful upsert");

        // Verify durability: reconstruct service from same store
        when(arenaService.find(ARENA_ID)).thenReturn(Optional.of(validArena));
        QueueService reconstructed = new QueueService(queueStore, arenaService, logger);
        assertTrue(reconstructed.find(QUEUE_ID).isPresent(), "Queue must survive a service restart");
    }

    // ── Test 3: remove deletes persisted queue ────────────────────────────────

    @Test
    void removeQueueDeletesPersistedQueue() throws IOException {
        when(arenaService.find(ARENA_ID)).thenReturn(Optional.of(validArena));

        QueueDefinition validQueue = new QueueDefinition(
            QUEUE_ID,
            "Test Queue",
            List.of(ARENA_ID),
            2,
            4,
            10,
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );

        queueService.upsert(validQueue);
        assertTrue(queueService.find(QUEUE_ID).isPresent());

        boolean removed = queueService.remove(QUEUE_ID);
        assertTrue(removed);
        assertTrue(queueService.find(QUEUE_ID).isEmpty(), "Queue must be absent after removal");

        // Verify durability of removal
        when(arenaService.find(ARENA_ID)).thenReturn(Optional.of(validArena));
        QueueService reconstructed = new QueueService(queueStore, arenaService, logger);
        assertTrue(reconstructed.find(QUEUE_ID).isEmpty(), "Queue must remain deleted after service restart");
    }
}
