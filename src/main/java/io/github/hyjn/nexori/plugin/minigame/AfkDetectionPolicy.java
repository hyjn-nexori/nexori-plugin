package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;

public record AfkDetectionPolicy(
    boolean enabled,
    int inactivityTimeoutSeconds
) {

    public static final int DEFAULT_INACTIVITY_TIMEOUT_SECONDS = 30;
    public static final int MIN_INACTIVITY_TIMEOUT_SECONDS = 5;
    public static final int MAX_INACTIVITY_TIMEOUT_SECONDS = 3600;

    @Nonnull
    public static AfkDetectionPolicy defaults() {
        return new AfkDetectionPolicy(false, DEFAULT_INACTIVITY_TIMEOUT_SECONDS);
    }

    @Nonnull
    public static AfkDetectionPolicy normalize(AfkDetectionPolicy policy) {
        return policy == null ? defaults() : policy.normalized();
    }

    @Nonnull
    public AfkDetectionPolicy normalized() {
        return new AfkDetectionPolicy(enabled, normalizeTimeoutSeconds(inactivityTimeoutSeconds));
    }

    public long inactivityTimeoutMs() {
        return (long) normalized().inactivityTimeoutSeconds() * 1000L;
    }

    private static int normalizeTimeoutSeconds(int rawSeconds) {
        if (rawSeconds <= 0) {
            return DEFAULT_INACTIVITY_TIMEOUT_SECONDS;
        }
        if (rawSeconds < MIN_INACTIVITY_TIMEOUT_SECONDS) {
            return MIN_INACTIVITY_TIMEOUT_SECONDS;
        }
        return Math.min(rawSeconds, MAX_INACTIVITY_TIMEOUT_SECONDS);
    }
}
