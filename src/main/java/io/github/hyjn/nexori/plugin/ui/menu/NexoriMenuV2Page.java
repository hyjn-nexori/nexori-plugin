package io.github.hyjn.nexori.plugin.ui.menu;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
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
import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingKind;
import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateConfigDocument;
import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateBypassPlayer;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotDefinition;
import io.github.hyjn.nexori.plugin.minigame.LastPlayerAliveArenaMatchResolutionTrigger;
import io.github.hyjn.nexori.plugin.minigame.LobbyDefinition;
import io.github.hyjn.nexori.plugin.minigame.NetworkLobbyDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.policy.ServerPolicySummary;
import io.github.hyjn.nexori.plugin.policy.ServerRuleGroupDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.ui.menu.state.AccessGateWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.MinigameWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;
import io.github.hyjn.nexori.plugin.ui.menu.state.PortalWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.RulesWorkspaceTab;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public final class NexoriMenuV2Page {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());
    private static final Gson GSON = new Gson();

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
    private static final String DESTINATION_DISPLAY_NAME_INPUT_ID = "nexori-v2-destination-display-name";
    private static final String QUEUE_DISPLAY_NAME_INPUT_ID = "nexori-v2-queue-display-name";
    private static final String QUEUE_MIN_PLAYERS_INPUT_ID = "nexori-v2-queue-min-players";
    private static final String QUEUE_MAX_PLAYERS_INPUT_ID = "nexori-v2-queue-max-players";
    private static final String QUEUE_COUNTDOWN_INPUT_ID = "nexori-v2-queue-countdown";
    private static final String RULE_GROUP_NAME_INPUT_ID = "nexori-v2-rule-group-name";
    private static final String TARGET_DISPLAY_NAME_INPUT_ID = "nexori-v2-target-display-name";
    private static final String ACCESS_GATE_MAX_PLAYERS_INPUT_ID = "nexori-v2-access-gate-max-players";
    private static final String ACCESS_GATE_RESERVED_SLOTS_INPUT_ID = "nexori-v2-access-gate-reserved-slots";
    private static final String ACCESS_GATE_FULL_MESSAGE_INPUT_ID = "nexori-v2-access-gate-full-message";
    private static final String ACCESS_GATE_ADD_UUID_INPUT_ID = "nexori-v2-access-gate-add-uuid";
    private static final String DEFAULT_MINIGAME_QUEUE_TRAVEL_PROFILE_ID = "keep_inventory";
    private static final int DEFAULT_DESTINATION_MAX_SUPPORTED_PLAYERS = 9999;
    private static final String NEW_RULE_GROUP_ID = "__new__";
    private static final String LOCAL_SERVER_KEY = ServerRuleGroupDefinition.LOCAL_SERVER_KEY;

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

    private static final HyUIStyle TITLE = new HyUIStyle().setFontSize(20).setRenderBold(true).setTextColor("#f1f6ff").setWrap(true);
    private static final HyUIStyle SUBTITLE = new HyUIStyle().setFontSize(14).setRenderBold(true).setTextColor("#bcd2eb").setWrap(true);
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
        if (state.selectedView() == NexoriMenuV2View.OPERATIONS) {
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
        sidebar.addChild(spacerY(12));

        List<NexoriMenuV2View> views = List.of(
            NexoriMenuV2View.HOME,
            NexoriMenuV2View.PORTALS,
            NexoriMenuV2View.RULES,
            NexoriMenuV2View.QUEUES,
            NexoriMenuV2View.ACCESS_GATE
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

        sidebar.addChild(spacerY(Math.max(24, CONTENT_H - 382)));
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
        if (state.selectedView() == NexoriMenuV2View.QUEUES) {
            panel.addChild(spacerY(12));
            panel.addChild(minigameTabs(ref, store, playerRef, player, plugin, state));
        }
        if (state.selectedView() == NexoriMenuV2View.ACCESS_GATE) {
            panel.addChild(spacerY(12));
            panel.addChild(accessGateTabs(ref, store, playerRef, player, plugin, state));
        }
        panel.addChild(spacerY(12));
        int viewportHeight = CONTENT_H - ((state.selectedView() == NexoriMenuV2View.PORTALS
            || state.selectedView() == NexoriMenuV2View.QUEUES
            || state.selectedView() == NexoriMenuV2View.ACCESS_GATE) ? 158 : 104);
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
            + (state.selectedView() == NexoriMenuV2View.PORTALS ? "-" + state.selectedPortalTab().name().toLowerCase() : "")
            + (state.selectedView() == NexoriMenuV2View.QUEUES ? "-" + state.selectedMinigameTab().name().toLowerCase() : "")
            + (state.selectedView() == NexoriMenuV2View.RULES ? "-" + state.selectedRulesTab().name().toLowerCase() : "")
            + (state.selectedView() == NexoriMenuV2View.ACCESS_GATE ? "-" + state.selectedAccessGateTab().name().toLowerCase() : "");
        return switch (state.selectedView()) {
            case HOME -> buildHomeScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case ABOUT -> buildAboutScroll(viewportHeight, scrollId);
            case PORTALS, TARGETS -> buildPortalWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case QUEUES -> buildMinigameWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case RULES -> buildRulesWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case ACCESS_GATE -> buildAccessGateScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
            case OPERATIONS -> buildPlaceholderScroll(state.selectedView(), viewportHeight, scrollId);
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
        int tabWidth = 180;
        int gap = 8;
        PortalWorkspaceTab[] tabs = PortalWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        boolean hasLobby = currentManagedLobby(plugin) != null;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            PortalWorkspaceTab tab = tabs[index];
            boolean locked = tab == PortalWorkspaceTab.SETTINGS && !hasLobby;
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(state.selectedPortalTab() == tab || locked)
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
    private static GroupBuilder minigameTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        int tabWidth = 180;
        int gap = 8;
        MinigameWorkspaceTab[] tabs = MinigameWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            MinigameWorkspaceTab tab = tabs[index];
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(state.selectedMinigameTab() == tab)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedMinigameTab(tab).withStatusText("")
                ));
            row.addChild(button);
            if (index + 1 < tabs.length) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }

    @Nonnull
    private static GroupBuilder accessGateTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        int tabWidth = 220;
        int gap = 8;
        AccessGateWorkspaceTab[] tabs = AccessGateWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            AccessGateWorkspaceTab tab = tabs[index];
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(state.selectedAccessGateTab() == tab)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedAccessGateTab(tab).withStatusText("")
                ));
            row.addChild(button);
            if (index + 1 < tabs.length) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }

    @Nonnull
    private static GroupBuilder rulesTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        int tabWidth = 180;
        int gap = 8;
        RulesWorkspaceTab[] tabs = RulesWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            RulesWorkspaceTab tab = tabs[index];
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(state.selectedRulesTab() == tab)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedRulesTab(tab).withStatusText("")
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
            case SETTINGS -> buildPortalQueueBindingsScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case TARGETS -> buildPortalTargetsScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
        };
    }

    @Nonnull
    private static ReorderableListBuilder buildMinigameWorkspaceScroll(
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
        return switch (state.selectedMinigameTab()) {
            case LOBBY -> buildLobbyWorkspaceScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
            case DESTINATIONS -> buildDestinationWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case QUEUES -> buildQueueWorkspaceScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
            case SPAWNS -> buildSpawnWorkspaceScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
        };
    }

    @Nonnull
    private static ReorderableListBuilder buildSpawnWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        InstanceSpawnSlotDefinition editing = currentEditedSpawnSlot(plugin, state);
        String selectedTemplateId = state.pendingDestinationInstanceTemplateId().isBlank()
            ? (editing == null ? "" : editing.instanceTemplateId())
            : state.pendingDestinationInstanceTemplateId();
        List<String> instanceIds = buildInstanceTemplateIds();
        List<InstanceSpawnSlotDefinition> slots = plugin.getInstanceSpawnSlotService().list();

        int setupHeight = 156;
        int selectorViewportHeight = 300;
        int selectorHeight = 72 + 12 + selectorViewportHeight + 24;
        int savedViewportHeight = 300;
        int savedHeight = 64 + 12 + savedViewportHeight + 24;
        int contentHeight = 16 + setupHeight + 12 + selectorHeight + 12 + savedHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(spawnSlotSetupCard(ref, store, playerRef, player, plugin, state, editing, selectedTemplateId, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(spawnSlotTemplateSelectionContainer(ref, store, playerRef, player, plugin, state, instanceIds, selectedTemplateId, innerWidth, selectorHeight, selectorViewportHeight, scrollId + "-spawn-instances"));
        scroll.addChild(spacerY(12));
        scroll.addChild(spawnSlotListContainer(ref, store, playerRef, player, plugin, state, slots, innerWidth, savedHeight, savedViewportHeight, scrollId + "-saved-spawn-slots"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder spawnSlotSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        InstanceSpawnSlotDefinition editing,
        @Nonnull String selectedTemplateId,
        int width,
        int height
    ) {
        boolean canSave = !selectedTemplateId.isBlank();
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Spawn Slots", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Select an instance template, stand on the exact position spawn you want, then save your current position. Nexori will distribute arriving players across these slots when the match instance is created.", MUTED, width - 32));
        card.addChild(spacerY(12));

        int summaryWidth = Math.max(360, width - 32 - 340 - 12);
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(72));
        row.addChild(selectionSummaryCard(
            "INSTANCE",
            selectedTemplateId.isBlank() ? "Select an instance template below." : selectedTemplateId,
            summaryWidth,
            !selectedTemplateId.isBlank()
        ));
        row.addChild(spacerX(12));

        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(340).setHeight(72));
        actions.addChild(spacerY(15));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(340).setHeight(42));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText(editing == null ? "SAVE CURRENT POSITION" : "UPDATE SLOT")
                .withDisabled(!canSave)
                .withAnchor(new HyUIAnchor().setWidth(220).setHeight(42))
                .onClick((ignored, ctx) -> {
                    if (selectedTemplateId.isBlank()) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS).withStatusText("Select an instance template first."));
                        return;
                    }
                    try {
                        Transform transform = captureCurrentTransform(store, ref);
                        String slotId = editing == null
                            ? deriveId(selectedTemplateId + "-" + UUID.randomUUID(), "spawn_slot")
                            : editing.slotId();
                        InstanceSpawnSlotDefinition saved = plugin.getInstanceSpawnSlotService().upsert(new InstanceSpawnSlotDefinition(
                            slotId,
                            selectedTemplateId,
                            transform.getPosition().x,
                            transform.getPosition().y,
                            transform.getPosition().z,
                            transform.getRotation().x,
                            transform.getRotation().y,
                            transform.getRotation().z,
                            editing == null ? System.currentTimeMillis() : editing.createdAtEpochMs()
                        ));
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                                .withEditingDestinationId("")
                                .withDestinationDraft(
                                    state.pendingDestinationDisplayName(),
                                    state.pendingDestinationConnectionAddress(),
                                    state.pendingDestinationTargetId(),
                                    saved.instanceTemplateId(),
                                    state.pendingDestinationTriggerId(),
                                    state.pendingDestinationMaxPlayers()
                                )
                                .withStatusText((editing == null ? "Saved" : "Updated") + " spawn slot for " + saved.instanceTemplateId() + ".")
                        );
                    } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                                .withStatusText("Could not save spawn slot: " + exception.getMessage())
                        );
                    }
                })
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("CANCEL")
                .withAnchor(new HyUIAnchor().setWidth(108).setHeight(42))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                        .withEditingDestinationId("")
                        .withStatusText("Spawn slot edit cleared.")
                ))
        );
        actions.addChild(actionRow);
        row.addChild(actions);
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder spawnSlotTemplateSelectionContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<String> instanceIds,
        @Nonnull String selectedTemplateId,
        int width,
        int height,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyContentHeight = instanceIds.isEmpty()
            ? viewportHeight
            : 8 + instanceIds.size() * HOME_SERVER_CARD_H + Math.max(0, instanceIds.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(selectionSummaryCard(
            "INSTANCE",
            selectedTemplateId.isBlank() ? "Select an instance template below." : selectedTemplateId,
            width - 32,
            !selectedTemplateId.isBlank()
        ));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, viewportHeight, Math.max(viewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (instanceIds.isEmpty()) {
            body.addChild(label("No instance templates found.", MUTED, width - 48));
        } else {
            for (int index = 0; index < instanceIds.size(); index++) {
                String instanceId = instanceIds.get(index);
                body.addChild(
                    selectorRowCard(
                        instanceId,
                        "",
                        selectedTemplateId.equalsIgnoreCase(instanceId),
                        width - 48,
                        HOME_SERVER_CARD_H,
                        () -> open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                                .withDestinationDraft(
                                    state.pendingDestinationDisplayName(),
                                    state.pendingDestinationConnectionAddress(),
                                    state.pendingDestinationTargetId(),
                                    instanceId,
                                    state.pendingDestinationTriggerId(),
                                    state.pendingDestinationMaxPlayers()
                                )
                                .withStatusText("Selected instance template " + instanceId + ".")
                        )
                    )
                );
                if (index + 1 < instanceIds.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder spawnSlotListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<InstanceSpawnSlotDefinition> slots,
        int width,
        int height,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyContentHeight = slots.isEmpty()
            ? viewportHeight
            : 8 + slots.size() * HOME_SERVER_CARD_H + Math.max(0, slots.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Spawn Slots", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, viewportHeight, Math.max(viewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (slots.isEmpty()) {
            body.addChild(label("No spawn slots saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < slots.size(); index++) {
                body.addChild(spawnSlotListRow(ref, store, playerRef, player, plugin, state, slots.get(index), width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < slots.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder spawnSlotListRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull InstanceSpawnSlotDefinition slot,
        int width,
        int height
    ) {
        List<InstanceSpawnSlotDefinition> templateSlots = plugin.getInstanceSpawnSlotService().listByInstanceTemplateId(slot.instanceTemplateId());
        int ordinal = 1;
        for (int index = 0; index < templateSlots.size(); index++) {
            if (templateSlots.get(index).slotId().equalsIgnoreCase(slot.slotId())) {
                ordinal = index + 1;
                break;
            }
        }

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 250).setHeight(rowHeight));
        identity.addChild(label(slot.instanceTemplateId(), SUBTITLE, width - 250));
        identity.addChild(spacerY(2));
        identity.addChild(label(buildSpawnSlotDetail(slot, ordinal), MUTED, width - 250));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                        .withEditingDestinationId(slot.slotId())
                        .withDestinationDraft(
                            state.pendingDestinationDisplayName(),
                            state.pendingDestinationConnectionAddress(),
                            state.pendingDestinationTargetId(),
                            slot.instanceTemplateId(),
                            state.pendingDestinationTriggerId(),
                            state.pendingDestinationMaxPlayers()
                        )
                        .withStatusText("Move to the new position and save again for " + slot.instanceTemplateId() + ".")
                ))
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getInstanceSpawnSlotService().remove(slot.slotId());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                                .withEditingDestinationId(state.editingDestinationId().equalsIgnoreCase(slot.slotId()) ? "" : state.editingDestinationId())
                                .withStatusText(removed ? "Removed spawn slot from " + slot.instanceTemplateId() + "." : "Spawn slot was already removed.")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedMinigameTab(MinigameWorkspaceTab.SPAWNS)
                                .withStatusText("Could not remove spawn slot: " + exception.getMessage())
                        );
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static String buildSpawnSlotDetail(@Nonnull InstanceSpawnSlotDefinition slot, int ordinal) {
        return "Slot " + ordinal
            + "  X " + formatCoordinate(slot.x())
            + " Y " + formatCoordinate(slot.y())
            + " Z " + formatCoordinate(slot.z())
            + "  Pitch " + formatCoordinate(slot.pitch())
            + " Yaw " + formatCoordinate(slot.yaw());
    }

    @Nonnull
    private static String formatCoordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    @Nonnull
    private static ReorderableListBuilder buildRulesWorkspaceScroll(
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
        return buildRulesGroupsScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
    }

    @Nonnull
    private static ReorderableListBuilder buildRulesGroupsScroll(
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
        List<ServerRuleGroupDefinition> ruleGroups = plugin.getServerRuleGroupService().list();
        List<RuleServerEntry> serverEntries = buildRuleServerEntries(plugin, peers);
        ServerRuleGroupDefinition selectedGroup = selectedRuleGroupForDisplay(plugin, state, ruleGroups);
        int setupHeight = 248;
        int tablesHeight = Math.max(360, viewportHeight - setupHeight - 60);
        int tableWidth = (innerWidth - 12) / 2;
        int contentHeight = 16 + setupHeight + 12 + tablesHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(ruleGroupSetupCard(ref, store, playerRef, player, plugin, state, selectedGroup, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(tablesHeight));
        row.addChild(ruleGroupsTableContainer(ref, store, playerRef, player, plugin, state, ruleGroups, selectedGroup, tableWidth, tablesHeight, scrollId + "-groups"));
        row.addChild(spacerX(12));
        row.addChild(ruleAssignmentsTableContainer(ref, store, playerRef, player, plugin, state, ruleGroups, serverEntries, selectedGroup, innerWidth - tableWidth - 12, tablesHeight, scrollId + "-servers"));
        scroll.addChild(row);
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder buildRulesSyncScroll(
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
        List<ServerRuleGroupDefinition> ruleGroups = plugin.getServerRuleGroupService().list();
        List<RuleServerEntry> serverEntries = buildRuleServerEntries(plugin, peers);
        ServerRuleGroupDefinition selectedGroup = selectedRuleGroupForDisplay(plugin, state, ruleGroups);
        ServerRuleGroupDefinition syncableGroup = isPersistedRuleGroup(selectedGroup) ? selectedGroup : null;
        int summaryHeight = 188;
        int statusHeight = Math.max(340, viewportHeight - summaryHeight - 44);
        int contentHeight = 16 + summaryHeight + 12 + statusHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(ruleSyncSummaryCard(ref, store, playerRef, player, plugin, state, syncableGroup, serverEntries, innerWidth, summaryHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(ruleStatusListContainer(ref, store, playerRef, player, plugin, state, syncableGroup, serverEntries, innerWidth, statusHeight, scrollId + "-status"));
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder buildAccessGateScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        return switch (state.selectedAccessGateTab()) {
            case ACCESS_GATE -> buildAccessGateMainScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
            case MANUAL_CONNECTIONS -> buildAccessGateManualConnectionsScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
        };
    }

    @Nonnull
    private static ReorderableListBuilder buildAccessGateMainScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        NexoriAccessGateConfigDocument config = plugin.getAccessGateService().getConfig();
        List<AccessGateConnectedPlayer> connectedPlayers = buildAccessGateConnectedPlayers(plugin);
        Set<String> bypassUuidSet = new LinkedHashSet<>();
        for (NexoriAccessGateBypassPlayer bypassPlayer : config.bypassPlayerUuids()) {
            if (bypassPlayer == null || bypassPlayer.uuid() == null || bypassPlayer.uuid().isBlank()) {
                continue;
            }
            bypassUuidSet.add(bypassPlayer.uuid().trim().toLowerCase(Locale.ROOT));
        }
        int setupHeight = 500;
        int tablesHeight = Math.max(360, viewportHeight - setupHeight - 60);
        int leftTableWidth = (innerWidth - 12) / 2;
        int contentHeight = 16 + setupHeight + 12 + tablesHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(accessGateSetupCard(ref, store, playerRef, player, plugin, state, config, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(tablesHeight));
        row.addChild(accessGateConnectedPlayersTable(ref, store, playerRef, player, plugin, state, connectedPlayers, bypassUuidSet, leftTableWidth, tablesHeight, scrollId + "-connected"));
        row.addChild(spacerX(12));
        row.addChild(accessGateBypassPlayersTable(ref, store, playerRef, player, plugin, state, config.bypassPlayerUuids(), innerWidth - leftTableWidth - 12, tablesHeight, scrollId + "-bypass"));
        scroll.addChild(row);
        return scroll;
    }

    @Nonnull
    private static GroupBuilder accessGateSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        int width,
        int height
    ) {
        int innerWidth = width - 32;
        int half = (innerWidth - 12) / 2;
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Access Gate", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Max Players is the total hard cap. Reserved Priority Slots are carved out for bypass entries. Example: maxPlayers=100 and reservedPrioritySlots=10 means up to 90 non-bypass joins, keeping 10 slots available for bypass traffic. The bypass list can contain many players, but only up to the reserved slots can use bypass concurrently.", MUTED, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder togglesRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(96));
        togglesRow.addChild(accessGateToggleField(ref, store, playerRef, player, plugin, state, "Gate Enabled", config.enabled(), half, 96, true));
        togglesRow.addChild(spacerX(12));
        togglesRow.addChild(accessGateToggleField(ref, store, playerRef, player, plugin, state, "Bypass Trusted Referrals", config.bypassReferralConnections(), half, 96, false));
        card.addChild(togglesRow);
        card.addChild(spacerY(12));

        GroupBuilder numberRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(96));
        numberRow.addChild(accessGateMaxPlayersCard(ref, store, playerRef, player, plugin, state, config, half, 96));
        numberRow.addChild(spacerX(12));
        numberRow.addChild(accessGateReservedSlotsCard(ref, store, playerRef, player, plugin, state, config, half, 96));
        card.addChild(numberRow);
        card.addChild(spacerY(12));

        GroupBuilder textRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(96));
        textRow.addChild(accessGateFullMessageCard(ref, store, playerRef, player, plugin, state, config, half, 96));
        textRow.addChild(spacerX(12));
        GroupBuilder addUuidCard = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(half).setHeight(96))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        addUuidCard.addChild(label("Add Bypass UUID", SUBTITLE, half - 28));
        addUuidCard.addChild(spacerY(10));
        GroupBuilder addUuidRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(half - 28).setHeight(HOME_INPUT_FIELD_H));
        addUuidRow.addChild(
            TextFieldBuilder.textInput()
                .withId(ACCESS_GATE_ADD_UUID_INPUT_ID)
                .withValue("")
                .withPlaceholderText("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx")
                .withMaxLength(64)
                .withAnchor(new HyUIAnchor().setWidth(half - 168).setHeight(HOME_INPUT_FIELD_H))
                .withBackground("#101926")
        );
        addUuidRow.addChild(spacerX(10));
        addUuidRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("ADD")
                .withAnchor(new HyUIAnchor().setWidth(130).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String rawUuid = ctx.getValue(ACCESS_GATE_ADD_UUID_INPUT_ID, String.class).orElse("").trim();
                    try {
                        UUID parsed = UUID.fromString(rawUuid);
                        plugin.getAccessGateService().addBypassPlayerUuid(parsed, "");
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Added bypass UUID " + parsed + "."));
                    } catch (IllegalArgumentException invalid) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Enter a valid UUID to add a bypass player."));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not add bypass UUID: " + exception.getMessage()));
                    }
                })
        );
        addUuidCard.addChild(addUuidRow);
        textRow.addChild(addUuidCard);
        card.addChild(textRow);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder buildAccessGateManualConnectionsScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        NexoriAccessGateConfigDocument config = plugin.getAccessGateService().getConfig();
        List<ConfiguredPeer> trustedPeers = new ArrayList<>(trustedNetworkPeers(plugin));
        Map<String, String> configuredDisplayNamesByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : plugin.getConfiguredPeerService().list()) {
            if (peer.connectionAddress().isBlank()) {
                continue;
            }
            configuredDisplayNamesByAddress.putIfAbsent(
                peer.connectionAddress().toLowerCase(Locale.ROOT),
                peer.displayName().isBlank() ? peer.connectionAddress() : peer.displayName()
            );
        }
        trustedPeers.sort(Comparator.comparing(peer -> configuredDisplayNamesByAddress
            .getOrDefault(peer.connectionAddress().toLowerCase(Locale.ROOT), peer.connectionAddress())
            .toLowerCase(Locale.ROOT)));

        int setupHeight = 220;
        int tableHeight = Math.max(360, viewportHeight - setupHeight - 60);
        int contentHeight = 16 + setupHeight + 12 + tableHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(accessGateManualConnectionsSetupCard(ref, store, playerRef, player, plugin, state, config, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(accessGateTrustedServersTable(ref, store, playerRef, player, plugin, state, config, trustedPeers, configuredDisplayNamesByAddress, innerWidth, tableHeight, scrollId + "-trusted-servers"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder accessGateManualConnectionsSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        int width,
        int height
    ) {
        int innerWidth = width - 32;
        int half = (innerWidth - 12) / 2;

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Manual Connections", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Protect this server from direct manual joins. Enable the guard, then select one trusted target server to redirect direct joins.", MUTED, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(96));
        row.addChild(accessGateToggleField(ref, store, playerRef, player, plugin, state, "Redirect Direct Joins", config.redirectManualConnections(), half, 96, false, true));
        row.addChild(spacerX(12));

        GroupBuilder targetCard = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(half).setHeight(96))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        targetCard.addChild(label("Current Redirect Target", SUBTITLE, half - 28));
        targetCard.addChild(spacerY(8));
        targetCard.addChild(label(config.manualRedirectAddress().isBlank() ? "Not set" : config.manualRedirectAddress(), MUTED, half - 28));
        targetCard.addChild(spacerY(10));
        targetCard.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("CLEAR TARGET")
                .withAnchor(new HyUIAnchor().setWidth(Math.min(170, half - 28)).setHeight(36))
                .withDisabled(config.manualRedirectAddress().isBlank())
                .onClick((ignored, ctx) -> {
                    try {
                        saveAccessGateManualRedirectTarget(plugin, config, "");
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Cleared manual redirect target."));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not clear manual redirect target: " + exception.getMessage()));
                    }
                })
        );
        row.addChild(targetCard);

        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder accessGateTrustedServersTable(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        @Nonnull List<ConfiguredPeer> trustedPeers,
        @Nonnull Map<String, String> configuredDisplayNamesByAddress,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int tableWidth = Math.max(560, width / 2);
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = trustedPeers.isEmpty()
            ? bodyViewportHeight
            : 8 + trustedPeers.size() * HOME_SERVER_CARD_H + Math.max(0, trustedPeers.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));

        GroupBuilder headerWrap = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(64))
            .withPadding(HyUIPadding.symmetric(0, 0));
        headerWrap.addChild(spacerX(Math.max(0, ((width - 32) - tableWidth) / 2)));

        GroupBuilder header = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(tableWidth).setHeight(64))
            .withPadding(HyUIPadding.symmetric(16, 0))
            .withBackground(PANEL_ALT_BG);
        int refreshWidth = 120;
        int leftPadWidth = refreshWidth;
        int titleWidth = Math.max(120, tableWidth - 32 - leftPadWidth - 12 - refreshWidth);
        header.addChild(spacerX(leftPadWidth));
        header.addChild(centeredLabel("Trusted Servers", TITLE, titleWidth, titleWidth, 8));
        header.addChild(spacerX(12));
        header.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("REFRESH")
                .withAnchor(new HyUIAnchor().setWidth(120).setHeight(38))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Refreshed trusted server list.")
                ))
        );
        headerWrap.addChild(header);
        container.addChild(headerWrap);
        container.addChild(spacerY(12));

        GroupBuilder bodyWrap = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(bodyViewportHeight));
        bodyWrap.addChild(spacerX(Math.max(0, ((width - 32) - tableWidth) / 2)));

        ReorderableListBuilder body = scrollList(tableWidth, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (trustedPeers.isEmpty()) {
            body.addChild(label("No trusted servers in the active trust bundle yet.", MUTED, tableWidth - 16));
        } else {
            for (int index = 0; index < trustedPeers.size(); index++) {
                ConfiguredPeer peer = trustedPeers.get(index);
                String address = peer.connectionAddress();
                String displayName = configuredDisplayNamesByAddress.getOrDefault(
                    address.toLowerCase(Locale.ROOT),
                    peer.displayName().isBlank() ? address : peer.displayName()
                );
                boolean selected = !config.manualRedirectAddress().isBlank()
                    && config.manualRedirectAddress().equalsIgnoreCase(address);
                body.addChild(accessGateTrustedServerRow(ref, store, playerRef, player, plugin, state, config, displayName, address, selected, tableWidth - 16, HOME_SERVER_CARD_H));
                if (index + 1 < trustedPeers.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        bodyWrap.addChild(body);
        container.addChild(bodyWrap);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder accessGateTrustedServerRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        @Nonnull String displayName,
        @Nonnull String connectionAddress,
        boolean selected,
        int width,
        int height
    ) {
        GroupBuilder row = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(12))
            .withBackground(SERVER_CARD_BG);

        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 180).setHeight(height - 24));
        identity.addChild(label(displayName, SUBTITLE, width - 196));
        identity.addChild(spacerY(6));
        identity.addChild(label(connectionAddress, MUTED, width - 196));
        row.addChild(identity);
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(selected ? "SELECTED" : "SET TARGET")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(height - 24))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> {
                    try {
                        saveAccessGateManualRedirectTarget(plugin, config, connectionAddress);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Manual redirects now target " + displayName + "."));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not set manual redirect target: " + exception.getMessage()));
                    }
                })
        );
        return row;
    }

    private static void saveAccessGateManualRedirectTarget(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriAccessGateConfigDocument config,
        @Nonnull String redirectAddress
    ) throws IOException {
        plugin.getAccessGateService().saveConfig(new NexoriAccessGateConfigDocument(
            config.schemaVersion(),
            config.enabled(),
            config.maxPlayers(),
            config.reservedPrioritySlots(),
            config.fullMessage(),
            config.bypassReferralConnections(),
            config.redirectManualConnections(),
            redirectAddress,
            config.bypassPlayerUuids()
        ));
    }

    @Nonnull
    private static GroupBuilder accessGateToggleField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull String labelText,
        boolean enabled,
        int width,
        int height,
        boolean gateEnabledToggle
    ) {
        return accessGateToggleField(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state,
            labelText,
            enabled,
            width,
            height,
            gateEnabledToggle,
            false
        );
    }

    @Nonnull
    private static GroupBuilder accessGateToggleField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull String labelText,
        boolean enabled,
        int width,
        int height,
        boolean gateEnabledToggle,
        boolean manualRedirectToggle
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label(labelText, SUBTITLE, width - 28));
        card.addChild(spacerY(10));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(enabled ? "ON" : "OFF")
                .withBackground(enabled ? GOOD_BG : PANEL_BG)
                .withAnchor(new HyUIAnchor().setWidth(96).setHeight(42))
                .withDisabled(true)
        );
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(enabled ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 134).setHeight(42))
                .onClick((ignored, ctx) -> {
                    try {
                        if (gateEnabledToggle) {
                            plugin.getAccessGateService().setEnabled(!enabled);
                        } else if (manualRedirectToggle) {
                            plugin.getAccessGateService().setRedirectManualConnections(!enabled);
                        } else {
                            plugin.getAccessGateService().setBypassReferralConnections(!enabled);
                        }
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText(labelText + " is now " + (!enabled ? "enabled" : "disabled") + "."));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not update Access Gate toggle: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder accessGateConnectedPlayersTable(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<AccessGateConnectedPlayer> connectedPlayers,
        @Nonnull Set<String> bypassUuidSet,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = connectedPlayers.isEmpty()
            ? bodyViewportHeight
            : 8 + connectedPlayers.size() * HOME_SERVER_CARD_H + Math.max(0, connectedPlayers.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        GroupBuilder header = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(64))
            .withPadding(HyUIPadding.symmetric(16, 0))
            .withBackground(PANEL_ALT_BG);
        int refreshWidth = 120;
        int leftPadWidth = refreshWidth;
        int titleWidth = Math.max(120, width - 32 - 32 - leftPadWidth - 12 - refreshWidth);
        header.addChild(spacerX(leftPadWidth));
        header.addChild(centeredLabel("Connected Players", TITLE, titleWidth, titleWidth, 8));
        header.addChild(spacerX(12));
        header.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("REFRESH")
                .withAnchor(new HyUIAnchor().setWidth(120).setHeight(38))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Refreshed connected players.")
                ))
        );
        container.addChild(header);
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (connectedPlayers.isEmpty()) {
            body.addChild(label("No connected players right now.", MUTED, width - 48));
        } else {
            for (int index = 0; index < connectedPlayers.size(); index++) {
                AccessGateConnectedPlayer entry = connectedPlayers.get(index);
                boolean alreadyBypass = bypassUuidSet.contains(entry.playerUuid().toString().toLowerCase(Locale.ROOT));
                body.addChild(accessGateConnectedPlayerRow(ref, store, playerRef, player, plugin, state, entry, alreadyBypass, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < connectedPlayers.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder accessGateConnectedPlayerRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull AccessGateConnectedPlayer entry,
        boolean alreadyBypass,
        int width,
        int height
    ) {
        GroupBuilder row = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(12))
            .withBackground(SERVER_CARD_BG);

        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 180).setHeight(height - 24));
        identity.addChild(label(entry.username(), SUBTITLE, width - 196));
        identity.addChild(spacerY(6));
        identity.addChild(label(entry.playerUuid().toString(), MUTED, width - 196));
        row.addChild(identity);
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(alreadyBypass ? "ADDED" : "ADD TO BYPASS")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(height - 24))
                .withDisabled(alreadyBypass)
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getAccessGateService().addBypassPlayerUuid(entry.playerUuid(), entry.username());
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Added " + entry.username() + " to bypass players."));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not add bypass player: " + exception.getMessage()));
                    }
                })
        );
        return row;
    }

    @Nonnull
    private static GroupBuilder accessGateBypassPlayersTable(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<NexoriAccessGateBypassPlayer> bypassPlayers,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = bypassPlayers.isEmpty()
            ? bodyViewportHeight
            : 8 + bypassPlayers.size() * HOME_SERVER_CARD_H + Math.max(0, bypassPlayers.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Bypass Players", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (bypassPlayers.isEmpty()) {
            body.addChild(label("No bypass players configured yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < bypassPlayers.size(); index++) {
                NexoriAccessGateBypassPlayer bypassPlayer = bypassPlayers.get(index);
                body.addChild(accessGateBypassRow(ref, store, playerRef, player, plugin, state, bypassPlayer, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < bypassPlayers.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder accessGateBypassRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateBypassPlayer bypassPlayer,
        int width,
        int height
    ) {
        String username = bypassPlayer.username() == null || bypassPlayer.username().isBlank() ? "<unknown>" : bypassPlayer.username();
        String uuid = bypassPlayer.uuid() == null ? "" : bypassPlayer.uuid();
        GroupBuilder row = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(12))
            .withBackground(SERVER_CARD_BG);
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(height - 24));
        identity.addChild(label(username, SUBTITLE, width - 186));
        identity.addChild(spacerY(6));
        identity.addChild(label(uuid, MUTED, width - 186));
        row.addChild(identity);
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(height - 24))
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getAccessGateService().removeBypassPlayerToken(uuid);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Removed bypass UUID " + uuid + "."));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not remove bypass UUID: " + exception.getMessage()));
                    }
                })
        );
        return row;
    }

    @Nonnull
    private static List<AccessGateConnectedPlayer> buildAccessGateConnectedPlayers(@Nonnull NexoriPlugin plugin) {
        List<AccessGateConnectedPlayer> entries = new ArrayList<>();
        for (UUID playerUuid : plugin.getAccessGateService().connectedPlayerUuids()) {
            PlayerRef playerRef = Universe.get().getPlayer(playerUuid);
            String username = (playerRef == null || playerRef.getUsername() == null || playerRef.getUsername().isBlank())
                ? "<unknown>"
                : playerRef.getUsername();
            entries.add(new AccessGateConnectedPlayer(playerUuid, username));
        }
        entries.sort(Comparator
            .comparing((AccessGateConnectedPlayer entry) -> entry.username().toLowerCase(Locale.ROOT))
            .thenComparing(entry -> entry.playerUuid().toString()));
        return entries;
    }

    private static Integer parseInteger(@Nonnull String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Nonnull
    private static GroupBuilder accessGateMaxPlayersCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        int width,
        int height
    ) {
        return accessGateSavableInputCard(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state,
            "Max Players",
            ACCESS_GATE_MAX_PLAYERS_INPUT_ID,
            Integer.toString(config.maxPlayers()),
            "80",
            width,
            height,
            (value) -> {
                Integer maxPlayers = parseInteger(value.trim());
                if (maxPlayers == null || maxPlayers < 1) {
                    return "Max Players must be a number greater than 0.";
                }
                if (config.reservedPrioritySlots() > maxPlayers) {
                    return "Max Players cannot be lower than Reserved Priority Slots.";
                }
                plugin.getAccessGateService().saveConfig(new NexoriAccessGateConfigDocument(
                    config.schemaVersion(),
                    config.enabled(),
                    maxPlayers,
                    config.reservedPrioritySlots(),
                    config.fullMessage(),
                    config.bypassReferralConnections(),
                    config.redirectManualConnections(),
                    config.manualRedirectAddress(),
                    config.bypassPlayerUuids()
                ));
                return "Saved Max Players.";
            }
        );
    }

    @Nonnull
    private static GroupBuilder accessGateReservedSlotsCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        int width,
        int height
    ) {
        return accessGateSavableInputCard(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state,
            "Reserved Priority Slots",
            ACCESS_GATE_RESERVED_SLOTS_INPUT_ID,
            Integer.toString(config.reservedPrioritySlots()),
            "0",
            width,
            height,
            (value) -> {
                Integer reservedSlots = parseInteger(value.trim());
                if (reservedSlots == null || reservedSlots < 0) {
                    return "Reserved Priority Slots must be a number of 0 or more.";
                }
                if (reservedSlots > config.maxPlayers()) {
                    return "Reserved Priority Slots cannot be greater than Max Players.";
                }
                plugin.getAccessGateService().saveConfig(new NexoriAccessGateConfigDocument(
                    config.schemaVersion(),
                    config.enabled(),
                    config.maxPlayers(),
                    reservedSlots,
                    config.fullMessage(),
                    config.bypassReferralConnections(),
                    config.redirectManualConnections(),
                    config.manualRedirectAddress(),
                    config.bypassPlayerUuids()
                ));
                return "Saved Reserved Priority Slots.";
            }
        );
    }

    @Nonnull
    private static GroupBuilder accessGateFullMessageCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull NexoriAccessGateConfigDocument config,
        int width,
        int height
    ) {
        return accessGateSavableInputCard(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state,
            "Server Full Message",
            ACCESS_GATE_FULL_MESSAGE_INPUT_ID,
            config.fullMessage(),
            "Server is full. Please try again later.",
            width,
            height,
            (value) -> {
                plugin.getAccessGateService().saveConfig(new NexoriAccessGateConfigDocument(
                    config.schemaVersion(),
                    config.enabled(),
                    config.maxPlayers(),
                    config.reservedPrioritySlots(),
                    value.trim(),
                    config.bypassReferralConnections(),
                    config.redirectManualConnections(),
                    config.manualRedirectAddress(),
                    config.bypassPlayerUuids()
                ));
                return "Saved Server Full Message.";
            }
        );
    }

    @Nonnull
    private static GroupBuilder accessGateSavableInputCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull String labelText,
        @Nonnull String fieldId,
        @Nonnull String currentValue,
        @Nonnull String placeholder,
        int width,
        int height,
        @Nonnull AccessGateInputSaveHandler handler
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label(labelText, SUBTITLE, width - 28));
        card.addChild(spacerY(10));
        int saveWidth = Math.min(120, width - 28);
        int inputWidth = Math.max(160, (width - 28) - saveWidth - 10);
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(HOME_INPUT_FIELD_H));
        row.addChild(
            TextFieldBuilder.textInput()
                .withId(fieldId)
                .withValue(currentValue)
                .withPlaceholderText(placeholder)
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(inputWidth).setHeight(HOME_INPUT_FIELD_H))
                .withBackground("#101926")
        );
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("SAVE")
                .withAnchor(new HyUIAnchor().setWidth(saveWidth).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String value = ctx.getValue(fieldId, String.class).orElse(currentValue);
                    try {
                        String status = handler.save(value);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText(status));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.ACCESS_GATE).withStatusText("Could not save Access Gate config: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @FunctionalInterface
    private interface AccessGateInputSaveHandler {
        @Nonnull String save(@Nonnull String value) throws IOException;
    }

    private record AccessGateConnectedPlayer(
        @Nonnull UUID playerUuid,
        @Nonnull String username
    ) {
    }

    @Nonnull
    private static GroupBuilder ruleGroupSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height
    ) {
        boolean persisted = isPersistedRuleGroup(selectedGroup);
        String displayValue = state.pendingRuleGroupName().isBlank()
            ? selectedGroup.displayName()
            : state.pendingRuleGroupName();
        int innerWidth = width - 32;
        int groupInputWidth = (innerWidth - 24) / 3;

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Rule Group Setup", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Here you can create server configuration templates and choose which servers to apply them to.", MUTED, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("NEW GROUP")
                .withAnchor(new HyUIAnchor().setWidth(130).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(NEW_RULE_GROUP_ID).withPendingRuleGroupName("").withStatusText("Started a new rule group draft.")
                ))
        );
        actionRow.addChild(spacerX(10));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText("SAVE GROUP")
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String displayName = ctx.getValue(RULE_GROUP_NAME_INPUT_ID, String.class).orElse(displayValue).trim();
                    try {
                        ServerRuleGroupDefinition saved = persisted
                            ? plugin.getServerRuleGroupService().rename(selectedGroup.groupId(), displayName)
                            : plugin.getServerRuleGroupService().create(displayName, selectedGroup.recoveryEnabled(), selectedGroup.maxBackupsPerPlayer());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(saved.groupId()).withPendingRuleGroupName("").withStatusText("Saved rule group " + saved.displayName() + ".")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withPendingRuleGroupName(displayName).withStatusText("Could not save rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        actionRow.addChild(spacerX(10));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("APPLY TO ASSIGNED")
                .withAnchor(new HyUIAnchor().setWidth(190).setHeight(HOME_INPUT_FIELD_H))
                .withDisabled(!persisted)
                .onClick((ignored, ctx) -> applyRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedGroup, buildRuleServerEntries(plugin, plugin.getConfiguredPeerService().list())))
        );
        card.addChild(actionRow);
        card.addChild(spacerY(12));

        GroupBuilder inputsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(96));
        inputsRow.addChild(ruleGroupNameField(displayValue, groupInputWidth, 96));
        inputsRow.addChild(spacerX(12));
        inputsRow.addChild(ruleRecoveryField(ref, store, playerRef, player, plugin, state, selectedGroup, persisted, groupInputWidth, 96));
        inputsRow.addChild(spacerX(12));
        inputsRow.addChild(ruleBackupsField(ref, store, playerRef, player, plugin, state, selectedGroup, persisted, groupInputWidth, 96));
        card.addChild(inputsRow);
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleGroupNameField(
        @Nonnull String displayValue,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label("Group Name", SUBTITLE, width - 28));
        card.addChild(spacerY(10));
        card.addChild(
            TextFieldBuilder.textInput()
                .withId(RULE_GROUP_NAME_INPUT_ID)
                .withValue(displayValue)
                .withPlaceholderText("New Rule Group")
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(HOME_INPUT_FIELD_H))
                .withBackground("#101926")
        );
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleGroupsTableContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = ruleGroups.isEmpty()
            ? bodyViewportHeight
            : 8 + ruleGroups.size() * HOME_SERVER_CARD_H + Math.max(0, ruleGroups.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Rule Groups", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (ruleGroups.isEmpty()) {
            body.addChild(label("No rule groups saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < ruleGroups.size(); index++) {
                ServerRuleGroupDefinition group = ruleGroups.get(index);
                body.addChild(ruleGroupListRowCard(ref, store, playerRef, player, plugin, state, group, selectedGroup, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < ruleGroups.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder ruleRecoveryField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        boolean persisted,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label("Recovery", SUBTITLE, width - 28));
        card.addChild(spacerY(10));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(selectedGroup.recoveryEnabled() ? "ON" : "OFF")
                .withBackground(selectedGroup.recoveryEnabled() ? GOOD_BG : PANEL_BG)
                .withAnchor(new HyUIAnchor().setWidth(96).setHeight(42))
                .withDisabled(true)
        );
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(selectedGroup.recoveryEnabled() ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 134).setHeight(42))
                .withDisabled(!persisted)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = plugin.getServerRuleGroupService().setRecoveryEnabled(selectedGroup.groupId(), !selectedGroup.recoveryEnabled());
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(updated.groupId()).withStatusText("Recovery is now " + (updated.recoveryEnabled() ? "enabled" : "disabled") + " for " + updated.displayName() + "."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withStatusText("Could not update recovery: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleBackupsField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        boolean persisted,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label("Max Backups", SUBTITLE, width - 28));
        card.addChild(spacerY(10));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("-")
                .withAnchor(new HyUIAnchor().setWidth(42).setHeight(42))
                .withDisabled(!persisted || selectedGroup.maxBackupsPerPlayer() <= 1)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = plugin.getServerRuleGroupService().setMaxBackupsPerPlayer(selectedGroup.groupId(), selectedGroup.maxBackupsPerPlayer() - 1);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(updated.groupId()).withStatusText("Max backups is now " + updated.maxBackupsPerPlayer() + " for " + updated.displayName() + "."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withStatusText("Could not lower max backups: " + exception.getMessage()));
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(Integer.toString(selectedGroup.maxBackupsPerPlayer()))
                .withBackground(INFO_BG)
                .withAnchor(new HyUIAnchor().setWidth(92).setHeight(42))
                .withDisabled(true)
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("+")
                .withAnchor(new HyUIAnchor().setWidth(42).setHeight(42))
                .withDisabled(!persisted)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = plugin.getServerRuleGroupService().setMaxBackupsPerPlayer(selectedGroup.groupId(), selectedGroup.maxBackupsPerPlayer() + 1);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(updated.groupId()).withStatusText("Max backups is now " + updated.maxBackupsPerPlayer() + " for " + updated.displayName() + "."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withStatusText("Could not increase max backups: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleAssignmentsTableContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull List<RuleServerEntry> serverEntries,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = serverEntries.isEmpty()
            ? bodyViewportHeight
            : 8 + serverEntries.size() * HOME_SERVER_CARD_H + Math.max(0, serverEntries.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Servers In This Group", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (serverEntries.isEmpty()) {
            body.addChild(label("No servers are available yet.", MUTED, width - 48));
        } else {
            Map<String, ServerRuleGroupDefinition> assignments = buildRuleGroupAssignments(ruleGroups);
            for (int index = 0; index < serverEntries.size(); index++) {
                body.addChild(ruleAssignmentRowCard(ref, store, playerRef, player, plugin, state, selectedGroup, serverEntries.get(index), assignments, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < serverEntries.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder ruleSyncSummaryCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        ServerRuleGroupDefinition selectedGroup,
        @Nonnull List<RuleServerEntry> serverEntries,
        int width,
        int height
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        if (selectedGroup == null) {
            card.addChild(label("Sync", TITLE, width - 32));
            card.addChild(spacerY(8));
            card.addChild(label("Select a saved rule group in GROUPS first.", MUTED, width - 32));
            return card;
        }

        GroupStatusCounts counts = computeGroupStatusCounts(plugin, selectedGroup, serverEntries);
        card.addChild(label(selectedGroup.displayName(), TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label(
            "Recovery " + (selectedGroup.recoveryEnabled() ? "ON" : "OFF")
                + " · Max Backups " + selectedGroup.maxBackupsPerPlayer()
                + " · Assigned " + counts.assigned(),
            MUTED,
            width - 32
        ));
        card.addChild(spacerY(12));

        GroupBuilder topStatus = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(32));
        topStatus.addChild(statusBadgeRow("Matching", Integer.toString(counts.matching()), 200, GOOD, GOOD_BG));
        topStatus.addChild(spacerX(12));
        topStatus.addChild(statusBadgeRow("Needs Apply", Integer.toString(counts.needsApply()), 220, BAD, BAD_BG));
        topStatus.addChild(spacerX(12));
        topStatus.addChild(statusBadgeRow("Unknown", Integer.toString(counts.unknown()), 180, INFO, INFO_BG));
        card.addChild(topStatus);
        card.addChild(spacerY(16));

        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(42));
        int totalWidth = 230 + 12 + 190;
        actions.addChild(spacerX(Math.max(0, ((width - 32) - totalWidth) / 2)));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("APPLY TO ASSIGNED")
                .withAnchor(new HyUIAnchor().setWidth(230).setHeight(42))
                .onClick((ignored, ctx) -> applyRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedGroup, serverEntries))
        );
        actions.addChild(spacerX(12));
        actions.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("REFRESH STATUS")
                .withAnchor(new HyUIAnchor().setWidth(190).setHeight(42))
                .onClick((ignored, ctx) -> refreshRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedGroup, serverEntries))
        );
        card.addChild(actions);
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleStatusListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        ServerRuleGroupDefinition selectedGroup,
        @Nonnull List<RuleServerEntry> serverEntries,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        List<RuleServerEntry> assignedServers = selectedGroup == null
            ? List.of()
            : serverEntries.stream().filter(entry -> selectedGroup.containsServer(entry.selectionKey())).toList();
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = assignedServers.isEmpty()
            ? bodyViewportHeight
            : 8 + assignedServers.size() * HOME_SERVER_CARD_H + Math.max(0, assignedServers.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Server Status", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (selectedGroup == null) {
            body.addChild(label("Select a saved rule group first.", MUTED, width - 48));
        } else if (assignedServers.isEmpty()) {
            body.addChild(label("This rule group has no assigned servers yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < assignedServers.size(); index++) {
                body.addChild(ruleStatusRow(plugin, selectedGroup, assignedServers.get(index), width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < assignedServers.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static List<RuleServerEntry> buildRuleServerEntries(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers
    ) {
        List<RuleServerEntry> entries = new ArrayList<>();
        String localConnectionAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();
        entries.add(new RuleServerEntry(
            LOCAL_SERVER_KEY,
            localConnectionAddress,
            "Current Server",
            localConnectionAddress.isBlank() ? "This server" : localConnectionAddress,
            true
        ));

        Map<String, String> displayNamesByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : peers) {
            if (!peer.connectionAddress().isBlank()) {
                displayNamesByAddress.putIfAbsent(
                    peer.connectionAddress(),
                    peer.displayName().isBlank() ? peer.connectionAddress() : peer.displayName()
                );
            }
        }

        Set<String> seenAddresses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConfiguredPeer trustedPeer : trustedNetworkPeers(plugin)) {
            if (!seenAddresses.add(trustedPeer.connectionAddress())) {
                continue;
            }
            String displayName = displayNamesByAddress.getOrDefault(
                trustedPeer.connectionAddress(),
                trustedPeer.displayName().isBlank() ? trustedPeer.connectionAddress() : trustedPeer.displayName()
            );
            entries.add(new RuleServerEntry(
                trustedPeer.connectionAddress(),
                trustedPeer.connectionAddress(),
                displayName,
                trustedPeer.connectionAddress(),
                false
            ));
        }
        return entries;
    }

    @Nonnull
    private static ServerRuleGroupDefinition selectedRuleGroupForDisplay(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups
    ) {
        if (NEW_RULE_GROUP_ID.equalsIgnoreCase(state.selectedRuleGroupId())) {
            return newRuleGroupDraft(plugin, state);
        }
        for (ServerRuleGroupDefinition group : ruleGroups) {
            if (group.groupId().equalsIgnoreCase(state.selectedRuleGroupId())) {
                return group;
            }
        }
        return ruleGroups.isEmpty() ? newRuleGroupDraft(plugin, state) : ruleGroups.getFirst();
    }

    @Nonnull
    private static ServerRuleGroupDefinition newRuleGroupDraft(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        String displayName = state.pendingRuleGroupName().isBlank()
            ? "New Rule Group"
            : state.pendingRuleGroupName();
        return new ServerRuleGroupDefinition(
            NEW_RULE_GROUP_ID,
            displayName,
            plugin.getInventoryTransferService().isRecoveryEnabled(),
            plugin.getInventoryTransferService().getMaxBackupsPerPlayer(),
            List.of()
        ).normalized();
    }

    private static boolean isPersistedRuleGroup(ServerRuleGroupDefinition group) {
        return group != null && !NEW_RULE_GROUP_ID.equalsIgnoreCase(group.groupId());
    }

    @Nonnull
    private static GroupBuilder ruleGroupListRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height
    ) {
        boolean selected = selectedGroup.groupId().equalsIgnoreCase(group.groupId());
        int deleteWidth = 104;
        int selectWidth = 110;
        int actionWidth = deleteWidth + 8 + selectWidth;
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int identityWidth = width - 28 - actionWidth - 12;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(identityWidth).setHeight(rowHeight));
        identity.addChild(label(group.displayName(), SUBTITLE, identityWidth));
        identity.addChild(spacerY(2));
        identity.addChild(label(
            "Recovery " + (group.recoveryEnabled() ? "ON" : "OFF")
                + " · Backups " + group.maxBackupsPerPlayer()
                + " · " + group.assignedServerKeys().size() + " server(s)",
            MUTED,
            identityWidth
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("DELETE")
                .withAnchor(new HyUIAnchor().setWidth(deleteWidth).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getServerRuleGroupService().remove(group.groupId());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId("")
                                .withPendingRuleGroupName("")
                                .withStatusText(removed ? "Deleted rule group " + group.displayName() + "." : "Rule group was already removed.")
                        );
                    } catch (IOException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not delete rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            (selected ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(selected ? "SELECTED" : "SELECT")
                .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(selectWidth).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state
                        .withSelectedView(NexoriMenuV2View.RULES)
                        .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                        .withSelectedRuleGroupId(group.groupId())
                        .withPendingRuleGroupName("")
                        .withStatusText("Selected rule group " + group.displayName() + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleGroupListRowCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height
    ) {
        boolean selected = selectedGroup.groupId().equalsIgnoreCase(group.groupId());
        int deleteWidth = 104;
        int selectWidth = 110;
        int actionWidth = deleteWidth + 8 + selectWidth;
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int identityWidth = width - 28 - actionWidth - 12;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(identityWidth).setHeight(rowHeight));
        identity.addChild(label(group.displayName(), SUBTITLE, identityWidth));
        identity.addChild(spacerY(2));
        identity.addChild(label(
            group.assignedServerKeys().size() + " server(s)",
            MUTED,
            identityWidth
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("DELETE")
                .withAnchor(new HyUIAnchor().setWidth(deleteWidth).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getServerRuleGroupService().remove(group.groupId());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId("")
                                .withPendingRuleGroupName("")
                                .withStatusText(removed ? "Deleted rule group " + group.displayName() + "." : "Rule group was already removed.")
                        );
                    } catch (IOException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not delete rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            (selected ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(selected ? "SELECTED" : "SELECT")
                .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(selectWidth).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state
                        .withSelectedView(NexoriMenuV2View.RULES)
                        .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                        .withSelectedRuleGroupId(group.groupId())
                        .withPendingRuleGroupName("")
                        .withStatusText("Selected rule group " + group.displayName() + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static Map<String, ServerRuleGroupDefinition> buildRuleGroupAssignments(@Nonnull List<ServerRuleGroupDefinition> ruleGroups) {
        Map<String, ServerRuleGroupDefinition> assignments = new LinkedHashMap<>();
        for (ServerRuleGroupDefinition group : ruleGroups) {
            for (String assignedServerKey : group.assignedServerKeys()) {
                assignments.put(assignedServerKey, group);
            }
        }
        return assignments;
    }

    @Nonnull
    private static GroupBuilder ruleAssignmentRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry,
        @Nonnull Map<String, ServerRuleGroupDefinition> assignments,
        int width,
        int height
    ) {
        ServerRuleGroupDefinition owningGroup = assignments.get(entry.selectionKey());
        boolean assignedHere = owningGroup != null && owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean assignedElsewhere = owningGroup != null && !owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean canChange = isPersistedRuleGroup(selectedGroup) && !assignedElsewhere;

        String detail = assignedHere
            ? "In this group · " + entry.displayAddress()
            : assignedElsewhere
                ? "Used by " + owningGroup.displayName() + " · " + entry.displayAddress()
                : "Available · " + entry.displayAddress();
        String buttonText = !isPersistedRuleGroup(selectedGroup)
            ? "SAVE FIRST"
            : assignedHere
                ? "REMOVE"
                : assignedElsewhere
                    ? "IN USE"
                    : "ADD";

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 150).setHeight(rowHeight));
        identity.addChild(label(entry.displayName(), SUBTITLE, width - 150));
        identity.addChild(spacerY(2));
        identity.addChild(label(detail, MUTED, width - 150));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            (assignedHere ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(buttonText)
                .withBackground(assignedHere ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(!canChange)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = assignedHere
                            ? plugin.getServerRuleGroupService().unassignServer(selectedGroup.groupId(), entry.selectionKey())
                            : plugin.getServerRuleGroupService().assignServer(selectedGroup.groupId(), entry.selectionKey());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId(updated.groupId())
                                .withStatusText(
                                    assignedHere
                                        ? "Removed " + entry.displayName() + " from " + updated.displayName() + "."
                                        : "Assigned " + entry.displayName() + " to " + updated.displayName() + "."
                                )
                        );
                    } catch (IOException | IllegalStateException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not update that assignment: " + exception.getMessage())
                        );
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder ruleAssignmentRowCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry,
        @Nonnull Map<String, ServerRuleGroupDefinition> assignments,
        int width,
        int height
    ) {
        ServerRuleGroupDefinition owningGroup = assignments.get(entry.selectionKey());
        boolean assignedHere = owningGroup != null && owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean assignedElsewhere = owningGroup != null && !owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean canChange = isPersistedRuleGroup(selectedGroup) && !assignedElsewhere;
        RuleMatchIndicator indicator = ruleMatchIndicator(plugin, selectedGroup, entry);

        String buttonText = !isPersistedRuleGroup(selectedGroup)
            ? "SAVE FIRST"
            : assignedHere
                ? "REMOVE"
                : assignedElsewhere
                    ? "IN USE"
                    : "ADD";

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        int statusWidth = 126;
        int actionWidth = 110;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int identityWidth = width - 28 - statusWidth - actionWidth - 24;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(identityWidth).setHeight(rowHeight));
        identity.addChild(label(entry.displayName(), SUBTITLE, identityWidth));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(indicator.label())
                .withBackground(indicator.background())
                .withAnchor(new HyUIAnchor().setWidth(statusWidth).setHeight(30))
                .withDisabled(true)
        );
        row.addChild(spacerX(12));
        row.addChild(
            (assignedHere ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(buttonText)
                .withBackground(assignedHere ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(actionWidth).setHeight(30))
                .withDisabled(!canChange)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = assignedHere
                            ? plugin.getServerRuleGroupService().unassignServer(selectedGroup.groupId(), entry.selectionKey())
                            : plugin.getServerRuleGroupService().assignServer(selectedGroup.groupId(), entry.selectionKey());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId(updated.groupId())
                                .withStatusText(
                                    assignedHere
                                        ? "Removed " + entry.displayName() + " from " + updated.displayName() + "."
                                        : "Assigned " + entry.displayName() + " to " + updated.displayName() + "."
                                )
                        );
                    } catch (IOException | IllegalStateException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not update that assignment: " + exception.getMessage())
                        );
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static RuleMatchIndicator ruleMatchIndicator(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry
    ) {
        if (entry.local()) {
            boolean matching = matchesPolicy(
                selectedGroup,
                plugin.getInventoryTransferService().isRecoveryEnabled(),
                plugin.getInventoryTransferService().getMaxBackupsPerPlayer()
            );
            return matching
                ? new RuleMatchIndicator("MATCHING", GOOD_BG)
                : new RuleMatchIndicator("NOT MATCHING", BAD_BG);
        }

        ServerPolicySummary cached = plugin.getServerPolicyCacheService().find(entry.connectionAddress()).orElse(null);
        if (cached == null) {
            return new RuleMatchIndicator("UNKNOWN", INFO_BG);
        }

        return matchesPolicy(selectedGroup, cached.recoveryEnabled(), cached.maxBackupsPerPlayer())
            ? new RuleMatchIndicator("MATCHING", GOOD_BG)
            : new RuleMatchIndicator("NOT MATCHING", BAD_BG);
    }

    @Nonnull
    private static GroupStatusCounts computeGroupStatusCounts(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<RuleServerEntry> serverEntries
    ) {
        int assigned = 0;
        int matching = 0;
        int needsApply = 0;
        int unknown = 0;
        for (String assignedServerKey : group.assignedServerKeys()) {
            assigned++;
            RuleServerEntry entry = null;
            for (RuleServerEntry serverEntry : serverEntries) {
                if (serverEntry.selectionKey().equalsIgnoreCase(assignedServerKey)) {
                    entry = serverEntry;
                    break;
                }
            }
            if (entry == null) {
                unknown++;
                continue;
            }

            if (entry.local()) {
                if (matchesPolicy(group, plugin.getInventoryTransferService().isRecoveryEnabled(), plugin.getInventoryTransferService().getMaxBackupsPerPlayer())) {
                    matching++;
                } else {
                    needsApply++;
                }
                continue;
            }

            ServerPolicySummary summary = plugin.getServerPolicyCacheService().find(entry.connectionAddress()).orElse(null);
            if (summary == null) {
                unknown++;
            } else if (matchesPolicy(group, summary.recoveryEnabled(), summary.maxBackupsPerPlayer())) {
                matching++;
            } else {
                needsApply++;
            }
        }
        return new GroupStatusCounts(assigned, matching, needsApply, unknown);
    }

    private static boolean matchesPolicy(
        @Nonnull ServerRuleGroupDefinition group,
        boolean recoveryEnabled,
        int maxBackupsPerPlayer
    ) {
        return group.recoveryEnabled() == recoveryEnabled
            && group.maxBackupsPerPlayer() == Math.max(1, maxBackupsPerPlayer);
    }

    @Nonnull
    private static GroupBuilder ruleStatusRow(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry,
        int width,
        int height
    ) {
        String statusText;
        HyUIPatchStyle statusBackground;
        if (entry.local()) {
            boolean matching = matchesPolicy(
                selectedGroup,
                plugin.getInventoryTransferService().isRecoveryEnabled(),
                plugin.getInventoryTransferService().getMaxBackupsPerPlayer()
            );
            statusText = matching ? "MATCHING" : "NEEDS APPLY";
            statusBackground = matching ? GOOD_BG : BAD_BG;
        } else {
            ServerPolicySummary cached = plugin.getServerPolicyCacheService().find(entry.connectionAddress()).orElse(null);
            if (cached == null) {
                statusText = "UNKNOWN";
                statusBackground = INFO_BG;
            } else if (matchesPolicy(selectedGroup, cached.recoveryEnabled(), cached.maxBackupsPerPlayer())) {
                statusText = "MATCHING";
                statusBackground = GOOD_BG;
            } else {
                statusText = "NEEDS APPLY";
                statusBackground = BAD_BG;
            }
        }

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(entry.displayName(), SUBTITLE, width - 170));
        identity.addChild(spacerY(2));
        identity.addChild(label(entry.displayAddress(), MUTED, width - 170));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(statusText)
                .withBackground(statusBackground)
                .withAnchor(new HyUIAnchor().setWidth(130).setHeight(30))
                .withDisabled(true)
        );
        card.addChild(row);
        return card;
    }

    private static void applyRuleGroupAssignments(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<RuleServerEntry> serverEntries
    ) {
        ServerRuleGroupDefinition currentGroup = plugin.getServerRuleGroupService().find(group.groupId()).orElse(group);
        List<RuleServerEntry> assignedServers = serverEntries.stream()
            .filter(entry -> currentGroup.containsServer(entry.selectionKey()))
            .toList();
        if (assignedServers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Assign at least one server before applying this rule group.")
            );
            return;
        }

        boolean localApplied = false;
        if (currentGroup.containsServer(LOCAL_SERVER_KEY)) {
            try {
                plugin.getInventoryTransferService().setRecoveryEnabled(currentGroup.recoveryEnabled());
                plugin.getInventoryTransferService().setMaxBackupsPerPlayer(currentGroup.maxBackupsPerPlayer());
                localApplied = true;
            } catch (IOException exception) {
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Could not apply local server rules: " + exception.getMessage())
                );
                return;
            }
        }

        Map<String, ConfiguredPeer> trustedPeersByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : trustedNetworkPeers(plugin)) {
            trustedPeersByAddress.putIfAbsent(peer.connectionAddress(), peer);
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (RuleServerEntry entry : assignedServers) {
            if (entry.local() || entry.connectionAddress().isBlank()) {
                continue;
            }
            ConfiguredPeer peer = trustedPeersByAddress.get(entry.connectionAddress());
            if (peer != null) {
                remotePeers.add(peer);
            } else {
                unresolved.add(entry.displayAddress());
            }
        }

        if (!unresolved.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("These assigned servers are not in the trusted network yet: " + String.join(", ", unresolved) + ".")
            );
            return;
        }

        if (remotePeers.isEmpty()) {
            String finalStatus = localApplied
                ? "Applied " + currentGroup.displayName() + " to the local server."
                : "There are no remote servers assigned to " + currentGroup.displayName() + ".";
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText(finalStatus)
            );
            return;
        }

        String successMessage = localApplied
            ? "Applied " + currentGroup.displayName() + " to the local server and " + remotePeers.size() + " remote server(s)."
            : "Applied " + currentGroup.displayName() + " to " + remotePeers.size() + " remote server(s).";
        try {
            applyRuleGroupAssignmentAtIndex(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state,
                currentGroup,
                remotePeers,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                0,
                successMessage,
                localApplied ? 1 : 0
            );
        } catch (IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Could not start rules apply: " + exception.getMessage())
            );
        }
    }

    private static void applyRuleGroupAssignmentAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index,
        @Nonnull String successMessage,
        int localAppliedCount
    ) throws IllegalStateException {
        if (index >= remotePeers.size()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(successMessage)
            );
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        try {
            plugin.getServerPolicySyncService().apply(
                playerRef,
                destination,
                originWorldName,
                originTransform.clone(),
                group.recoveryEnabled(),
                group.maxBackupsPerPlayer(),
                chainedRuleApplyResumeAction(
                    plugin,
                    state,
                    group,
                    remotePeers,
                    originWorldName,
                    originTransform,
                    index + 1,
                    successMessage,
                    localAppliedCount
                )
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException(partialRuleApplyFailurePrefix(localAppliedCount) + exception.getMessage(), exception);
        }
    }

    private static void refreshRuleGroupAssignments(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<RuleServerEntry> serverEntries
    ) {
        ServerRuleGroupDefinition currentGroup = plugin.getServerRuleGroupService().find(group.groupId()).orElse(group);
        List<RuleServerEntry> remoteAssignedServers = serverEntries.stream()
            .filter(entry -> currentGroup.containsServer(entry.selectionKey()))
            .filter(entry -> !entry.local() && !entry.connectionAddress().isBlank())
            .toList();
        if (remoteAssignedServers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("There are no remote servers assigned to " + currentGroup.displayName() + ".")
            );
            return;
        }

        Map<String, ConfiguredPeer> trustedPeersByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : trustedNetworkPeers(plugin)) {
            trustedPeersByAddress.putIfAbsent(peer.connectionAddress(), peer);
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (RuleServerEntry entry : remoteAssignedServers) {
            ConfiguredPeer peer = trustedPeersByAddress.get(entry.connectionAddress());
            if (peer != null) {
                remotePeers.add(peer);
            } else {
                unresolved.add(entry.displayAddress());
            }
        }

        if (!unresolved.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("These assigned servers are not in the trusted network yet: " + String.join(", ", unresolved) + ".")
            );
            return;
        }

        try {
            refreshRuleGroupAssignmentAtIndex(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state,
                currentGroup,
                remotePeers,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                0
            );
        } catch (IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Could not start rules refresh: " + exception.getMessage())
            );
        }
    }

    private static void refreshRuleGroupAssignmentAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index
    ) throws IllegalStateException {
        if (index >= remotePeers.size()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText("Refreshed status for " + remotePeers.size() + " remote server(s).")
            );
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        try {
            plugin.getServerPolicySyncService().refresh(
                playerRef,
                destination,
                originWorldName,
                originTransform.clone(),
                chainedRuleRefreshResumeAction(
                    plugin,
                    state,
                    group,
                    remotePeers,
                    originWorldName,
                    originTransform,
                    index + 1
                )
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    @Nonnull
    private static UiResumeAction chainedRuleApplyResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int nextIndex,
        @Nonnull String successMessage,
        int localAppliedCount
    ) {
        return new UiResumeAction() {
            @Override
            public boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
                if (nextIndex >= remotePeers.size()) {
                    return false;
                }
                ConfiguredPeer nextDestination = remotePeers.get(nextIndex);
                plugin.getServerPolicySyncService().apply(
                    event,
                    nextDestination,
                    originWorldName,
                    originTransform.clone(),
                    group.recoveryEnabled(),
                    group.maxBackupsPerPlayer(),
                    chainedRuleApplyResumeAction(
                        plugin,
                        state,
                        group,
                        remotePeers,
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
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(successMessage)
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
                String finalStatus = success ? successMessage : partialRuleApplyFailurePrefix(localAppliedCount) + status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(finalStatus)
                );
            }
        };
    }

    @Nonnull
    private static UiResumeAction chainedRuleRefreshResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
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
                plugin.getServerPolicySyncService().refresh(
                    event,
                    nextDestination,
                    originWorldName,
                    originTransform.clone(),
                    chainedRuleRefreshResumeAction(
                        plugin,
                        state,
                        group,
                        remotePeers,
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
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText("Refreshed status for " + remotePeers.size() + " remote server(s).")
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
                String finalStatus = success ? "Refreshed status for " + remotePeers.size() + " remote server(s)." : status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(finalStatus)
                );
            }
        };
    }

    @Nonnull
    private static String partialRuleApplyFailurePrefix(int localAppliedCount) {
        if (localAppliedCount <= 0) {
            return "";
        }
        return "Applied local rules, but ";
    }

    @Nonnull
    private static List<ConfiguredPeer> trustedNetworkPeers(@Nonnull NexoriPlugin plugin) {
        String localServerId = localServerId(plugin);
        Map<String, ConfiguredPeer> peersByAddress = new LinkedHashMap<>();
        for (BundleMember member : plugin.getBootstrapCoordinator().getTrustBundle().members()) {
            if (localServerId.equals(member.serverId())
                || member.connectionAddress() == null
                || member.connectionAddress().isBlank()) {
                continue;
            }
            try {
                ConfiguredPeer peer = ConfiguredPeer.parse(member.connectionAddress());
                peersByAddress.putIfAbsent(peer.connectionAddress(), peer);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return List.copyOf(peersByAddress.values());
    }

    @Nonnull
    private static LocalServerSummary currentLocalServerSummary(@Nonnull NexoriPlugin plugin) {
        String localConnectionAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();
        for (ConfiguredPeer peer : plugin.getConfiguredPeerService().list()) {
            if (!localConnectionAddress.isBlank() && localConnectionAddress.equalsIgnoreCase(peer.connectionAddress())) {
                return new LocalServerSummary(peer.displayName(), peer.connectionAddress());
            }
        }
        if (!localConnectionAddress.isBlank()) {
            return new LocalServerSummary("Current Server", localConnectionAddress);
        }
        return new LocalServerSummary("Current Server", "Connection address not configured");
    }

    @Nonnull
    private static String localServerId(@Nonnull NexoriPlugin plugin) {
        return plugin.getLocalIdentity().serverId().toString();
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
        int serversTablesHeight = Math.max(340, viewportHeight - setupCardHeight - 32);
        int contentHeight = 16 + setupCardHeight + 16 + serversTablesHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(homeSetupCard(ref, store, playerRef, player, plugin, state, setup, innerWidth, setupCardHeight));
        scroll.addChild(spacerY(16));
        scroll.addChild(homeServersTablesCard(ref, store, playerRef, player, plugin, state, peers, setup, innerWidth, serversTablesHeight, scrollId + "-servers"));
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
    private static GroupBuilder homeServersTablesCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> localPeers,
        @Nonnull HomeSetupState setup,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);
        int availableWidth = width - 32;
        int columnGap = 12;
        int columnWidth = (availableWidth - columnGap) / 2;
        int outerGap = 12;
        int viewportHeight = Math.max(220, height - 72 - 12 - (outerGap * 2));

        List<HomeServerEntry> bundleServers = buildBundleHomeServers(plugin, localPeers, setup);

        container.addChild(spacerY(outerGap));
        GroupBuilder headerRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(availableWidth).setHeight(72));
        headerRow.addChild(selectionSummaryCard("LOCAL PEERS", "Source for Initial Setup.", columnWidth, !localPeers.isEmpty()));
        headerRow.addChild(spacerX(columnGap));
        headerRow.addChild(selectionSummaryCard("TRUST BUNDLE", "Travel across the active network.", columnWidth, !bundleServers.isEmpty()));
        container.addChild(headerRow);
        container.addChild(spacerY(12));

        GroupBuilder columnsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(availableWidth).setHeight(viewportHeight));
        columnsRow.addChild(localPeersColumnScroll(ref, store, playerRef, player, plugin, state, localPeers, columnWidth, viewportHeight, scrollId + "-local"));
        columnsRow.addChild(spacerX(columnGap));
        columnsRow.addChild(bundleServersColumnScroll(ref, store, playerRef, player, plugin, state, setup, bundleServers, columnWidth, viewportHeight, scrollId + "-bundle"));
        container.addChild(columnsRow);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static ReorderableListBuilder localPeersColumnScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        int contentHeight = peers.isEmpty()
            ? height
            : 8 + peers.size() * rowHeight + Math.max(0, peers.size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (peers.isEmpty()) {
            scroll.addChild(label("No local peers saved yet.", MUTED, width - 16));
            return scroll;
        }
        for (int index = 0; index < peers.size(); index++) {
            ConfiguredPeer peer = peers.get(index);
            scroll.addChild(localPeerRowCard(ref, store, playerRef, player, plugin, state, peer, width - 16, rowHeight));
            if (index + 1 < peers.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static GroupBuilder localPeerRowCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ConfiguredPeer peer,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        int verticalOffset = Math.max(0, (height - rowHeight) / 2);
        card.addChild(spacerY(verticalOffset));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int actionsWidth = SERVER_ACTION_EDIT_W + 8 + SERVER_ACTION_REMOVE_W;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - actionsWidth - 40).setHeight(rowHeight));
        identity.addChild(label(peer.displayName(), SUBTITLE, width - actionsWidth - 40));
        identity.addChild(spacerY(2));
        identity.addChild(label(peer.connectionAddress(), MUTED, width - actionsWidth - 40));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(SERVER_ACTION_EDIT_W).setHeight(30))
                .onClick((ignored, ctx) -> {
                    open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withPendingServerDisplayName(peer.displayName())
                            .withPendingServerAddress(peer.connectionAddress())
                            .withEditingServerAddress(peer.connectionAddress())
                            .withStatusText("Editing " + peer.displayName() + ".")
                    );
                })
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
    private static ReorderableListBuilder bundleServersColumnScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull HomeSetupState setup,
        @Nonnull List<HomeServerEntry> servers,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        int contentHeight = servers.isEmpty()
            ? height
            : 8 + servers.size() * rowHeight + Math.max(0, servers.size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (servers.isEmpty()) {
            scroll.addChild(label("No trusted servers visible yet.", MUTED, width - 16));
            return scroll;
        }
        for (int index = 0; index < servers.size(); index++) {
            scroll.addChild(bundlePeerRowCard(ref, store, playerRef, player, plugin, state, setup, servers.get(index), width - 16, rowHeight));
            if (index + 1 < servers.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static GroupBuilder bundlePeerRowCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull HomeSetupState setup,
        @Nonnull HomeServerEntry peer,
        int width,
        int height
    ) {
        boolean canTravel = !peer.local() && !setup.running();
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 24).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 150).setHeight(rowHeight));
        identity.addChild(label(peer.displayName(), SUBTITLE, width - 150));
        identity.addChild(spacerY(2));
        identity.addChild(label(peer.connectionAddress(), MUTED, width - 150));
        row.addChild(identity);
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(peer.local() ? "CURRENT" : "TRAVEL")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(!canTravel)
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getSecureTravelService().travelToServer(playerRef, ConfiguredPeer.parse(peer.connectionAddress()), TravelProfileType.KEEP_INVENTORY.id(), "");
                        dismissPage(player, ref, store);
                    } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not start travel to " + peer.displayName() + ": " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
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
        List<TriggerBindingDefinition> savedBindings = plugin.getTriggerBindingService().list().stream()
            .filter(binding -> binding.triggerKind() == TriggerBindingKind.PORTAL_COLLISION_ENTER)
            .filter(binding -> binding.action() == TriggerBindingAction.TRAVEL || binding.action() == TriggerBindingAction.LOCAL_TARGET)
            .toList();

        int bindCardHeight = Math.max(620, viewportHeight - 32);
        int savedBindingsViewportHeight = 300;
        int savedBindingsHeight = 64 + 12 + savedBindingsViewportHeight + 24;
        int contentHeight = 16 + bindCardHeight + 12 + savedBindingsHeight + 20;
        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(travelBindCard(ref, store, playerRef, player, plugin, state, peers, groups, setup.localConnectionAddress(), localSelectorAddress, innerWidth, bindCardHeight, scrollId));
        scroll.addChild(spacerY(12));
        scroll.addChild(portalBindingListContainer(ref, store, playerRef, player, plugin, state, savedBindings, localSelectorAddress, innerWidth, savedBindingsHeight, savedBindingsViewportHeight, scrollId + "-saved-portal-binds"));
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
        int height,
        @Nonnull String scrollId
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Travel Bind", TITLE, width - 32));
        card.addChild(label(
                "Here you can connect portals to each other or send them to exact coordinates, whether across servers, between worlds on the same server, or between two different locations in the same world.",
                MUTED,
                width - 32
        ));
        card.addChild(spacerY(12));

        GroupBuilder stack = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(height - 32));
        boolean hasSelectedIn = !state.selectedTravelInDisplayName().isBlank();
        boolean hasSelectedOut = !state.selectedTravelOutDisplayName().isBlank();
        int summaryGap = 12;
        int summaryRowWidth = width - 32;
        int summaryWidth = (summaryRowWidth - summaryGap) / 2;

        GroupBuilder actionsCenter = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(34));
        int syncButtonWidth = 280;
        int profileButtonWidth = 220;
        int bindGroupWidth = profileButtonWidth + 8 + 140 + 8 + 140 + 8 + 150 + 8 + 92;
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
        actions.addChild(travelProfileToggleButton(ref, store, playerRef, player, plugin, state, profileButtonWidth));
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
                .withAnchor(new HyUIAnchor().setWidth(92).setHeight(34))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.clearedTravelSelection().withStatusText("Cleared A / B selection.")))
        );
        actionsCenter.addChild(actions);
        stack.addChild(actionsCenter);
        stack.addChild(spacerY(12));

        GroupBuilder summaryRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(72));
        summaryRow.addChild(selectionSummaryCard("A", hasSelectedIn ? state.selectedTravelInDisplayName() : "Select portal A below.", summaryWidth, hasSelectedIn));
        summaryRow.addChild(spacerX(summaryGap));
        summaryRow.addChild(selectionSummaryCard("B", hasSelectedOut ? state.selectedTravelOutDisplayName() : "Select portal or target B below.", summaryWidth, hasSelectedOut));
        stack.addChild(summaryRow);
        stack.addChild(spacerY(12));

        int consumedHeight = 34 + 12 + 72 + 12;
        int listHeight = Math.max(260, height - 32 - consumedHeight);
        GroupBuilder listsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(listHeight));
        listsRow.addChild(travelBindListScroll(ref, store, playerRef, player, plugin, state, groups, summaryWidth, listHeight, scrollId + "-bind-a", TravelBindColumnMode.A));
        listsRow.addChild(spacerX(summaryGap));
        listsRow.addChild(travelBindListScroll(ref, store, playerRef, player, plugin, state, groups, summaryWidth, listHeight, scrollId + "-bind-b", TravelBindColumnMode.B));
        stack.addChild(listsRow);

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
    private static ButtonBuilder travelProfileToggleButton(
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
        return ButtonBuilder.smallSecondaryTextButton()
            .withText(buttonText)
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(34))
            .onClick((ignored, ctx) -> open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedTravelProfileId(toggleTravelProfile(profile).id()).withStatusText(nextStatus)
            ));
    }

    @Nonnull
    private static ReorderableListBuilder travelBindListScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelServerGroup> groups,
        int width,
        int height,
        @Nonnull String scrollId,
        @Nonnull TravelBindColumnMode mode
    ) {
        int rowGap = 8;
        int groupGap = 16;
        int dividerHeight = 52;
        int emptyRowHeight = 56;
        int contentHeight = 8;
        for (int i = 0; i < groups.size(); i++) {
            TravelServerGroup group = groups.get(i);
            contentHeight += dividerHeight;
            int itemCount = group.portals().size() + group.coordinateTargets().size();
            if (itemCount == 0) {
                contentHeight += rowGap + emptyRowHeight;
            } else {
                contentHeight += rowGap + (itemCount * HOME_SERVER_CARD_H) + Math.max(0, itemCount - 1) * rowGap;
            }
            if (i + 1 < groups.size()) {
                contentHeight += groupGap;
            }
        }
        contentHeight += 8;

        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));

        if (groups.isEmpty()) {
            scroll.addChild(label("No trusted servers or synced endpoints are available yet.", MUTED, width - 16));
            return scroll;
        }

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            TravelServerGroup group = groups.get(groupIndex);
            scroll.addChild(travelBindServerDividerRow(group, width - 16, dividerHeight));
            List<GroupBuilder> rows = new ArrayList<>();
            for (TravelPortalEndpoint portal : group.portals()) {
                rows.add(travelBindPortalRow(ref, store, playerRef, player, plugin, state, portal, width - 16, HOME_SERVER_CARD_H, mode));
            }
            for (TravelTargetEndpoint target : group.coordinateTargets()) {
                rows.add(travelBindTargetRow(ref, store, playerRef, player, plugin, state, target, width - 16, HOME_SERVER_CARD_H, mode));
            }

            if (rows.isEmpty()) {
                scroll.addChild(spacerY(rowGap));
                scroll.addChild(travelBindEmptyGroupRow(width - 16, emptyRowHeight));
            } else {
                scroll.addChild(spacerY(rowGap));
                for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                    scroll.addChild(rows.get(rowIndex));
                    if (rowIndex + 1 < rows.size()) {
                        scroll.addChild(spacerY(rowGap));
                    }
                }
            }

            if (groupIndex + 1 < groups.size()) {
                scroll.addChild(spacerY(groupGap));
            }
        }
        return scroll;
    }

    @Nonnull
    private static GroupBuilder travelBindServerDividerRow(@Nonnull TravelServerGroup group, int width, int height) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 34;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 24).setHeight(rowHeight));
        identity.addChild(label(group.displayName(), SUBTITLE, width - 24));
        identity.addChild(spacerY(2));
        identity.addChild(label(group.connectionAddress().isBlank() ? "local" : group.connectionAddress(), MUTED, width - 24));
        card.addChild(identity);
        return card;
    }

    @Nonnull
    private static GroupBuilder travelBindEmptyGroupRow(int width, int height) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(PANEL_BG);
        int rowHeight = 22;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        card.addChild(label("No endpoints discovered for this server yet.", MUTED, width - 24));
        return card;
    }

    @Nonnull
    private static GroupBuilder travelBindPortalRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelPortalEndpoint portal,
        int width,
        int height,
        @Nonnull TravelBindColumnMode mode
    ) {
        boolean selected = mode == TravelBindColumnMode.A
            ? state.selectedTravelInPortalId().equals(portal.portalId()) && state.selectedTravelInConnectionAddress().equals(portal.connectionAddress())
            : state.selectedTravelOutTargetId().equals(portal.targetId()) && state.selectedTravelOutConnectionAddress().equals(portal.connectionAddress());
        boolean blocked = mode == TravelBindColumnMode.A
            ? state.selectedTravelOutConnectionAddress().equals(portal.connectionAddress()) && state.selectedTravelOutTargetId().equals(portal.targetId())
            : state.selectedTravelInConnectionAddress().equals(portal.connectionAddress()) && state.selectedTravelInTargetId().equals(portal.targetId());

        return travelBindSelectorRowCard(
            portal.displayName(),
            "Portal",
            selected ? "SELECTED" : blocked ? "IN USE" : "SELECT",
            selected,
            blocked,
            width,
            height,
            () -> {
                NexoriMenuV2State next = mode == TravelBindColumnMode.A
                    ? state.withSelectedTravelIn(portal.connectionAddress(), portal.portalId(), portal.targetId(), portal.displayName()).withStatusText("Selected A: " + portal.displayName() + ".")
                    : state.withSelectedTravelOut(portal.connectionAddress(), portal.portalId(), portal.targetId(), portal.displayName()).withStatusText("Selected B: " + portal.displayName() + ".");
                open(ref, store, playerRef, player, plugin, next);
            }
        );
    }

    @Nonnull
    private static GroupBuilder travelBindTargetRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelTargetEndpoint target,
        int width,
        int height,
        @Nonnull TravelBindColumnMode mode
    ) {
        if (mode == TravelBindColumnMode.A) {
            return travelBindSelectorRowCard(
                target.displayName(),
                "Coordinate Target",
                "B ONLY",
                false,
                true,
                width,
                height,
                () -> { }
            );
        }

        boolean selected = state.selectedTravelOutTargetId().equals(target.targetId()) && state.selectedTravelOutConnectionAddress().equals(target.connectionAddress());
        boolean blocked = state.selectedTravelInConnectionAddress().equals(target.connectionAddress()) && state.selectedTravelInTargetId().equals(target.targetId());
        return travelBindSelectorRowCard(
            target.displayName(),
            "Coordinate Target",
            selected ? "SELECTED" : blocked ? "IN USE" : "SELECT",
            selected,
            blocked,
            width,
            height,
            () -> open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedTravelOut(target.connectionAddress(), "", target.targetId(), target.displayName()).withStatusText("Selected B: " + target.displayName() + ".")
            )
        );
    }

    @Nonnull
    private static GroupBuilder travelBindSelectorRowCard(
        @Nonnull String titleText,
        @Nonnull String detailText,
        @Nonnull String buttonText,
        boolean selected,
        boolean disabled,
        int width,
        int height,
        @Nonnull Runnable onSelect
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 24).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 150).setHeight(rowHeight));
        identity.addChild(label(titleText, SUBTITLE, width - 150));
        if (!detailText.isBlank()) {
            identity.addChild(spacerY(2));
            identity.addChild(label(detailText, MUTED, width - 150));
        }
        row.addChild(identity);
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(buttonText)
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(disabled || selected)
                .onClick((ignored, ctx) -> onSelect.run())
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder portalBindingListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TriggerBindingDefinition> bindings,
        @Nonnull String localSelectorAddress,
        int width,
        int height,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyContentHeight = bindings.isEmpty()
            ? viewportHeight
            : 8 + bindings.size() * HOME_SERVER_CARD_H + Math.max(0, bindings.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Portal Binds", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, viewportHeight, Math.max(viewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (bindings.isEmpty()) {
            body.addChild(label("No travel binds saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < bindings.size(); index++) {
                body.addChild(portalBindingListRow(ref, store, playerRef, player, plugin, state, bindings.get(index), localSelectorAddress, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < bindings.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder portalBindingListRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TriggerBindingDefinition binding,
        @Nonnull String localSelectorAddress,
        int width,
        int height
    ) {
        PortalInstanceDefinition sourcePortal = plugin.getPortalInstanceService().findById(binding.sourceId()).orElse(null);
        String sourceDisplayName = sourcePortal == null ? binding.sourceId() : sourcePortal.displayName();
        String destinationDisplayName = resolveBindingDestinationDisplayName(plugin, binding);
        String detail = binding.action() == TriggerBindingAction.LOCAL_TARGET
            ? "Local Target -> " + destinationDisplayName
            : ((binding.destinationConnectionAddress().equals(localSelectorAddress) ? "local" : binding.destinationConnectionAddress())
                + " -> "
                + destinationDisplayName
                + "  "
                + TravelProfileType.parse(binding.travelProfileId()).displayName());

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 250).setHeight(rowHeight));
        identity.addChild(label(sourceDisplayName, SUBTITLE, width - 250));
        identity.addChild(spacerY(2));
        identity.addChild(label(detail, MUTED, width - 250));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    editTravelBindingState(plugin, state, binding, localSelectorAddress)
                        .withSelectedView(NexoriMenuV2View.PORTALS)
                        .withSelectedPortalTab(PortalWorkspaceTab.BIND)
                        .withStatusText("Editing portal bind from " + sourceDisplayName + ".")
                ))
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getTriggerBindingService().remove(binding.id());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.PORTALS)
                                .withSelectedPortalTab(PortalWorkspaceTab.BIND)
                                .withStatusText(removed ? "Removed portal bind from " + sourceDisplayName + "." : "That portal bind was already removed.")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.PORTALS)
                                .withSelectedPortalTab(PortalWorkspaceTab.BIND)
                                .withStatusText("Could not remove portal bind: " + exception.getMessage())
                        );
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static NexoriMenuV2State editTravelBindingState(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TriggerBindingDefinition binding,
        @Nonnull String localSelectorAddress
    ) {
        NexoriMenuV2State next = state.clearedTravelSelection().withSelectedTravelProfileId(binding.travelProfileId());

        PortalInstanceDefinition sourcePortal = plugin.getPortalInstanceService().findById(binding.sourceId()).orElse(null);
        if (sourcePortal != null) {
            next = next.withSelectedTravelIn(
                localSelectorAddress,
                sourcePortal.portalId(),
                sourcePortal.autoDestinationTargetId(),
                sourcePortal.displayName()
            );
        }

        String destinationPortalId = "";
        String destinationDisplayName = binding.destinationTargetId();
        if (binding.destinationConnectionAddress().equals(localSelectorAddress)) {
            DestinationTargetDefinition target = plugin.getDestinationTargetService().find(binding.destinationTargetId()).orElse(null);
            if (target != null) {
                destinationDisplayName = target.displayName();
            }
            PortalInstanceDefinition portal = plugin.getPortalInstanceService().findByAutoDestinationTargetId(binding.destinationTargetId()).orElse(null);
            if (portal != null) {
                destinationPortalId = portal.portalId();
                destinationDisplayName = portal.displayName();
            }
        } else {
            DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(binding.destinationConnectionAddress()).orElse(null);
            if (discovery != null) {
                for (DiscoveredDestinationTargetSummary target : discovery.targets()) {
                    if (!target.id().equalsIgnoreCase(binding.destinationTargetId())) {
                        continue;
                    }
                    destinationDisplayName = target.displayName();
                    destinationPortalId = target.portalId() == null ? "" : target.portalId();
                    break;
                }
            }
        }

        return next.withSelectedTravelOut(
            binding.destinationConnectionAddress(),
            destinationPortalId,
            binding.destinationTargetId(),
            destinationDisplayName
        );
    }

    @Nonnull
    private static String resolveBindingDestinationDisplayName(
        @Nonnull NexoriPlugin plugin,
        @Nonnull TriggerBindingDefinition binding
    ) {
        DestinationTargetDefinition localTarget = plugin.getDestinationTargetService().find(binding.destinationTargetId()).orElse(null);
        if (localTarget != null && binding.destinationConnectionAddress().isBlank()) {
            return localTarget.displayName();
        }
        if (localTarget != null && plugin.getPortalInstanceService().findByAutoDestinationTargetId(binding.destinationTargetId()).isPresent()) {
            return plugin.getPortalInstanceService().findByAutoDestinationTargetId(binding.destinationTargetId()).orElseThrow().displayName();
        }

        if (localTarget != null && (binding.destinationConnectionAddress().isBlank() || plugin.getDiscoveredDestinationTargetCacheService().find(binding.destinationConnectionAddress()).isEmpty())) {
            return localTarget.displayName();
        }

        DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(binding.destinationConnectionAddress()).orElse(null);
        if (discovery != null) {
            for (DiscoveredDestinationTargetSummary target : discovery.targets()) {
                if (target.id().equalsIgnoreCase(binding.destinationTargetId())) {
                    return target.displayName();
                }
            }
        }
        return binding.destinationTargetId();
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

    private static LobbyDefinition currentManagedLobby(@Nonnull NexoriPlugin plugin) {
        List<LobbyDefinition> lobbies = plugin.getLobbyService().list();
        return lobbies.isEmpty() ? null : lobbies.getFirst();
    }

    private static ArenaDefinition currentEditedDestination(@Nonnull NexoriPlugin plugin, @Nonnull NexoriMenuV2State state) {
        if (state.editingDestinationId().isBlank()) {
            return null;
        }
        return plugin.getArenaService().find(state.editingDestinationId()).orElse(null);
    }

    private static QueueDefinition currentEditedQueue(@Nonnull NexoriPlugin plugin, @Nonnull NexoriMenuV2State state) {
        if (state.editingQueueId().isBlank()) {
            return null;
        }
        return plugin.getQueueService().find(state.editingQueueId()).orElse(null);
    }

    private static InstanceSpawnSlotDefinition currentEditedSpawnSlot(@Nonnull NexoriPlugin plugin, @Nonnull NexoriMenuV2State state) {
        if (state.editingDestinationId().isBlank()) {
            return null;
        }
        return plugin.getInstanceSpawnSlotService().find(state.editingDestinationId()).orElse(null);
    }

    @Nonnull
    private static List<LobbyWorldOption> buildLocalLobbyWorldOptions(@Nonnull NexoriPlugin plugin) {
        Map<String, LobbyWorldOption> optionsByWorld = new LinkedHashMap<>();
        for (DestinationTargetDefinition target : plugin.getDestinationTargetService().list()) {
            if (target.kind() == DestinationTargetKind.PORTAL) {
                continue;
            }
            String worldName = target.worldName() == null ? "" : target.worldName().trim().toLowerCase();
            if (worldName.isBlank() || worldName.startsWith("instance-")) {
                continue;
            }
            LobbyWorldOption current = optionsByWorld.get(worldName);
            if (current == null || isPreferredLobbyTarget(target, current)) {
                optionsByWorld.put(worldName, new LobbyWorldOption(worldName, target.id(), target.displayName()));
            }
        }
        return optionsByWorld.values().stream()
            .sorted(Comparator.comparing(LobbyWorldOption::worldName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private static boolean isPreferredLobbyTarget(@Nonnull DestinationTargetDefinition target, @Nonnull LobbyWorldOption current) {
        String preferredId = target.worldName() + ".natural_spawn";
        boolean currentNatural = current.targetId().equalsIgnoreCase(preferredId) || current.targetDisplayName().toLowerCase().contains("natural spawn");
        boolean nextNatural = target.id().equalsIgnoreCase(preferredId) || target.displayName().toLowerCase().contains("natural spawn");
        return nextNatural && !currentNatural;
    }

    private static LobbyWorldOption findLobbyWorldOption(@Nonnull List<LobbyWorldOption> options, @Nonnull String worldName) {
        for (LobbyWorldOption option : options) {
            if (option.worldName().equalsIgnoreCase(worldName)) {
                return option;
            }
        }
        return null;
    }

    @Nonnull
    private static List<RemoteServerOption> buildRemoteDestinationServerOptions(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull String rawLocalConnectionAddress
    ) {
        String localConnectionAddress = rawLocalConnectionAddress == null ? "" : rawLocalConnectionAddress.trim().toLowerCase();
        List<RemoteServerOption> options = new ArrayList<>();
        for (ConfiguredPeer peer : peers) {
            if (!localConnectionAddress.isBlank() && localConnectionAddress.equalsIgnoreCase(peer.connectionAddress())) {
                continue;
            }
            DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(peer.connectionAddress()).orElse(null);
            options.add(new RemoteServerOption(
                peer.displayName(),
                peer.connectionAddress(),
                discovery == null ? List.of() : discovery.targets()
            ));
        }
        return options;
    }

    @Nonnull
    private static List<RemoteTargetOption> buildRemoteTargetOptions(@Nonnull NexoriPlugin plugin, @Nonnull String connectionAddress) {
        if (connectionAddress.isBlank()) {
            return List.of();
        }
        DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(connectionAddress).orElse(null);
        if (discovery == null) {
            return List.of();
        }
        return discovery.targets().stream()
            .map(target -> new RemoteTargetOption(
                target.id(),
                target.displayName(),
                (target.kind() == null || target.kind().isBlank() ? "TARGET" : target.kind()) + "  " + (target.worldName() == null ? "" : target.worldName())
            ))
            .sorted(Comparator.comparing(RemoteTargetOption::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Nonnull
    private static List<String> buildInstanceTemplateIds() {
        return InstancesPlugin.get().getInstanceAssets().stream()
            .filter(id -> id != null && !id.isBlank() && !ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(id))
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    private static ArenaDefinition findDestination(@Nonnull List<ArenaDefinition> destinations, @Nonnull String arenaId) {
        for (ArenaDefinition destination : destinations) {
            if (destination.arenaId().equalsIgnoreCase(arenaId)) {
                return destination;
            }
        }
        return null;
    }

    @Nonnull
    private static String deriveId(String rawValue, @Nonnull String fallback) {
        if (rawValue == null) {
            return fallback;
        }
        String normalized = rawValue.trim().toLowerCase()
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_+", "")
            .replaceAll("_+$", "");
        return normalized.isBlank() ? fallback : normalized;
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
    private static ReorderableListBuilder buildPortalQueueBindingsScroll(
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
        LobbyDefinition currentLobby = currentManagedLobby(plugin);
        if (currentLobby == null) {
            int lockedHeight = Math.max(220, viewportHeight - 32);
            int contentHeight = 16 + lockedHeight + 20;
            ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
            scroll.addChild(spacerY(16));
            GroupBuilder card = card(innerWidth, lockedHeight, PANEL_BG);
            card.addChild(label("Queue Portals", TITLE, innerWidth - 32));
            card.addChild(spacerY(8));
            card.addChild(label("This tab only unlocks on the server marked as the lobby. Set the lobby first in Minigames > Lobby.", MUTED, innerWidth - 32));
            scroll.addChild(card);
            return scroll;
        }

        String localSelectorAddress = localSelectorAddress(setup.localConnectionAddress());
        List<TravelServerGroup> localGroups = buildTravelServerGroups(plugin, peers, setup.localConnectionAddress(), localSelectorAddress).stream()
            .filter(TravelServerGroup::local)
            .toList();
        List<QueueDefinition> queues = plugin.getQueueService().list();
        List<ArenaDefinition> destinations = plugin.getArenaService().list();
        List<TriggerBindingDefinition> savedBindings = plugin.getTriggerBindingService().list().stream()
            .filter(binding -> binding.triggerKind() == TriggerBindingKind.PORTAL_COLLISION_ENTER)
            .filter(binding -> binding.action() == TriggerBindingAction.JOIN_QUEUE || binding.action() == TriggerBindingAction.LEAVE_QUEUE)
            .toList();

        int setupHeight = 520;
        int savedViewportHeight = 300;
        int savedHeight = 64 + 12 + savedViewportHeight + 24;
        int contentHeight = 16 + setupHeight + 12 + savedHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(portalQueueBindingSetupCard(ref, store, playerRef, player, plugin, state, localGroups, queues, destinations, innerWidth, setupHeight, scrollId + "-queue-portals"));
        scroll.addChild(spacerY(12));
        scroll.addChild(portalQueueActionListContainer(ref, store, playerRef, player, plugin, state, savedBindings, queues, innerWidth, savedHeight, savedViewportHeight, scrollId + "-saved-queue-portals"));
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder buildPortalTargetsScroll(
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
        String rawLocalConnectionAddress = setup.localConnectionAddress();
        List<TravelServerGroup> groups = buildCoordinateTargetServerGroups(plugin, peers, rawLocalConnectionAddress, localSelectorAddress);

        int setupHeight = Math.max(520, viewportHeight - 32);
        int contentHeight = 16 + setupHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(portalTargetsSetupCard(ref, store, playerRef, player, plugin, state, peers, groups, rawLocalConnectionAddress, localSelectorAddress, innerWidth, setupHeight, scrollId + "-targets"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder portalTargetsSetupCard(
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
        int height,
        @Nonnull String scrollId
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Targets", TITLE, width - 32));
        card.addChild(label(
            "Here you can save your current location as a coordinate target and manage targets on each server.",
            MUTED,
            width - 32
        ));
        card.addChild(spacerY(12));

        TravelServerGroup selectedGroup = groups.stream()
            .filter(g -> g.connectionAddress().equalsIgnoreCase(state.selectedTargetServerConnectionAddress()))
            .findFirst()
            .orElse(groups.isEmpty() ? null : groups.getFirst());

        int inputW = 320;
        int saveW = 220;
        int syncW = 220;
        int actionWidth = inputW + 12 + saveW + 12 + syncW;
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(HOME_INPUT_BLOCK_H));
        actionRow.addChild(spacerX(Math.max(0, ((width - 32) - actionWidth) / 2)));
        actionRow.addChild(inputField("Display Name", TARGET_DISPLAY_NAME_INPUT_ID, "", "My Target", inputW));
        actionRow.addChild(spacerX(12));
        GroupBuilder buttonsColumn = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(saveW + 12 + syncW).setHeight(HOME_INPUT_BLOCK_H));
        GroupBuilder saveColumn = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(saveW).setHeight(HOME_INPUT_BLOCK_H));
        saveColumn.addChild(spacerY(HOME_ACTION_BUTTON_TOP));
        saveColumn.addChild(
            ButtonBuilder.textButton()
                .withText("SAVE CURRENT LOCATION")
                .withAnchor(new HyUIAnchor().setWidth(saveW).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String displayName = ctx.getValue(TARGET_DISPLAY_NAME_INPUT_ID, String.class).orElse("").trim();
                    if (displayName.isBlank()) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Enter a display name before saving a target."));
                        return;
                    }
                    try {
                        DestinationTargetDefinition saved = plugin.getDestinationTargetService().upsert(new DestinationTargetDefinition(
                            deriveId(displayName, "target"),
                            displayName,
                            DestinationTargetKind.COORDINATE,
                            player.getWorld().getName(),
                            "",
                            "",
                            buildCoordinateTargetMetadataJson(store, ref)
                        ));
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedTargetServerConnectionAddress(plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank())
                                .withStatusText("Saved target " + saved.displayName() + ".")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not save target: " + exception.getMessage()));
                    }
                })
        );
        buttonsColumn.addChild(saveColumn);
        buttonsColumn.addChild(spacerX(12));
        GroupBuilder syncColumn = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(syncW).setHeight(HOME_INPUT_BLOCK_H));
        syncColumn.addChild(spacerY(HOME_ACTION_BUTTON_TOP));
        syncColumn.addChild(
            ButtonBuilder.textButton()
                .withText("SYNC INFO ON ALL SERVERS")
                .withAnchor(new HyUIAnchor().setWidth(syncW).setHeight(HOME_INPUT_FIELD_H))
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
        buttonsColumn.addChild(syncColumn);
        actionRow.addChild(buttonsColumn);
        card.addChild(actionRow);
        card.addChild(spacerY(12));

        int summaryGap = 12;
        int summaryRowWidth = width - 32;
        int summaryWidth = (summaryRowWidth - summaryGap) / 2;
        GroupBuilder summaryRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(72));
        summaryRow.addChild(selectionSummaryCard(
            "SERVER",
            selectedGroup == null ? "Select a server below." : selectedGroup.displayName(),
            summaryWidth,
            selectedGroup != null
        ));
        summaryRow.addChild(spacerX(summaryGap));
        summaryRow.addChild(selectionSummaryCard(
            "TARGETS",
            selectedGroup == null ? "Select a server below." : "Coordinate targets on " + selectedGroup.displayName() + ".",
            summaryWidth,
            selectedGroup != null
        ));
        card.addChild(summaryRow);
        card.addChild(spacerY(12));

        int listHeight = Math.max(320, height - 32 - HOME_INPUT_BLOCK_H - 12 - 72 - 12);
        GroupBuilder listsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(listHeight));
        listsRow.addChild(targetServerListScroll(ref, store, playerRef, player, plugin, state, groups, summaryWidth, listHeight, scrollId + "-servers"));
        listsRow.addChild(spacerX(summaryGap));
        listsRow.addChild(targetListScroll(ref, store, playerRef, player, plugin, state, selectedGroup, rawLocalConnectionAddress, localSelectorAddress, summaryWidth, listHeight, scrollId + "-targets"));
        card.addChild(listsRow);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder targetServerListScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelServerGroup> groups,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowGap = 8;
        int contentHeight = groups.isEmpty()
            ? height
            : 8 + groups.size() * HOME_SERVER_CARD_H + Math.max(0, groups.size() - 1) * rowGap + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (groups.isEmpty()) {
            scroll.addChild(label("No servers available yet.", MUTED, width - 16));
        } else {
            for (int index = 0; index < groups.size(); index++) {
                scroll.addChild(targetServerRow(ref, store, playerRef, player, plugin, state, groups.get(index), width - 16, HOME_SERVER_CARD_H));
                if (index + 1 < groups.size()) {
                    scroll.addChild(spacerY(rowGap));
                }
            }
        }
        scroll.addChild(spacerY(8));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder targetServerRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelServerGroup group,
        int width,
        int height
    ) {
        boolean selected = state.selectedTargetServerConnectionAddress().equalsIgnoreCase(group.connectionAddress())
            || (state.selectedTargetServerConnectionAddress().isBlank() && group.local());
        return travelBindSelectorRowCard(
            group.displayName(),
            group.connectionAddress().isBlank() ? "local" : group.connectionAddress(),
            selected ? "SELECTED" : "SELECT",
            selected,
            false,
            width,
            height,
            () -> open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedTargetServerConnectionAddress(group.connectionAddress()).withStatusText("Selected server " + group.displayName() + ".")
            )
        );
    }

    @Nonnull
    private static ReorderableListBuilder targetListScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        TravelServerGroup selectedGroup,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        List<TravelTargetEndpoint> targets = selectedGroup == null ? List.of() : selectedGroup.coordinateTargets();
        int rowGap = 8;
        int contentHeight = targets.isEmpty()
            ? height
            : 8 + targets.size() * HOME_SERVER_CARD_H + Math.max(0, targets.size() - 1) * rowGap + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (selectedGroup == null) {
            scroll.addChild(label("Select a server first.", MUTED, width - 16));
        } else if (targets.isEmpty()) {
            scroll.addChild(label("No coordinate targets saved on this server.", MUTED, width - 16));
        } else {
            for (int index = 0; index < targets.size(); index++) {
                scroll.addChild(targetManagementRow(ref, store, playerRef, player, plugin, state, selectedGroup, targets.get(index), rawLocalConnectionAddress, localSelectorAddress, width - 16, HOME_SERVER_CARD_H));
                if (index + 1 < targets.size()) {
                    scroll.addChild(spacerY(rowGap));
                }
            }
        }
        scroll.addChild(spacerY(8));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder targetManagementRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TravelServerGroup selectedGroup,
        @Nonnull TravelTargetEndpoint target,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress,
        int width,
        int height
    ) {
        boolean local = selectedGroup.local();
        // Para viajar a un server remoto necesitamos su connection address real, no el selector address.
        // El connectionAddress del grupo es el real (o blank si es local sin dirección configurada).
        String destinationAddress = local ? rawLocalConnectionAddress : selectedGroup.connectionAddress();
        boolean canTravel = !destinationAddress.isBlank();

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 24).setHeight(rowHeight));
        int buttonAreaW = 80 + 8 + 110;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 24 - buttonAreaW - 10).setHeight(rowHeight));
        identity.addChild(label(target.displayName(), SUBTITLE, width - 24 - buttonAreaW - 10));
        identity.addChild(spacerY(2));
        identity.addChild(label(target.targetId(), MUTED, width - 24 - buttonAreaW - 10));
        row.addChild(identity);
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("TRAVEL")
                .withAnchor(new HyUIAnchor().setWidth(80).setHeight(30))
                .withDisabled(!canTravel)
                .onClick((ignored, ctx) -> {
                    if (local) {
                        // Viaje local: mismo servidor, puede ser mismo mundo o mundo diferente.
                        Optional<DestinationTargetDefinition> maybeDefinition = plugin.getDestinationTargetService().find(target.targetId());
                        if (maybeDefinition.isEmpty()) {
                            open(ref, store, playerRef, player, plugin, state.withStatusText("Target " + target.displayName() + " not found."));
                            return;
                        }
                        DestinationTargetDefinition definition = maybeDefinition.get();
                        String metadataJson = definition.metadataJson();
                        if (metadataJson == null || metadataJson.isBlank()) {
                            open(ref, store, playerRef, player, plugin, state.withStatusText("Target " + target.displayName() + " has no position data."));
                            return;
                        }
                        try {
                            JsonObject root = GSON.fromJson(metadataJson, JsonObject.class);
                            JsonObject position = root.getAsJsonObject("position");
                            Vector3d pos = new Vector3d(
                                position.get("x").getAsDouble(),
                                position.get("y").getAsDouble(),
                                position.get("z").getAsDouble()
                            );
                            JsonObject rotation = root.has("rotation") ? root.getAsJsonObject("rotation") : null;
                            Vector3f rot = rotation == null
                                ? new Vector3f(0.0f, 0.0f, 0.0f)
                                : new Vector3f(
                                    rotation.get("pitch").getAsFloat(),
                                    rotation.get("yaw").getAsFloat(),
                                    rotation.get("roll").getAsFloat()
                                );
                            Transform transform = new Transform(pos, rot);
                            String targetWorldName = definition.worldName();
                            boolean sameWorld = player.getWorld() != null
                                && player.getWorld().getName().equalsIgnoreCase(targetWorldName);
                            Teleport teleport;
                            if (sameWorld) {
                                teleport = Teleport.createForPlayer(transform.clone());
                            } else {
                                World targetWorld = Universe.get().getWorld(targetWorldName);
                                if (targetWorld == null) {
                                    open(ref, store, playerRef, player, plugin, state.withStatusText("World " + targetWorldName + " is not loaded."));
                                    return;
                                }
                                teleport = Teleport.createForPlayer(targetWorld, transform.clone());
                            }
                            store.addComponent(ref, Teleport.getComponentType(), teleport);
                            dismissPage(player, ref, store);
                        } catch (Exception exception) {
                            open(ref, store, playerRef, player, plugin, state.withStatusText("Could not travel to " + target.displayName() + ": " + exception.getMessage()));
                        }
                    } else {
                        // Viaje a otro servidor.
                        try {
                            ConfiguredPeer destination = ConfiguredPeer.parse(destinationAddress);
                            plugin.getSecureTravelService().travel(
                                playerRef,
                                destination,
                                target.targetId(),
                                "",
                                TravelProfileType.KEEP_INVENTORY.id(),
                                ""
                            );
                            dismissPage(player, ref, store);
                        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                            open(ref, store, playerRef, player, plugin, state.withStatusText("Could not travel to " + target.displayName() + ": " + exception.getMessage()));
                        }
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(!local)
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getDestinationTargetService().remove(target.targetId());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedTargetServerConnectionAddress(selectedGroup.connectionAddress())
                                .withStatusText(removed ? "Removed target " + target.displayName() + "." : target.displayName() + " was already removed.")
                        );
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not remove target: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static List<TravelServerGroup> buildCoordinateTargetServerGroups(
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
            List<TravelTargetEndpoint> targets = new ArrayList<>();

            if (local) {
                for (DestinationTargetDefinition target : plugin.getDestinationTargetService().list()) {
                    if (target.kind() != DestinationTargetKind.COORDINATE) {
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
                        if (kind != DestinationTargetKind.COORDINATE) {
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

            groups.add(new TravelServerGroup(displayName, connectionAddress, local, List.of(), targets));
        }
        return groups;
    }

    @Nonnull
    private static String buildCoordinateTargetMetadataJson(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        Transform transform = captureCurrentTransform(store, ref);
        JsonObject root = new JsonObject();
        JsonObject position = new JsonObject();
        position.addProperty("x", transform.getPosition().x);
        position.addProperty("y", transform.getPosition().y);
        position.addProperty("z", transform.getPosition().z);
        root.add("position", position);
        JsonObject rotation = new JsonObject();
        rotation.addProperty("pitch", transform.getRotation().x);
        rotation.addProperty("yaw", transform.getRotation().y);
        rotation.addProperty("roll", transform.getRotation().z);
        root.add("rotation", rotation);
        return GSON.toJson(root);
    }

    @Nonnull
    private static GroupBuilder portalQueueBindingSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelServerGroup> localGroups,
        @Nonnull List<QueueDefinition> queues,
        @Nonnull List<ArenaDefinition> destinations,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Queue Portals", TITLE, width - 32));
        card.addChild(label(
                "Here you can configure portals so that, when crossed, they add a player to a queue or remove them from one.",
                MUTED,
                width - 32
        ));
        card.addChild(spacerY(12));

        boolean hasPortal = !state.selectedQueuePortalId().isBlank();
        boolean hasQueue = !state.selectedQueueBindingQueueId().isBlank();
        int actionsWidth = 170 + 8 + 200;
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(34));
        actionRow.addChild(spacerX(Math.max(0, ((width - 32) - actionsWidth) / 2)));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText("ATTACH QUEUE")
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(34))
                .withDisabled(!hasPortal || !hasQueue)
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getTriggerBindingService().bindPortalCollisionQueue(
                            state.selectedQueuePortalId(),
                            state.selectedQueueBindingQueueId(),
                            TriggerBindingAction.JOIN_QUEUE
                        );
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.clearedPortalQueueSelection()
                                .withSelectedPortalTab(PortalWorkspaceTab.SETTINGS)
                                .withStatusText("Attached queue " + state.selectedQueueBindingQueueDisplayName() + " to " + state.selectedQueuePortalDisplayName() + ".")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not attach queue to portal: " + exception.getMessage()));
                    }
                })
        );
        actionRow.addChild(spacerX(8));
        actionRow.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("MAKE LEAVE QUEUE")
                .withAnchor(new HyUIAnchor().setWidth(200).setHeight(34))
                .withDisabled(!hasPortal || hasQueue)
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getTriggerBindingService().bindPortalCollisionQueue(
                            state.selectedQueuePortalId(),
                            "",
                            TriggerBindingAction.LEAVE_QUEUE
                        );
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.clearedPortalQueueSelection()
                                .withSelectedPortalTab(PortalWorkspaceTab.SETTINGS)
                                .withStatusText("Portal " + state.selectedQueuePortalDisplayName() + " now leaves the current queue.")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not mark portal as leave-queue: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(actionRow);
        card.addChild(spacerY(12));

        int summaryGap = 12;
        int summaryRowWidth = width - 32;
        int summaryWidth = (summaryRowWidth - summaryGap) / 2;
        GroupBuilder summaryRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(72));
        summaryRow.addChild(selectionSummaryCard("PORTAL", hasPortal ? state.selectedQueuePortalDisplayName() : "Select a portal below.", summaryWidth, hasPortal));
        summaryRow.addChild(spacerX(summaryGap));
        summaryRow.addChild(selectionSummaryCard("QUEUE", hasQueue ? state.selectedQueueBindingQueueDisplayName() : "Select a queue below.", summaryWidth, hasQueue));
        card.addChild(summaryRow);
        card.addChild(spacerY(12));

        int listHeight = Math.max(320, height - 32 - 34 - 12 - 72 - 12);
        GroupBuilder listsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(summaryRowWidth).setHeight(listHeight));
        listsRow.addChild(portalQueuePortalListScroll(ref, store, playerRef, player, plugin, state, localGroups, summaryWidth, listHeight, scrollId + "-portals"));
        listsRow.addChild(spacerX(summaryGap));
        listsRow.addChild(portalQueueListScroll(ref, store, playerRef, player, plugin, state, queues, destinations, summaryWidth, listHeight, scrollId + "-queues"));
        card.addChild(listsRow);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder portalQueuePortalListScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TravelServerGroup> groups,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowGap = 8;
        int groupGap = 16;
        int dividerHeight = 52;
        int emptyRowHeight = 56;
        int contentHeight = 8;
        for (int i = 0; i < groups.size(); i++) {
            TravelServerGroup group = groups.get(i);
            contentHeight += dividerHeight;
            int itemCount = group.portals().size();
            if (itemCount == 0) {
                contentHeight += rowGap + emptyRowHeight;
            } else {
                contentHeight += rowGap + (itemCount * HOME_SERVER_CARD_H) + Math.max(0, itemCount - 1) * rowGap;
            }
            if (i + 1 < groups.size()) {
                contentHeight += groupGap;
            }
        }
        contentHeight += 8;

        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            TravelServerGroup group = groups.get(groupIndex);
            scroll.addChild(travelBindServerDividerRow(group, width - 16, dividerHeight));
            if (group.portals().isEmpty()) {
                scroll.addChild(spacerY(rowGap));
                scroll.addChild(travelBindEmptyGroupRow(width - 16, emptyRowHeight));
            } else {
                scroll.addChild(spacerY(rowGap));
                for (int rowIndex = 0; rowIndex < group.portals().size(); rowIndex++) {
                    TravelPortalEndpoint portal = group.portals().get(rowIndex);
                    scroll.addChild(portalQueuePortalRow(ref, store, playerRef, player, plugin, state, portal, width - 16, HOME_SERVER_CARD_H));
                    if (rowIndex + 1 < group.portals().size()) {
                        scroll.addChild(spacerY(rowGap));
                    }
                }
            }
            if (groupIndex + 1 < groups.size()) {
                scroll.addChild(spacerY(groupGap));
            }
        }
        scroll.addChild(spacerY(8));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder portalQueuePortalRow(
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
        boolean selected = state.selectedQueuePortalId().equals(portal.portalId()) && state.selectedQueuePortalConnectionAddress().equals(portal.connectionAddress());
        return travelBindSelectorRowCard(
            portal.displayName(),
            "Portal",
            selected ? "CLEAR" : "SELECT",
            false,
            false,
            width,
            height,
            () -> open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                selected
                    ? state.clearedPortalQueueSelection().withSelectedPortalTab(PortalWorkspaceTab.SETTINGS).withStatusText("Cleared queue-portal selection.")
                    : state.withSelectedQueuePortal(portal.connectionAddress(), portal.portalId(), portal.displayName()).withSelectedPortalTab(PortalWorkspaceTab.SETTINGS).withStatusText("Selected portal " + portal.displayName() + ".")
            )
        );
    }

    @Nonnull
    private static ReorderableListBuilder portalQueueListScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<QueueDefinition> queues,
        @Nonnull List<ArenaDefinition> destinations,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowGap = 8;
        int contentHeight = queues.isEmpty()
            ? height
            : 8 + queues.size() * HOME_SERVER_CARD_H + Math.max(0, queues.size() - 1) * rowGap + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (queues.isEmpty()) {
            scroll.addChild(label("Create at least one queue first.", MUTED, width - 16));
        } else {
            for (int index = 0; index < queues.size(); index++) {
                scroll.addChild(portalQueueQueueRow(ref, store, playerRef, player, plugin, state, queues.get(index), destinations, width - 16, HOME_SERVER_CARD_H));
                if (index + 1 < queues.size()) {
                    scroll.addChild(spacerY(rowGap));
                }
            }
        }
        scroll.addChild(spacerY(8));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder portalQueueQueueRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull QueueDefinition queue,
        @Nonnull List<ArenaDefinition> destinations,
        int width,
        int height
    ) {
        boolean selected = state.selectedQueueBindingQueueId().equals(queue.queueId());
        ArenaDefinition destination = findDestination(destinations, queue.arenaIds().isEmpty() ? "" : queue.arenaIds().getFirst());
        String detail = (destination == null ? "<missing game>" : destination.displayName()) + "  " + queue.minPlayers() + "-" + queue.maxPlayers() + "  " + queue.countdownSeconds() + "s";
        return travelBindSelectorRowCard(
            queue.displayName(),
            detail,
            selected ? "CLEAR" : "SELECT",
            false,
            false,
            width,
            height,
            () -> open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                selected
                    ? state.clearedPortalQueueQueueSelection().withSelectedPortalTab(PortalWorkspaceTab.SETTINGS).withStatusText("Cleared queue selection.")
                    : state.withSelectedQueueBindingQueue(queue.queueId(), queue.displayName()).withSelectedPortalTab(PortalWorkspaceTab.SETTINGS).withStatusText("Selected queue " + queue.displayName() + ".")
            )
        );
    }

    @Nonnull
    private static GroupBuilder portalQueueActionListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<TriggerBindingDefinition> bindings,
        @Nonnull List<QueueDefinition> queues,
        int width,
        int height,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyContentHeight = bindings.isEmpty()
            ? viewportHeight
            : 8 + bindings.size() * HOME_SERVER_CARD_H + Math.max(0, bindings.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Queue Portals", width - 32));
        container.addChild(spacerY(12));
        ReorderableListBuilder body = scrollList(width - 32, viewportHeight, Math.max(viewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (bindings.isEmpty()) {
            body.addChild(label("No queue-portal actions saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < bindings.size(); index++) {
                body.addChild(portalQueueActionRow(ref, store, playerRef, player, plugin, state, bindings.get(index), queues, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < bindings.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder portalQueueActionRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull TriggerBindingDefinition binding,
        @Nonnull List<QueueDefinition> queues,
        int width,
        int height
    ) {
        PortalInstanceDefinition portal = plugin.getPortalInstanceService().findById(binding.sourceId()).orElse(null);
        String portalDisplayName = portal == null ? binding.sourceId() : portal.displayName();
        QueueDefinition queue = queues.stream().filter(candidate -> candidate.queueId().equals(binding.queueId())).findFirst().orElse(null);
        String detail = binding.action() == TriggerBindingAction.JOIN_QUEUE
            ? "Join queue  " + (queue == null ? binding.queueId() : queue.displayName())
            : binding.queueId().isBlank()
                ? "Leave current queue"
                : "Leave queue  " + (queue == null ? binding.queueId() : queue.displayName());

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 250).setHeight(rowHeight));
        identity.addChild(label(portalDisplayName, SUBTITLE, width - 250));
        identity.addChild(spacerY(2));
        identity.addChild(label(detail, MUTED, width - 250));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> {
                    NexoriMenuV2State next = state
                        .withSelectedPortalTab(PortalWorkspaceTab.SETTINGS)
                        .withSelectedView(NexoriMenuV2View.PORTALS)
                        .withSelectedQueuePortal(localSelectorAddress(""), binding.sourceId(), portalDisplayName);
                    if (binding.action() == TriggerBindingAction.JOIN_QUEUE) {
                        next = next.withSelectedQueueBindingQueue(binding.queueId(), queue == null ? binding.queueId() : queue.displayName());
                    } else {
                        next = next.clearedPortalQueueQueueSelection();
                    }
                    open(ref, store, playerRef, player, plugin, next.withStatusText("Editing queue portal " + portalDisplayName + "."));
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getTriggerBindingService().remove(binding.id());
                        open(ref, store, playerRef, player, plugin, state.withSelectedPortalTab(PortalWorkspaceTab.SETTINGS).withStatusText(removed ? "Removed queue portal " + portalDisplayName + "." : portalDisplayName + " was already removed."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedPortalTab(PortalWorkspaceTab.SETTINGS).withStatusText("Could not remove queue portal action: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder buildLobbyWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        LobbyDefinition currentLobby = currentManagedLobby(plugin);
        List<LobbyWorldOption> worlds = buildLocalLobbyWorldOptions(plugin);
        String selectedWorldName = state.pendingLobbyWorldName().isBlank()
            ? (currentLobby == null ? "" : currentLobby.worldName())
            : state.pendingLobbyWorldName();
        LobbyWorldOption selectedWorld = findLobbyWorldOption(worlds, selectedWorldName);

        int setupHeight = 176;
        int worldsHeight = 400;
        int contentHeight = 16 + setupHeight + 12 + worldsHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(lobbySetupCard(ref, store, playerRef, player, plugin, state, currentLobby, selectedWorld, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(lobbyWorldsContainer(ref, store, playerRef, player, plugin, state, currentLobby, worlds, innerWidth, worldsHeight, scrollId + "-lobby-worlds"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder lobbySetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        LobbyDefinition currentLobby,
        LobbyWorldOption selectedWorld,
        int width,
        int height
    ) {
        NetworkLobbyDefinition currentNetworkLobby = plugin.getNetworkLobbyService().current().orElse(null);
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Lobby", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Only one server can act as the lobby and host the queues. The world you select here also defines the return point from minigames. If you later move the lobby to another server, queues on the previous server will stop working.", MUTED, width - 32));
        card.addChild(spacerY(12));

        String selectedWorldLabel = selectedWorld == null ? "Select a lobby world below." : selectedWorld.worldName();
        String selectedTargetLabel = selectedWorld == null ? "A local arrival target will be used automatically." : selectedWorld.targetDisplayName();
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(72));
        row.addChild(selectionSummaryCard("LOBBY WORLD", selectedWorldLabel, 420, selectedWorld != null));
        row.addChild(spacerX(12));
        row.addChild(selectionSummaryCard("ARRIVAL TARGET", selectedTargetLabel, 520, selectedWorld != null));
        row.addChild(spacerX(12));

        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(260).setHeight(72));
        actions.addChild(spacerY(15));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(260).setHeight(42));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText(currentLobby == null ? "SAVE LOBBY" : "UPDATE LOBBY")
                .withDisabled(selectedWorld == null)
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(42))
                .onClick((ignored, ctx) -> {
                    if (selectedWorld == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Pick a local world first."));
                        return;
                    }
                    try {
                        String localConnectionAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();
                        if (localConnectionAddress.isBlank()) {
                            open(
                                ref,
                                store,
                                playerRef,
                                player,
                                plugin,
                                lobbyResumeState(state, selectedWorld.worldName(), "Configure this server's connection address before setting the lobby.")
                            );
                            return;
                        }

                        LobbyDefinition saved = new LobbyDefinition(
                            "main",
                            "Lobby",
                            selectedWorld.worldName(),
                            selectedWorld.targetId(),
                            selectedWorld.targetId(),
                            true
                        );
                        NetworkLobbyDefinition networkLobby = new NetworkLobbyDefinition(
                            localConnectionAddress,
                            selectedWorld.worldName(),
                            selectedWorld.targetId(),
                            selectedWorld.targetId(),
                            System.currentTimeMillis()
                        );
                        plugin.getLobbyService().replaceAll(List.of(saved));
                        plugin.getNetworkLobbyService().save(networkLobby);

                        List<ConfiguredPeer> remotePeers = trustedNetworkPeers(plugin);
                        String successMessage = remotePeers.isEmpty()
                            ? "This server is now the active Nexori lobby."
                            : "Marked this server as the active Nexori lobby across " + (remotePeers.size() + 1) + " server(s).";
                        if (remotePeers.isEmpty()) {
                            open(
                                ref,
                                store,
                                playerRef,
                                player,
                                plugin,
                                lobbyResumeState(state, saved.worldName(), successMessage)
                            );
                            return;
                        }

                        applyLobbyRoleAtIndex(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state,
                            remotePeers,
                            networkLobby,
                            player.getWorld().getName(),
                            captureCurrentTransform(store, ref),
                            0,
                            successMessage,
                            saved.worldName()
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            lobbyResumeState(state, selectedWorld.worldName(), "Could not save lobby: " + exception.getMessage())
                        );
                    } catch (GeneralSecurityException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            lobbyResumeState(state, selectedWorld.worldName(), "Saved the lobby locally, but could not propagate it: " + exception.getMessage())
                        );
                    }
                })
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("CLEAR")
                .withDisabled(currentLobby == null && currentNetworkLobby == null)
                .withAnchor(new HyUIAnchor().setWidth(98).setHeight(42))
                .onClick((ignored, ctx) -> {
                    if (currentLobby == null && currentNetworkLobby == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("No lobby is configured on this server."));
                        return;
                    }
                    try {
                        plugin.getNetworkLobbyService().clear();
                        plugin.getLobbyService().clearAll();

                        List<ConfiguredPeer> remotePeers = trustedNetworkPeers(plugin);
                        String successMessage = remotePeers.isEmpty()
                            ? "Cleared the active Nexori lobby."
                            : "Cleared the active Nexori lobby across " + (remotePeers.size() + 1) + " server(s).";
                        if (remotePeers.isEmpty()) {
                            open(
                                ref,
                                store,
                                playerRef,
                                player,
                                plugin,
                                lobbyResumeState(state, "", successMessage)
                            );
                            return;
                        }

                        applyLobbyRoleAtIndex(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state,
                            remotePeers,
                            null,
                            player.getWorld().getName(),
                            captureCurrentTransform(store, ref),
                            0,
                            successMessage,
                            ""
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            lobbyResumeState(state, "", "Could not clear the lobby: " + exception.getMessage())
                        );
                    } catch (GeneralSecurityException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            lobbyResumeState(state, "", "Cleared the lobby locally, but could not propagate it: " + exception.getMessage())
                        );
                    }
                })
        );
        actions.addChild(actionRow);
        row.addChild(actions);
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static NexoriMenuV2State lobbyResumeState(
        @Nonnull NexoriMenuV2State state,
        @Nonnull String pendingWorldName,
        @Nonnull String status
    ) {
        return state
            .withSelectedView(NexoriMenuV2View.QUEUES)
            .withSelectedMinigameTab(MinigameWorkspaceTab.LOBBY)
            .withPendingLobbyWorldName(pendingWorldName)
            .withStatusText(status);
    }

    private static void applyLobbyRoleAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> remotePeers,
        NetworkLobbyDefinition definition,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index,
        @Nonnull String successMessage,
        @Nonnull String pendingWorldName
    ) throws IOException, GeneralSecurityException {
        if (index >= remotePeers.size()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                lobbyResumeState(state, pendingWorldName, successMessage)
            );
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        plugin.getLobbyRoleSyncService().apply(
            playerRef,
            destination,
            originWorldName,
            originTransform.clone(),
            definition,
            chainedLobbyApplyResumeAction(
                plugin,
                state,
                remotePeers,
                definition,
                originWorldName,
                originTransform,
                index + 1,
                successMessage,
                pendingWorldName
            )
        );
    }

    @Nonnull
    private static UiResumeAction chainedLobbyApplyResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> remotePeers,
        NetworkLobbyDefinition definition,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int nextIndex,
        @Nonnull String successMessage,
        @Nonnull String pendingWorldName
    ) {
        return new UiResumeAction() {
            @Override
            public boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
                if (nextIndex >= remotePeers.size()) {
                    return false;
                }
                ConfiguredPeer nextDestination = remotePeers.get(nextIndex);
                plugin.getLobbyRoleSyncService().apply(
                    event,
                    nextDestination,
                    originWorldName,
                    originTransform.clone(),
                    definition,
                    chainedLobbyApplyResumeAction(
                        plugin,
                        state,
                        remotePeers,
                        definition,
                        originWorldName,
                        originTransform,
                        nextIndex + 1,
                        successMessage,
                        pendingWorldName
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
                    lobbyResumeState(state, pendingWorldName, successMessage)
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
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    lobbyResumeState(state, pendingWorldName, success ? successMessage : status)
                );
            }
        };
    }

    @Nonnull
    private static GroupBuilder lobbyWorldsContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        LobbyDefinition currentLobby,
        @Nonnull List<LobbyWorldOption> worlds,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int tableWidth = Math.max(560, width / 2);
        int outerGap = 12;
        int bodyViewportHeight = 300;
        int bodyContentHeight = worlds.isEmpty()
            ? bodyViewportHeight
            : 8 + worlds.size() * 64 + Math.max(0, worlds.size() - 1) * 8 + 8;

        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));

        GroupBuilder headerWrap = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(64));
        headerWrap.addChild(spacerX(Math.max(0, ((width - 32) - tableWidth) / 2)));
        headerWrap.addChild(singleColumnHeaderCard("Worlds In Current Server", tableWidth));
        container.addChild(headerWrap);
        container.addChild(spacerY(12));

        GroupBuilder bodyWrap = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(bodyViewportHeight));
        bodyWrap.addChild(spacerX(Math.max(0, ((width - 32) - tableWidth) / 2)));

        ReorderableListBuilder body = scrollList(tableWidth, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (worlds.isEmpty()) {
            body.addChild(label("No local worlds are ready yet.", MUTED, tableWidth - 16));
        } else {
            for (int index = 0; index < worlds.size(); index++) {
                LobbyWorldOption option = worlds.get(index);
                body.addChild(
                    selectorRowCard(
                        option.worldName(),
                        option.targetDisplayName(),
                        currentLobby != null && currentLobby.worldName().equals(option.worldName()),
                        tableWidth - 16,
                        64,
                        () -> open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withPendingLobbyWorldName(option.worldName()).withStatusText("Selected lobby world " + option.worldName() + ".")
                        )
                    )
                );
                if (index + 1 < worlds.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        bodyWrap.addChild(body);
        container.addChild(bodyWrap);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static ReorderableListBuilder buildDestinationWorkspaceScroll(
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
        ArenaDefinition editing = currentEditedDestination(plugin, state);
        List<RemoteServerOption> remoteServers = buildRemoteDestinationServerOptions(plugin, peers, setup.localConnectionAddress());
        String selectedConnectionAddress = state.pendingDestinationConnectionAddress().isBlank()
            ? (editing == null ? "" : editing.destinationConnectionAddress())
            : state.pendingDestinationConnectionAddress();
        List<RemoteTargetOption> remoteTargets = buildRemoteTargetOptions(plugin, selectedConnectionAddress);
        List<String> instanceIds = buildInstanceTemplateIds();
        String localSelectorAddress = localSelectorAddress(setup.localConnectionAddress());
        List<TravelServerGroup> groups = buildTravelServerGroups(plugin, peers, setup.localConnectionAddress(), localSelectorAddress);

        int setupHeight = 156;
        int selectorsViewportHeight = 300;
        int tableHeight = 72 + 12 + selectorsViewportHeight + 64;
        List<ArenaDefinition> destinations = plugin.getArenaService().list();
        int existingViewportHeight = 300;
        int existingHeight = 64 + 12 + existingViewportHeight + 56;
        int contentHeight = 16 + setupHeight + 12 + tableHeight + 12 + existingHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(
                destinationSetupCard(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state,
                        peers,
                        groups,
                        setup.localConnectionAddress(),
                        localSelectorAddress,
                        editing,
                        innerWidth,
                        setupHeight
                )
        );
        scroll.addChild(spacerY(12));
        scroll.addChild(destinationSelectionTableCard(ref, store, playerRef, player, plugin, state, remoteServers, remoteTargets, instanceIds, innerWidth, tableHeight, selectorsViewportHeight, scrollId));
        scroll.addChild(spacerY(12));
        scroll.addChild(destinationListContainer(ref, store, playerRef, player, plugin, state, destinations, innerWidth, existingHeight, scrollId + "-saved-games"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder destinationSetupCard(
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
            ArenaDefinition editing,
            int width,
            int height
    ) {
        String displayValue = state.pendingDestinationDisplayName().isBlank()
            ? (editing == null ? "New Destination" : editing.displayName())
            : state.pendingDestinationDisplayName();
        String selectedServer = state.pendingDestinationConnectionAddress().isBlank()
                ? (editing == null ? "Select a remote server below." : editing.destinationConnectionAddress())
                : state.pendingDestinationConnectionAddress();
        String selectedInstance = state.pendingDestinationInstanceTemplateId().isBlank()
                ? (editing == null ? "Select an instance template below." : editing.instanceTemplateId())
                : state.pendingDestinationInstanceTemplateId();
        String selectedTrigger = "last_player_alive".equalsIgnoreCase(state.pendingDestinationTriggerId())
                ? "Last Player Alive"
                : "Manual";

        boolean canSave = !state.pendingDestinationConnectionAddress().isBlank()
                && !state.pendingDestinationInstanceTemplateId().isBlank();

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Games", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Choose the server where the arena instance will be created, the instance template to launch, and the trigger that returns players to the lobby. Nexori includes a built-in trigger called Last Player Alive, where eliminated players return as losses and the final surviving player is resolved as the winner. The Manual option is intended for third-party mods that manage their own rule engine and use the Nexori API to decide when a player should be returned.", MUTED, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder topRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(HOME_INPUT_BLOCK_H));
        topRow.addChild(inputField("Display Name", DESTINATION_DISPLAY_NAME_INPUT_ID, displayValue, "New Game", 280));
        topRow.addChild(spacerX(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(614).setHeight(HOME_INPUT_BLOCK_H));
        actions.addChild(spacerY(HOME_ACTION_BUTTON_TOP));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(614).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
                ButtonBuilder.textButton()
                        .withText(editing == null ? "SAVE GAME" : "UPDATE GAME")
                        .withDisabled(!canSave)
                        .withAnchor(new HyUIAnchor().setWidth(190).setHeight(HOME_INPUT_FIELD_H))
                        .onClick((ignored, ctx) -> {
                            String displayName = ctx.getValue(DESTINATION_DISPLAY_NAME_INPUT_ID, String.class).orElse(displayValue).trim();
                            try {
                                String destinationId = editing == null ? deriveId(displayName, "destination") : editing.arenaId();
                                ArenaDefinition saved = plugin.getArenaService().upsert(new ArenaDefinition(
                                        destinationId,
                                        displayName,
                                        state.pendingDestinationConnectionAddress(),
                                        "",
                                        state.pendingDestinationInstanceTemplateId(),
                                        state.pendingDestinationTriggerId(),
                                        DEFAULT_DESTINATION_MAX_SUPPORTED_PLAYERS,
                                        true
                                ));
                                open(ref, store, playerRef, player, plugin, state.clearedDestinationDraft().withStatusText("Saved game " + saved.displayName() + "."));
                            } catch (IOException | IllegalArgumentException exception) {
                                open(ref, store, playerRef, player, plugin, state.withDestinationDraft(displayName, state.pendingDestinationConnectionAddress(), "", state.pendingDestinationInstanceTemplateId(), state.pendingDestinationTriggerId(), state.pendingDestinationMaxPlayers()).withStatusText("Could not save game: " + exception.getMessage()));
                            }
                        })
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                        .withText("CANCEL")
                        .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                        .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.clearedDestinationDraft().withStatusText("Game edit cleared.")))
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
                ButtonBuilder.textButton()
                        .withText("SYNC INFO ON ALL SERVERS")
                        .withAnchor(new HyUIAnchor().setWidth(280).setHeight(HOME_INPUT_FIELD_H))
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
        actions.addChild(actionRow);
        topRow.addChild(actions);
        card.addChild(topRow);
        return card;
    }

    @Nonnull
    private static GroupBuilder destinationSelectionTableCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<RemoteServerOption> remoteServers,
        @Nonnull List<RemoteTargetOption> remoteTargets,
        @Nonnull List<String> instanceIds,
        int width,
        int height,
        int selectorsViewportHeight,
        @Nonnull String scrollId
    ) {
        String selectedServer = state.pendingDestinationConnectionAddress().isBlank()
                ? "Select a remote server below."
                : state.pendingDestinationConnectionAddress();
        String selectedInstance = state.pendingDestinationInstanceTemplateId().isBlank()
                ? "Select an instance template below."
                : state.pendingDestinationInstanceTemplateId();
        String selectedTrigger = "last_player_alive".equalsIgnoreCase(state.pendingDestinationTriggerId())
                ? "Last Player Alive"
                : "Manual";

        int availableWidth = width - 32;
        int columnGap = 12;
        int columnWidth = (availableWidth - (columnGap * 2)) / 3;
        int outerGap = 12;
        GroupBuilder card = card(width, height, PANEL_BG);

        card.addChild(spacerY(outerGap));
        GroupBuilder headerRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(availableWidth).setHeight(72));
        headerRow.addChild(selectionSummaryCard("SERVER", selectedServer, columnWidth, !state.pendingDestinationConnectionAddress().isBlank()));
        headerRow.addChild(spacerX(columnGap));
        headerRow.addChild(selectionSummaryCard("INSTANCE", selectedInstance, columnWidth, !state.pendingDestinationInstanceTemplateId().isBlank()));
        headerRow.addChild(spacerX(columnGap));
        headerRow.addChild(selectionSummaryCard("RETURN TO LOBBY TRIGGER", selectedTrigger, columnWidth, true));
        card.addChild(headerRow);
        card.addChild(spacerY(12));

        GroupBuilder columnsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(availableWidth).setHeight(selectorsViewportHeight));
        columnsRow.addChild(destinationServerColumnScroll(ref, store, playerRef, player, plugin, state, remoteServers, columnWidth, selectorsViewportHeight, scrollId + "-games-server"));
        columnsRow.addChild(spacerX(columnGap));
        columnsRow.addChild(destinationInstanceColumnScroll(ref, store, playerRef, player, plugin, state, instanceIds, columnWidth, selectorsViewportHeight, scrollId + "-games-instance"));
        columnsRow.addChild(spacerX(columnGap));
        columnsRow.addChild(destinationTriggerColumnScroll(ref, store, playerRef, player, plugin, state, columnWidth, selectorsViewportHeight, scrollId + "-games-trigger"));
        card.addChild(columnsRow);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder destinationServerColumnScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<RemoteServerOption> servers,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        int contentHeight = servers.isEmpty()
            ? height
            : 8 + servers.size() * rowHeight + Math.max(0, servers.size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (servers.isEmpty()) {
            scroll.addChild(label("No remote servers yet.", MUTED, width - 16));
            return scroll;
        }
        for (int index = 0; index < servers.size(); index++) {
            RemoteServerOption option = servers.get(index);
            scroll.addChild(
                selectorRowCard(
                    option.displayName(),
                    option.connectionAddress(),
                    state.pendingDestinationConnectionAddress().equals(option.connectionAddress()),
                    width - 16,
                    rowHeight,
                    () -> open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withDestinationDraft(
                            state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName(),
                            option.connectionAddress(),
                            "",
                            state.pendingDestinationInstanceTemplateId(),
                            state.pendingDestinationTriggerId(),
                            state.pendingDestinationMaxPlayers()
                        ).withStatusText("Selected game server " + option.displayName() + ".")
                    )
                )
            );
            if (index + 1 < servers.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder destinationTargetColumnScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<RemoteTargetOption> targets,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        int contentHeight = targets.isEmpty()
            ? height
            : 8 + targets.size() * rowHeight + Math.max(0, targets.size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (targets.isEmpty()) {
            scroll.addChild(label("Select a synced server first.", MUTED, width - 16));
            return scroll;
        }
        for (int index = 0; index < targets.size(); index++) {
            RemoteTargetOption option = targets.get(index);
            scroll.addChild(
                selectorRowCard(
                    option.displayName(),
                    option.detail(),
                    state.pendingDestinationTargetId().equals(option.targetId()),
                    width - 16,
                    rowHeight,
                    () -> open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withDestinationDraft(
                            state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName(),
                            state.pendingDestinationConnectionAddress(),
                            option.targetId(),
                            state.pendingDestinationInstanceTemplateId(),
                            state.pendingDestinationTriggerId(),
                            state.pendingDestinationMaxPlayers()
                        ).withStatusText("Selected remote target " + option.displayName() + ".")
                    )
                )
            );
            if (index + 1 < targets.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder destinationInstanceColumnScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<String> instanceIds,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        int contentHeight = instanceIds.isEmpty()
            ? height
            : 8 + instanceIds.size() * rowHeight + Math.max(0, instanceIds.size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (instanceIds.isEmpty()) {
            scroll.addChild(label("No instance templates found.", MUTED, width - 16));
            return scroll;
        }
        for (int index = 0; index < instanceIds.size(); index++) {
            String instanceId = instanceIds.get(index);
            scroll.addChild(
                selectorRowCard(
                    instanceId,
                    "",
                    state.pendingDestinationInstanceTemplateId().equalsIgnoreCase(instanceId),
                    width - 16,
                    rowHeight,
                    () -> open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withDestinationDraft(
                            state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName(),
                            state.pendingDestinationConnectionAddress(),
                            state.pendingDestinationTargetId(),
                            instanceId,
                            state.pendingDestinationTriggerId(),
                            state.pendingDestinationMaxPlayers()
                        ).withStatusText("Selected instance template " + instanceId + ".")
                    )
                )
            );
            if (index + 1 < instanceIds.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static ReorderableListBuilder destinationTriggerColumnScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        List<TriggerOption> triggerOptions = List.of(
            new TriggerOption("Manual", "Return only when your minigame resolves it.", ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID),
            new TriggerOption("Last Player Alive", "Return when one player remains alive.", LastPlayerAliveArenaMatchResolutionTrigger.ID)
        );
        int contentHeight = 8 + triggerOptions.size() * rowHeight + (triggerOptions.size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        for (int index = 0; index < triggerOptions.size(); index++) {
            TriggerOption option = triggerOptions.get(index);
            boolean selected = state.pendingDestinationTriggerId().equalsIgnoreCase(option.triggerId());
            scroll.addChild(
                selectorRowCard(
                    option.displayName(),
                    option.detail(),
                    selected,
                    width - 16,
                    rowHeight,
                    () -> open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withDestinationDraft(
                            state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName(),
                            state.pendingDestinationConnectionAddress(),
                            state.pendingDestinationTargetId(),
                            state.pendingDestinationInstanceTemplateId(),
                            option.triggerId(),
                            state.pendingDestinationMaxPlayers()
                        ).withStatusText("Set return trigger to " + option.displayName() + ".")
                    )
                )
            );
            if (index + 1 < triggerOptions.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static GroupBuilder selectorRowCard(
        @Nonnull String titleText,
        @Nonnull String detailText,
        boolean selected,
        int width,
        int height,
        @Nonnull Runnable onSelect
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(12, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 24).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 150).setHeight(rowHeight));
        identity.addChild(label(titleText, SUBTITLE, width - 150));
        if (!detailText.isBlank()) {
            identity.addChild(spacerY(2));
            identity.addChild(label(detailText, MUTED, width - 150));
        }
        row.addChild(identity);
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(selected ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> onSelect.run())
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder remoteServerSelectionContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<RemoteServerOption> servers,
        int width,
        int height
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(label("Remote Servers", SUBTITLE, width - 32));
        if (servers.isEmpty()) {
            container.addChild(spacerY(8));
            container.addChild(label("Add and trust at least one remote server first.", MUTED, width - 32));
            return container;
        }

        container.addChild(spacerY(8));
        for (int index = 0; index < servers.size(); index++) {
            RemoteServerOption option = servers.get(index);
            container.addChild(remoteServerRow(ref, store, playerRef, player, plugin, state, option, width - 32, HOME_SERVER_CARD_H));
            if (index + 1 < servers.size()) {
                container.addChild(spacerY(8));
            }
        }
        return container;
    }

    @Nonnull
    private static GroupBuilder remoteServerRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull RemoteServerOption option,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(option.displayName(), TITLE, 220));
        identity.addChild(spacerX(4));
        identity.addChild(label(option.connectionAddress(), MUTED, Math.max(120, width - 394)));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(state.pendingDestinationConnectionAddress().equals(option.connectionAddress()) ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(state.pendingDestinationConnectionAddress().equals(option.connectionAddress()))
                .onClick((ignored, ctx) -> open(
                    ref, store, playerRef, player, plugin,
                    state.withDestinationDraft(
                        ctx.getValue(DESTINATION_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName()).trim(),
                        option.connectionAddress(),
                        "",
                        state.pendingDestinationInstanceTemplateId(),
                        state.pendingDestinationTriggerId(),
                        state.pendingDestinationMaxPlayers()
                    ).withStatusText("Selected game server " + option.displayName() + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder remoteTargetSelectionContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<RemoteTargetOption> targets,
        int width,
        int height
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(label("Remote Entry Targets", SUBTITLE, width - 32));
        container.addChild(spacerY(8));
        if (targets.isEmpty()) {
            container.addChild(label("Select a synced remote server above. If this stays empty, use the Portals tab and run sync first.", MUTED, width - 32));
            return container;
        }

        for (int index = 0; index < targets.size(); index++) {
            RemoteTargetOption option = targets.get(index);
            container.addChild(remoteTargetRow(ref, store, playerRef, player, plugin, state, option, width - 32, HOME_SERVER_CARD_H));
            if (index + 1 < targets.size()) {
                container.addChild(spacerY(8));
            }
        }
        return container;
    }

    @Nonnull
    private static GroupBuilder remoteTargetRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull RemoteTargetOption option,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(option.displayName(), TITLE, 360));
        identity.addChild(spacerX(4));
        identity.addChild(label(option.detail(), MUTED, Math.max(120, width - 534)));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(state.pendingDestinationTargetId().equals(option.targetId()) ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(state.pendingDestinationTargetId().equals(option.targetId()))
                .onClick((ignored, ctx) -> open(
                    ref, store, playerRef, player, plugin,
                    state.withDestinationDraft(
                        ctx.getValue(DESTINATION_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName()).trim(),
                        state.pendingDestinationConnectionAddress(),
                        option.targetId(),
                        state.pendingDestinationInstanceTemplateId(),
                        state.pendingDestinationTriggerId(),
                        state.pendingDestinationMaxPlayers()
                    ).withStatusText("Selected remote target " + option.displayName() + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder instanceTemplateSelectionContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<String> instanceIds,
        int width,
        int height
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(label("Instance Templates", SUBTITLE, width - 32));
        container.addChild(spacerY(8));
        if (instanceIds.isEmpty()) {
            container.addChild(label("No Hytale instance templates are loaded on this server.", MUTED, width - 32));
            return container;
        }

        for (int index = 0; index < instanceIds.size(); index++) {
            String instanceId = instanceIds.get(index);
            container.addChild(instanceTemplateRow(ref, store, playerRef, player, plugin, state, instanceId, width - 32, HOME_SERVER_CARD_H));
            if (index + 1 < instanceIds.size()) {
                container.addChild(spacerY(8));
            }
        }
        return container;
    }

    @Nonnull
    private static GroupBuilder instanceTemplateRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull String instanceId,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        row.addChild(label(instanceId, TITLE, width - 170));
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(state.pendingDestinationInstanceTemplateId().equalsIgnoreCase(instanceId) ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(state.pendingDestinationInstanceTemplateId().equalsIgnoreCase(instanceId))
                .onClick((ignored, ctx) -> open(
                    ref, store, playerRef, player, plugin,
                    state.withDestinationDraft(
                        ctx.getValue(DESTINATION_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingDestinationDisplayName().isBlank() ? "New Game" : state.pendingDestinationDisplayName()).trim(),
                        state.pendingDestinationConnectionAddress(),
                        state.pendingDestinationTargetId(),
                        instanceId,
                        state.pendingDestinationTriggerId(),
                        state.pendingDestinationMaxPlayers()
                    ).withStatusText("Selected instance template " + instanceId + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder destinationListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ArenaDefinition> destinations,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int headerHeight = 64;
        int outerGap = 12;
        int bodyViewportHeight = height - headerHeight - 12 - 32 - (outerGap * 2);
        int bodyContentHeight = destinations.isEmpty()
            ? bodyViewportHeight
            : 8 + destinations.size() * HOME_SERVER_CARD_H + Math.max(0, destinations.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Games", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (destinations.isEmpty()) {
            body.addChild(label("No games saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < destinations.size(); index++) {
                ArenaDefinition destination = destinations.get(index);
                body.addChild(destinationListRow(ref, store, playerRef, player, plugin, state, destination, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < destinations.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder singleColumnHeaderCard(@Nonnull String titleText, int width) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(64))
            .withPadding(HyUIPadding.all(12))
            .withBackground(SERVER_CARD_BG);
        int innerWidth = width - 24;
        card.addChild(spacerY(8));
        card.addChild(centeredLabel(titleText, TITLE, innerWidth, innerWidth, 12));
        return card;
    }

    @Nonnull
    private static GroupBuilder destinationListRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ArenaDefinition destination,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 250).setHeight(rowHeight));
        identity.addChild(label(destination.displayName(), SUBTITLE, width - 250));
        identity.addChild(spacerY(2));
        identity.addChild(label(
            destination.destinationConnectionAddress() + " -> " + destination.destinationTargetId() + "  " + destination.instanceTemplateId(),
            MUTED,
            width - 250
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> open(
                    ref, store, playerRef, player, plugin,
                    state.withEditingDestinationId(destination.arenaId())
                        .withDestinationDraft(destination.displayName(), destination.destinationConnectionAddress(), destination.destinationTargetId(), destination.instanceTemplateId(), destination.matchResolutionTriggerId(), Integer.toString(DEFAULT_DESTINATION_MAX_SUPPORTED_PLAYERS))
                        .withStatusText("Editing game " + destination.displayName() + ".")
                ))
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getArenaService().remove(destination.arenaId());
                        open(ref, store, playerRef, player, plugin, state.clearedDestinationDraft().withStatusText(removed ? "Removed game " + destination.displayName() + "." : destination.displayName() + " was already removed."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not remove game: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder buildQueueWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        LobbyDefinition currentLobby = currentManagedLobby(plugin);
        QueueDefinition editing = currentEditedQueue(plugin, state);
        List<ArenaDefinition> destinations = plugin.getArenaService().list();
        String selectedDestinationId = state.pendingQueueDestinationId().isBlank()
            ? (editing == null || editing.arenaIds().isEmpty() ? "" : editing.arenaIds().getFirst())
            : state.pendingQueueDestinationId();
        ArenaDefinition selectedDestination = findDestination(destinations, selectedDestinationId);

        int setupHeight = 168;
        int destinationViewportHeight = 300;
        int destinationSelectionHeight = 72 + 12 + destinationViewportHeight + 56;
        List<QueueDefinition> queues = plugin.getQueueService().list();
        int existingViewportHeight = 300;
        int existingHeight = 64 + 12 + existingViewportHeight + 56;
        int contentHeight = 16 + setupHeight + 12 + destinationSelectionHeight + 12 + existingHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(queueSetupCard(ref, store, playerRef, player, plugin, state, currentLobby, editing, selectedDestination, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(queueDestinationSelectionContainer(ref, store, playerRef, player, plugin, state, destinations, selectedDestination, innerWidth, destinationSelectionHeight, destinationViewportHeight, scrollId + "-queue-games"));
        scroll.addChild(spacerY(12));
        scroll.addChild(queueListContainer(ref, store, playerRef, player, plugin, state, destinations, queues, innerWidth, existingHeight, existingViewportHeight, scrollId + "-saved-queues"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder queueSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        LobbyDefinition currentLobby,
        QueueDefinition editing,
        ArenaDefinition selectedDestination,
        int width,
        int height
    ) {
        String displayValue = state.pendingQueueDisplayName().isBlank()
            ? (editing == null ? "New Queue" : editing.displayName())
            : state.pendingQueueDisplayName();
        String minValue = state.pendingQueueMinPlayers().isBlank()
            ? (editing == null ? "2" : Integer.toString(editing.minPlayers()))
            : state.pendingQueueMinPlayers();
        String maxValue = state.pendingQueueMaxPlayers().isBlank()
            ? (editing == null ? "8" : Integer.toString(editing.maxPlayers()))
            : state.pendingQueueMaxPlayers();
        String countdownValue = state.pendingQueueCountdownSeconds().isBlank()
            ? (editing == null ? "15" : Integer.toString(editing.countdownSeconds()))
            : state.pendingQueueCountdownSeconds();

        boolean canSave = currentLobby != null && selectedDestination != null;

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Queues", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label(
            currentLobby == null
                ? "This server is not a lobby yet. Use the Lobby tab first, then create queues here."
                : "Queues created here belong to the lobby on world '" + currentLobby.worldName() + "'. Queues can be attached to multiple portals, but each queue can point to only one game at a time. Once the minimum player count is reached, the countdown begins, and additional players may still join until it reaches zero. If a player leaves and the queue no longer meets the minimum requirement, the countdown resets and waits again.",
            currentLobby == null ? BAD : MUTED,
            width - 32
        ));
        card.addChild(spacerY(12));

        GroupBuilder topRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(HOME_INPUT_BLOCK_H));
        topRow.addChild(inputField("Display Name", QUEUE_DISPLAY_NAME_INPUT_ID, displayValue, "New Queue", 280));
        topRow.addChild(spacerX(12));
        topRow.addChild(inputField("Min Players", QUEUE_MIN_PLAYERS_INPUT_ID, minValue, "2", 110));
        topRow.addChild(spacerX(12));
        topRow.addChild(inputField("Max Players", QUEUE_MAX_PLAYERS_INPUT_ID, maxValue, "8", 110));
        topRow.addChild(spacerX(12));
        topRow.addChild(inputField("Countdown", QUEUE_COUNTDOWN_INPUT_ID, countdownValue, "15", 120));
        topRow.addChild(spacerX(12));

        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(330).setHeight(HOME_INPUT_BLOCK_H));
        actions.addChild(spacerY(HOME_ACTION_BUTTON_TOP));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(330).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText(editing == null ? "SAVE QUEUE" : "UPDATE QUEUE")
                .withDisabled(!canSave)
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    if (currentLobby == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Make this server the lobby first."));
                        return;
                    }
                    if (selectedDestination == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Pick a game first."));
                        return;
                    }
                    String displayName = ctx.getValue(QUEUE_DISPLAY_NAME_INPUT_ID, String.class).orElse(displayValue).trim();
                    String rawMin = ctx.getValue(QUEUE_MIN_PLAYERS_INPUT_ID, String.class).orElse(minValue).trim();
                    String rawMax = ctx.getValue(QUEUE_MAX_PLAYERS_INPUT_ID, String.class).orElse(maxValue).trim();
                    String rawCountdown = ctx.getValue(QUEUE_COUNTDOWN_INPUT_ID, String.class).orElse(countdownValue).trim();
                    try {
                        int minPlayers = Integer.parseInt(rawMin);
                        int maxPlayers = Integer.parseInt(rawMax);
                        int countdown = Integer.parseInt(rawCountdown);
                        String queueId = editing == null ? deriveId(displayName, "queue") : editing.queueId();
                        QueueDefinition saved = plugin.getQueueService().upsert(new QueueDefinition(
                            queueId,
                            displayName,
                            List.of(selectedDestination.arenaId()),
                            minPlayers,
                            maxPlayers,
                            countdown,
                            DEFAULT_MINIGAME_QUEUE_TRAVEL_PROFILE_ID,
                            true
                        ));
                        open(ref, store, playerRef, player, plugin, state.clearedQueueDraft().withStatusText("Saved queue " + saved.displayName() + "."));
                    } catch (NumberFormatException exception) {
                        open(ref, store, playerRef, player, plugin, state.withQueueDraft(displayName, state.pendingQueueDestinationId(), rawMin, rawMax, rawCountdown).withStatusText("Queue numbers must be whole numbers."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withQueueDraft(displayName, state.pendingQueueDestinationId(), rawMin, rawMax, rawCountdown).withStatusText("Could not save queue: " + exception.getMessage()));
                    }
                })
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("CANCEL")
                .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.clearedQueueDraft().withStatusText("Queue edit cleared.")))
        );
        actions.addChild(actionRow);
        topRow.addChild(actions);
        card.addChild(topRow);
        return card;
    }

    @Nonnull
    private static GroupBuilder queueDestinationSelectionContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ArenaDefinition> destinations,
        ArenaDefinition selectedDestination,
        int width,
        int height,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        GroupBuilder container = card(width, height, PANEL_BG);
        int summaryGap = 12;
        int outerGap = 12;
        int rowGap = 8;

        container.addChild(spacerY(outerGap));
        container.addChild(selectionSummaryCard(
            "GAME",
            selectedDestination == null ? "Select a game below." : selectedDestination.displayName(),
            width - 32,
            selectedDestination != null
        ));
        container.addChild(spacerY(summaryGap));
        int contentHeight = destinations.isEmpty()
            ? viewportHeight
            : 8 + destinations.size() * HOME_SERVER_CARD_H + Math.max(0, destinations.size() - 1) * rowGap + 8;
        ReorderableListBuilder body = scrollList(width - 32, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (destinations.isEmpty()) {
            body.addChild(label("Create at least one game in the tab above before creating queues.", MUTED, width - 48));
        } else {
            for (int index = 0; index < destinations.size(); index++) {
                ArenaDefinition destination = destinations.get(index);
                body.addChild(queueDestinationRow(ref, store, playerRef, player, plugin, state, destination, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < destinations.size()) {
                    body.addChild(spacerY(rowGap));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder queueDestinationRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ArenaDefinition destination,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(destination.displayName(), TITLE, 260));
        identity.addChild(spacerX(4));
        identity.addChild(label(destination.instanceTemplateId(), MUTED, Math.max(120, width - 434)));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(state.pendingQueueDestinationId().equals(destination.arenaId()) ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(state.pendingQueueDestinationId().equals(destination.arenaId()))
                .onClick((ignored, ctx) -> open(
                    ref, store, playerRef, player, plugin,
                    state.withQueueDraft(
                        ctx.getValue(QUEUE_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingQueueDisplayName().isBlank() ? "New Queue" : state.pendingQueueDisplayName()).trim(),
                        destination.arenaId(),
                        ctx.getValue(QUEUE_MIN_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMinPlayers()).trim(),
                        ctx.getValue(QUEUE_MAX_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMaxPlayers()).trim(),
                        ctx.getValue(QUEUE_COUNTDOWN_INPUT_ID, String.class).orElse(state.pendingQueueCountdownSeconds()).trim()
                    ).withStatusText("Selected game " + destination.displayName() + " for this queue.")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static GroupBuilder queueListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ArenaDefinition> destinations,
        @Nonnull List<QueueDefinition> queues,
        int width,
        int height,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyContentHeight = queues.isEmpty()
            ? viewportHeight
            : 8 + queues.size() * HOME_SERVER_CARD_H + Math.max(0, queues.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Queues", width - 32));
        container.addChild(spacerY(12));
        ReorderableListBuilder body = scrollList(width - 32, viewportHeight, Math.max(viewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (queues.isEmpty()) {
            body.addChild(label("No queues saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < queues.size(); index++) {
                QueueDefinition queue = queues.get(index);
                body.addChild(queueListRow(ref, store, playerRef, player, plugin, state, destinations, queue, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < queues.size()) {
                    body.addChild(spacerY(8));
                }
            }
        }
        body.addChild(spacerY(8));
        container.addChild(body);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static GroupBuilder queueListRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ArenaDefinition> destinations,
        @Nonnull QueueDefinition queue,
        int width,
        int height
    ) {
        ArenaDefinition destination = findDestination(destinations, queue.arenaIds().isEmpty() ? "" : queue.arenaIds().getFirst());
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 250).setHeight(rowHeight));
        identity.addChild(label(queue.displayName(), SUBTITLE, width - 250));
        identity.addChild(spacerY(2));
        identity.addChild(label((destination == null ? "<missing destination>" : destination.displayName()) + "  " + queue.minPlayers() + "-" + queue.maxPlayers() + "  " + queue.countdownSeconds() + "s", MUTED, width - 250));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> open(
                    ref, store, playerRef, player, plugin,
                    state.withEditingQueueId(queue.queueId())
                        .withQueueDraft(
                            queue.displayName(),
                            queue.arenaIds().isEmpty() ? "" : queue.arenaIds().getFirst(),
                            Integer.toString(queue.minPlayers()),
                            Integer.toString(queue.maxPlayers()),
                            Integer.toString(queue.countdownSeconds())
                        ).withStatusText("Editing queue " + queue.displayName() + ".")
                ))
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getQueueService().remove(queue.queueId());
                        open(ref, store, playerRef, player, plugin, state.clearedQueueDraft().withStatusText(removed ? "Removed queue " + queue.displayName() + "." : queue.displayName() + " was already removed."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not remove queue: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
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
            case RULES -> "";
            case QUEUES -> "Configure the lobby, reusable games, and the queues that launch into them.";
            case ACCESS_GATE -> "Control server join caps, reserved slots, and bypass player access for the server you are currently on. To configure another server, move to that server first and then open this view there.";
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
            case QUEUES -> "This view owns the clean product flow: mark the lobby, create games, then create queues.";
            case ACCESS_GATE -> "";
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

        boolean dirty = !hasBundle || (!configured.isEmpty() && !configured.equals(new TreeSet<>(trusted)));
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
        String detail = peers.isEmpty()
            ? "This server is using the active trust bundle to render the secure network list."
            : "The local server list matches the active trust bundle. Nexori is ready for secure travel.";
        return new HomeSetupState(false, false, false, localConnectionAddress, trusted, "Trusted network ready", detail, "Bundle v" + bundle.bundleVersion() + " updated at " + TIME_FORMAT.format(Instant.ofEpochMilli(bundle.updatedAtEpochMillis())) + ".", GOOD, GOOD_BG);
    }

    @Nonnull
    private static List<HomeServerEntry> buildBundleHomeServers(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull HomeSetupState setup
    ) {
        Map<String, ConfiguredPeer> localPeersByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : peers) {
            localPeersByAddress.putIfAbsent(peer.connectionAddress().toLowerCase(), peer);
        }

        List<HomeServerEntry> entries = new ArrayList<>();
        Set<String> seenAddresses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        if (!setup.trustedAddresses().isEmpty()) {
            for (BundleMember member : plugin.getBootstrapCoordinator().getTrustBundle().members()) {
                String rawConnectionAddress = member.connectionAddress();
                if (rawConnectionAddress == null || rawConnectionAddress.isBlank()) {
                    continue;
                }
                String connectionAddress = rawConnectionAddress.trim().toLowerCase();
                if (!seenAddresses.add(connectionAddress)) {
                    continue;
                }
                ConfiguredPeer localPeer = localPeersByAddress.get(connectionAddress);
                boolean local = !setup.localConnectionAddress().isBlank() && setup.localConnectionAddress().equalsIgnoreCase(connectionAddress);
                String displayName = localPeer != null
                    ? localPeer.displayName()
                    : (local ? "Current Server" : connectionAddress);
                entries.add(new HomeServerEntry(
                    displayName,
                    connectionAddress,
                    local
                ));
            }
        }

        return entries;
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

    private record LobbyWorldOption(
        String worldName,
        String targetId,
        String targetDisplayName
    ) {
    }

    private record RemoteServerOption(
        String displayName,
        String connectionAddress,
        List<DiscoveredDestinationTargetSummary> discoveredTargets
    ) {
    }

    private record RemoteTargetOption(
        String targetId,
        String displayName,
        String detail
    ) {
    }

    private record TriggerOption(
        String displayName,
        String detail,
        String triggerId
    ) {
    }

    private record RuleServerEntry(
        String selectionKey,
        String connectionAddress,
        String displayName,
        String displayAddress,
        boolean local
    ) {
    }

    private record GroupStatusCounts(
        int assigned,
        int matching,
        int needsApply,
        int unknown
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

    private record LocalServerSummary(
        @Nonnull String displayName,
        @Nonnull String connectionAddress
    ) {
    }

    private record TravelBindOperation(
        String sourceConnectionAddress,
        String sourcePortalId,
        String destinationConnectionAddress,
        String destinationTargetId,
        String travelProfileId
    ) {
    }

    private record RuleMatchIndicator(
        @Nonnull String label,
        @Nonnull HyUIPatchStyle background
    ) {
    }

    private record HomeServerEntry(
        @Nonnull String displayName,
        @Nonnull String connectionAddress,
        boolean local
    ) {
    }

    private enum TravelBindColumnMode {
        A,
        B
    }
}
