package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Optional;

public enum QueueBackfillMode {
    NONE("NONE"),
    PLACEMENT_ONLY("PLACEMENT_ONLY"),
    ACTIVE_WINDOW("ACTIVE_WINDOW");

    private final String id;

    QueueBackfillMode(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public static QueueBackfillMode defaultMode() {
        return NONE;
    }

    @Nonnull
    public static Optional<QueueBackfillMode> tryParse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(value -> value.id.equalsIgnoreCase(rawValue.trim()))
            .findFirst();
    }
}
