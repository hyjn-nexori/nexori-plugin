package io.github.hyjn.nexori.plugin.profile;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum TravelProfileType {
    KEEP_INVENTORY("keep_inventory", "Keep Inventory", "Use the inventory that already exists on the destination server."),
    CLEAR_INVENTORY("clear_inventory", "Clear Inventory", "Clear the destination inventory before the player fully loads."),
    APPLY_INVENTORY("apply_inventory", "Apply Inventory", "Move the origin inventory into the destination server with backup and recover support.");

    private final String id;
    private final String displayName;
    private final String description;

    TravelProfileType(
        @Nonnull String id,
        @Nonnull String displayName,
        @Nonnull String description
    ) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Nonnull
    public String description() {
        return description;
    }

    @Nonnull
    public static TravelProfileType parse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return KEEP_INVENTORY;
        }

        String normalized = rawValue
            .trim()
            .replace('-', '_')
            .replace(' ', '_')
            .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "KEEP", "KEEP_INVENTORY" -> KEEP_INVENTORY;
            case "CLEAR", "CLEAR_INVENTORY" -> CLEAR_INVENTORY;
            case "APPLY", "APPLY_INVENTORY" -> APPLY_INVENTORY;
            default -> throw new IllegalArgumentException(
                "Unknown Nexori travel profile '" + rawValue + "'. Use KEEP_INVENTORY, CLEAR_INVENTORY, or APPLY_INVENTORY."
            );
        };
    }
}
