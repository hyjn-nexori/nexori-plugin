package io.github.hyjn.nexori.plugin.ui.menu.components;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIPadding;
import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import au.ellie.hyui.builders.TextFieldBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2View;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;

import javax.annotation.Nonnull;

import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.BODY_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.CONTENT_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_BLOCK_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_FIELD_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_LABEL_GAP;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_LABEL_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_TOP_PADDING;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BODY;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.DEFAULT_SCROLLBAR;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.MUTED;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.PANEL_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.SERVER_CARD_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.STATUS_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.SUBTITLE;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.TITLE;

/**
 * Shared UI builders reused across the Nexori V2 menu shell and section views.
 */
public final class NexoriMenuComponents {

    private NexoriMenuComponents() {
    }

    @Nonnull
    public static ReorderableListBuilder buildPlaceholderScroll(@Nonnull NexoriMenuV2View view, int viewportHeight, @Nonnull String scrollId) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        int summaryHeight = 126;
        int canvasHeight = Math.max(420, viewportHeight - 170);
        int contentHeight = 16 + summaryHeight + 12 + canvasHeight + 20;
        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(sectionHeaderCard(view.label() + " workspace", placeholderDetail(view), innerWidth, summaryHeight));
        scroll.addChild(spacerY(12));

        GroupBuilder canvas = card(innerWidth, canvasHeight, PANEL_BG);
        canvas.addChild(label("Placeholder canvas", SUBTITLE, innerWidth - 32));
        canvas.addChild(spacerY(8));
        canvas.addChild(label("This view is intentionally empty for now. The shell is ready, but the real " + view.label() + " editor will be built in its own pass.", MUTED, innerWidth - 32));
        scroll.addChild(canvas);
        return scroll;
    }

    @Nonnull
    public static String viewSubtitle(@Nonnull NexoriMenuV2View view) {
        return switch (view) {
            case HOME -> "Minimal server setup, trust bundle status, and local server list.";
            case PORTALS -> "";
            case TARGETS -> "Targets are now folded into the Portals travel bind workspace.";
            case RULES -> "";
            case QUEUES -> "Configure reusable games, queues, spawn slots, and cross server catalog sync.";
            case BACKEND -> "Configure backend sync, result reporting, request health, and endpoint reference details.";
            case ACCESS_GATE -> "Control server join caps, reserved slots, and bypass player access for the server you are currently on. To configure another server, move to that server first and then open this view there.";
            case OPERATIONS -> "Live runtime, diagnostics, and recovery belong here.";
            case ABOUT -> "Detailed context for what Nexori does and how this workspace is organized.";
        };
    }

    @Nonnull
    public static GroupBuilder statusBar(@Nonnull NexoriMenuV2State state) {
        GroupBuilder bar = card(BODY_W, NexoriMenuLayout.STATUS_H, STATUS_BG);
        bar.addChild(label("Action / Status", SUBTITLE, BODY_W - 32));
        bar.addChild(spacerY(6));
        bar.addChild(label(state.statusText().isBlank() ? "No action yet. This footer is reserved for confirmations, errors, guided actions, and next-step hints." : state.statusText(), BODY, BODY_W - 32));
        return bar;
    }

    @Nonnull
    public static GroupBuilder inputField(@Nonnull String labelText, @Nonnull String fieldId, @Nonnull String currentValue, @Nonnull String placeholder, int width) {
        GroupBuilder field = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_BLOCK_H));
        field.addChild(spacerY(HOME_INPUT_TOP_PADDING));
        GroupBuilder labelSlot = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_LABEL_H));
        labelSlot.addChild(label(labelText, SUBTITLE, width));
        field.addChild(labelSlot);
        field.addChild(spacerY(HOME_INPUT_LABEL_GAP));
        field.addChild(
            TextFieldBuilder.textInput()
                .withId(fieldId)
                .withValue(currentValue)
                .withPlaceholderText(placeholder)
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_FIELD_H))
                .withBackground("#101926")
        );
        return field;
    }

    @Nonnull
    public static GroupBuilder statusBadgeRow(@Nonnull String prefix, @Nonnull String value, int width, @Nonnull HyUIStyle valueStyle, @Nonnull HyUIPatchStyle background) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(32));
        row.addChild(label(prefix, SUBTITLE, 140));
        GroupBuilder badge = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width - 140).setHeight(30))
            .withPadding(HyUIPadding.symmetric(10, 6))
            .withBackground(background);
        badge.addChild(label(value, valueStyle, width - 180));
        row.addChild(badge);
        return row;
    }

    @Nonnull
    public static GroupBuilder card(int width, int height, @Nonnull HyUIPatchStyle background) {
        return GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(background);
    }

    @Nonnull
    public static GroupBuilder sectionHeaderCard(@Nonnull String title, @Nonnull String detail, int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label(title, SUBTITLE, width - 32));
        if (!detail.isBlank()) {
            card.addChild(spacerY(8));
            card.addChild(label(detail, MUTED, width - 32));
        }
        return card;
    }

    @Nonnull
    public static GroupBuilder selectionSummaryCard(@Nonnull String labelText, @Nonnull String value, int width, boolean selected) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(72))
            .withPadding(HyUIPadding.all(12))
            .withBackground(SERVER_CARD_BG);
        int innerWidth = width - 24;
        card.addChild(centeredLabel(labelText, TITLE, innerWidth, innerWidth, 14));
        card.addChild(spacerY(4));
        card.addChild(centeredLabel(value, TITLE, innerWidth, innerWidth, 11));
        return card;
    }

    @Nonnull
    private static GroupBuilder centeredLabel(@Nonnull String text, @Nonnull HyUIStyle style, int width, int labelWidth, int estimatedCharWidth) {
        int estimatedWidth = Math.min(width, Math.max(48, text.length() * estimatedCharWidth));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(24));
        row.addChild(spacerX(Math.max(0, (width - estimatedWidth) / 2)));
        row.addChild(label(text, style, labelWidth));
        return row;
    }

    @Nonnull
    public static ReorderableListBuilder scrollList(int width, int height, int contentHeight, @Nonnull String id, boolean keepScrollPosition) {
        return ReorderableListBuilder.reorderableList()
            .withId(id)
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withContentHeight(contentHeight)
            .withKeepScrollPosition(keepScrollPosition)
            .withPadding(HyUIPadding.all(0))
            .withBackground(new HyUIPatchStyle().setColor("#132235"))
            .withScrollbarStyle(DEFAULT_SCROLLBAR);
    }

    @Nonnull
    public static GroupBuilder spacerY(int height) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(1).setHeight(height));
    }

    @Nonnull
    public static GroupBuilder spacerX(int width) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(width).setHeight(1));
    }

    @Nonnull
    public static LabelBuilder label(@Nonnull String text, @Nonnull HyUIStyle style, int width) {
        return LabelBuilder.label().withText(text).withAnchor(new HyUIAnchor().setWidth(width)).withStyle(style);
    }

    public static void dismissPage(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PageManager pages = player.getPageManager();
        if (pages != null) {
            pages.setPage(ref, store, Page.None);
        }
    }

    @Nonnull
    private static GroupBuilder conceptItem(@Nonnull String title, @Nonnull String detail, int width) {
        GroupBuilder item = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(48));
        item.addChild(label(title, SUBTITLE, width));
        item.addChild(spacerY(2));
        item.addChild(label(detail, MUTED, width));
        return item;
    }

    @Nonnull
    private static String placeholderDetail(@Nonnull NexoriMenuV2View view) {
        return switch (view) {
            case PORTALS -> "This will become the star visualizer: server/world groups, portal nodes, bindings, and arrows.";
            case TARGETS -> "Targets are now grouped inside the travel bind workspace.";
            case RULES -> "Rules keep their own mental model: define policies once, then attach servers under them.";
            case QUEUES -> "Create games locally, create queues locally, manage spawn slots, and sync saved catalog entities to trusted servers one operation at a time.";
            case BACKEND -> "Backend config can send authenticated matchmaking sync requests and event-driven match result reports.";
            case ACCESS_GATE -> "";
            case OPERATIONS -> "Operations will show active queues, active matches, handoffs, diagnostics, and repair flows.";
            case HOME, ABOUT -> "";
        };
    }
}
