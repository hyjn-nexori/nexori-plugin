package io.github.hyjn.nexori.plugin.travel.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.travel.SecureTravelPayload;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SecureTravelDispatchPlannerTest {

    private static final String OPERATION_ID = "travel-op-1";
    private static final String SOURCE_SERVER_ID = "source-server-1";
    private static final String DESTINATION_TARGET_ID = "hub.portal";
    private static final String ARRIVAL_POINT_ID = "spawn";
    private static final String CONTEXT_JSON = "{\"flowType\":\"minigame.launch\"}";
    private static final String INVENTORY_TRANSFER_ID = "transfer-1";

    private final SecureTravelDispatchPlanner planner = new SecureTravelDispatchPlanner();

    @Test
    void buildsDispatchPlanWithExpectedFields() {
        SecureTravelDispatchPlan plan = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            TravelProfileType.KEEP_INVENTORY.id(),
            CONTEXT_JSON,
            "",
            null
        );

        SecureTravelPayload payload = plan.payload();
        assertEquals(TravelProfileType.KEEP_INVENTORY, plan.travelProfileType());
        assertEquals(OPERATION_ID, payload.travelOperationId());
        assertEquals(SOURCE_SERVER_ID, payload.sourceServerId());
        assertEquals("", payload.sourceConnectionAddress());
        assertEquals("Secure travel accepted from " + SOURCE_SERVER_ID + ".", payload.arrivalMessage());
        assertFalse(plan.shouldPrepareOriginInventoryTransfer());
    }

    @Test
    void preservesContextJson() {
        SecureTravelPayload payload = basePlan(CONTEXT_JSON).payload();

        assertEquals(CONTEXT_JSON, payload.contextJson());
    }

    @Test
    void defaultsBlankContextJsonToEmptyObject() {
        SecureTravelPayload payload = basePlan("   ").payload();

        assertEquals("{}", payload.contextJson());
    }

    @Test
    void defaultsNullContextJsonToEmptyObject() {
        SecureTravelPayload payload = basePlan(null).payload();

        assertEquals("{}", payload.contextJson());
    }

    @Test
    void preservesPayloadTypeOutsidePlannerAccordingToCurrentBehavior() {
        assertEquals(TravelProfileType.KEEP_INVENTORY.id(), basePlan(CONTEXT_JSON).payload().travelProfileId());
    }

    @Test
    void preservesDestinationTargetId() {
        assertEquals(DESTINATION_TARGET_ID, basePlan(CONTEXT_JSON).payload().destinationTargetId());
    }

    @Test
    void preservesTravelProfileIdOrNormalizesAccordingToCurrentBehavior() {
        SecureTravelDispatchPlan plan = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            " APPLY-INVENTORY ",
            CONTEXT_JSON,
            INVENTORY_TRANSFER_ID,
            inventoryState()
        );

        assertEquals(TravelProfileType.APPLY_INVENTORY, plan.travelProfileType());
        assertEquals("apply_inventory", plan.payload().travelProfileId());
    }

    @Test
    void includesIssuerAndDestinationServerIds() {
        SecureTravelPayload payload = basePlan(CONTEXT_JSON).payload();

        assertEquals(SOURCE_SERVER_ID, payload.sourceServerId());
        assertEquals("", payload.sourceConnectionAddress());
        assertEquals(DESTINATION_TARGET_ID, payload.destinationTargetId());
    }

    @Test
    void usesProvidedOperationIdAsDispatchIdentity() {
        assertEquals(OPERATION_ID, basePlan(CONTEXT_JSON).payload().travelOperationId());
    }

    @Test
    void allowsBlankDestinationTargetIdAccordingToCurrentBehavior() {
        SecureTravelPayload payload = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            "",
            ARRIVAL_POINT_ID,
            TravelProfileType.KEEP_INVENTORY.id(),
            CONTEXT_JSON,
            "",
            null
        ).payload();

        assertEquals("", payload.destinationTargetId());
    }

    @Test
    void travelProfileTypeDefaultsBlankToKeepInventory() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, planner.travelProfileType("   "));
    }

    @Test
    void travelProfileTypeDefaultsNullToKeepInventory() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, planner.travelProfileType(null));
    }

    @Test
    void clearInventoryDoesNotRequireInventoryCapture() {
        SecureTravelDispatchPlan plan = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            TravelProfileType.CLEAR_INVENTORY.id(),
            CONTEXT_JSON,
            "",
            null
        );

        assertFalse(planner.requiresInventoryCapture(TravelProfileType.CLEAR_INVENTORY.id()));
        assertEquals(TravelProfileType.CLEAR_INVENTORY, plan.travelProfileType());
        assertFalse(plan.shouldPrepareOriginInventoryTransfer());
        assertEquals("clear_inventory", plan.payload().travelProfileId());
    }

    @Test
    void applyInventoryRequiresInventoryCapture() {
        assertTrue(planner.requiresInventoryCapture(TravelProfileType.APPLY_INVENTORY.id()));
    }

    @Test
    void preservesInventoryModeOrProfileDecisionIfCurrentBehaviorHasIt() {
        InventoryTransferState inventoryState = inventoryState();
        SecureTravelDispatchPlan plan = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            TravelProfileType.APPLY_INVENTORY.id(),
            CONTEXT_JSON,
            INVENTORY_TRANSFER_ID,
            inventoryState
        );

        assertTrue(plan.shouldPrepareOriginInventoryTransfer());
        assertSame(inventoryState, plan.payload().inventoryState());
        assertEquals(INVENTORY_TRANSFER_ID, plan.payload().inventoryTransferId());
    }

    @Test
    void doesNotPrepareOriginInventoryTransferWithoutTransferId() {
        SecureTravelDispatchPlan plan = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            TravelProfileType.APPLY_INVENTORY.id(),
            CONTEXT_JSON,
            "   ",
            inventoryState()
        );

        assertFalse(plan.shouldPrepareOriginInventoryTransfer());
    }

    @Test
    void doesNotPrepareOriginInventoryTransferWithoutInventoryState() {
        SecureTravelDispatchPlan plan = planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            TravelProfileType.APPLY_INVENTORY.id(),
            CONTEXT_JSON,
            INVENTORY_TRANSFER_ID,
            null
        );

        assertFalse(plan.shouldPrepareOriginInventoryTransfer());
    }

    @Test
    void unknownTravelProfileStillThrowsCurrentMessage() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(
                OPERATION_ID,
                SOURCE_SERVER_ID,
                DESTINATION_TARGET_ID,
                ARRIVAL_POINT_ID,
                "unknown",
                CONTEXT_JSON,
                "",
                null
            )
        );

        assertEquals("Unknown Nexori travel profile 'unknown'. Use KEEP_INVENTORY, CLEAR_INVENTORY, or APPLY_INVENTORY.", exception.getMessage());
    }

    @Test
    void doesNotUseRuntimeDependencies() {
        SecureTravelDispatchPlan plan = basePlan(CONTEXT_JSON);

        assertEquals(SecureTravelDispatchPlan.class, plan.getClass());
    }

    private SecureTravelDispatchPlan basePlan(String contextJson) {
        return planner.plan(
            OPERATION_ID,
            SOURCE_SERVER_ID,
            DESTINATION_TARGET_ID,
            ARRIVAL_POINT_ID,
            TravelProfileType.KEEP_INVENTORY.id(),
            contextJson,
            "",
            null
        );
    }

    private static InventoryTransferState inventoryState() {
        return new InventoryTransferState(1, null, null, null, null, null, null, 0, 0, 0);
    }
}
