package io.github.hyjn.nexori.plugin.accessgate;

import javax.annotation.Nonnull;
import java.util.Locale;

public record NexoriAccessGateBypassPlayer(
    String uuid,
    String username
) {

    @Nonnull
    public NexoriAccessGateBypassPlayer normalized() {
        return new NexoriAccessGateBypassPlayer(
            normalizeUuid(uuid),
            normalizeUsername(username)
        );
    }

    @Nonnull
    private static String normalizeUuid(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private static String normalizeUsername(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        return rawValue.trim();
    }
}
