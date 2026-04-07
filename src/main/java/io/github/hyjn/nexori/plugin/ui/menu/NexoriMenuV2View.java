package io.github.hyjn.nexori.plugin.ui.menu;

public enum NexoriMenuV2View {
    HOME("Servers"),
    PORTALS("Portals"),
    TARGETS("Targets"),
    RULES("Rules"),
    QUEUES("Minigames"),
    OPERATIONS("Operations"),
    ABOUT("HOW TO USE");

    private final String label;

    NexoriMenuV2View(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isAbout() {
        return this == ABOUT;
    }
}
