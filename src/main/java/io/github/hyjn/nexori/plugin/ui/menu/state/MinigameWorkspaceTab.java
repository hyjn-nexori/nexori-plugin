package io.github.hyjn.nexori.plugin.ui.menu.state;

public enum MinigameWorkspaceTab {
    DESTINATIONS("GAMES"),
    QUEUES("QUEUES"),
    SPAWNS("SPAWNS");

    private final String label;

    MinigameWorkspaceTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
