package io.github.hyjn.nexori.plugin.ui.menu;

import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.ContainerBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIPadding;
import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import au.ellie.hyui.types.ScrollbarStyle;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;

import javax.annotation.Nonnull;

public final class NexoriMenuV2Page {

    private static final int PAGE_W = 1900;
    private static final int PAGE_H = 1040;
    private static final int BODY_W = PAGE_W - 52;
    private static final int BODY_H = PAGE_H - 170;
    private static final int HEADER_H = 72;
    private static final int SIDEBAR_W = 320;
    private static final int CONTENT_W = BODY_W - SIDEBAR_W - 20;
    private static final int STATUS_H = 96;
    private static final int CONTENT_H = BODY_H - STATUS_H - 12;

    private static final HyUIPatchStyle ROOT_BG = new HyUIPatchStyle().setColor("#0e1824");
    private static final HyUIPatchStyle PANEL_BG = new HyUIPatchStyle().setColor("#17273a");
    private static final HyUIPatchStyle PANEL_ALT_BG = new HyUIPatchStyle().setColor("#20354e");
    private static final HyUIPatchStyle BUTTON_BG = new HyUIPatchStyle().setColor("#243a54");
    private static final HyUIPatchStyle BUTTON_SELECTED_BG = new HyUIPatchStyle().setColor("#486f9f");
    private static final HyUIPatchStyle STATUS_BG = new HyUIPatchStyle().setColor("#1a3149");
    private static final ScrollbarStyle DEFAULT_SCROLLBAR = ScrollbarStyle.defaultExtraSpacingStyle()
        .withOnlyVisibleWhenHovered(false)
        .withSize(8)
        .withSpacing(4)
        .withBackground(new HyUIPatchStyle().setColor("#132235"))
        .withHandle(new HyUIPatchStyle().setColor("#39577b"))
        .withHoveredHandle(new HyUIPatchStyle().setColor("#5d87bb"))
        .withDraggedHandle(new HyUIPatchStyle().setColor("#76a3dd"));

