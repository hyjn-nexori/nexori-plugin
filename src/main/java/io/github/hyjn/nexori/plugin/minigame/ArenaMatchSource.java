package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Optional;

public enum ArenaMatchSource {
    LOCAL_FIFO("LOCAL_FIFO"),
    BACKEND_DRIVEN("BACKEND_DRIVEN");

    private final String id;

    ArenaMatchSource(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nonnull
    public static ArenaMatchSource defaultSource() {
        return LOCAL_FIFO;
    }

    @Nonnull
    public static Optional<ArenaMatchSource> tryParse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(value -> value.id.equalsIgnoreCase(rawValue.trim()))
            .findFirst();
    }
}
