package io.github.hyjn.nexori.plugin.binding;

import javax.annotation.Nonnull;
import java.util.Locale;

public enum TriggerBindingKind {
    PORTAL_COLLISION_ENTER,
    COMMAND;

    @Nonnull
    public static TriggerBindingKind parse(@Nonnull String rawValue) {
        String normalized = rawValue.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.equals("PORTAL")) {
            return PORTAL_COLLISION_ENTER;
        }
        return valueOf(normalized);
    }
}
