package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * Public player result values that external integrations can report back into Nexori.
 */
public enum NexoriPlayerResolutionOutcome {
    WIN,
    LOSS;

    @Nonnull
    public static NexoriPlayerResolutionOutcome parse(@Nonnull String rawValue) {
        String normalized = rawValue == null ? "" : rawValue.trim().toUpperCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Resolution outcome cannot be blank.");
        }
        return valueOf(normalized);
    }
}
