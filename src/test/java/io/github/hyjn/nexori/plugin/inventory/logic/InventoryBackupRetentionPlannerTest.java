package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryBackupRetentionPlannerTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final InventoryBackupRetentionPlanner planner = new InventoryBackupRetentionPlanner();

    @Test
    void emptyBackupListRemovesNothing() {
        InventoryBackupRetentionPlan plan = planner.plan(List.of(), 3);

        assertEquals(3, plan.effectiveMaxBackupsPerPlayer());
        assertTrue(plan.transferIdsToRemove().isEmpty());
    }

    @Test
    void backupsAtLimitRemoveNothing() {
        InventoryBackupRetentionPlan plan = planner.plan(List.of(
            backup("newest", 300L),
            backup("middle", 200L)
        ), 2);

        assertTrue(plan.transferIdsToRemove().isEmpty());
    }

    @Test
    void backupsOverLimitRemoveOldestBackupsAccordingToCurrentOrdering() {
        InventoryBackupRetentionPlan plan = planner.plan(newestFirstBackups(), 2);

        assertEquals(List.of("old", "oldest"), plan.transferIdsToRemove());
    }

    @Test
    void keepsNewestBackupsAccordingToCurrentOrdering() {
        InventoryBackupRetentionPlan plan = planner.plan(newestFirstBackups(), 3);

        assertEquals(List.of("oldest"), plan.transferIdsToRemove());
    }

    @Test
    void maxBackupsOneKeepsOnlyNewestAccordingToCurrentOrdering() {
        InventoryBackupRetentionPlan plan = planner.plan(newestFirstBackups(), 1);

        assertEquals(1, plan.effectiveMaxBackupsPerPlayer());
        assertEquals(List.of("middle", "old", "oldest"), plan.transferIdsToRemove());
    }

    @Test
    void maxBackupsZeroFollowsCurrentBehavior() {
        InventoryBackupRetentionPlan plan = planner.plan(newestFirstBackups(), 0);

        assertEquals(1, plan.effectiveMaxBackupsPerPlayer());
        assertEquals(List.of("middle", "old", "oldest"), plan.transferIdsToRemove());
    }

    @Test
    void negativeMaxBackupsFollowsCurrentBehavior() {
        InventoryBackupRetentionPlan plan = planner.plan(newestFirstBackups(), -4);

        assertEquals(1, plan.effectiveMaxBackupsPerPlayer());
        assertEquals(List.of("middle", "old", "oldest"), plan.transferIdsToRemove());
    }

    @Test
    void duplicateTransferIdsFollowCurrentBehavior() {
        InventoryBackupRetentionPlan plan = planner.plan(List.of(
            backup("duplicate", 300L),
            backup("duplicate", 200L),
            backup("old", 100L)
        ), 1);

        assertEquals(List.of("duplicate", "old"), plan.transferIdsToRemove());
    }

    @Test
    void nullBackupsInListFollowCurrentBehaviorIfPossible() {
        assertThrows(NullPointerException.class, () -> planner.plan(
            java.util.Arrays.asList(backup("newest", 300L), null),
            1
        ));
    }

    @Test
    void preservesTransferIdValuesForRemoval() {
        InventoryBackupRetentionPlan plan = planner.plan(List.of(
            backup("newest", 300L),
            backup(" Transfer-1 ", 200L)
        ), 1);

        assertEquals(List.of(" Transfer-1 "), plan.transferIdsToRemove());
    }

    @Test
    void doesNotUseStoreLoggerRuntimeOrFilesystemDependencies() {
        InventoryBackupRetentionPlan plan = planner.plan(newestFirstBackups(), 2);

        assertNotNull(plan);
        assertEquals(List.of("old", "oldest"), plan.transferIdsToRemove());
    }

    private static List<InventoryTransferBackupRecord> newestFirstBackups() {
        return List.of(
            backup("newest", 400L),
            backup("middle", 300L),
            backup("old", 200L),
            backup("oldest", 100L)
        );
    }

    private static InventoryTransferBackupRecord backup(String transferId, long createdAtEpochMs) {
        return new InventoryTransferBackupRecord(
            transferId,
            createdAtEpochMs,
            PLAYER_ONE,
            "origin_query",
            "destination.example:25565",
            "arena-one",
            "apply_inventory",
            inventory()
        );
    }

    private static InventoryTransferState inventory() {
        return new InventoryTransferState(
            1,
            new ContainerTransferState("storage", 10, Map.of()),
            new ContainerTransferState("armor", 4, Map.of()),
            new ContainerTransferState("hotbar", 9, Map.of()),
            new ContainerTransferState("utility", 3, Map.of()),
            new ContainerTransferState("backpack", 20, Map.of()),
            new ContainerTransferState("tool", 1, Map.of()),
            0,
            0,
            0
        );
    }
}
