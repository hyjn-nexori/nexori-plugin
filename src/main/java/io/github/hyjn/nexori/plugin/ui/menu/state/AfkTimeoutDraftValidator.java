package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;

import javax.annotation.Nonnull;

public final class AfkTimeoutDraftValidator {

    private AfkTimeoutDraftValidator() {
    }

    public static int parseTimeoutSeconds(@Nonnull String rawValue) {
        String normalized = rawValue.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("AFK timeout must be a number between "
                + AfkDetectionPolicy.MIN_INACTIVITY_TIMEOUT_SECONDS
                + " and "
                + AfkDetectionPolicy.MAX_INACTIVITY_TIMEOUT_SECONDS
                + " seconds.");
        }
        int seconds;
        try {
            seconds = Integer.parseInt(normalized);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("AFK timeout must be a number.", exception);
        }
        if (seconds < AfkDetectionPolicy.MIN_INACTIVITY_TIMEOUT_SECONDS
            || seconds > AfkDetectionPolicy.MAX_INACTIVITY_TIMEOUT_SECONDS) {
            throw new IllegalArgumentException("AFK timeout must be between "
                + AfkDetectionPolicy.MIN_INACTIVITY_TIMEOUT_SECONDS
                + " and "
                + AfkDetectionPolicy.MAX_INACTIVITY_TIMEOUT_SECONDS
                + " seconds.");
        }
        return seconds;
    }
}
