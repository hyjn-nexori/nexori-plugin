package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

/**
 * Normalizes the local source-context/server ids that Nexori uses for return routing.
 * The legacy runtime still serializes these values under originLobbyId for compatibility,
 * but they no longer imply a special lobby-server role.
 */
public final class SourceContextId {

    private SourceContextId() {
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        String normalized = rawId == null ? "" : rawId.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Source context id cannot be blank.");
        }
        return normalized;
    }
}
