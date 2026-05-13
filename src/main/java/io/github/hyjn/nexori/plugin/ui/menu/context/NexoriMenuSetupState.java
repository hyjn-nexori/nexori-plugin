package io.github.hyjn.nexori.plugin.ui.menu.context;

import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * High level setup and trust-bundle readiness information shared by multiple menu views.
 */
public record NexoriMenuSetupState(
    boolean viewsLocked,
    boolean canRunBootstrap,
    boolean dirty,
    @Nonnull String localConnectionAddress,
    @Nonnull Set<String> trustedAddresses,
    @Nonnull String statusLabel,
    @Nonnull String detail,
    @Nonnull String actionHint,
    @Nonnull HyUIStyle statusStyle,
    @Nonnull HyUIPatchStyle statusBackground
) {
    public boolean running() {
        return statusLabel.equals("Initial Setup running");
    }

    public boolean showBootstrapAction() {
        return !running() && dirty;
    }
}
