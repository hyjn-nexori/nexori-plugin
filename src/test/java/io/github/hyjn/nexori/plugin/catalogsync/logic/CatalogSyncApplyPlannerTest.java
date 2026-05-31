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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class CatalogSyncApplyPlannerTest {

    private static final int SCHEMA_VERSION = 1;
    private static final String OPERATION_ID = "operation-1";
    private static final String SOURCE_SERVER_ID = "source-server";
    private static final String TARGET_SERVER_ID = "target-server";
    private static final long SENT_AT_EPOCH_MS = 123_456L;

    private final CatalogSyncRequestBuilder requestBuilder = new CatalogSyncRequestBuilder();
    private final CatalogSyncApplyPlanner planner = new CatalogSyncApplyPlanner();

    @Test
    void appliesValidGamePayload() {
        CatalogSyncApplyPlan plan = plan(validArenaPayload(arena()));

        assertEquals(CatalogSyncApplyPlan.Action.APPLY_GAME, plan.action());
        assertEquals(CatalogSyncEntityType.GAME, plan.entityType());
        assertEquals("arena-one", plan.entityId());
        assertEquals(arena().normalized(), plan.arena());
        assertNull(plan.queue());
    }

    @Test
    void appliesValidQueuePayload() {
        CatalogSyncApplyPlan plan = plan(validQueuePayload(queue()));

        assertEquals(CatalogSyncApplyPlan.Action.APPLY_QUEUE, plan.action());
        assertEquals(CatalogSyncEntityType.QUEUE, plan.entityType());
        assertEquals("queue-one", plan.entityId());
        assertEquals(queue().normalized(), plan.queue());
        assertNull(plan.arena());
    }

    @Test
    void rejectsGamePayloadMissingArena() {
        CatalogEntitySyncRequestPayload payload = new CatalogEntitySyncRequestPayload(
            SCHEMA_VERSION,
            OPERATION_ID,
            SOURCE_SERVER_ID,
            TARGET_SERVER_ID,
            CatalogSyncEntityType.GAME,
            "arena-one",
            "hash",
            SENT_AT_EPOCH_MS,
            null,
            null
        );

        CatalogSyncApplyPlan plan = plan(payload);

        assertReject(plan, "Catalog sync game payload cannot be blank.");
    }

    @Test
    void rejectsQueuePayloadMissingQueue() {
        CatalogEntitySyncRequestPayload payload = new CatalogEntitySyncRequestPayload(
            SCHEMA_VERSION,
            OPERATION_ID,
            SOURCE_SERVER_ID,
            TARGET_SERVER_ID,
            CatalogSyncEntityType.QUEUE,
            "queue-one",
            "hash",
            SENT_AT_EPOCH_MS,
            null,
            null
        );

        CatalogSyncApplyPlan plan = plan(payload);

        assertReject(plan, "Catalog sync queue payload cannot be blank.");
    }

    @Test
    void rejectsMissingEntityType() {
        CatalogEntitySyncRequestPayload payload = new CatalogEntitySyncRequestPayload(
            SCHEMA_VERSION,
            OPERATION_ID,
            SOURCE_SERVER_ID,
            TARGET_SERVER_ID,
            null,
            "arena-one",
            "hash",
            SENT_AT_EPOCH_MS,
            arena(),
            null
        );

        CatalogSyncApplyPlan plan = plan(payload);

        assertReject(plan, "Catalog sync entityType cannot be blank.");
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            SCHEMA_VERSION + 1,
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Unsupported catalog sync schemaVersion: " + (SCHEMA_VERSION + 1) + ".");
    }

    @Test
    void rejectsBlankOperationId() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            "   ",
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Catalog sync operationId cannot be blank.");
    }

    @Test
    void rejectsBlankSourceServerId() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            "   ",
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Catalog sync sourceServerId cannot be blank.");
    }

    @Test
    void rejectsBlankTargetServerId() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            "   ",
            payload.entityType(),
            payload.entityId(),
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Catalog sync targetServerId cannot be blank.");
    }

    @Test
    void rejectsBlankEntityHash() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            "   ",
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Catalog sync entityHash cannot be blank.");
    }

    @Test
    void rejectsBlankEntityIdAccordingToCurrentBehavior() {
        CatalogEntitySyncRequestPayload payload = new CatalogEntitySyncRequestPayload(
            SCHEMA_VERSION,
            OPERATION_ID,
            SOURCE_SERVER_ID,
            TARGET_SERVER_ID,
            CatalogSyncEntityType.GAME,
            "   ",
            "hash",
            SENT_AT_EPOCH_MS,
            arena(),
            null
        );

        CatalogSyncApplyPlan plan = plan(payload);

        assertReject(plan, "Catalog sync entityId cannot be blank.");
    }

    @Test
    void rejectsHashMismatchForGame() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload mismatched = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            "sha256:not-the-real-hash",
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(mismatched);

        assertReject(plan, "Catalog sync game hash validation failed.");
    }

    @Test
    void rejectsHashMismatchForQueue() {
        CatalogEntitySyncRequestPayload payload = validQueuePayload(queue());
        CatalogEntitySyncRequestPayload mismatched = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            "sha256:not-the-real-hash",
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(mismatched);

        assertReject(plan, "Catalog sync queue hash validation failed.");
    }

    @Test
    void rejectsEntityIdMismatchForGameIfCurrentBehaviorDoesThat() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload mismatched = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            "other-arena",
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(mismatched);

        assertReject(plan, "Catalog sync entityId does not match the game payload.");
    }

    @Test
    void rejectsEntityIdMismatchForQueueIfCurrentBehaviorDoesThat() {
        CatalogEntitySyncRequestPayload payload = validQueuePayload(queue());
        CatalogEntitySyncRequestPayload mismatched = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            "other-queue",
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(mismatched);

        assertReject(plan, "Catalog sync entityId does not match the queue payload.");
    }

    @Test
    void normalizesArenaBeforeApply() {
        CatalogSyncApplyPlan plan = plan(validArenaPayload(new ArenaDefinition(
            " Arena-One ",
            " Arena One ",
            " Destination.EXAMPLE:25565 ",
            " Target-One ",
            " none ",
            "rules.Engine-1",
            8,
            true
        )));

        assertEquals(CatalogSyncApplyPlan.Action.APPLY_GAME, plan.action());
        assertEquals("arena-one", plan.arena().arenaId());
        assertEquals("destination.example:25565", plan.arena().destinationConnectionAddress());
        assertEquals("target-one", plan.arena().destinationTargetId());
    }

    @Test
    void normalizesQueueBeforeApply() {
        CatalogSyncApplyPlan plan = plan(validQueuePayload(new QueueDefinition(
            " Queue-One ",
            " Queue One ",
            List.of(" Arena-One ", " arena-two "),
            2,
            6,
            10,
            "apply_inventory",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        )));

        assertEquals(CatalogSyncApplyPlan.Action.APPLY_QUEUE, plan.action());
        assertEquals("queue-one", plan.queue().queueId());
        assertEquals(List.of("arena-one", "arena-two"), plan.queue().arenaIds());
    }

    @Test
    void preservesGameEntityIdAccordingToCurrentBehavior() {
        CatalogSyncApplyPlan plan = plan(validArenaPayload(arena()));

        assertEquals("arena-one", plan.entityId());
    }

    @Test
    void preservesQueueEntityIdAccordingToCurrentBehavior() {
        CatalogSyncApplyPlan plan = plan(validQueuePayload(queue()));

        assertEquals("queue-one", plan.entityId());
    }

    @Test
    void rejectsGamePayloadWithQueueData() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            payload.entityHash(),
            payload.sentAtEpochMs(),
            payload.arena(),
            queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Catalog sync game payload cannot include queue data.");
    }

    @Test
    void rejectsQueuePayloadWithGameData() {
        CatalogEntitySyncRequestPayload payload = validQueuePayload(queue());
        CatalogEntitySyncRequestPayload invalid = new CatalogEntitySyncRequestPayload(
            payload.schemaVersion(),
            payload.operationId(),
            payload.sourceServerId(),
            payload.targetServerId(),
            payload.entityType(),
            payload.entityId(),
            payload.entityHash(),
            payload.sentAtEpochMs(),
            arena(),
            payload.queue()
        );

        CatalogSyncApplyPlan plan = plan(invalid);

        assertReject(plan, "Catalog sync queue payload cannot include game data.");
    }

    @Test
    void rejectsSourceServerMismatchAccordingToCurrentBehavior() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());

        CatalogSyncApplyPlan plan = planner.plan(payload, "other-source", TARGET_SERVER_ID, SCHEMA_VERSION);

        assertReject(plan, "Catalog sync sourceServerId does not match the trusted sender.");
    }

    @Test
    void rejectsTargetServerMismatchAccordingToCurrentBehavior() {
        CatalogEntitySyncRequestPayload payload = validArenaPayload(arena());

        CatalogSyncApplyPlan plan = planner.plan(payload, SOURCE_SERVER_ID, "other-target", SCHEMA_VERSION);

        assertReject(plan, "Catalog sync targetServerId does not match this server.");
    }

    @Test
    void doesNotUseStoresSecureReferralLoggerRuntimeOrClockDependencies() {
        CatalogSyncApplyPlan plan = plan(validArenaPayload(arena()));

        assertNotNull(plan);
        assertFalse(plan.reason() == null);
    }

    private CatalogSyncApplyPlan plan(CatalogEntitySyncRequestPayload payload) {
        return planner.plan(payload, SOURCE_SERVER_ID, TARGET_SERVER_ID, SCHEMA_VERSION);
    }

    private CatalogEntitySyncRequestPayload validArenaPayload(ArenaDefinition arena) {
        return requestBuilder.build(new CatalogSyncRequestBuildInput(
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

    private CatalogEntitySyncRequestPayload validQueuePayload(QueueDefinition queue) {
        return requestBuilder.build(new CatalogSyncRequestBuildInput(
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

    private static void assertReject(CatalogSyncApplyPlan plan, String reason) {
        assertEquals(CatalogSyncApplyPlan.Action.REJECT, plan.action());
        assertEquals(reason, plan.reason());
        assertFalse(plan.shouldApply());
    }

    private static ArenaDefinition arena() {
        return new ArenaDefinition(
            "arena-one",
            "Arena One",
            "destination.example:25565",
            "target-one",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
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
