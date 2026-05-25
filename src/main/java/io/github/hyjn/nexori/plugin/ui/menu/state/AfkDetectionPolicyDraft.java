package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;

import javax.annotation.Nonnull;

public record AfkDetectionPolicyDraft(
    boolean enabled,
    String inactivityTimeoutSeconds
) {

    @Nonnull
    public AfkDetectionPolicyDraft normalized(ArenaDefinition editing) {
        AfkDetectionPolicy fallback = editing == null
            ? AfkDetectionPolicy.defaults()
            : AfkDetectionPolicy.normalize(editing.afkDetectionPolicy());
        String normalizedTimeout = inactivityTimeoutSeconds == null || inactivityTimeoutSeconds.isBlank()
            ? Integer.toString(fallback.inactivityTimeoutSeconds())
            : inactivityTimeoutSeconds.trim();
        return new AfkDetectionPolicyDraft(enabled, normalizedTimeout);
    }

    @Nonnull
    public AfkDetectionPolicyDraft withInactivityTimeoutSeconds(@Nonnull String timeoutSeconds) {
        return new AfkDetectionPolicyDraft(enabled, timeoutSeconds);
    }

    @Nonnull
    public AfkDetectionPolicyDraft withEnabled(boolean enabled) {
        return new AfkDetectionPolicyDraft(enabled, inactivityTimeoutSeconds);
    }
}
