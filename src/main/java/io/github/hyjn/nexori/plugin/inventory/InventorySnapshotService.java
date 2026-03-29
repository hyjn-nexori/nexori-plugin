package io.github.hyjn.nexori.plugin.inventory;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import org.bson.BsonDocument;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InventorySnapshotService {

    @SuppressWarnings("removal")
    @Nonnull
    public InventoryTransferState capture(@Nonnull Player player) {
        Inventory inventory = requireInventory(player);
        return new InventoryTransferState(
            Inventory.VERSION,
            snapshotContainer(inventory.getStorage()),
            snapshotContainer(inventory.getArmor()),
            snapshotContainer(inventory.getHotbar()),
            snapshotContainer(inventory.getUtility()),
            snapshotContainer(inventory.getBackpack()),
            snapshotContainer(inventory.getTools()),
            inventory.getActiveHotbarSlot(),
            inventory.getActiveToolsSlot(),
            inventory.getActiveUtilitySlot()
        );
    }

    @SuppressWarnings("removal")
    public void applyToPlayer(@Nonnull Player player, @Nonnull InventoryTransferState state) {
        Inventory inventory = requireInventory(player);

        applyBackpackState(inventory, state.backpack());
        applyContainer(inventory.getStorage(), state.storage());
        applyContainer(inventory.getArmor(), state.armor());
        applyContainer(inventory.getHotbar(), state.hotBar());
        applyContainer(inventory.getUtility(), state.utility());
        applyContainer(inventory.getTools(), state.tool());
        applyContainer(inventory.getBackpack(), state.backpack());
        player.markNeedsSave();
    }

    @SuppressWarnings("removal")
    @Nonnull
    private static Inventory requireInventory(@Nonnull Player player) {
        Inventory inventory = player.getInventory();
        if (inventory == null) {
            throw new IllegalStateException("Player inventory is not available.");
        }
        return inventory;
    }

    @SuppressWarnings("removal")
    @Nonnull
    private static ContainerTransferState snapshotContainer(@Nonnull ItemContainer container) {
        Map<Integer, ItemTransferState> items = new LinkedHashMap<>();
        container.forEach((slot, stack) -> {
            if (stack == null || stack.isEmpty()) {
                return;
            }
            items.put((int) slot, snapshotItem(stack));
        });

        String id = container instanceof EmptyItemContainer ? "Empty" : "Simple";
        return new ContainerTransferState(id, container.getCapacity(), items);
    }

    @SuppressWarnings("removal")
    @Nonnull
    private static ItemTransferState snapshotItem(@Nonnull ItemStack stack) {
        BsonDocument metadata = stack.getMetadata();
        String metadataJson = null;
        if (metadata != null && !metadata.isEmpty()) {
            metadataJson = metadata.toJson();
        }

        return new ItemTransferState(
            stack.getItemId(),
            stack.getQuantity(),
            stack.getDurability(),
            stack.getMaxDurability(),
            stack.getOverrideDroppedItemAnimation(),
            metadataJson
        );
    }

    @SuppressWarnings("removal")
    private static void applyBackpackState(@Nonnull Inventory inventory, @Nonnull ContainerTransferState state) {
        if ("Empty".equals(state.id())) {
            inventory.getBackpack().clear();
        }
    }

    @SuppressWarnings("removal")
    private static void applyContainer(@Nonnull ItemContainer target, @Nonnull ContainerTransferState state) {
        if ("Empty".equals(state.id())) {
            target.clear();
            return;
        }

        target.clear();
        for (Map.Entry<Integer, ItemTransferState> entry : state.items().entrySet()) {
            int slot = entry.getKey();
            ItemTransferState item = entry.getValue();
            if (slot < 0 || slot >= target.getCapacity()) {
                continue;
            }
            target.setItemStackForSlot((short) slot, toItemStack(item));
        }
    }

    @SuppressWarnings("removal")
    @Nonnull
    private static ItemStack toItemStack(@Nonnull ItemTransferState state) {
        BsonDocument metadata = state.metadataJson() == null || state.metadataJson().isBlank()
            ? new BsonDocument()
            : BsonDocument.parse(state.metadataJson());

        ItemStack stack = new ItemStack(
            state.id(),
            state.quantity(),
            state.durability(),
            state.maxDurability(),
            metadata
        );
        stack.setOverrideDroppedItemAnimation(state.overrideDroppedItemAnimation());
        return stack;
    }
}
