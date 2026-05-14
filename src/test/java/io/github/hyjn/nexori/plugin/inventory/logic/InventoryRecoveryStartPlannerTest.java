package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupMode;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryRecoveryStartPlannerTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InventoryRecoveryStartPlanner planner = new InventoryRecoveryStartPlanner();

    @Test
    void disabledRecoveryReturnsDisabledPlan() {
        InventoryRecoveryStartPlan plan = planner.plan(false, backup("local_restore"));

        assertEquals(InventoryRecoveryStartPlan.Action.DISABLED, plan.action());
        assertEquals(null, plan.backupMode());
        assertFalse(plan.remoteTravelStarted());
    }

    @Test
    void missingBackupReturnsBackupMissingPlan() {
        InventoryRecoveryStartPlan plan = planner.plan(true, null);

        assertEquals(InventoryRecoveryStartPlan.Action.BACKUP_MISSING, plan.action());
        assertEquals(null, plan.backupMode());
        assertFalse(plan.remoteTravelStarted());
    }

    @Test
    void localRestoreBackupReturnsLocalRestorePlan() {
        InventoryRecoveryStartPlan plan = planner.plan(true, backup(" local_restore "));

        assertEquals(InventoryRecoveryStartPlan.Action.LOCAL_RESTORE, plan.action());
        assertEquals(InventoryTransferBackupMode.LOCAL_RESTORE, plan.backupMode());
        assertFalse(plan.remoteTravelStarted());
    }

    @Test
    void originQueryBackupReturnsRemoteQueryPlan() {
        InventoryRecoveryStartPlan plan = planner.plan(true, backup(" origin_query "));

        assertEquals(InventoryRecoveryStartPlan.Action.REMOTE_QUERY, plan.action());
        assertEquals(InventoryTransferBackupMode.ORIGIN_QUERY, plan.backupMode());
        assertTrue(plan.remoteTravelStarted());
    }

    @Test
    void unknownBackupModeThrowsAccordingToCurrentBehavior() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> planner.plan(true, backup("mystery"))
        );

        assertEquals("Unknown Nexori inventory backup mode: mystery", exception.getMessage());
    }

    @Test
    void disabledTakesPriorityOverBackupMissing() {
        InventoryRecoveryStartPlan plan = planner.plan(false, null);

        assertEquals(InventoryRecoveryStartPlan.Action.DISABLED, plan.action());
    }

    @Test
    void disabledPlanUsesExpectedMessage() {
        InventoryRecoveryStartPlan plan = planner.plan(false, backup("local_restore"));

        assertEquals(
            "Nexori inventory recovery is currently disabled by this server's admin.",
            plan.message()
        );
    }

    @Test
    void disabledTakesPriorityOverInvalidBackupMode() {
        InventoryRecoveryStartPlan plan = planner.plan(false, backup("mystery"));

        assertEquals(InventoryRecoveryStartPlan.Action.DISABLED, plan.action());
    }

    @Test
    void backupMissingTakesPriorityOverParsingBackupMode() {
        InventoryRecoveryStartPlan plan = planner.plan(true, null);

        assertEquals(InventoryRecoveryStartPlan.Action.BACKUP_MISSING, plan.action());
    }

    @Test
    void missingBackupPlanUsesExpectedMessage() {
        InventoryRecoveryStartPlan plan = planner.plan(true, null);

        assertEquals(
            "That Nexori inventory backup does not exist for your player.",
            plan.message()
        );
    }

    @Test
    void localRestorePlanUsesExpectedMessage() {
        InventoryRecoveryStartPlan plan = planner.plan(true, backup("local_restore"));

        assertEquals(
            "Nexori claim restored your saved local inventory backup on this server.",
            plan.message()
        );
    }

    @Test
    void remoteQueryPlanUsesExpectedMessage() {
        InventoryRecoveryStartPlan plan = planner.plan(true, backup("origin_query"));

        assertEquals(
            "Started Nexori inventory recovery query. If the destination is reachable, you will be sent there and back to resolve it.",
            plan.message()
        );
    }

    private static InventoryTransferBackupRecord backup(String backupModeId) {
        return new InventoryTransferBackupRecord(
            "transfer-1",
            100L,
            PLAYER_ONE,
            backupModeId,
            "server-one",
            "target-one",
            "keep_inventory",
            emptyInventory()
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
