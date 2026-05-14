package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;

import javax.annotation.Nonnull;

/**
 * Analyzes pure inventory transfer snapshots without touching player runtime or persistence.
 */
public final class InventoryStateAnalyzer {

    public boolean shouldTransferInventory(InventoryTransferState state) {
        return state != null && occupiedVisibleSlots(state) > 0;
    }

    public int occupiedVisibleSlots(@Nonnull InventoryTransferState state) {
        return occupiedSlots(state.storage())
            + occupiedSlots(state.armor())
            + occupiedSlots(state.hotBar())
            + occupiedSlots(state.utility())
            + occupiedSlots(state.backpack());
    }

    public int totalVisibleCapacity(@Nonnull InventoryTransferState state) {
        return Math.max(state.storage().capacity(), 0)
            + Math.max(state.armor().capacity(), 0)
            + Math.max(state.hotBar().capacity(), 0)
            + Math.max(state.utility().capacity(), 0)
            + Math.max(state.backpack().capacity(), 0);
    }

    public int occupiedSlots(@Nonnull ContainerTransferState container) {
        return (int) container.items().values().stream()
            .filter(item -> item != null && item.quantity() > 0)
            .count();
    }
}
