package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryInboundApplyPlannerTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long NOW = 123_456L;

    private final InventoryInboundApplyPlanner planner = new InventoryInboundApplyPlanner();

    @Test
    void applyInventoryProfileWithInventoryStatePlansApplyAndReceipt() {
        InventoryTransferState inventory = inventoryState();

        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            inventory,
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(InventoryInboundApplyPlan.Action.APPLY_AND_CREATE_RECEIPT, plan.action());
        assertTrue(plan.shouldApplyInventory());
        assertTrue(plan.shouldSaveReceipt());
        assertEquals(inventory, plan.inventoryState());
    }

    @Test
    void applyInventoryProfileWithoutInventoryStateFollowsCurrentBehavior() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(
                TravelProfileType.APPLY_INVENTORY,
                "transfer-1",
                null,
                "origin-server",
                "origin.example:25565"
            )
        );

        assertEquals("APPLY_INVENTORY travel requires an inventory snapshot.", exception.getMessage());
    }

    @Test
    void applyInventoryProfileWithoutTransferIdAndInventoryIgnoresAccordingToCurrentBehavior() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "",
            null,
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(InventoryInboundApplyPlan.Action.IGNORE, plan.action());
        assertFalse(plan.shouldApplyInventory());
        assertFalse(plan.shouldSaveReceipt());
    }

    @Test
    void keepInventoryProfileDoesNotApplyInventory() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.KEEP_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(InventoryInboundApplyPlan.Action.IGNORE, plan.action());
        assertFalse(plan.shouldApplyInventory());
        assertFalse(plan.shouldSaveReceipt());
    }

    @Test
    void clearInventoryProfileDoesNotApplyInboundInventoryAccordingToCurrentBehavior() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.CLEAR_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(InventoryInboundApplyPlan.Action.CLEAR_INVENTORY, plan.action());
        assertFalse(plan.shouldApplyInventory());
        assertFalse(plan.shouldSaveReceipt());
    }

    @Test
    void preservesTransferId() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            " Transfer-1 ",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(" Transfer-1 ", plan.transferId());
        assertEquals(" Transfer-1 ", plan.toReceiptRecord(PLAYER_ONE, NOW).transferId());
    }

    @Test
    void preservesPlayerUuid() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(PLAYER_ONE, plan.toReceiptRecord(PLAYER_ONE, NOW).playerUuid());
    }

    @Test
    void buildsReceiptRecordWhenApplySucceedsIfCurrentBehaviorDoesThat() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        InventoryTransferReceiptRecord receipt = plan.toReceiptRecord(PLAYER_ONE, NOW);
        assertNotNull(receipt);
        assertEquals("transfer-1", receipt.transferId());
        assertEquals(NOW, receipt.createdAtEpochMs());
        assertEquals("origin-server", receipt.originServerId());
        assertEquals("origin.example:25565", receipt.originConnectionAddress());
    }

    @Test
    void doesNotBuildReceiptWhenInventoryIsNotAppliedIfCurrentBehaviorDoesThat() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.KEEP_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        assertFalse(plan.shouldSaveReceipt());
    }

    @Test
    void handlesBlankTransferIdAccordingToCurrentBehavior() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(
                TravelProfileType.APPLY_INVENTORY,
                "   ",
                inventoryState(),
                "origin-server",
                "origin.example:25565"
            )
        );

        assertEquals("APPLY_INVENTORY travel requires a transferId.", exception.getMessage());
    }

    @Test
    void handlesNullInventoryStateAccordingToCurrentBehavior() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(
                TravelProfileType.APPLY_INVENTORY,
                "transfer-1",
                null,
                "origin-server",
                "origin.example:25565"
            )
        );

        assertEquals("APPLY_INVENTORY travel requires an inventory snapshot.", exception.getMessage());
    }

    @Test
    void nullSourceFieldsDefaultToBlankInReceiptRecord() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            inventoryState(),
            null,
            null
        );

        assertEquals("", plan.toReceiptRecord(PLAYER_ONE, NOW).originServerId());
        assertEquals("", plan.toReceiptRecord(PLAYER_ONE, NOW).originConnectionAddress());
    }

    @Test
    void buildReceiptRecordUsesProvidedTimestamp() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        InventoryTransferReceiptRecord receipt = plan.toReceiptRecord(PLAYER_ONE, NOW);

        assertEquals(NOW, receipt.createdAtEpochMs());
        assertEquals("transfer-1", receipt.transferId());
        assertEquals(PLAYER_ONE, receipt.playerUuid());
        assertEquals("origin-server", receipt.originServerId());
        assertEquals("origin.example:25565", receipt.originConnectionAddress());
    }

    @Test
    void doesNotUseStoresPlayerRuntimeOrLoggerDependencies() {
        InventoryInboundApplyPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            inventoryState(),
            "origin-server",
            "origin.example:25565"
        );

        assertEquals(InventoryInboundApplyPlan.Action.APPLY_AND_CREATE_RECEIPT, plan.action());
        assertNotNull(plan.toReceiptRecord(PLAYER_ONE, NOW));
    }

    private static InventoryTransferState inventoryState() {
        return new InventoryTransferState(
            1,
            emptyContainer("storage", 10),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", 9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", 20),
            emptyContainer("tool", 1),
            0,
            0,
            0
        );
    }

    private static ContainerTransferState emptyContainer(String id, int capacity) {
        return new ContainerTransferState(id, capacity, Map.of());
    }
}
