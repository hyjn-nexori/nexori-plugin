package io.github.hyjn.nexori.plugin.ui.menu.state;

import javax.annotation.Nonnull;

/**
 * Selected backend workspace tab in the Nexori V2 menu.
 */
public enum BackendWorkspaceTab {
    CONFIG("CONFIG"),
    TERMINAL("TERMINAL");

    private final String label;

    BackendWorkspaceTab(@Nonnull String label) {
        this.label = label;
    }

    @Nonnull
    public String label() {
        return label;
    }
}
