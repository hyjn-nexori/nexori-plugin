package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.inventory.ItemTransferState;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryStateAnalyzerTest {

    private final InventoryStateAnalyzer analyzer = new InventoryStateAnalyzer();

    @Test
    void shouldTransferInventoryReturnsFalseForNull() {
        assertFalse(analyzer.shouldTransferInventory(null));
    }

    @Test
    void shouldTransferInventoryReturnsFalseForEmptyInventory() {
        assertFalse(analyzer.shouldTransferInventory(emptyInventory()));
    }

    @Test
    void shouldTransferInventoryReturnsTrueWhenVisibleContainerHasPositiveQuantityItem() {
        InventoryTransferState state = inventoryWith(container("storage", 10, item(1)));

        assertTrue(analyzer.shouldTransferInventory(state));
    }

    @Test
    void shouldTransferInventoryIgnoresNullItems() {
        InventoryTransferState state = inventoryWith(nullItemContainer());

        assertFalse(analyzer.shouldTransferInventory(state));
    }

    @Test
    void shouldTransferInventoryIgnoresZeroQuantityItems() {
        InventoryTransferState state = inventoryWith(container("storage", 10, item(0)));

        assertFalse(analyzer.shouldTransferInventory(state));
    }

    @Test
    void shouldTransferInventoryIgnoresNegativeQuantityItems() {
        InventoryTransferState state = inventoryWith(container("storage", 10, item(-1)));

        assertFalse(analyzer.shouldTransferInventory(state));
    }

    @Test
    void shouldTransferInventoryIgnoresToolContainerItemsAccordingToCurrentBehavior() {
        InventoryTransferState state = new InventoryTransferState(
            1,
            emptyContainer("storage", 10),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", 9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", 20),
            container("tool", 1, item(1)),
            2,
            3,
            4
        );

        assertFalse(analyzer.shouldTransferInventory(state));
    }

    @Test
    void occupiedVisibleSlotsCountsOnlyStorageArmorHotbarUtilityBackpack() {
        InventoryTransferState state = new InventoryTransferState(
            1,
            container("storage", 10, item(1)),
            container("armor", 4, item(1), item(2)),
            container("hotbar", 9, item(1)),
            container("utility", 3, item(1)),
            container("backpack", 20, item(1)),
            container("tool", 1, item(1)),
            0,
            0,
            0
        );

        assertEquals(6, analyzer.occupiedVisibleSlots(state));
    }

    @Test
    void totalVisibleCapacitySumsOnlyVisibleContainers() {
        InventoryTransferState state = new InventoryTransferState(
            1,
            emptyContainer("storage", 10),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", 9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", 20),
            emptyContainer("tool", 99),
            0,
            0,
            0
        );

        assertEquals(46, analyzer.totalVisibleCapacity(state));
    }

    @Test
    void totalVisibleCapacityClampsNegativeCapacitiesToZero() {
        InventoryTransferState state = new InventoryTransferState(
            1,
            emptyContainer("storage", -10),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", -9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", -20),
            emptyContainer("tool", 99),
            0,
            0,
            0
        );

        assertEquals(7, analyzer.totalVisibleCapacity(state));
    }

    @Test
    void emptyLikePreservesVersionSlotsAndCapacitiesButClearsItems() {
        InventoryTransferState source = new InventoryTransferState(
            7,
            container("storage", 10, item(1)),
            container("armor", 4, item(1)),
            container("hotbar", 9, item(1)),
            container("utility", 3, item(1)),
            container("backpack", 20, item(1)),
            container("tool", 1, item(1)),
            2,
            3,
            4
        );

        InventoryTransferState emptied = InventoryTransferState.emptyLike(source);

        assertEquals(7, emptied.version());
        assertEquals(2, emptied.activeHotbarSlot());
        assertEquals(3, emptied.activeToolsSlot());
        assertEquals(4, emptied.activeUtilitySlot());
        assertEquals(10, emptied.storage().capacity());
        assertEquals(4, emptied.armor().capacity());
        assertEquals(9, emptied.hotBar().capacity());
        assertEquals(3, emptied.utility().capacity());
        assertEquals(20, emptied.backpack().capacity());
        assertEquals(1, emptied.tool().capacity());
        assertTrue(emptied.storage().items().isEmpty());
        assertTrue(emptied.tool().items().isEmpty());
    }

    @Test
    void inventoryIsEmptyWhenAllContainersEmpty() {
        assertTrue(emptyInventory().isEmpty());
    }

    @Test
    void inventoryIsNotEmptyWhenToolContainerHasItemsAccordingToCurrentBehaviorIfCurrentBehaviorDoesThat() {
        InventoryTransferState state = new InventoryTransferState(
            1,
            emptyContainer("storage", 10),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", 9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", 20),
            container("tool", 1, item(1)),
            0,
            0,
            0
        );

        assertFalse(state.isEmpty());
    }

    @Test
    void containerIsEmptyWhenIdIsEmptyEvenIfItemsExistAccordingToCurrentBehavior() {
        ContainerTransferState container = container("Empty", 10, item(1));

        assertTrue(container.isEmpty());
    }

    @Test
    void occupiedSlotsCountsPositiveItemsEvenWhenContainerIdIsEmptyAccordingToCurrentBehavior() {
        ContainerTransferState container = container("Empty", 10, item(1));

        assertTrue(container.isEmpty());
        assertEquals(1, analyzer.occupiedSlots(container));
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

    private static InventoryTransferState inventoryWith(ContainerTransferState storage) {
        return new InventoryTransferState(
            1,
            storage,
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

    private static ContainerTransferState nullItemContainer() {
        return new ContainerTransferState("storage", 10, mapWithNullItem());
    }

    private static Map<Integer, ItemTransferState> mapWithNullItem() {
        java.util.HashMap<Integer, ItemTransferState> items = new java.util.HashMap<>();
        items.put(0, null);
        return items;
    }

    private static ContainerTransferState container(String id, int capacity, ItemTransferState... items) {
        java.util.LinkedHashMap<Integer, ItemTransferState> mappedItems = new java.util.LinkedHashMap<>();
        for (int index = 0; index < items.length; index++) {
            mappedItems.put(index, items[index]);
        }
        return new ContainerTransferState(id, capacity, mappedItems);
    }

    private static ItemTransferState item(int quantity) {
        return new ItemTransferState("stone", quantity, 0.0, 0.0, false, null);
    }
}
