package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncRequestPayload;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncResultPayload;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CatalogSyncReturnPlannerTest {

    private static final int SCHEMA_VERSION = 1;
    private static final String OPERATION_ID = "operation-1";
    private static final String TARGET_SERVER_ID = "target-server";
    private static final String FALLBACK_TARGET_SERVER_ID = "fallback-target";

    private final CatalogSyncReturnPlanner planner = new CatalogSyncReturnPlanner();

    @Test
    void buildsSuccessReturnForAppliedGame() {
        CatalogSyncReturnPlan plan = planner.buildAppliedReturn(
            SCHEMA_VERSION,
            OPERATION_ID,
            CatalogSyncEntityType.GAME,
            "arena-one",
            TARGET_SERVER_ID,
            "CREATED"
        );

        CatalogEntitySyncResultPayload payload = plan.resultPayload();

        assertEquals(CatalogSyncReturnPlan.Action.SEND_RETURN_PAYLOAD, plan.action());
        assertTrue(payload.success());
        assertEquals(CatalogSyncEntityType.GAME, payload.entityType());
        assertEquals("arena-one", payload.entityId());
        assertEquals("CREATED", payload.applyResult());
        assertEquals("Synced game 'arena-one' to 'target-server' (created).", payload.message());
    }

    @Test
    void buildsSuccessReturnForAppliedQueue() {
        CatalogSyncReturnPlan plan = planner.buildAppliedReturn(
            SCHEMA_VERSION,
            OPERATION_ID,
            CatalogSyncEntityType.QUEUE,
            "queue-one",
            TARGET_SERVER_ID,
            "UPDATED"
        );

        CatalogEntitySyncResultPayload payload = plan.resultPayload();

        assertEquals(CatalogSyncReturnPlan.Action.SEND_RETURN_PAYLOAD, plan.action());
        assertTrue(payload.success());
        assertEquals(CatalogSyncEntityType.QUEUE, payload.entityType());
        assertEquals("queue-one", payload.entityId());
        assertEquals("UPDATED", payload.applyResult());
        assertEquals("Synced queue 'queue-one' to 'target-server' (updated).", payload.message());
    }

    @Test
    void buildsRejectReturnForRejectedApplyPlan() {
        CatalogSyncReturnPlan plan = planner.buildRejectedReturn(
            SCHEMA_VERSION,
            requestPayload(CatalogSyncEntityType.GAME, "arena-one"),
            TARGET_SERVER_ID,
            "Catalog sync entityHash did not match."
        );

        CatalogEntitySyncResultPayload payload = plan.resultPayload();

        assertEquals(CatalogSyncReturnPlan.Action.SEND_RETURN_PAYLOAD, plan.action());
        assertFalse(payload.success());
        assertEquals("FAILED", payload.applyResult());
        assertEquals("Catalog sync entityHash did not match.", payload.message());
    }

    @Test
    void preservesOperationId() {
        CatalogSyncReturnPlan plan = planner.buildAppliedReturn(
            SCHEMA_VERSION,
            OPERATION_ID,
            CatalogSyncEntityType.GAME,
            "arena-one",
            TARGET_SERVER_ID,
            "CREATED"
        );

        assertEquals(OPERATION_ID, plan.operationId());
        assertEquals(OPERATION_ID, plan.resultPayload().operationId());
    }

    @Test
    void preservesEntityTypeAndEntityId() {
        CatalogSyncReturnPlan plan = planner.buildAppliedReturn(
            SCHEMA_VERSION,
            OPERATION_ID,
            CatalogSyncEntityType.QUEUE,
            "queue-one",
            TARGET_SERVER_ID,
            "CREATED"
        );

        assertEquals(CatalogSyncEntityType.QUEUE, plan.resultPayload().entityType());
        assertEquals("queue-one", plan.resultPayload().entityId());
    }

    @Test
    void preservesReasonMessage() {
        CatalogSyncReturnPlan plan = planner.buildRejectedReturn(
            SCHEMA_VERSION,
            requestPayload(CatalogSyncEntityType.QUEUE, "queue-one"),
            TARGET_SERVER_ID,
            "Missing referenced games on target."
        );

        assertEquals("Missing referenced games on target.", plan.message());
        assertEquals("Missing referenced games on target.", plan.resultPayload().message());
    }

    @Test
    void ignoresBlankOperationIdAccordingToCurrentBehavior() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload("   ", true, CatalogSyncEntityType.GAME, "arena-one", TARGET_SERVER_ID, "CREATED", "done"),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.IGNORE_STALE, plan.action());
        assertFalse(plan.shouldRecordPendingReturn());
    }

    @Test
    void ignoresUnknownPendingRequestAccordingToCurrentBehavior() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", TARGET_SERVER_ID, "CREATED", "done"),
            false,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.IGNORE_STALE, plan.action());
        assertFalse(plan.shouldRecordPendingReturn());
    }

    @Test
    void expiredPendingRequestFollowsCurrentBehaviorIfExpiryExists() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", TARGET_SERVER_ID, "CREATED", "done"),
            true,
            true,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.IGNORE_STALE, plan.action());
        assertFalse(plan.shouldRecordPendingReturn());
    }

    @Test
    void mismatchedPendingPlayerIsIgnoredAccordingToCurrentBehavior() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", TARGET_SERVER_ID, "CREATED", "done"),
            true,
            false,
            false,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.IGNORE_STALE, plan.action());
        assertFalse(plan.shouldRecordPendingReturn());
    }

    @Test
    void targetServerMismatchDoesNotRejectAccordingToCurrentBehavior() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", "different-target", "CREATED", "done"),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.ACCEPT_RETURN, plan.action());
        assertTrue(plan.shouldRecordPendingReturn());
        assertEquals("done", plan.message());
    }

    @Test
    void acceptedReturnCompletesPendingRequestIfCurrentBehaviorDoesThat() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.QUEUE, "queue-one", TARGET_SERVER_ID, "UPDATED", "updated"),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.ACCEPT_RETURN, plan.action());
        assertTrue(plan.success());
        assertTrue(plan.shouldRecordPendingReturn());
        assertEquals("updated", plan.message());
    }

    @Test
    void rejectedReturnFailsPendingRequestIfCurrentBehaviorDoesThat() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, false, CatalogSyncEntityType.QUEUE, "queue-one", TARGET_SERVER_ID, "FAILED", "remote failed"),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.ACCEPT_RETURN, plan.action());
        assertFalse(plan.success());
        assertTrue(plan.shouldRecordPendingReturn());
        assertEquals("remote failed", plan.message());
    }

    @Test
    void blankSuccessReturnMessageUsesDefaultMessage() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", TARGET_SERVER_ID, "CREATED", "   "),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals("Synced game 'arena-one' to 'target-server'.", plan.message());
    }

    @Test
    void blankFailureReturnMessageUsesDefaultMessage() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, false, CatalogSyncEntityType.QUEUE, "queue-one", TARGET_SERVER_ID, "FAILED", ""),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals("Failed to sync queue 'queue-one' to 'target-server': The remote apply failed.", plan.message());
    }

    @Test
    void blankPayloadTargetServerUsesPendingRequestTargetForDefaultMessage() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", "   ", "CREATED", ""),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals("Synced game 'arena-one' to 'fallback-target'.", plan.message());
    }

    @Test
    void rejectedReturnSafesBlankOperationAndEntityIdsAccordingToCurrentBehavior() {
        CatalogSyncReturnPlan plan = planner.buildRejectedReturn(
            SCHEMA_VERSION,
            requestPayload(CatalogSyncEntityType.GAME, "   "),
            TARGET_SERVER_ID,
            "failed"
        );

        assertEquals("<blank>", plan.resultPayload().entityId());
    }

    @Test
    void doesNotUseStoresSecureReferralLoggerRuntimeOrClockDependencies() {
        CatalogSyncReturnPlan plan = planner.planReceivedReturn(
            resultPayload(OPERATION_ID, true, CatalogSyncEntityType.GAME, "arena-one", TARGET_SERVER_ID, "CREATED", "done"),
            true,
            false,
            true,
            FALLBACK_TARGET_SERVER_ID
        );

        assertEquals(CatalogSyncReturnPlan.Action.ACCEPT_RETURN, plan.action());
        assertNull(plan.resultPayload());
    }

    private static CatalogEntitySyncRequestPayload requestPayload(CatalogSyncEntityType entityType, String entityId) {
        return new CatalogEntitySyncRequestPayload(
            SCHEMA_VERSION,
            OPERATION_ID,
            "source-server",
            TARGET_SERVER_ID,
            entityType,
            entityId,
            "hash",
            123L,
            null,
            null
        );
    }

    private static CatalogEntitySyncResultPayload resultPayload(
        String operationId,
        boolean success,
        CatalogSyncEntityType entityType,
        String entityId,
        String targetServerId,
        String applyResult,
        String message
    ) {
        return new CatalogEntitySyncResultPayload(
            SCHEMA_VERSION,
            operationId,
            success,
            entityType,
            entityId,
            targetServerId,
            applyResult,
            message
        );
    }
}