    private static final HyUIStyle TITLE = new HyUIStyle().setFontSize(20).setRenderBold(true).setTextColor("#f1f6ff");
    private static final HyUIStyle SUBTITLE = new HyUIStyle().setFontSize(14).setRenderBold(true).setTextColor("#bcd2eb");
    private static final HyUIStyle BODY = new HyUIStyle().setFontSize(14).setTextColor("#d6e5f7").setWrap(true);
    private static final HyUIStyle MUTED = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true);

    private NexoriMenuV2Page() {
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin
    ) {
        open(ref, store, playerRef, player, plugin, NexoriMenuV2State.initial());
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State rawState
    ) {
        if (player == null) {
            playerRef.sendMessage(Message.raw("nexorimenuv2: could not resolve the live player entity."));
            return;
        }

        NexoriMenuV2State state = rawState.normalized();

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI MENU V2")
            .withBackground(ROOT_BG)
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group()
            .withLayoutMode("Top")
            .withPadding(HyUIPadding.all(16));

        content.addChild(header(state));
        content.addChild(spacerY(12));
        content.addChild(body(ref, store, playerRef, player, plugin, state));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    @Nonnull
    private static GroupBuilder header(@Nonnull NexoriMenuV2State state) {
        GroupBuilder header = card(BODY_W, HEADER_H, PANEL_BG);
        header.addChild(label("New Nexori workspace shell", TITLE, BODY_W - 32));
        header.addChild(spacerY(4));
        header.addChild(label(
            "This is the new menu foundation. Left sidebar switches views, the middle panel will become the main editor/visualizer, and the bottom bar is reserved for action feedback and guided workflows.",
            BODY,
            BODY_W - 32
        ));
        return header;
    }

    @Nonnull
    private static GroupBuilder body(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        GroupBuilder body = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(BODY_H));

        GroupBuilder mainRow = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(CONTENT_H));
        mainRow.addChild(sidebar(ref, store, playerRef, player, plugin, state));
        mainRow.addChild(spacerX(20));
        mainRow.addChild(contentPanel(state));

        body.addChild(mainRow);
        body.addChild(spacerY(12));
        body.addChild(statusBar(state));
        return body;
    }

    @Nonnull
    private static GroupBuilder sidebar(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        GroupBuilder sidebar = card(SIDEBAR_W, CONTENT_H, PANEL_BG);
        sidebar.addChild(label("Views", TITLE, SIDEBAR_W - 32));
        sidebar.addChild(spacerY(8));
        sidebar.addChild(label("The left rail stays stable. The center/right area changes with the selected view.", MUTED, SIDEBAR_W - 32));
        sidebar.addChild(spacerY(16));

        for (NexoriMenuV2View view : NexoriMenuV2View.values()) {
            boolean selected = state.selectedView() == view;
            sidebar.addChild(
                (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                    .withText(view.label())
                    .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                    .withAnchor(new HyUIAnchor().setWidth(SIDEBAR_W - 32).setHeight(46))
                    .withDisabled(selected)
                    .onClick((ignored, ctx) -> open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withSelectedView(view).withStatusText("Switched to " + view.label() + ".")
                    ))
            );
            sidebar.addChild(spacerY(8));
        }

        return sidebar;
    }

    @Nonnull
    private static GroupBuilder contentPanel(@Nonnull NexoriMenuV2State state) {
        GroupBuilder panel = card(CONTENT_W, CONTENT_H, PANEL_ALT_BG);
        panel.addChild(label(state.selectedView().label(), TITLE, CONTENT_W - 32));
        panel.addChild(spacerY(8));
        panel.addChild(label(
            "Placeholder canvas for the new Nexori UI. This center area is where the selected view will render its actual content.",
            BODY,
            CONTENT_W - 32
        ));
        panel.addChild(spacerY(20));

        int viewportHeight = CONTENT_H - 136;
        int summaryCardHeight = 116;
        int notesCardHeight = 128;
        int canvasCardHeight = Math.max(520, viewportHeight - 120);
        int scrollContentHeight = 24 + summaryCardHeight + 12 + canvasCardHeight + 12 + notesCardHeight;

        ReorderableListBuilder scroll = scrollList(
            CONTENT_W - 32,
            viewportHeight,
            scrollContentHeight,
            "nexori-menu-v2-content-scroll",
            true
        );
        scroll.addChild(contentSummaryCard(state, CONTENT_W - 48, summaryCardHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(contentCanvasCard(state, CONTENT_W - 48, canvasCardHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(contentNotesCard(CONTENT_W - 48, notesCardHeight));
        panel.addChild(scroll);
        return panel;
    }

    @Nonnull
    private static GroupBuilder contentSummaryCard(@Nonnull NexoriMenuV2State state, int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Workspace summary", SUBTITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label(
            switch (state.selectedView()) {
                case HOME -> "Future server summary, warnings, and quick actions.";
                case PORTALS -> "Future portal visualizer and inspector.";
                case TARGETS -> "Future target catalog split by server/world.";
                case RULES -> "Future rule groups and assignments.";
                case MATCHMAKING -> "Future lobbies, arenas, queues, and instance config.";
                case OPERATIONS -> "Future active queues, matches, handoffs, recovery, and diagnostics.";
            },
            MUTED,
            width - 32
        ));
        return card;
    }

    @Nonnull
    private static GroupBuilder contentCanvasCard(@Nonnull NexoriMenuV2State state, int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Empty workspace", SUBTITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label(
            "This block is intentionally scroll-hosted. As we add more sections, inspectors, lists, or graphs to "
                + state.selectedView().label()
                + ", this viewport stays fixed while the inner content height grows.",
            MUTED,
            width - 32
        ));
        return card;
    }

    @Nonnull
    private static GroupBuilder contentNotesCard(int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Scroll behavior", SUBTITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label(
            "The center panel is now a reusable scroll shell. Future cards, lists, inspectors, and visualizers can be appended here without changing the outer layout.",
            MUTED,
            width - 32
        ));
        return card;
    }

    @Nonnull
    private static GroupBuilder statusBar(@Nonnull NexoriMenuV2State state) {
        GroupBuilder bar = card(BODY_W, STATUS_H, STATUS_BG);
        bar.addChild(label("Action / Status", SUBTITLE, BODY_W - 32));
        bar.addChild(spacerY(6));
        bar.addChild(label(
            state.statusText().isBlank()
                ? "No action yet. This footer will be used for guided actions, confirmations, errors, and next-step hints."
                : state.statusText(),
            BODY,
            BODY_W - 32
        ));
        return bar;
    }

    @Nonnull
    private static GroupBuilder card(int width, int height, @Nonnull HyUIPatchStyle background) {
        return GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(background);
    }

    @Nonnull
    private static ReorderableListBuilder scrollList(
        int width,
        int height,
        int contentHeight,
        @Nonnull String id,
        boolean keepScrollPosition
    ) {
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
    private static GroupBuilder spacerY(int height) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(1).setHeight(height));
    }

    @Nonnull
    private static GroupBuilder spacerX(int width) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(width).setHeight(1));
    }

    @Nonnull
    private static LabelBuilder label(@Nonnull String text, @Nonnull HyUIStyle style, int width) {
        return LabelBuilder.label()
            .withText(text)
            .withAnchor(new HyUIAnchor().setWidth(width))
            .withStyle(style);
    }
}
