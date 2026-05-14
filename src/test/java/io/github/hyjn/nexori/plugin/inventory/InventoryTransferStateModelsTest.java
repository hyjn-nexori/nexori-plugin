package io.github.hyjn.nexori.plugin.inventory;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryTransferStateModelsTest {

    @Test
    void itemTransferStatePreservesItemIdMetadataAndQuantity() {
        ItemTransferState item = new ItemTransferState(
            "minecraft:stone",
            3,
            4.5,
            10.0,
            true,
            "{\"tag\":\"value\"}"
        );

        assertEquals("minecraft:stone", item.id());
        assertEquals(3, item.quantity());
        assertEquals(4.5, item.durability());
        assertEquals(10.0, item.maxDurability());
        assertTrue(item.overrideDroppedItemAnimation());
        assertEquals("{\"tag\":\"value\"}", item.metadataJson());
    }

    @Test
    void itemTransferStatePreservesNonPositiveQuantityAccordingToCurrentBehavior() {
        assertEquals(0, item(0).quantity());
        assertEquals(-1, item(-1).quantity());
    }

    @Test
    void containerIsEmptyReturnsTrueForIdEmptyAccordingToCurrentBehavior() {
        ContainerTransferState container = container("Empty", 10, item(1));

        assertTrue(container.isEmpty());
    }

    @Test
    void containerIsEmptyWithNoItems() {
        assertTrue(emptyContainer("storage", 10).isEmpty());
    }

    @Test
    void containerIsNotEmptyWithPositiveQuantityItem() {
        assertFalse(container("storage", 10, item(1)).isEmpty());
    }

    @Test
    void containerCapacityIsPreserved() {
        assertEquals(-10, emptyContainer("storage", -10).capacity());
    }

    @Test
    void containerItemsMapIsPreserved() {
        Map<Integer, ItemTransferState> items = Map.of(3, item(1));
        ContainerTransferState container = new ContainerTransferState("storage", 10, items);

        assertSame(items, container.items());
    }

    @Test
    void containerEmptyLikePreservesIdAndCapacityButClearsItems() {
        ContainerTransferState source = container("storage", 10, item(1));

        ContainerTransferState empty = ContainerTransferState.emptyLike(source);

        assertEquals("storage", empty.id());
        assertEquals(10, empty.capacity());
        assertTrue(empty.items().isEmpty());
    }

    @Test
    void inventoryIsEmptyWhenAllContainersEmpty() {
        assertTrue(emptyInventory().isEmpty());
    }

    @Test
    void inventoryIsNotEmptyWhenAnyContainerHasItems() {
        InventoryTransferState state = new InventoryTransferState(
            1,
            container("storage", 10, item(1)),
            emptyContainer("armor", 4),
            emptyContainer("hotbar", 9),
            emptyContainer("utility", 3),
            emptyContainer("backpack", 20),
            emptyContainer("tool", 1),
            0,
            0,
            0
        );

        assertFalse(state.isEmpty());
    }

    @Test
    void inventoryIsNotEmptyWhenToolContainerHasItemsAccordingToCurrentBehavior() {
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
    void emptyLikePreservesVersionSlotsAndCapacities() {
        InventoryTransferState source = inventoryWithItems();

        InventoryTransferState empty = InventoryTransferState.emptyLike(source);

        assertEquals(7, empty.version());
        assertEquals(2, empty.activeHotbarSlot());
        assertEquals(3, empty.activeToolsSlot());
        assertEquals(4, empty.activeUtilitySlot());
        assertEquals(10, empty.storage().capacity());
        assertEquals(4, empty.armor().capacity());
        assertEquals(9, empty.hotBar().capacity());
        assertEquals(3, empty.utility().capacity());
        assertEquals(20, empty.backpack().capacity());
        assertEquals(1, empty.tool().capacity());
    }

    @Test
    void emptyLikeClearsItems() {
        InventoryTransferState empty = InventoryTransferState.emptyLike(inventoryWithItems());

        assertTrue(empty.storage().items().isEmpty());
        assertTrue(empty.armor().items().isEmpty());
        assertTrue(empty.hotBar().items().isEmpty());
        assertTrue(empty.utility().items().isEmpty());
        assertTrue(empty.backpack().items().isEmpty());
        assertTrue(empty.tool().items().isEmpty());
    }

    private static InventoryTransferState inventoryWithItems() {
        return new InventoryTransferState(
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
