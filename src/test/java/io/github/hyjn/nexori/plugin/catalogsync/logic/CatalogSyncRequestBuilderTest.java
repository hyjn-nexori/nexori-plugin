package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncRequestPayload;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CatalogSyncRequestBuilderTest {

    private static final int SCHEMA_VERSION = 1;
    private static final String OPERATION_ID = "operation-1";
    private static final String SOURCE_SERVER_ID = "source-server";
    private static final String TARGET_SERVER_ID = "target-server";
    private static final long SENT_AT_EPOCH_MS = 123_456L;

    private final CatalogSyncRequestBuilder builder = new CatalogSyncRequestBuilder();

    @Test
    void buildsRequestWithProvidedSchemaSyncIdSequenceAndSentAt() {
        CatalogEntitySyncRequestPayload payload = buildArenaRequest(arena());

        assertEquals(SCHEMA_VERSION, payload.schemaVersion());
        assertEquals(OPERATION_ID, payload.operationId());
        assertEquals(SENT_AT_EPOCH_MS, payload.sentAtEpochMs());
    }

    @Test
    void includesServerIdentityFields() {
        CatalogEntitySyncRequestPayload payload = buildArenaRequest(arena());

        assertEquals(SOURCE_SERVER_ID, payload.sourceServerId());
        assertEquals(TARGET_SERVER_ID, payload.targetServerId());
    }

    @Test
    void includesQueueCatalogEntriesIfCurrentRequestDoesThat() {
        CatalogEntitySyncRequestPayload payload = buildQueueRequest(queue());

        assertEquals(CatalogSyncEntityType.QUEUE, payload.entityType());
        assertEquals("queue-one", payload.entityId());
        assertEquals(queue().normalized(), payload.queue());
        assertNull(payload.arena());
    }

    @Test
    void includesArenaCatalogEntriesIfCurrentRequestDoesThat() {
        CatalogEntitySyncRequestPayload payload = buildArenaRequest(arena());

        assertEquals(CatalogSyncEntityType.GAME, payload.entityType());
        assertEquals("arena-one", payload.entityId());
        assertEquals(arena().normalized(), payload.arena());
        assertNull(payload.queue());
    }

    @Test
    void portalCatalogEntriesAreNotPartOfCurrentRequest() {
        CatalogEntitySyncRequestPayload payload = buildArenaRequest(arena());

        assertNotNull(payload);
        assertNull(payload.queue());
    }

    @Test
    void targetCatalogEntriesAreNotPartOfCurrentRequest() {
        CatalogEntitySyncRequestPayload payload = buildQueueRequest(queue());

        assertNotNull(payload);
        assertNull(payload.arena());
    }

    @Test
    void preservesEntryOrdering() {
        CatalogEntitySyncRequestPayload payload = buildQueueRequest(new QueueDefinition(
            "queue-one",
            "Queue One",
            List.of("arena-two", "arena-one", "arena-three"),
            2,
            6,
            10,
            "apply_inventory",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        ));

        assertEquals(List.of("arena-two", "arena-one", "arena-three"), payload.queue().arenaIds());
    }

    @Test
    void preservesEntryIdsExactlyOrNormalizesAccordingToCurrentBehavior() {
        CatalogEntitySyncRequestPayload arenaPayload = buildArenaRequest(new ArenaDefinition(
            " Arena-One ",
            "Arena One",
            " Destination.EXAMPLE:25565 ",
            " Target-One ",
            " Template-One ",
            " Trigger-One ",
            "rules.Engine-1",
            8,
            true
        ));
        CatalogEntitySyncRequestPayload queuePayload = buildQueueRequest(new QueueDefinition(
            " Queue-One ",
            "Queue One",
            List.of(" Arena-One "),
            2,
            6,
            10,
            "apply_inventory",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        ));

        assertEquals("arena-one", arenaPayload.entityId());
        assertEquals("queue-one", queuePayload.entityId());
        assertEquals(List.of("arena-one"), queuePayload.queue().arenaIds());
    }

    @Test
    void includesHashesOrFingerprintsIfCurrentRequestDoesThat() {
        CatalogEntitySyncRequestPayload arenaPayload = buildArenaRequest(arena());
        CatalogEntitySyncRequestPayload queuePayload = buildQueueRequest(queue());

        assertEquals(builder.hashArena(arena()), arenaPayload.entityHash());
        assertEquals(builder.hashQueue(queue()), queuePayload.entityHash());
        assertFalse(arenaPayload.entityHash().isBlank());
        assertFalse(queuePayload.entityHash().isBlank());
    }

    @Test
    void emptyCatalogListsFollowCurrentBehavior() {
        CatalogEntitySyncRequestPayload payload = buildQueueRequest(new QueueDefinition(
            "queue-one",
            "Queue One",
            null,
            2,
            6,
            10,
            "keep_inventory",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        ));

        assertEquals(List.of(), payload.queue().arenaIds());
    }

    @Test
    void hashIsStableForSameInputs() {
        CatalogEntitySyncRequestPayload first = buildArenaRequest(arena());
        CatalogEntitySyncRequestPayload second = buildArenaRequest(arena());

        assertEquals(first.entityHash(), second.entityHash());
    }

    @Test
    void hashChangesWhenContractFieldChanges() {
        CatalogEntitySyncRequestPayload first = buildArenaRequest(arena());
        CatalogEntitySyncRequestPayload second = buildArenaRequest(new ArenaDefinition(
            "arena-one",
            "Renamed Arena",
            "destination.example:25565",
            "target-one",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID,
            "rules.Engine-1",
            8,
            true
        ));

        assertNotEquals(first.entityHash(), second.entityHash());
    }

    @Test
    void doesNotUseHttpStoresLoggerRuntimeOrClockDependencies() {
        CatalogEntitySyncRequestPayload payload = buildArenaRequest(arena());

        assertNotNull(payload);
        assertEquals(SENT_AT_EPOCH_MS, payload.sentAtEpochMs());
    }

    private CatalogEntitySyncRequestPayload buildArenaRequest(ArenaDefinition arena) {
        return builder.build(new CatalogSyncRequestBuildInput(
            SCHEMA_VERSION,
            OPERATION_ID,
            SOURCE_SERVER_ID,
            TARGET_SERVER_ID,
            CatalogSyncEntityType.GAME,
            SENT_AT_EPOCH_MS,
            arena,
            null
        ));
    }

    private CatalogEntitySyncRequestPayload buildQueueRequest(QueueDefinition queue) {
        return builder.build(new CatalogSyncRequestBuildInput(
            SCHEMA_VERSION,
            OPERATION_ID,
            SOURCE_SERVER_ID,
            TARGET_SERVER_ID,
            CatalogSyncEntityType.QUEUE,
            SENT_AT_EPOCH_MS,
            null,
            queue
        ));
    }

    private static ArenaDefinition arena() {
        return new ArenaDefinition(
            "arena-one",
            "Arena One",
            "destination.example:25565",
            "target-one",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID,
            "rules.Engine-1",
            8,
            true
        );
    }

    private static QueueDefinition queue() {
        return new QueueDefinition(
            "queue-one",
            "Queue One",
            List.of("arena-one", "arena-two"),
            2,
            6,
            10,
            "apply_inventory",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );
    }
}
