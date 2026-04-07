package io.github.hyjn.nexori.plugin.ui.menu.state;

public enum RulesWorkspaceTab {
    GROUPS("GROUPS");

    private final String label;

    RulesWorkspaceTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
