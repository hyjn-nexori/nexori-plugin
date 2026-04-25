package io.github.hyjn.nexori.plugin.ui.menu;

public enum NexoriMenuV2View {
    HOME("Servers"),
    PORTALS("Portals"),
    TARGETS("Targets"),
    RULES("Rules"),
    QUEUES("Minigames"),
    ACCESS_GATE("Access Gate"),
    OPERATIONS("Operations"),
    ABOUT("ABOUT NEXORI");

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
