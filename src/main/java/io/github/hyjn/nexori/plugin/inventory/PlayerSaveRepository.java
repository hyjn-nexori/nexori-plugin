package io.github.hyjn.nexori.plugin.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PlayerSaveRepository {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final HytaleLogger logger;

    public PlayerSaveRepository(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    public boolean applyInventoryState(@Nonnull UUID playerUuid, @Nonnull InventoryTransferState state) {
        Path file = getPlayerFile(playerUuid);
        if (!Files.exists(file)) {
            logger.atInfo().log("Player save file does not exist yet for " + playerUuid + ", deferring runtime apply.");
            return false;
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return false;
            }

            JsonObject components = ensureObject(root, "Components");
            JsonObject player = ensureObject(components, "Player");
            player.add("Inventory", toInventoryJson(state));

            backupExisting(file);
            writeJson(file, root);
            return true;
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to patch player save file for " + playerUuid);
            return false;
        }
    }

    @Nonnull
    public Optional<InventoryTransferState> readInventoryState(@Nonnull UUID playerUuid) {
        Path file = getPlayerFile(playerUuid);
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                return Optional.empty();
            }

            JsonObject components = root.has("Components") && root.get("Components").isJsonObject()
                ? root.getAsJsonObject("Components")
                : null;
            JsonObject player = components != null && components.has("Player") && components.get("Player").isJsonObject()
                ? components.getAsJsonObject("Player")
                : null;
            JsonObject inventory = player != null && player.has("Inventory") && player.get("Inventory").isJsonObject()
                ? player.getAsJsonObject("Inventory")
                : null;
            if (inventory == null) {
                return Optional.empty();
            }

            return Optional.of(fromInventoryJson(inventory));
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to read player save file inventory for " + playerUuid);
            return Optional.empty();
        }
    }

    @Nonnull
    private static JsonObject toInventoryJson(@Nonnull InventoryTransferState state) {
        JsonObject inventory = new JsonObject();
        inventory.addProperty("Version", state.version());
        inventory.add("Storage", toContainerJson(state.storage()));
        inventory.add("Armor", toContainerJson(state.armor()));
        inventory.add("HotBar", toContainerJson(state.hotBar()));
        inventory.add("Utility", toContainerJson(state.utility()));
        inventory.add("Backpack", toContainerJson(state.backpack()));
        inventory.add("Tool", toContainerJson(state.tool()));
        inventory.addProperty("ActiveHotbarSlot", state.activeHotbarSlot());
        inventory.addProperty("ActiveToolsSlot", state.activeToolsSlot());
        inventory.addProperty("ActiveUtilitySlot", state.activeUtilitySlot());
        return inventory;
    }

    @Nonnull
    private static InventoryTransferState fromInventoryJson(@Nonnull JsonObject inventory) {
        return new InventoryTransferState(
            inventory.has("Version") ? inventory.get("Version").getAsInt() : 0,
            fromContainerJson(inventory.getAsJsonObject("Storage"), "Storage"),
            fromContainerJson(inventory.getAsJsonObject("Armor"), "Armor"),
            fromContainerJson(inventory.getAsJsonObject("HotBar"), "HotBar"),
            fromContainerJson(inventory.getAsJsonObject("Utility"), "Utility"),
            fromContainerJson(inventory.getAsJsonObject("Backpack"), "Backpack"),
            fromContainerJson(inventory.getAsJsonObject("Tool"), "Tool"),
            inventory.has("ActiveHotbarSlot") ? inventory.get("ActiveHotbarSlot").getAsInt() : -1,
            inventory.has("ActiveToolsSlot") ? inventory.get("ActiveToolsSlot").getAsInt() : -1,
            inventory.has("ActiveUtilitySlot") ? inventory.get("ActiveUtilitySlot").getAsInt() : -1
        );
    }

    @Nonnull
    private static ContainerTransferState fromContainerJson(JsonObject container, @Nonnull String defaultId) {
        if (container == null) {
            return new ContainerTransferState(defaultId, 0, Map.of());
        }

        String id = container.has("Id") ? container.get("Id").getAsString() : defaultId;
        int capacity = container.has("Capacity") ? container.get("Capacity").getAsInt() : 0;
        if ("Empty".equals(id)) {
            return new ContainerTransferState(id, capacity, Map.of());
        }

        Map<Integer, ItemTransferState> items = new LinkedHashMap<>();
        JsonObject itemsObject = container.has("Items") && container.get("Items").isJsonObject()
            ? container.getAsJsonObject("Items")
            : null;
        if (itemsObject != null) {
            for (Map.Entry<String, JsonElement> entry : itemsObject.entrySet()) {
                if (!entry.getValue().isJsonObject()) {
                    continue;
                }
                try {
                    items.put(Integer.parseInt(entry.getKey()), fromItemJson(entry.getValue().getAsJsonObject()));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return new ContainerTransferState(id, capacity, items);
    }

    @Nonnull
    private static ItemTransferState fromItemJson(@Nonnull JsonObject item) {
        String metadataJson = null;
        if (item.has("Metadata") && item.get("Metadata").isJsonObject()) {
            metadataJson = GSON.toJson(item.getAsJsonObject("Metadata"));
        }

        return new ItemTransferState(
            item.has("Id") ? item.get("Id").getAsString() : "",
            item.has("Quantity") ? item.get("Quantity").getAsInt() : 0,
            item.has("Durability") ? item.get("Durability").getAsDouble() : 0.0D,
            item.has("MaxDurability") ? item.get("MaxDurability").getAsDouble() : 0.0D,
            item.has("OverrideDroppedItemAnimation") && item.get("OverrideDroppedItemAnimation").getAsBoolean(),
            metadataJson
        );
    }

    @Nonnull
    private static JsonObject toContainerJson(@Nonnull ContainerTransferState state) {
        JsonObject container = new JsonObject();
        container.addProperty("Id", state.id());
        if ("Empty".equals(state.id())) {
            return container;
        }

        container.addProperty("Capacity", state.capacity());
        JsonObject items = new JsonObject();
        for (Map.Entry<Integer, ItemTransferState> entry : state.items().entrySet()) {
            items.add(String.valueOf(entry.getKey()), toItemJson(entry.getValue()));
        }
        container.add("Items", items);
        return container;
    }

    @Nonnull
    private static JsonObject toItemJson(@Nonnull ItemTransferState state) {
        JsonObject item = new JsonObject();
        item.addProperty("Id", state.id());
        item.addProperty("Quantity", state.quantity());
        item.addProperty("Durability", state.durability());
        item.addProperty("MaxDurability", state.maxDurability());
        item.addProperty("OverrideDroppedItemAnimation", state.overrideDroppedItemAnimation());
        if (state.metadataJson() != null && !state.metadataJson().isBlank()) {
            item.add("Metadata", GSON.fromJson(state.metadataJson(), JsonObject.class));
        }
        return item;
    }

    @Nonnull
    private static JsonObject ensureObject(@Nonnull JsonObject parent, @Nonnull String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }

    @Nonnull
    private static Path getPlayerFile(@Nonnull UUID playerUuid) {
        return Universe.get().getPath().resolve("players").resolve(playerUuid + ".json");
    }

    private static void backupExisting(@Nonnull Path file) throws IOException {
        Path backup = file.resolveSibling(file.getFileName() + ".bak_" + System.currentTimeMillis());
        Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
    }

    private static void writeJson(@Nonnull Path file, @Nonnull JsonObject root) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        String json = GSON.toJson(root);
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
    }
}
