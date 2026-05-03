package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum ArenaPlayerResolutionOutcome {
    WIN,
    LOSS,
    DISCONNECTED;

    @Nonnull
    public static ArenaPlayerResolutionOutcome parse(@Nonnull String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Resolution outcome cannot be blank.");
        }
        return valueOf(normalized);
    }
}
