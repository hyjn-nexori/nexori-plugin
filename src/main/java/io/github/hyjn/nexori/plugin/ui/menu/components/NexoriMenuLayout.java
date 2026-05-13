package io.github.hyjn.nexori.plugin.ui.menu.components;

/**
 * Shared layout metrics for the Nexori V2 menu shell and reusable UI sections.
 */
public final class NexoriMenuLayout {

    public static final int PAGE_W = 1900;
    public static final int PAGE_H = 1040;
    public static final int BODY_W = PAGE_W - 52;
    public static final int BODY_H = PAGE_H - 98;
    public static final int SIDEBAR_W = 320;
    public static final int STATUS_H = 96;
    public static final int CONTENT_W = BODY_W - SIDEBAR_W - 20;
    public static final int CONTENT_H = BODY_H - STATUS_H - 12;
    public static final int HOME_SERVER_CARD_H = 64;
    public static final int SERVER_ACTION_CURRENT_W = 150;
    public static final int SERVER_ACTION_EDIT_W = 90;
    public static final int SERVER_ACTION_REMOVE_W = 120;
    public static final int SERVER_ACTION_EXTRA_LEFT_SHIFT = SERVER_ACTION_REMOVE_W / 2;
    public static final int TRAVEL_SERVER_GROUP_GAP = 64;
    public static final int TRAVEL_SECTION_INSET = 12;
    public static final int HOME_INPUT_BLOCK_H = 76;
    public static final int HOME_INPUT_LABEL_H = 18;
    public static final int HOME_INPUT_LABEL_GAP = 8;
    public static final int HOME_INPUT_FIELD_H = 42;
    public static final int HOME_INPUT_CONTENT_H = HOME_INPUT_LABEL_H + HOME_INPUT_LABEL_GAP + HOME_INPUT_FIELD_H;
    public static final int HOME_INPUT_TOP_PADDING = Math.max(0, (HOME_INPUT_BLOCK_H - HOME_INPUT_CONTENT_H) / 2);
    public static final int HOME_ACTION_BUTTON_BOTTOM_PADDING = 2;
    public static final int HOME_ACTION_BUTTON_TOP = HOME_INPUT_BLOCK_H - HOME_INPUT_FIELD_H - HOME_ACTION_BUTTON_BOTTOM_PADDING;

    private NexoriMenuLayout() {
    }
}
