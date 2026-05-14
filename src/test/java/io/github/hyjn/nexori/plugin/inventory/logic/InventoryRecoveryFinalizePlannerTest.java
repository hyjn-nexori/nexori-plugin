package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferQueryResult;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryRecoveryFinalizePlannerTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final InventoryRecoveryFinalizePlanner planner = new InventoryRecoveryFinalizePlanner();

    @Test
    void ignoresMissingPendingQuery() {
        InventoryRecoveryFinalizePlan plan = planner.planPending(PLAYER_ONE, false, false, null);

        assertEquals(InventoryRecoveryFinalizePlan.Action.IGNORE, plan.action());
    }

    @Test
    void ignoresExpiredPendingQuery() {
        InventoryRecoveryFinalizePlan plan = planner.planPending(PLAYER_ONE, true, true, PLAYER_ONE);

        assertEquals(InventoryRecoveryFinalizePlan.Action.IGNORE, plan.action());
    }

    @Test
    void ignoresPendingQueryForDifferentPlayer() {
        InventoryRecoveryFinalizePlan plan = planner.planPending(PLAYER_ONE, true, false, PLAYER_TWO);

        assertEquals(InventoryRecoveryFinalizePlan.Action.IGNORE, plan.action());
    }

    @Test
    void validPendingQueryReturnsPendingValid() {
        InventoryRecoveryFinalizePlan plan = planner.planPending(PLAYER_ONE, true, false, PLAYER_ONE);

        assertEquals(InventoryRecoveryFinalizePlan.Action.PENDING_VALID, plan.action());
        assertFalse(plan.action() == InventoryRecoveryFinalizePlan.Action.IGNORE);
        assertEquals("", plan.message());
    }

    @Test
    void appliedResultRemovesBackupWhenBackupExists() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", backup("backup-1"), "APPLIED");

        assertEquals(InventoryRecoveryFinalizePlan.Action.FINALIZE, plan.action());
        assertTrue(plan.shouldRemoveBackup());
    }

    @Test
    void appliedResultDoesNotRestoreBackup() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", backup("backup-1"), "APPLIED");

        assertFalse(plan.shouldRestoreBackup());
    }

    @Test
    void appliedResultCreatesReturnWithAppliedMessage() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", backup("backup-1"), "APPLIED");

        assertTrue(plan.shouldCreatePendingReturn());
        assertEquals("", plan.pendingReturnTransferId());
        assertEquals(
            "Nexori recovery confirmed that the destination already applied your inventory transfer.",
            plan.message()
        );
    }

    @Test
    void appliedResultWithMissingBackupCreatesAppliedReturnWithoutRemoveOrRestore() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", null, "APPLIED");

        assertTrue(plan.shouldCreatePendingReturn());
        assertEquals(
            "Nexori recovery confirmed that the destination already applied your inventory transfer.",
            plan.message()
        );
        assertFalse(plan.shouldRemoveBackup());
        assertFalse(plan.shouldRestoreBackup());
    }

    @Test
    void notFoundWithMissingBackupCreatesReturnWithMissingBackupMessage() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", null, "NOT_FOUND");

        assertTrue(plan.shouldCreatePendingReturn());
        assertEquals("", plan.pendingReturnTransferId());
        assertEquals(
            "Nexori recovery could not restore that backup because it no longer exists on this origin server.",
            plan.message()
        );
    }

    @Test
    void notFoundWithMissingBackupDoesNotRestoreOrRemoveBackup() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", null, "NOT_FOUND");

        assertFalse(plan.shouldRestoreBackup());
        assertFalse(plan.shouldRemoveBackup());
    }

    @Test
    void notFoundWithBackupRestoresAndRemovesBackup() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", backup("backup-1"), "NOT_FOUND");

        assertTrue(plan.shouldRestoreBackup());
        assertTrue(plan.shouldRemoveBackup());
    }

    @Test
    void notFoundWithBackupUsesBackupTransferIdInPendingReturn() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", backup("backup-1"), "NOT_FOUND");

        assertEquals("backup-1", plan.pendingReturnTransferId());
    }

    @Test
    void invalidResultDefaultsAccordingToInventoryTransferQueryResultParse() {
        InventoryRecoveryFinalizePlan plan = planner.planFinalization("transfer-1", backup("backup-1"), "   ");

        assertEquals(InventoryTransferQueryResult.NOT_FOUND, plan.result());
        assertTrue(plan.shouldRestoreBackup());
    }

    @Test
    void invalidUnknownResultThrowsAccordingToInventoryTransferQueryResultParse() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> planner.planFinalization("transfer-1", backup("backup-1"), "mystery")
        );

        assertEquals("Unknown Nexori inventory transfer query result 'mystery'.", exception.getMessage());
    }

    @Test
    void preservesMessagesExactly() {
        assertEquals(
            "Nexori recovery confirmed that the destination already applied your inventory transfer.",
            planner.planFinalization("transfer-1", backup("backup-1"), "APPLIED").message()
        );
        assertEquals(
            "Nexori recovery could not restore that backup because it no longer exists on this origin server.",
            planner.planFinalization("transfer-1", null, "NOT_FOUND").message()
        );
        assertEquals(
            "Nexori recovery restored your origin inventory because the destination did not report an applied transfer.",
            planner.planFinalization("transfer-1", backup("backup-1"), "NOT_FOUND").message()
        );
    }

    private static InventoryTransferBackupRecord backup(String transferId) {
        return new InventoryTransferBackupRecord(
            transferId,
            100L,
            PLAYER_ONE,
            "origin_query",
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
