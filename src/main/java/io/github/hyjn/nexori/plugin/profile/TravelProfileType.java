package io.github.hyjn.nexori.plugin.profile;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum TravelProfileType {
    KEEP_INVENTORY("keep_inventory"),
    CLEAR_INVENTORY("clear_inventory"),
    APPLY_INVENTORY("apply_inventory");

    private final String id;

    TravelProfileType(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
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
