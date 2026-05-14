package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QueueDefinitionTest {

    @Test
    void normalizedLowercasesIdsDeduplicatesArenasAndPreservesDisplayName() {
        QueueDefinition queue = new QueueDefinition(
            " Queue-One ",
            " Queue One ",
            List.of(" Arena-A ", "arena-a", "", " Arena-B "),
            2,
            8,
            15,
            "clear_inventory",
            "backend-driven",
            true,
            true,
            " active_window ",
            30
        ).normalized();

        assertEquals("queue-one", queue.queueId());
        assertEquals("Queue One", queue.displayName());
        assertEquals(List.of("arena-a", "arena-b"), queue.arenaIds());
        assertEquals("keep_inventory", queue.launchTravelProfileId());
        assertEquals(QueueMatchmakingMode.BACKEND_DRIVEN.id(), queue.matchmakingMode());
        assertEquals(QueueBackfillMode.ACTIVE_WINDOW.id(), queue.backfillMode());
        assertEquals(30, queue.backfillWindowSeconds());
    }

    @Test
    void normalizedDefaultsDisplayNameToQueueIdAndBackfillToNoneWhenDisabled() {
        QueueDefinition queue = new QueueDefinition(
            " Queue-One ",
            " ",
            null,
            1,
            4,
            10,
            "",
            "",
            false,
            false,
            "ACTIVE_WINDOW",
            -5
        ).normalized();

        assertEquals("queue-one", queue.displayName());
        assertEquals(List.of(), queue.arenaIds());
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO.id(), queue.matchmakingMode());
        assertEquals(QueueBackfillMode.NONE.id(), queue.backfillMode());
        assertEquals(0, queue.backfillWindowSeconds());
    }

    @Test
    void effectiveModesParseFlexibleValuesWithoutChangingOriginalRecord() {
        QueueDefinition queue = new QueueDefinition(
            "queue-one",
            "Queue One",
            List.of(),
            1,
            4,
            10,
            "keep_inventory",
            "backend driven",
            true,
            true,
            "placement_only",
            0
        );

        assertEquals(QueueMatchmakingMode.BACKEND_DRIVEN, queue.effectiveMatchmakingMode());
        assertEquals(QueueBackfillMode.PLACEMENT_ONLY, queue.effectiveBackfillMode());
        assertFalse(queue.hasInvalidMatchmakingMode());
        assertFalse(queue.hasInvalidBackfillMode());
    }

    @Test
    void invalidModesAreReportedAndDefaulted() {
        QueueDefinition queue = new QueueDefinition(
            "queue-one",
            "Queue One",
            List.of(),
            1,
            4,
            10,
            "keep_inventory",
            "not-real",
            true,
            true,
            "not-real",
            0
        );

        assertTrue(queue.hasInvalidMatchmakingMode());
        assertTrue(queue.hasInvalidBackfillMode());
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO, queue.effectiveMatchmakingMode());
        assertEquals(QueueBackfillMode.NONE, queue.effectiveBackfillMode());
    }

    @Test
    void normalizeIdRejectsBlankQueueId() {
        assertThrows(IllegalArgumentException.class, () -> QueueDefinition.normalizeId(" "));
    }
}
