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
import au.ellie.hyui.builders.TextFieldBuilder;
import au.ellie.hyui.types.ScrollbarStyle;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;
import io.github.hyjn.nexori.plugin.ui.menu.state.PortalWorkspaceTab;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class NexoriMenuV2Page {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final int PAGE_W = 1900;
    private static final int PAGE_H = 1040;
    private static final int BODY_W = PAGE_W - 52;
    private static final int BODY_H = PAGE_H - 98;
    private static final int SIDEBAR_W = 320;
    private static final int STATUS_H = 96;
    private static final int CONTENT_W = BODY_W - SIDEBAR_W - 20;
    private static final int CONTENT_H = BODY_H - STATUS_H - 12;
    private static final int HOME_SERVER_CARD_H = 64;
    private static final int SERVER_ACTION_CURRENT_W = 150;
    private static final int SERVER_ACTION_EDIT_W = 90;
    private static final int SERVER_ACTION_REMOVE_W = 120;
    private static final int SERVER_ACTION_EXTRA_LEFT_SHIFT = SERVER_ACTION_REMOVE_W / 2;
    private static final int TRAVEL_SERVER_GROUP_GAP = 64;
    private static final int TRAVEL_SECTION_INSET = 12;
    private static final int HOME_INPUT_BLOCK_H = 76;
    private static final int HOME_INPUT_LABEL_H = 18;
    private static final int HOME_INPUT_LABEL_GAP = 8;
    private static final int HOME_INPUT_FIELD_H = 42;
    private static final int HOME_INPUT_CONTENT_H = HOME_INPUT_LABEL_H + HOME_INPUT_LABEL_GAP + HOME_INPUT_FIELD_H;
    private static final int HOME_INPUT_TOP_PADDING = Math.max(0, (HOME_INPUT_BLOCK_H - HOME_INPUT_CONTENT_H) / 2);
    private static final int HOME_ACTION_BUTTON_BOTTOM_PADDING = 2;
    private static final int HOME_ACTION_BUTTON_TOP = HOME_INPUT_BLOCK_H - HOME_INPUT_FIELD_H - HOME_ACTION_BUTTON_BOTTOM_PADDING;
    private static final String HOME_DISPLAY_NAME_INPUT_ID = "nexori-v2-home-display-name";
    private static final String HOME_SERVER_ADDRESS_INPUT_ID = "nexori-v2-home-server-address";
    private static final String DEFAULT_REMOTE_TARGET_ID = "default.natural_spawn";

    private static final HyUIPatchStyle ROOT_BG = new HyUIPatchStyle().setColor("#0e1824");
    private static final HyUIPatchStyle PANEL_BG = new HyUIPatchStyle().setColor("#17273a");
    private static final HyUIPatchStyle PANEL_ALT_BG = new HyUIPatchStyle().setColor("#20354e");
    private static final HyUIPatchStyle SERVER_CARD_BG = new HyUIPatchStyle().setColor("#23374f");
    private static final HyUIPatchStyle BUTTON_BG = new HyUIPatchStyle().setColor("#243a54");
    private static final HyUIPatchStyle BUTTON_SELECTED_BG = new HyUIPatchStyle().setColor("#486f9f");
    private static final HyUIPatchStyle BUTTON_ABOUT_BG = new HyUIPatchStyle().setColor("#31496d");
    private static final HyUIPatchStyle BUTTON_ABOUT_SELECTED_BG = new HyUIPatchStyle().setColor("#5f7fab");
    private static final HyUIPatchStyle STATUS_BG = new HyUIPatchStyle().setColor("#1a3149");
    private static final HyUIPatchStyle GOOD_BG = new HyUIPatchStyle().setColor("#1f5c35");
    private static final HyUIPatchStyle BAD_BG = new HyUIPatchStyle().setColor("#7b3742");
    private static final HyUIPatchStyle INFO_BG = new HyUIPatchStyle().setColor("#2d4f75");
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
    private static final HyUIStyle GOOD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#7de3a6").setWrap(true);
    private static final HyUIStyle BAD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ff8b9a").setWrap(true);
    private static final HyUIStyle INFO = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#8fc7ff").setWrap(true);

    private NexoriMenuV2Page() {
    }

    public static void open(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef, Player player, @Nonnull NexoriPlugin plugin) {
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

        List<ConfiguredPeer> peers = plugin.getConfiguredPeerService().list();
        HomeSetupState setup = buildHomeSetupState(plugin, peers);
        NexoriMenuV2State state = rawState.normalized();
        if (setup.viewsLocked() && state.selectedView() != NexoriMenuV2View.HOME) {
            state = state.withSelectedView(NexoriMenuV2View.HOME);
        }

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI MENU V2")
            .withBackground(ROOT_BG)
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(16));
        content.addChild(body(ref, store, playerRef, player, plugin, state, peers, setup));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    @Nonnull
    private static GroupBuilder body(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup
    ) {
        GroupBuilder body = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(BODY_H));
        GroupBuilder mainRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(CONTENT_H));
        mainRow.addChild(sidebar(ref, store, playerRef, player, plugin, state, setup));
        mainRow.addChild(spacerX(20));
        mainRow.addChild(contentPanel(ref, store, playerRef, player, plugin, state, peers, setup));
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
        @Nonnull NexoriMenuV2State state,
        @Nonnull HomeSetupState setup
    ) {
        GroupBuilder sidebar = card(SIDEBAR_W, CONTENT_H, PANEL_BG);
        sidebar.addChild(label("Views", TITLE, SIDEBAR_W - 32));
        sidebar.addChild(spacerY(8));
        sidebar.addChild(label(
            setup.viewsLocked()
                ? "Finish Servers setup first to unlock the rest of the workspace."
                : "Servers is the operational view. The center panel changes with the selected view.",
            MUTED,
            SIDEBAR_W - 32
        ));
        sidebar.addChild(spacerY(16));

        List<NexoriMenuV2View> views = List.of(
            NexoriMenuV2View.HOME,
            NexoriMenuV2View.PORTALS,
            NexoriMenuV2View.RULES,
            NexoriMenuV2View.QUEUES,
            NexoriMenuV2View.OPERATIONS
        );
        for (NexoriMenuV2View view : views) {
            boolean selected = state.selectedView() == view;
            boolean locked = setup.viewsLocked() && view != NexoriMenuV2View.HOME;
            sidebar.addChild(
                (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                    .withText(view.label())
                    .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                    .withAnchor(new HyUIAnchor().setWidth(SIDEBAR_W - 32).setHeight(46))
                    .withDisabled(selected || locked)
                    .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedView(view).withStatusText("Switched to " + view.label() + ".")))
            );
            sidebar.addChild(spacerY(8));
        }

        sidebar.addChild(spacerY(Math.max(24, CONTENT_H - 470)));
        boolean aboutSelected = state.selectedView() == NexoriMenuV2View.ABOUT;
        sidebar.addChild(
            (aboutSelected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText(NexoriMenuV2View.ABOUT.label())
                .withBackground(aboutSelected ? BUTTON_ABOUT_SELECTED_BG : BUTTON_ABOUT_BG)
                .withAnchor(new HyUIAnchor().setWidth(SIDEBAR_W - 32).setHeight(44))
                .withDisabled(aboutSelected || setup.viewsLocked())
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ABOUT).withStatusText("Opened How To Use.")))
        );
        return sidebar;
    }

    @Nonnull
    private static GroupBuilder contentPanel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup
    ) {
        GroupBuilder panel = card(CONTENT_W, CONTENT_H, PANEL_ALT_BG);
        panel.addChild(label(state.selectedView().label(), TITLE, CONTENT_W - 32));
        if (!viewSubtitle(state.selectedView()).isBlank()) {
            panel.addChild(spacerY(8));
            panel.addChild(label(viewSubtitle(state.selectedView()), BODY, CONTENT_W - 32));
        }
        if (state.selectedView() == NexoriMenuV2View.PORTALS) {
            panel.addChild(spacerY(12));
            panel.addChild(portalTabs(ref, store, playerRef, player, plugin, state));
        }
        panel.addChild(spacerY(12));
        int viewportHeight = CONTENT_H - (state.selectedView() == NexoriMenuV2View.PORTALS ? 158 : 104);
        panel.addChild(buildContentScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight));
        return panel;
    }

    @Nonnull
    private static ReorderableListBuilder buildContentScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup,
        int viewportHeight
    ) {
        String scrollId = "nexori-v2-scroll-" + state.selectedView().name().toLowerCase()
            + (state.selectedView() == NexoriMenuV2View.PORTALS ? "-" + state.selectedPortalTab().name().toLowerCase() : "");
        return switch (state.selectedView()) {
            case HOME -> buildHomeScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case ABOUT -> buildAboutScroll(viewportHeight, scrollId);
            case PORTALS, TARGETS -> buildPortalWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case RULES, QUEUES, OPERATIONS -> buildPlaceholderScroll(state.selectedView(), viewportHeight, scrollId);
        };
    }

    @Nonnull
    private static GroupBuilder portalTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        int tabWidth = 160;
        int gap = 8;
        PortalWorkspaceTab[] tabs = PortalWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            PortalWorkspaceTab tab = tabs[index];
            ButtonBuilder button = (state.selectedPortalTab() == tab ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedPortalTab(tab).withStatusText("")
                ));
            row.addChild(button);
            if (index + 1 < tabs.length) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }

    @Nonnull
    private static ReorderableListBuilder buildPortalWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        return switch (state.selectedPortalTab()) {
            case BIND -> buildTravelBindScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case SETTINGS -> buildPortalSettingsScroll(viewportHeight, scrollId);
        };
    }

    @Nonnull
    private static ReorderableListBuilder buildHomeScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        int setupCardHeight = 156;
        int emptyCardHeight = 126;
        int serverCardsHeight = peers.isEmpty()
            ? emptyCardHeight
            : peers.size() * HOME_SERVER_CARD_H + Math.max(0, peers.size() - 1) * 8 + 32;
        int contentHeight = 16 + setupCardHeight + 16 + serverCardsHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(homeSetupCard(ref, store, playerRef, player, plugin, state, setup, innerWidth, setupCardHeight));
        scroll.addChild(spacerY(16));
        scroll.addChild(serverCardsContainer(ref, store, playerRef, player, plugin, state, setup, peers, innerWidth, serverCardsHeight));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder homeSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull HomeSetupState setup,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(16, 0))
            .withBackground(PANEL_BG);
        int contentHeight = 24 + 12 + HOME_INPUT_BLOCK_H;
        int topOffset = Math.max(0, (height - contentHeight) / 2);
        card.addChild(spacerY(topOffset));
        card.addChild(label("MY SECURE SERVERS", TITLE, width - 32));
        card.addChild(spacerY(12));

        boolean editingServer = !state.editingServerAddress().isBlank();
        String displayNameValue = state.pendingServerDisplayName().isBlank() ? "My Server" : state.pendingServerDisplayName();
        String addressValue = state.pendingServerAddress().isBlank() ? "127.0.0.1" : state.pendingServerAddress();

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(HOME_INPUT_BLOCK_H));
        row.addChild(inputField("Display Name", HOME_DISPLAY_NAME_INPUT_ID, displayNameValue, "My Server", 250));
        row.addChild(spacerX(12));
        row.addChild(inputField("Connection Address", HOME_SERVER_ADDRESS_INPUT_ID, addressValue, "127.0.0.1", 310));
        row.addChild(spacerX(12));
        GroupBuilder actionColumn = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(420).setHeight(HOME_INPUT_BLOCK_H));
        actionColumn.addChild(spacerY(HOME_ACTION_BUTTON_TOP));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(420).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText(editingServer ? "SAVE SERVER" : "ADD SERVER")
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String displayName = ctx.getValue(HOME_DISPLAY_NAME_INPUT_ID, String.class).orElse(displayNameValue).trim();
                    String address = ctx.getValue(HOME_SERVER_ADDRESS_INPUT_ID, String.class).orElse(addressValue).trim();
                    try {
                        if (editingServer) {
                            ConfiguredPeer updated = plugin.getConfiguredPeerService().update(state.editingServerAddress(), displayName, address);
                            open(ref, store, playerRef, player, plugin, state.clearedPendingServerDraft().withStatusText("Updated " + updated.displayName() + " (" + updated.connectionAddress() + ")."));
                        } else {
                            ConfiguredPeer added = plugin.getConfiguredPeerService().add(displayName, address);
                            open(ref, store, playerRef, player, plugin, state.clearedPendingServerDraft().withStatusText("Added " + added.displayName() + " (" + added.connectionAddress() + ")."));
                        }
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withPendingServerDisplayName(displayName).withPendingServerAddress(address).withStatusText((editingServer ? "Could not rename server: " : "Could not add server: ") + exception.getMessage()));
                    }
                })
        );
        if (editingServer) {
            actionRow.addChild(spacerX(12));
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText("CANCEL")
                    .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                    .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.clearedPendingServerDraft().withStatusText("Edit cancelled.")))
            );
        } else if (setup.showBootstrapAction()) {
            actionRow.addChild(spacerX(12));
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText("RUN INITIAL SETUP")
                    .withAnchor(new HyUIAnchor().setWidth(230).setHeight(HOME_INPUT_FIELD_H))
                    .withDisabled(!setup.canRunBootstrap())
                    .onClick((ignored, ctx) -> {
                        BootstrapCoordinator.StartResult result = plugin.getBootstrapCoordinator().start(playerRef);
                        if (result.started()) {
                            plugin.getBootstrapCoordinator().requestMenuResume(playerRef.getUuid());
                            dismissPage(player, ref, store);
                            return;
                        }
                        open(ref, store, playerRef, player, plugin, state.withStatusText(result.message()));
                    })
            );
        }
        actionColumn.addChild(actionRow);
        row.addChild(actionColumn);
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder serverCardsContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull HomeSetupState setup,
        @Nonnull List<ConfiguredPeer> peers,
        int width,
        int height
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(8));
        if (peers.isEmpty()) {
            container.addChild(emptyServersCard(width - 32, 94));
            return container;
        }

        for (int i = 0; i < peers.size(); i++) {
            container.addChild(homeServerCard(ref, store, playerRef, player, plugin, state, setup, peers.get(i), width - 32, HOME_SERVER_CARD_H));
            if (i + 1 < peers.size()) {
                container.addChild(spacerY(8));
            }
        }
        return container;
    }

    @Nonnull
    private static GroupBuilder homeServerCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull HomeSetupState setup,
        @Nonnull ConfiguredPeer peer,
        int width,
        int height
    ) {
        boolean local = !setup.localConnectionAddress().isBlank() && setup.localConnectionAddress().equalsIgnoreCase(peer.connectionAddress());
        boolean trusted = setup.trustedAddresses().contains(peer.connectionAddress().toLowerCase());
        boolean canTravel = !local && trusted && !setup.running();

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        int verticalOffset = Math.max(0, (height - rowHeight) / 2);
        card.addChild(spacerY(verticalOffset));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));

        int actionsWidth = SERVER_ACTION_CURRENT_W + 8 + SERVER_ACTION_EDIT_W + 8 + SERVER_ACTION_REMOVE_W;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - actionsWidth - 12 - SERVER_ACTION_EXTRA_LEFT_SHIFT).setHeight(rowHeight));
        identity.addChild(label(peer.displayName(), TITLE, 220));
        identity.addChild(spacerX(3));
        identity.addChild(label(peer.connectionAddress(), MUTED, Math.max(80, width - actionsWidth - 247 - SERVER_ACTION_EXTRA_LEFT_SHIFT)));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(local ? "CURRENT" : (trusted ? "TRAVEL" : "PENDING"))
                .withAnchor(new HyUIAnchor().setWidth(SERVER_ACTION_CURRENT_W).setHeight(30))
                .withDisabled(!canTravel)
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getSecureTravelService().travel(playerRef, peer, DEFAULT_REMOTE_TARGET_ID, "", TravelProfileType.KEEP_INVENTORY.id(), "");
                        dismissPage(player, ref, store);
                    } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not start travel to " + peer.displayName() + ": " + exception.getMessage()));
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(SERVER_ACTION_EDIT_W).setHeight(30))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withPendingServerDisplayName(peer.displayName())
                        .withPendingServerAddress(peer.connectionAddress())
                        .withEditingServerAddress(peer.connectionAddress())
                        .withStatusText("Editing " + peer.displayName() + ".")
                ))
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("Remove")
                .withAnchor(new HyUIAnchor().setWidth(SERVER_ACTION_REMOVE_W).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getConfiguredPeerService().remove(peer.connectionAddress());
                        open(ref, store, playerRef, player, plugin, state.clearedPendingServerDraft().withStatusText(removed ? "Removed " + peer.displayName() + "." : peer.displayName() + " was already removed."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not remove " + peer.displayName() + ": " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder emptyServersCard(int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("No servers saved yet", SUBTITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Add the servers that should belong to this Nexori network here first. Once the first trust bundle exists, the rest of the workspace unlocks.", MUTED, width - 32));
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder buildTravelBindScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        String localSelectorAddress = localSelectorAddress(setup.localConnectionAddress());
        List<TravelServerGroup> groups = buildTravelServerGroups(plugin, peers, setup.localConnectionAddress(), localSelectorAddress);

        int bindCardHeight = 188;
        int groupsHeight = 0;
        for (TravelServerGroup group : groups) {
            groupsHeight += serverEndpointsGroupHeight(group);
        }
        if (groups.size() > 1) {
            groupsHeight += (groups.size() - 1) * TRAVEL_SERVER_GROUP_GAP;
        }
        if (groupsHeight <= 0) {
            groupsHeight = 128;
        }

        int contentHeight = 16 + bindCardHeight + 12 + groupsHeight + 20;
        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(travelBindCard(ref, store, playerRef, player, plugin, state, peers, groups, setup.localConnectionAddress(), localSelectorAddress, innerWidth, bindCardHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(serverEndpointsContainer(ref, store, playerRef, player, plugin, state, groups, innerWidth, groupsHeight));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder travelBindCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull List<TravelServerGroup> groups,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress,
        int width,
        int height
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Travel Bind", TITLE, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder stack = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(height - 32));
        boolean hasSelectedIn = !state.selectedTravelInDisplayName().isBlank();
        boolean hasSelectedOut = !state.selectedTravelOutDisplayName().isBlank();
        int summaryGap = 12;
        int summaryRowWidth = width - 32;
        int profileWidth = 240;
        int inWidth = (summaryRowWidth - (summaryGap * 2) - profileWidth) / 2;
        int outWidth = summaryRowWidth - (summaryGap * 2) - profileWidth - inWidth;
        GroupBuilder summaryRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(72));
        summaryRow.addChild(selectionSummaryCard("A", hasSelectedIn ? state.selectedTravelInDisplayName() : "Select portal A below.", inWidth, hasSelectedIn));
        summaryRow.addChild(spacerX(summaryGap));
        summaryRow.addChild(travelProfileToggleCard(ref, store, playerRef, player, plugin, state, profileWidth));
        summaryRow.addChild(spacerX(summaryGap));
        summaryRow.addChild(selectionSummaryCard("B", hasSelectedOut ? state.selectedTravelOutDisplayName() : "Select portal or target B below.", outWidth, hasSelectedOut));
        stack.addChild(summaryRow);
        stack.addChild(spacerY(12));

        GroupBuilder actionsCenter = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(34));
        int syncButtonWidth = 280;
        int bindGroupWidth = 140 + 8 + 140 + 8 + 150 + 8 + 92;
        int clearWidth = 92;
        int actionsWidth = syncButtonWidth + 8 + bindGroupWidth;
        actionsCenter.addChild(spacerX(Math.max(0, (summaryRowWidth - actionsWidth) / 2)));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(actionsWidth).setHeight(34));
        boolean canBindForward = !state.selectedTravelInPortalId().isBlank() && !state.selectedTravelOutTargetId().isBlank();
        boolean canBindReverse = !state.selectedTravelOutPortalId().isBlank() && !state.selectedTravelInTargetId().isBlank();
        boolean canBindTwoWay = !state.selectedTravelInPortalId().isBlank() && !state.selectedTravelOutPortalId().isBlank();
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("SYNC INFO ON ALL SERVERS")
                .withAnchor(new HyUIAnchor().setWidth(syncButtonWidth).setHeight(34))
                .onClick((ignored, ctx) -> synchronizeTravelInfo(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state,
                    peers,
                    groups,
                    rawLocalConnectionAddress,
                    localSelectorAddress
                ))
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("Bind A -> B")
                .withDisabled(!canBindForward)
                .withAnchor(new HyUIAnchor().setWidth(140).setHeight(34))
                .onClick((ignored, ctx) -> {
                    TravelBindOperation operation = forwardBindingOperation(state);
                    if (operation == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Pick portal A first."));
                        return;
                    }
                    executeTravelBindingPlan(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state,
                        localSelectorAddress,
                        List.of(operation),
                        "Bound A -> B from " + state.selectedTravelInDisplayName() + " to " + state.selectedTravelOutDisplayName() + "."
                    );
                })
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("Bind B -> A")
                .withDisabled(!canBindReverse)
                .withAnchor(new HyUIAnchor().setWidth(140).setHeight(34))
                .onClick((ignored, ctx) -> {
                    TravelBindOperation operation = reverseBindingOperation(state);
                    if (operation == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Pick portal B first."));
                        return;
                    }
                    executeTravelBindingPlan(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state,
                        localSelectorAddress,
                        List.of(operation),
                        "Bound B -> A from " + state.selectedTravelOutDisplayName() + " to " + state.selectedTravelInDisplayName() + "."
                    );
                })
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("Bind A <-> B")
                .withDisabled(!canBindTwoWay)
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(34))
                .onClick((ignored, ctx) -> {
                    TravelBindOperation forward = forwardBindingOperation(state);
                    TravelBindOperation reverse = reverseBindingOperation(state);
                    if (forward == null || reverse == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Pick two portals to use Bind A <-> B."));
                        return;
                    }
                    executeTravelBindingPlan(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state,
                        localSelectorAddress,
                        List.of(forward, reverse),
                        "Bound A <-> B between " + state.selectedTravelInDisplayName() + " and " + state.selectedTravelOutDisplayName() + "."
                    );
                })
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("CLEAR")
                .withAnchor(new HyUIAnchor().setWidth(clearWidth).setHeight(34))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.clearedTravelSelection().withStatusText("Cleared A / B selection.")))
        );
        actionsCenter.addChild(actions);
        stack.addChild(actionsCenter);

        card.addChild(stack);
        return card;
    }

    @Nonnull
    private static GroupBuilder selectionSummaryCard(@Nonnull String labelText, @Nonnull String value, int width, boolean selected) {
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
    private static GroupBuilder travelProfileToggleCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int width
    ) {
        TravelProfileType profile = currentTravelProfile(state);
        String buttonText = profile == TravelProfileType.APPLY_INVENTORY
            ? "TRAVEL WITH INVENTORY"
            : "NORMAL TRAVEL";
        String nextStatus = profile == TravelProfileType.APPLY_INVENTORY
            ? "Selected normal travel for new portal binds."
            : "Selected travel with inventory for new portal binds.";

        int cardHeight = 72;
        int buttonHeight = 42;
        int horizontalInset = 12;
        int verticalOffset = Math.max(0, (cardHeight - buttonHeight) / 2);

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(cardHeight))
            .withBackground(SERVER_CARD_BG);
        card.addChild(spacerY(verticalOffset));
        GroupBuilder row = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(buttonHeight));
        row.addChild(spacerX(horizontalInset));
        row.addChild(
            ButtonBuilder.textButton()
                .withText(buttonText)
                .withAnchor(new HyUIAnchor().setWidth(width - (horizontalInset * 2)).setHeight(buttonHeight))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedTravelProfileId(toggleTravelProfile(profile).id()).withStatusText(nextStatus)
                ))
        );
        card.addChild(row);
        return card;
    }

    private static void executeTravelBindingPlan(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull String localSelectorAddress,
        @Nonnull List<TravelBindOperation> requestedOperations,
        @Nonnull String successMessage
    ) {
        List<TravelBindOperation> localOperations = new ArrayList<>();
        List<TravelBindOperation> remoteOperations = new ArrayList<>();
        for (TravelBindOperation operation : requestedOperations) {
            if (operation == null) {
                continue;
            }
            if (operation.sourceConnectionAddress().equals(localSelectorAddress)) {
                localOperations.add(operation);
            } else {
                remoteOperations.add(operation);
            }
        }

        int localAppliedCount = 0;
        try {
            for (TravelBindOperation operation : localOperations) {
                applyBindingOperationLocally(plugin, localSelectorAddress, operation);
                localAppliedCount++;
            }
        } catch (IOException | IllegalArgumentException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText("Could not save portal binding: " + exception.getMessage())
            );
            return;
        }

        if (remoteOperations.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.clearedTravelSelection().withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(successMessage)
            );
            return;
        }

        String originWorldName = player.getWorld().getName();
        Transform originTransform = captureCurrentTransform(store, ref);
        try {
            applyRemoteBindingOperationAtIndex(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state,
                remoteOperations,
                originWorldName,
                originTransform,
                0,
                successMessage,
                localAppliedCount
            );
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(partialBindingFailurePrefix(localAppliedCount) + "Could not start secure portal binding: " + exception.getMessage())
            );
        }
    }

    private static void synchronizeTravelInfo(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull List<TravelServerGroup> groups,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress
    ) {
        List<ConfiguredPeer> remotePeers = trustedDiscoveryPeers(groups, rawLocalConnectionAddress, localSelectorAddress);
        if (remotePeers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText("No other trusted servers are available to synchronize.")
            );
            return;
        }

        try {
            discoverRemoteEndpointsAtIndex(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state,
                remotePeers,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                0
            );
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText("Could not start endpoint sync: " + exception.getMessage())
            );
        }
    }

    private static void discoverRemoteEndpointsAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index
    ) throws IOException, GeneralSecurityException {
        ConfiguredPeer destination = remotePeers.get(index);
        plugin.getDestinationTargetDiscoveryService().discover(
            playerRef,
            destination,
            originWorldName,
            originTransform.clone(),
            chainedTravelDiscoveryResumeAction(
                plugin,
                state,
                remotePeers,
                originWorldName,
                originTransform,
                index + 1
            )
        );
    }

    @Nonnull
    private static UiResumeAction chainedTravelDiscoveryResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int nextIndex
    ) {
        return new UiResumeAction() {
            @Override
            public boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
                if (nextIndex < remotePeers.size()) {
                    ConfiguredPeer nextDestination = remotePeers.get(nextIndex);
                    plugin.getDestinationTargetDiscoveryService().discover(
                        event,
                        nextDestination,
                        originWorldName,
                        originTransform.clone(),
                        chainedTravelDiscoveryResumeAction(
                            plugin,
                            state,
                            remotePeers,
                            originWorldName,
                            originTransform,
                            nextIndex + 1
                        )
                    );
                    return true;
                }

                List<DiscoveredDestinationTargetSet> snapshot = plugin.getDestinationTargetDiscoverySyncService().currentNetworkSnapshot();
                if (snapshot.isEmpty()) {
                    return false;
                }

                ConfiguredPeer firstDestination = remotePeers.getFirst();
                plugin.getDestinationTargetDiscoverySyncService().apply(
                    event,
                    firstDestination,
                    originWorldName,
                    originTransform.clone(),
                    snapshot,
                    chainedTravelDiscoverySyncResumeAction(
                        plugin,
                        state,
                        remotePeers,
                        snapshot,
                        originWorldName,
                        originTransform,
                        1
                    )
                );
                return true;
            }

            @Override
            public void reopen(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player
            ) {
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText("Synchronized portal and target info across " + (remotePeers.size() + 1) + " server(s).")
                );
            }

            @Override
            public void reopenWithStatus(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player,
                @Nonnull String status,
                boolean success
            ) {
                String finalStatus = success
                    ? "Synchronized portal and target info across " + (remotePeers.size() + 1) + " server(s)."
                    : status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(finalStatus)
                );
            }
        };
    }

    @Nonnull
    private static UiResumeAction chainedTravelDiscoverySyncResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull List<DiscoveredDestinationTargetSet> snapshot,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int nextIndex
    ) {
        return new UiResumeAction() {
            @Override
            public boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
                if (nextIndex >= remotePeers.size()) {
                    return false;
                }
                ConfiguredPeer nextDestination = remotePeers.get(nextIndex);
                plugin.getDestinationTargetDiscoverySyncService().apply(
                    event,
                    nextDestination,
                    originWorldName,
                    originTransform.clone(),
                    snapshot,
                    chainedTravelDiscoverySyncResumeAction(
                        plugin,
                        state,
                        remotePeers,
                        snapshot,
                        originWorldName,
                        originTransform,
                        nextIndex + 1
                    )
                );
                return true;
            }

            @Override
            public void reopen(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player
            ) {
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText("Synchronized portal and target info across " + (remotePeers.size() + 1) + " server(s).")
                );
            }

            @Override
            public void reopenWithStatus(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player,
                @Nonnull String status,
                boolean success
            ) {
                String finalStatus = success
                    ? "Synchronized portal and target info across " + (remotePeers.size() + 1) + " server(s)."
                    : status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(finalStatus)
                );
            }
        };
    }

    @Nonnull
    private static List<ConfiguredPeer> trustedDiscoveryPeers(
        @Nonnull List<TravelServerGroup> groups,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress
    ) {
        String normalizedLocal = rawLocalConnectionAddress == null ? "" : rawLocalConnectionAddress.trim().toLowerCase();
        List<ConfiguredPeer> peers = new ArrayList<>();
        Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (TravelServerGroup group : groups) {
            String address = group.connectionAddress() == null ? "" : group.connectionAddress().trim().toLowerCase();
            if (address.isBlank()) {
                continue;
            }
            if (address.equals(localSelectorAddress) || (!normalizedLocal.isBlank() && address.equals(normalizedLocal))) {
                continue;
            }
            if (seen.add(address)) {
                peers.add(ConfiguredPeer.parse(address));
            }
        }
        return peers;
    }

    private static void applyRemoteBindingOperationAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelBindOperation> remoteOperations,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index,
        @Nonnull String successMessage,
        int localAppliedCount
    ) throws IOException, GeneralSecurityException {
        if (index >= remoteOperations.size()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.clearedTravelSelection().withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(successMessage)
            );
            return;
        }

        TravelBindOperation operation = remoteOperations.get(index);
        ConfiguredPeer destination = ConfiguredPeer.parse(operation.sourceConnectionAddress());
        plugin.getPortalBindingSyncService().apply(
            playerRef,
            destination,
            originWorldName,
            originTransform.clone(),
            operation.sourcePortalId(),
            operation.destinationConnectionAddress(),
            operation.destinationTargetId(),
            operation.travelProfileId(),
            "{}",
            chainedPortalBindingResumeAction(
                plugin,
                state,
                remoteOperations,
                originWorldName,
                originTransform,
                index + 1,
                successMessage,
                localAppliedCount
            )
        );
    }

    @Nonnull
    private static UiResumeAction chainedPortalBindingResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelBindOperation> remoteOperations,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int nextIndex,
        @Nonnull String successMessage,
        int localAppliedCount
    ) {
        return new UiResumeAction() {
            @Override
            public boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
                if (nextIndex >= remoteOperations.size()) {
                    return false;
                }
                TravelBindOperation nextOperation = remoteOperations.get(nextIndex);
                ConfiguredPeer destination = ConfiguredPeer.parse(nextOperation.sourceConnectionAddress());
                plugin.getPortalBindingSyncService().apply(
                    event,
                    destination,
                    originWorldName,
                    originTransform.clone(),
                    nextOperation.sourcePortalId(),
                    nextOperation.destinationConnectionAddress(),
                    nextOperation.destinationTargetId(),
                    nextOperation.travelProfileId(),
                    "{}",
                    chainedPortalBindingResumeAction(
                        plugin,
                        state,
                        remoteOperations,
                        originWorldName,
                        originTransform,
                        nextIndex + 1,
                        successMessage,
                        localAppliedCount
                    )
                );
                return true;
            }

            @Override
            public void reopen(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player
            ) {
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.clearedTravelSelection().withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(successMessage)
                );
            }

            @Override
            public void reopenWithStatus(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player,
                @Nonnull String status,
                boolean success
            ) {
                String finalStatus = success ? successMessage : partialBindingFailurePrefix(localAppliedCount) + status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.clearedTravelSelection().withSelectedView(NexoriMenuV2View.PORTALS).withStatusText(finalStatus)
                );
            }
        };
    }

    private static void applyBindingOperationLocally(
        @Nonnull NexoriPlugin plugin,
        @Nonnull String localSelectorAddress,
        @Nonnull TravelBindOperation operation
    ) throws IOException {
        if (!operation.sourceConnectionAddress().equals(localSelectorAddress)) {
            throw new IllegalArgumentException("This portal binding must be applied on " + operation.sourceConnectionAddress() + ".");
        }
        boolean localTarget = operation.destinationConnectionAddress().isBlank()
            || operation.destinationConnectionAddress().equals(localSelectorAddress);
        if (localTarget) {
            plugin.getTriggerBindingService().bindPortalCollisionLocalTarget(
                operation.sourcePortalId(),
                operation.destinationTargetId()
            );
            return;
        }
            plugin.getTriggerBindingService().bindPortalCollisionTravel(
                operation.sourcePortalId(),
                operation.destinationConnectionAddress(),
                operation.destinationTargetId(),
                operation.travelProfileId(),
                "{}"
            );
    }

    @Nonnull
    private static String partialBindingFailurePrefix(int localAppliedCount) {
        if (localAppliedCount <= 0) {
            return "";
        }
        return "Saved " + localAppliedCount + " local portal binding" + (localAppliedCount == 1 ? "" : "s") + ", but ";
    }

    @Nonnull
    private static TravelProfileType currentTravelProfile(@Nonnull NexoriMenuV2State state) {
        return TravelProfileType.parse(state.selectedTravelProfileId());
    }

    @Nonnull
    private static TravelProfileType toggleTravelProfile(@Nonnull TravelProfileType profile) {
        return profile == TravelProfileType.APPLY_INVENTORY
            ? TravelProfileType.KEEP_INVENTORY
            : TravelProfileType.APPLY_INVENTORY;
    }

    private static TravelBindOperation forwardBindingOperation(@Nonnull NexoriMenuV2State state) {
        if (state.selectedTravelInPortalId().isBlank() || state.selectedTravelOutTargetId().isBlank()) {
            return null;
        }
        return new TravelBindOperation(
            state.selectedTravelInConnectionAddress(),
            state.selectedTravelInPortalId(),
            state.selectedTravelOutConnectionAddress(),
            state.selectedTravelOutTargetId(),
            currentTravelProfile(state).id()
        );
    }

    private static TravelBindOperation reverseBindingOperation(@Nonnull NexoriMenuV2State state) {
        if (state.selectedTravelOutPortalId().isBlank() || state.selectedTravelInTargetId().isBlank()) {
            return null;
        }
        return new TravelBindOperation(
            state.selectedTravelOutConnectionAddress(),
            state.selectedTravelOutPortalId(),
            state.selectedTravelInConnectionAddress(),
            state.selectedTravelInTargetId(),
            currentTravelProfile(state).id()
        );
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
    private static GroupBuilder serverEndpointsContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelServerGroup> groups,
        int width,
        int height
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);

        if (groups.isEmpty()) {
            container.addChild(label("No trusted servers or discovered endpoints are available yet.", MUTED, width - 32));
            return container;
        }

        int contentHeight = 0;
        for (TravelServerGroup group : groups) {
            contentHeight += serverEndpointsGroupHeight(group) - 16;
        }
        if (groups.size() > 1) {
            contentHeight += (groups.size() - 1) * TRAVEL_SERVER_GROUP_GAP;
        }
        int availableHeight = Math.max(0, height - 32);
        int verticalOffset = Math.max(0, (availableHeight - contentHeight) / 2);
        container.addChild(spacerY(verticalOffset));

        for (int i = 0; i < groups.size(); i++) {
            container.addChild(serverEndpointsGroupCard(ref, store, playerRef, player, plugin, state, groups.get(i), width - 32));
            if (i + 1 < groups.size()) {
                container.addChild(spacerY(TRAVEL_SERVER_GROUP_GAP));
            }
        }
        return container;
    }

    @Nonnull
    private static GroupBuilder serverEndpointsGroupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelServerGroup group,
        int width
    ) {
        int height = serverEndpointsGroupHeight(group) - 16;
        GroupBuilder card = card(width, height, SERVER_CARD_BG);
        String address = group.connectionAddress().isBlank() ? "local" : group.connectionAddress();
        int contentHeight = 30;
        if (group.portals().isEmpty() && group.coordinateTargets().isEmpty()) {
            contentHeight += 12 + 20;
        } else {
            contentHeight += 12;
            if (!group.portals().isEmpty()) {
                contentHeight += sectionHeight(group.portals().size());
            }
            if (!group.coordinateTargets().isEmpty()) {
                contentHeight += (!group.portals().isEmpty() ? 12 : 0) + sectionHeight(group.coordinateTargets().size());
            }
        }
        int availableHeight = Math.max(0, height - 32);
        int verticalOffset = Math.max(0, ((availableHeight - contentHeight) / 2) + 4);
        card.addChild(spacerY(verticalOffset));

        GroupBuilder headerRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(30));
        headerRow.addChild(label(group.displayName() + "   " + address, TITLE, width - 32));
        card.addChild(headerRow);
        card.addChild(spacerY(12));

        if (group.portals().isEmpty() && group.coordinateTargets().isEmpty()) {
            card.addChild(label("No endpoints are known for this server yet.", MUTED, width - 32));
            return card;
        }

        if (!group.portals().isEmpty()) {
            int portalContentHeight = sectionContentHeight(group.portals().size());
            card.addChild(endpointSectionCard(
                width - 32,
                sectionHeight(group.portals().size()),
                portalContentHeight,
                buildPortalSectionContent(ref, store, playerRef, player, plugin, state, group.portals(), width - 64)
            ));
        }

        if (!group.coordinateTargets().isEmpty()) {
            if (!group.portals().isEmpty()) {
                card.addChild(spacerY(12));
            }
            int targetContentHeight = sectionContentHeight(group.coordinateTargets().size());
            card.addChild(endpointSectionCard(
                width - 32,
                sectionHeight(group.coordinateTargets().size()),
                targetContentHeight,
                buildTargetSectionContent(ref, store, playerRef, player, plugin, state, group.coordinateTargets(), width - 64)
            ));
        }
        return card;
    }

    @Nonnull
    private static GroupBuilder endpointSectionCard(
        int width,
        int height,
        int contentHeight,
        @Nonnull GroupBuilder content
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        int verticalOffset = Math.max(TRAVEL_SECTION_INSET, (height - contentHeight) / 2);
        card.addChild(spacerY(verticalOffset));
        card.addChild(content);
        return card;
    }

    @Nonnull
    private static GroupBuilder buildPortalSectionContent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelPortalEndpoint> portals,
        int width
    ) {
        int totalHeight = portals.size() * HOME_SERVER_CARD_H + Math.max(0, portals.size() - 1) * 8;
        GroupBuilder group = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(totalHeight));
        for (int i = 0; i < portals.size(); i++) {
            group.addChild(portalEndpointRow(ref, store, playerRef, player, plugin, state, portals.get(i), width, HOME_SERVER_CARD_H));
            if (i + 1 < portals.size()) {
                group.addChild(spacerY(8));
            }
        }
        return group;
    }

    @Nonnull
    private static GroupBuilder buildTargetSectionContent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelTargetEndpoint> targets,
        int width
    ) {
        int totalHeight = targets.size() * HOME_SERVER_CARD_H + Math.max(0, targets.size() - 1) * 8;
        GroupBuilder group = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(totalHeight));
        for (int i = 0; i < targets.size(); i++) {
            group.addChild(targetEndpointRow(ref, store, playerRef, player, plugin, state, targets.get(i), width, HOME_SERVER_CARD_H));
            if (i + 1 < targets.size()) {
                group.addChild(spacerY(8));
            }
        }
        return group;
    }

    @Nonnull
    private static GroupBuilder portalEndpointRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelPortalEndpoint portal,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        int verticalOffset = Math.max(0, (height - rowHeight) / 2);
        card.addChild(spacerY(verticalOffset));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int actionsWidth = 68 + 8 + 68;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - actionsWidth - 40).setHeight(rowHeight));
        identity.addChild(label(portal.displayName(), TITLE, width - actionsWidth - 52));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            endpointSelectionButton(
                "A",
                state.selectedTravelInPortalId().equals(portal.portalId()) && state.selectedTravelInConnectionAddress().equals(portal.connectionAddress()),
                68,
                () -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedTravelIn(
                        portal.connectionAddress(),
                        portal.portalId(),
                        portal.targetId(),
                        portal.displayName()
                    ).withStatusText("Selected A: " + portal.displayName() + ".")
                )
            )
        );
        row.addChild(spacerX(8));
        row.addChild(
            endpointSelectionButton(
                "B",
                state.selectedTravelOutTargetId().equals(portal.targetId()) && state.selectedTravelOutConnectionAddress().equals(portal.connectionAddress()),
                68,
                () -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedTravelOut(
                        portal.connectionAddress(),
                        portal.portalId(),
                        portal.targetId(),
                        portal.displayName()
                    ).withStatusText("Selected B: " + portal.displayName() + ".")
                )
            )
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder targetEndpointRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelTargetEndpoint target,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        int verticalOffset = Math.max(0, (height - rowHeight) / 2);
        card.addChild(spacerY(verticalOffset));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 120).setHeight(rowHeight));
        identity.addChild(label(target.displayName(), TITLE, width - 132));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            endpointSelectionButton(
                "B",
                state.selectedTravelOutTargetId().equals(target.targetId()) && state.selectedTravelOutConnectionAddress().equals(target.connectionAddress()),
                68,
                () -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedTravelOut(
                        target.connectionAddress(),
                        "",
                        target.targetId(),
                        target.displayName()
                    ).withStatusText("Selected B: " + target.displayName() + ".")
                )
            )
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static ButtonBuilder endpointSelectionButton(
        @Nonnull String text,
        boolean selected,
        int width,
        @Nonnull Runnable onClick
    ) {
        return ButtonBuilder.smallSecondaryTextButton()
            .withText(text)
            .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(30))
            .onClick((ignored, ctx) -> onClick.run());
    }

    @Nonnull
    private static List<TravelServerGroup> buildTravelServerGroups(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress
    ) {
        String localConnectionAddress = rawLocalConnectionAddress == null ? "" : rawLocalConnectionAddress.trim().toLowerCase();
        Map<String, ConfiguredPeer> configuredByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : peers) {
            configuredByAddress.put(peer.connectionAddress().toLowerCase(), peer);
        }

        List<String> orderedAddresses = new ArrayList<>();
        if (!localConnectionAddress.isBlank()) {
            orderedAddresses.add(localConnectionAddress);
        }
        for (ConfiguredPeer peer : peers) {
            if (!orderedAddresses.contains(peer.connectionAddress().toLowerCase())) {
                orderedAddresses.add(peer.connectionAddress().toLowerCase());
            }
        }
        for (BundleMember member : plugin.getBootstrapCoordinator().getTrustBundle().members()) {
            String connectionAddress = member.connectionAddress() == null ? "" : member.connectionAddress().trim().toLowerCase();
            if (!connectionAddress.isBlank() && !orderedAddresses.contains(connectionAddress)) {
                orderedAddresses.add(connectionAddress);
            }
        }

        List<TravelServerGroup> groups = new ArrayList<>();
        for (String connectionAddress : orderedAddresses) {
            boolean local = !localConnectionAddress.isBlank() && connectionAddress.equals(localConnectionAddress);
            String selectorAddress = local ? localSelectorAddress : connectionAddress;
            String displayName = resolveServerDisplayName(configuredByAddress, connectionAddress, local);
            List<TravelPortalEndpoint> portals = new ArrayList<>();
            List<TravelTargetEndpoint> targets = new ArrayList<>();

            if (local) {
                for (PortalInstanceDefinition portal : plugin.getPortalInstanceService().list()) {
                    portals.add(new TravelPortalEndpoint(
                        selectorAddress,
                        displayName,
                        portal.portalId(),
                        portal.autoDestinationTargetId(),
                        portal.displayName()
                    ));
                }
                for (DestinationTargetDefinition target : plugin.getDestinationTargetService().list()) {
                    if (target.kind() == DestinationTargetKind.PORTAL) {
                        continue;
                    }
                    targets.add(new TravelTargetEndpoint(
                        selectorAddress,
                        displayName,
                        target.id(),
                        target.displayName()
                    ));
                }
            } else {
                DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(connectionAddress).orElse(null);
                if (discovery != null) {
                    for (DiscoveredDestinationTargetSummary target : discovery.targets()) {
                        DestinationTargetKind kind;
                        try {
                            kind = DestinationTargetKind.parse(target.kind());
                        } catch (IllegalArgumentException ignored) {
                            continue;
                        }
                        if (kind == DestinationTargetKind.PORTAL) {
                            portals.add(new TravelPortalEndpoint(
                                selectorAddress,
                                displayName,
                                target.portalId() == null ? "" : target.portalId(),
                                target.id(),
                                target.displayName()
                            ));
                            continue;
                        }
                        targets.add(new TravelTargetEndpoint(
                            selectorAddress,
                            displayName,
                            target.id(),
                            target.displayName()
                        ));
                    }
                }
            }

            groups.add(new TravelServerGroup(displayName, connectionAddress, local, portals, targets));
        }
        return groups;
    }

    @Nonnull
    private static String resolveServerDisplayName(@Nonnull Map<String, ConfiguredPeer> configuredByAddress, @Nonnull String connectionAddress, boolean local) {
        ConfiguredPeer peer = configuredByAddress.get(connectionAddress.toLowerCase());
        if (peer != null) {
            return peer.displayName();
        }
        return local ? "Current Server" : connectionAddress;
    }

    private static int serverEndpointsGroupHeight(@Nonnull TravelServerGroup group) {
        int portalRows = group.portals().size();
        int targetRows = group.coordinateTargets().size();
        if (portalRows == 0 && targetRows == 0) {
            return 112;
        }
        int height = 58;
        if (portalRows > 0) {
            height += sectionHeight(portalRows);
        }
        if (targetRows > 0) {
            height += (portalRows > 0 ? 12 : 0) + sectionHeight(targetRows);
        }
        return height + 26;
    }

    private static int sectionHeight(int rows) {
        return sectionContentHeight(rows) + 32 + (TRAVEL_SECTION_INSET * 2);
    }

    private static int sectionContentHeight(int rows) {
        return rows * HOME_SERVER_CARD_H + Math.max(0, rows - 1) * 8;
    }

    @Nonnull
    private static String localSelectorAddress(@Nonnull String rawLocalConnectionAddress) {
        String normalized = rawLocalConnectionAddress == null ? "" : rawLocalConnectionAddress.trim().toLowerCase();
        return normalized.isBlank() ? "__local__" : normalized;
    }

    @Nonnull
    private static Transform captureCurrentTransform(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            throw new IllegalStateException("Could not read the live player position for secure portal binding.");
        }
        Vector3f rotation = transformComponent.getRotation();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            rotation = headRotation.getRotation();
        }
        return new Transform(transformComponent.getPosition(), rotation);
    }

    @Nonnull
    private static GroupBuilder sectionHeaderCard(@Nonnull String title, @Nonnull String detail, int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label(title, SUBTITLE, width - 32));
        if (!detail.isBlank()) {
            card.addChild(spacerY(8));
            card.addChild(label(detail, MUTED, width - 32));
        }
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder buildAboutScroll(int viewportHeight, @Nonnull String scrollId) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        int introHeight = 320;
        int conceptsHeight = 450;
        int securityHeight = 200;
        int contentHeight = 16 + introHeight + 12 + conceptsHeight + 12 + securityHeight + 20;
        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));

        GroupBuilder intro = card(innerWidth, introHeight, PANEL_BG);
        intro.addChild(label("What Nexori is", TITLE, innerWidth - 32));
        intro.addChild(spacerY(8));
        intro.addChild(label("Nexori is a secure cross-server routing layer for Hytale. Its main purpose is to use the context payloads that travel with each server-to-server transfer to connect servers and let them interact. That context can be modified from the client side while a player moves between servers, so the destination server must be able to know, cryptographically, that the payload really came from a trusted server and that the client did not tamper with the context while it was in transit. That is why Nexori uses key pairs and signatures, allowing the destination server to verify the message instead of trusting raw client input.", BODY, innerWidth - 32));
        intro.addChild(spacerY(8));
        intro.addChild(label("Once those transfers are secured, Nexori treats player travel itself as a server-to-server communication protocol. That means you do not need a backend acting as the central communication bus for every server. Through travel context, Nexori can move inventories, run lobby-to-arena queue flows, launch minigame instances, and support adventure networks that carry a player's state between servers.", BODY, innerWidth - 32));
        intro.addChild(spacerY(8));
        intro.addChild(label("Nexori is built both for people who do not want to touch code and only need secure travel between adventure servers, and for modders building minigames. If you build on top of Nexori, you can focus on your own game logic while Nexori handles secure travel, lobbies, queues, instance creation, instance cleanup, and the rest of the network plumbing around the match.", MUTED, innerWidth - 32));
        scroll.addChild(intro);
        scroll.addChild(spacerY(12));

        GroupBuilder blocks = card(innerWidth, conceptsHeight, PANEL_BG);
        blocks.addChild(label("Key Concepts", TITLE, innerWidth - 32));
        blocks.addChild(spacerY(8));
        blocks.addChild(conceptItem("Portal", "A Nexori portal is a world interaction point that can trigger secure travel, local target travel, queue join, or queue leave.", innerWidth - 32));
        blocks.addChild(spacerY(10));
        blocks.addChild(conceptItem("Target Destination", "A named arrival point. For short, the UI and commands usually just call it a target.", innerWidth - 32));
        blocks.addChild(spacerY(10));
        blocks.addChild(conceptItem("Queue", "A lobby-side waiting line that groups players until a ready batch can launch.", innerWidth - 32));
        blocks.addChild(spacerY(10));
        blocks.addChild(conceptItem("Arena", "A remote match destination that can launch players into a built-in Hytale instance template.", innerWidth - 32));
        blocks.addChild(spacerY(10));
        blocks.addChild(conceptItem("Initial Setup / Trust Bundle", "The bootstrap step that installs trusted server identities and keys so secure travel can be verified.", innerWidth - 32));
        blocks.addChild(spacerY(10));
        blocks.addChild(conceptItem("Owner-driven backend-free requests", "These requests are intended for the server owner through Nexori's admin tools and UI, not for regular players. For example, Initial Setup uses this pattern to ask each server for its public key, then builds a trust bundle containing all known public keys so every server can verify the identity of another server and the authenticity of server-to-server travel messages.", innerWidth - 32));
        scroll.addChild(blocks);
        scroll.addChild(spacerY(12));

        GroupBuilder security = card(innerWidth, securityHeight, PANEL_BG);
        security.addChild(label("Security Model", TITLE, innerWidth - 32));
        security.addChild(spacerY(8));
        security.addChild(label("Nexori is designed around the idea that third parties may try to imitate the server-to-server protocol with malicious requests. After Initial Setup runs, each server has the trust bundle it needs to reject fake server-to-server traffic that is not signed by a trusted server identity.", BODY, innerWidth - 32));
        security.addChild(spacerY(8));
        security.addChild(label("Nexori also aims to let owners restrict which players are allowed to connect to specific servers, such as preventing players from skipping directly into arena servers. That restriction layer is planned as part of the product direction and will harden the network further in future versions.", MUTED, innerWidth - 32));
        scroll.addChild(security);
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder buildPortalSettingsScroll(int viewportHeight, @Nonnull String scrollId) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        int cardHeight = Math.max(220, viewportHeight - 32);
        int contentHeight = 16 + cardHeight + 20;
        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));

        GroupBuilder card = card(innerWidth, cardHeight, PANEL_BG);
        scroll.addChild(card);
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder buildPlaceholderScroll(@Nonnull NexoriMenuV2View view, int viewportHeight, @Nonnull String scrollId) {
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
    private static String viewSubtitle(@Nonnull NexoriMenuV2View view) {
        return switch (view) {
            case HOME -> "Minimal server setup, trust bundle status, and local server list.";
            case PORTALS -> "";
            case TARGETS -> "Targets are now folded into the Portals travel bind workspace.";
            case RULES -> "Rules stay separate because they group servers under policies.";
            case QUEUES -> "Queues, lobbies, and arenas will be configured here.";
            case OPERATIONS -> "Live runtime, diagnostics, and recovery belong here.";
            case ABOUT -> "Detailed context for what Nexori does and how this workspace is organized.";
        };
    }

    @Nonnull
    private static String placeholderDetail(@Nonnull NexoriMenuV2View view) {
        return switch (view) {
            case PORTALS -> "This will become the star visualizer: server/world groups, portal nodes, bindings, and arrows.";
            case TARGETS -> "Targets are now grouped inside the travel bind workspace.";
            case RULES -> "Rules keep their own mental model: define policies once, then attach servers under them.";
            case QUEUES -> "This view will own lobbies, queues, arenas, and instance-backed minigame config.";
            case OPERATIONS -> "Operations will show active queues, active matches, handoffs, diagnostics, and repair flows.";
            case HOME, ABOUT -> "";
        };
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
    private static GroupBuilder statusBar(@Nonnull NexoriMenuV2State state) {
        GroupBuilder bar = card(BODY_W, STATUS_H, STATUS_BG);
        bar.addChild(label("Action / Status", SUBTITLE, BODY_W - 32));
        bar.addChild(spacerY(6));
        bar.addChild(label(state.statusText().isBlank() ? "No action yet. This footer is reserved for confirmations, errors, guided actions, and next-step hints." : state.statusText(), BODY, BODY_W - 32));
        return bar;
    }

    @Nonnull
    private static GroupBuilder inputField(@Nonnull String labelText, @Nonnull String fieldId, @Nonnull String currentValue, @Nonnull String placeholder, int width) {
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
    private static GroupBuilder statusBadgeRow(@Nonnull String prefix, @Nonnull String value, int width, @Nonnull HyUIStyle valueStyle, @Nonnull HyUIPatchStyle background) {
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
    private static GroupBuilder card(int width, int height, @Nonnull HyUIPatchStyle background) {
        return GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(background);
    }

    @Nonnull
    private static ReorderableListBuilder scrollList(int width, int height, int contentHeight, @Nonnull String id, boolean keepScrollPosition) {
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
        return LabelBuilder.label().withText(text).withAnchor(new HyUIAnchor().setWidth(width)).withStyle(style);
    }

    private static void dismissPage(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PageManager pages = player.getPageManager();
        if (pages != null) {
            pages.setPage(ref, store, Page.None);
        }
    }

    @Nonnull
    private static HomeSetupState buildHomeSetupState(@Nonnull NexoriPlugin plugin, @Nonnull List<ConfiguredPeer> peers) {
        BootstrapState bootstrapState = plugin.getBootstrapStateStore().getCurrentState();
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        boolean hasBundle = bundle.bundleVersion() > 0 && !bundle.bundleHash().isBlank() && !bundle.members().isEmpty();
        boolean running = bootstrapState.hasActiveSession();
        String localConnectionAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();

        Set<String> configured = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConfiguredPeer peer : peers) {
            configured.add(peer.connectionAddress());
        }
        Set<String> trusted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (BundleMember member : bundle.members()) {
            if (member.connectionAddress() != null && !member.connectionAddress().isBlank()) {
                trusted.add(member.connectionAddress().trim().toLowerCase());
            }
        }

        boolean dirty = !hasBundle || !configured.equals(new TreeSet<>(trusted));
        boolean localMissing = !localConnectionAddress.isBlank() && configured.stream().noneMatch(address -> address.equalsIgnoreCase(localConnectionAddress));

        if (running) {
            return new HomeSetupState(true, false, true, localConnectionAddress, trusted, "Initial Setup running", "Nexori is verifying peers and rebuilding the active trust bundle.", "Wait for the run to finish, then reopen Servers.", INFO, INFO_BG);
        }
        if (!hasBundle) {
            String detail = peers.isEmpty()
                ? "Add every server that should belong to this network here, including the current server, before running the first Initial Setup."
                : "The first trust bundle does not exist yet. Review the servers below and run Initial Setup.";
            if (localMissing) {
                detail += " Include this server as well (" + localConnectionAddress + ").";
            }
            return new HomeSetupState(true, !peers.isEmpty(), true, localConnectionAddress, trusted, peers.isEmpty() ? "Waiting for servers" : "Ready for first bundle", detail, "Until the first trust bundle exists, Servers is the only unlocked view.", peers.isEmpty() ? BAD : INFO, peers.isEmpty() ? BAD_BG : INFO_BG);
        }
        if (dirty) {
            String detail = "The saved server list on this machine no longer matches the active trust bundle.";
            if (localMissing) {
                detail += " The current server is also missing from this local list (" + localConnectionAddress + ").";
            }
            return new HomeSetupState(false, !peers.isEmpty(), true, localConnectionAddress, trusted, "Bundle needs re-run", detail, "Run Initial Setup again after every add/remove.", BAD, BAD_BG);
        }
        return new HomeSetupState(false, false, false, localConnectionAddress, trusted, "Trusted network ready", "The local server list matches the active trust bundle. Nexori is ready for secure travel.", "Bundle v" + bundle.bundleVersion() + " updated at " + TIME_FORMAT.format(Instant.ofEpochMilli(bundle.updatedAtEpochMillis())) + ".", GOOD, GOOD_BG);
    }

    private record TravelServerGroup(
        String displayName,
        String connectionAddress,
        boolean local,
        List<TravelPortalEndpoint> portals,
        List<TravelTargetEndpoint> coordinateTargets
    ) {
    }

    private record TravelPortalEndpoint(
        String connectionAddress,
        String serverDisplayName,
        String portalId,
        String targetId,
        String displayName
    ) {
    }

    private record TravelTargetEndpoint(
        String connectionAddress,
        String serverDisplayName,
        String targetId,
        String displayName
    ) {
    }

    private record HomeSetupState(
        boolean viewsLocked,
        boolean canRunBootstrap,
        boolean dirty,
        @Nonnull String localConnectionAddress,
        @Nonnull Set<String> trustedAddresses,
        @Nonnull String statusLabel,
        @Nonnull String detail,
        @Nonnull String actionHint,
        @Nonnull HyUIStyle statusStyle,
        @Nonnull HyUIPatchStyle statusBackground
    ) {
        private boolean running() {
            return statusLabel.equals("Initial Setup running");
        }

        private boolean showBootstrapAction() {
            return !running() && dirty;
        }
    }

    private record TravelBindOperation(
        String sourceConnectionAddress,
        String sourcePortalId,
        String destinationConnectionAddress,
        String destinationTargetId,
        String travelProfileId
    ) {
    }
}
