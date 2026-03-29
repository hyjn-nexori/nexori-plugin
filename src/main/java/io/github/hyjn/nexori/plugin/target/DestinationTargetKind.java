package io.github.hyjn.nexori.plugin.target;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum DestinationTargetKind {
    NATURAL_SPAWN("Natural Spawn"),
    COORDINATE("Coordinate"),
    PORTAL("Portal");

    private final String displayName;

    DestinationTargetKind(@Nonnull String displayName) {
        this.displayName = displayName;
    }

    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Nonnull
    public static DestinationTargetKind parse(@Nonnull String rawValue) {
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Destination target kind cannot be blank.");
        }

        return switch (normalized) {
            case "NATURAL_SPAWN", "SPAWN" -> NATURAL_SPAWN;
            case "COORDINATE", "COORD" -> COORDINATE;
            case "PORTAL" -> PORTAL;
            default -> throw new IllegalArgumentException(
                "Unknown destination target kind '" + rawValue + "'. Use NATURAL_SPAWN, COORDINATE, or PORTAL."
            );
        };
    }
}
