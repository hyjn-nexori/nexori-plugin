package io.github.hyjn.nexori.plugin.ui.menu;

public enum NexoriMenuV2View {
    HOME("Home"),
    PORTALS("Portals"),
    TARGETS("Targets"),
    RULES("Rules"),
    MATCHMAKING("Matchmaking"),
    OPERATIONS("Operations");

    private final String label;

    NexoriMenuV2View(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
