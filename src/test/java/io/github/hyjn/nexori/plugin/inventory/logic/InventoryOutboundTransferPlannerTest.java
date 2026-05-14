package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupMode;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.inventory.ItemTransferState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryOutboundTransferPlannerTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final long NOW = 987_654L;

    private final InventoryOutboundTransferPlanner planner = new InventoryOutboundTransferPlanner();

    @Test
    void keepInventoryProfileDoesNotSaveBackupOrClearInventory() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.KEEP_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(InventoryOutboundTransferPlan.Action.IGNORE, plan.action());
        assertFalse(plan.shouldSaveBackup());
        assertFalse(plan.shouldClearOriginInventory());
        assertFalse(plan.shouldIncludeInventoryState());
    }

    @Test
    void clearInventoryProfilePlansNoOriginClearAccordingToCurrentBehavior() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.CLEAR_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(InventoryOutboundTransferPlan.Action.IGNORE, plan.action());
        assertFalse(plan.shouldSaveBackup());
        assertFalse(plan.shouldClearOriginInventory());
        assertFalse(plan.shouldIncludeInventoryState());
    }

    @Test
    void applyInventoryProfileWithTransferableInventoryPlansBackupClearAndTransfer() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(InventoryOutboundTransferPlan.Action.BACKUP_CLEAR_AND_TRANSFER, plan.action());
        assertTrue(plan.shouldSaveBackup());
        assertTrue(plan.shouldClearOriginInventory());
        assertTrue(plan.shouldIncludeInventoryState());
    }

    @Test
    void applyInventoryProfileWithEmptyInventoryFollowsCurrentBehavior() {
        InventoryTransferState inventory = emptyInventory();

        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventory,
            false,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(InventoryOutboundTransferPlan.Action.TRANSFER_WITHOUT_BACKUP, plan.action());
        assertFalse(plan.shouldSaveBackup());
        assertFalse(plan.shouldClearOriginInventory());
        assertTrue(plan.shouldIncludeInventoryState());
        assertSame(inventory, plan.inventoryState());
    }

    @Test
    void applyInventoryProfileWithNullInventoryFollowsCurrentBehavior() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            null,
            false,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(InventoryOutboundTransferPlan.Action.IGNORE, plan.action());
        assertFalse(plan.shouldSaveBackup());
        assertFalse(plan.shouldClearOriginInventory());
        assertFalse(plan.shouldIncludeInventoryState());
    }

    @Test
    void applyInventoryProfileWithBlankTransferIdFollowsCurrentBehavior() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "   ",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(InventoryOutboundTransferPlan.Action.IGNORE, plan.action());
        assertFalse(plan.shouldSaveBackup());
        assertFalse(plan.shouldClearOriginInventory());
    }

    @Test
    void preservesTransferId() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            " Transfer-1 ",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(" Transfer-1 ", plan.transferId());
    }

    @Test
    void preservesPlayerUuid() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertEquals(PLAYER_ONE, plan.playerUuid());
    }

    @Test
    void backupRecordUsesProvidedTimestamp() {
        InventoryOutboundTransferPlan plan = backupPlan();

        InventoryTransferBackupRecord backup = plan.toBackupRecord(NOW);

        assertEquals(NOW, backup.createdAtEpochMs());
        assertEquals("transfer-1", backup.transferId());
        assertEquals(PLAYER_ONE, backup.playerUuid());
        assertEquals(InventoryTransferBackupMode.ORIGIN_QUERY.id(), backup.backupModeId());
        assertEquals("destination.example:25565", backup.destinationConnectionAddress());
        assertEquals("arena-one", backup.destinationTargetId());
        assertEquals("apply_inventory", backup.travelProfileId());
    }

    @Test
    void backupRecordPreservesInventoryState() {
        InventoryTransferState inventory = inventoryWithVisibleItem();
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventory,
            true,
            "destination.example:25565",
            "arena-one"
        );

        assertSame(inventory, plan.toBackupRecord(NOW).inventoryState());
    }

    @Test
    void nullDestinationFieldsDefaultToBlankInBackupRecord() {
        InventoryOutboundTransferPlan plan = planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            null,
            null
        );

        InventoryTransferBackupRecord backup = plan.toBackupRecord(NOW);

        assertEquals("", backup.destinationConnectionAddress());
        assertEquals("", backup.destinationTargetId());
    }

    @Test
    void doesNotUseRuntimeClockInsidePlanner() {
        InventoryOutboundTransferPlan plan = backupPlan();

        assertEquals(NOW, plan.toBackupRecord(NOW).createdAtEpochMs());
        assertEquals(NOW + 1L, plan.toBackupRecord(NOW + 1L).createdAtEpochMs());
    }

    @Test
    void doesNotUseStoresPlayerRuntimeOrLoggerDependencies() {
        InventoryOutboundTransferPlan plan = backupPlan();

        assertNotNull(plan);
        assertTrue(plan.shouldSaveBackup());
    }

    private InventoryOutboundTransferPlan backupPlan() {
        return planner.plan(
            TravelProfileType.APPLY_INVENTORY,
            "transfer-1",
            PLAYER_ONE,
            inventoryWithVisibleItem(),
            true,
            "destination.example:25565",
            "arena-one"
        );
    }

    private static InventoryTransferState inventoryWithVisibleItem() {
        return new InventoryTransferState(
            1,
            new ContainerTransferState("storage", 10, Map.of(
                0, new ItemTransferState("stone", 1, 0.0, 0.0, false, "{}")
            )),
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

    private static InventoryTransferState emptyInventory() {
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
