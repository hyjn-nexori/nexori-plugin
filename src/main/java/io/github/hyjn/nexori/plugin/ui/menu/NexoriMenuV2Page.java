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
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
            NexoriMenuV2View.TARGETS,
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
        panel.addChild(spacerY(8));
        panel.addChild(label(viewSubtitle(state.selectedView()), BODY, CONTENT_W - 32));
        panel.addChild(spacerY(12));
        panel.addChild(buildContentScroll(ref, store, playerRef, player, plugin, state, peers, setup));
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
        @Nonnull HomeSetupState setup
    ) {
        int viewportHeight = CONTENT_H - 104;
        String scrollId = "nexori-v2-scroll-" + state.selectedView().name().toLowerCase();
        return switch (state.selectedView()) {
            case HOME -> buildHomeScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case ABOUT -> buildAboutScroll(viewportHeight, scrollId);
            case PORTALS, TARGETS, RULES, QUEUES, OPERATIONS -> buildPlaceholderScroll(state.selectedView(), viewportHeight, scrollId);
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
            case PORTALS -> "Visual portal routing and contextual actions will live here.";
            case TARGETS -> "Targets and world destinations will be managed here.";
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
            case TARGETS -> "Targets will stay grouped by server, with destination details in a reactive inspector.";
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
}
