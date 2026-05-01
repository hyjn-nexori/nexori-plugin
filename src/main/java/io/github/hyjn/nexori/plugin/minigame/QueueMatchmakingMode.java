package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Optional;

public enum QueueMatchmakingMode {
    LOCAL_FIFO,
    BACKEND_DRIVEN;

    @Nonnull
    public static QueueMatchmakingMode defaultMode() {
        return LOCAL_FIFO;
    }

    @Nonnull
    public static Optional<QueueMatchmakingMode> tryParse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return Optional.of(valueOf(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Nonnull
    public String id() {
        return name();
    }
}
