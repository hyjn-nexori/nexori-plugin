package io.github.hyjn.nexori.plugin.ui.menu.components;

import au.ellie.hyui.builders.Alignment;
import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.types.ScrollbarStyle;

/**
 * Shared visual styles for the Nexori V2 menu.
 */
public final class NexoriMenuStyles {

    public static final HyUIPatchStyle ROOT_BG = new HyUIPatchStyle().setColor("#0e1824");
    public static final HyUIPatchStyle PANEL_BG = new HyUIPatchStyle().setColor("#17273a");
    public static final HyUIPatchStyle PANEL_ALT_BG = new HyUIPatchStyle().setColor("#20354e");
    public static final HyUIPatchStyle SERVER_CARD_BG = new HyUIPatchStyle().setColor("#23374f");
    public static final HyUIPatchStyle BUTTON_BG = new HyUIPatchStyle().setColor("#243a54");
    public static final HyUIPatchStyle BUTTON_SELECTED_BG = new HyUIPatchStyle().setColor("#486f9f");
    public static final HyUIPatchStyle BUTTON_ABOUT_BG = new HyUIPatchStyle().setColor("#31496d");
    public static final HyUIPatchStyle BUTTON_ABOUT_SELECTED_BG = new HyUIPatchStyle().setColor("#5f7fab");
    public static final HyUIPatchStyle STATUS_BG = new HyUIPatchStyle().setColor("#1a3149");
    public static final HyUIPatchStyle GOOD_BG = new HyUIPatchStyle().setColor("#1f5c35");
    public static final HyUIPatchStyle WARN_BG = new HyUIPatchStyle().setColor("#8a5c24");
    public static final HyUIPatchStyle BAD_BG = new HyUIPatchStyle().setColor("#7b3742");
    public static final HyUIPatchStyle INFO_BG = new HyUIPatchStyle().setColor("#2d4f75");
    public static final ScrollbarStyle DEFAULT_SCROLLBAR = ScrollbarStyle.defaultExtraSpacingStyle()
        .withOnlyVisibleWhenHovered(false)
        .withSize(8)
        .withSpacing(4)
        .withBackground(new HyUIPatchStyle().setColor("#132235"))
        .withHandle(new HyUIPatchStyle().setColor("#39577b"))
        .withHoveredHandle(new HyUIPatchStyle().setColor("#5d87bb"))
        .withDraggedHandle(new HyUIPatchStyle().setColor("#76a3dd"));

    public static final HyUIStyle TITLE = new HyUIStyle().setFontSize(20).setRenderBold(true).setTextColor("#f1f6ff").setWrap(true);
    public static final HyUIStyle SUBTITLE = new HyUIStyle().setFontSize(14).setRenderBold(true).setTextColor("#bcd2eb").setWrap(true);
    public static final HyUIStyle BODY = new HyUIStyle().setFontSize(14).setTextColor("#d6e5f7").setWrap(true);
    public static final HyUIStyle MUTED = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true);
    public static final HyUIStyle MUTED_CENTER = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true).setAlignment(Alignment.Center);
    public static final HyUIStyle GOOD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#7de3a6").setWrap(true);
    public static final HyUIStyle WARN = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ffc66d").setWrap(true);
    public static final HyUIStyle BAD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ff8b9a").setWrap(true);
    public static final HyUIStyle INFO = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#8fc7ff").setWrap(true);
    public static final HyUIStyle GOOD_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#d5ffe2").setWrap(true).setAlignment(Alignment.Center);
    public static final HyUIStyle WARN_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ffe4ae").setWrap(true).setAlignment(Alignment.Center);
    public static final HyUIStyle BAD_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ffd7dc").setWrap(true).setAlignment(Alignment.Center);
    public static final HyUIStyle INFO_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#d7ecff").setWrap(true).setAlignment(Alignment.Center);

    private NexoriMenuStyles() {
    }
}
