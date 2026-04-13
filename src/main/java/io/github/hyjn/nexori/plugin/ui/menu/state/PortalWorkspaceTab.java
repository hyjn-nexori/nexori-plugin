package io.github.hyjn.nexori.plugin.ui.menu.state;

import javax.annotation.Nonnull;

public enum PortalWorkspaceTab {
    BIND("TRAVEL"),
    SETTINGS("QUEUE PORTALS"),
    TARGETS("TARGETS");

    private final String label;

    PortalWorkspaceTab(@Nonnull String label) {
        this.label = label;
    }

    @Nonnull
    public String label() {
        return label;
    }
}
