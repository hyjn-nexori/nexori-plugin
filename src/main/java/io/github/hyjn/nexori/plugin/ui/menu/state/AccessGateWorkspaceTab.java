package io.github.hyjn.nexori.plugin.ui.menu.state;

public enum AccessGateWorkspaceTab {
    ACCESS_GATE("ACCESS GATE"),
    MANUAL_CONNECTIONS("MANUAL CONNECTIONS");

    private final String label;

    AccessGateWorkspaceTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
