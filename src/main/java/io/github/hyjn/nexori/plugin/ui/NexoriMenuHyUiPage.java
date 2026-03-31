package io.github.hyjn.nexori.plugin.ui;

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
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.policy.ServerRuleGroupDefinition;
import io.github.hyjn.nexori.plugin.policy.ServerPolicySummary;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class NexoriMenuHyUiPage {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final int PAGE_W = 1500;
    private static final int PAGE_H = 780;
    private static final int BODY_W = 1440;
    private static final int LEFT_W = 400;
    private static final int RIGHT_W = 1024;
    private static final int TAB_W = 336;
    private static final String LOCAL_SERVER_KEY = ServerRuleGroupDefinition.LOCAL_SERVER_KEY;
    private static final String SERVER_ADDRESS_INPUT_ID = "server-address-input";
    private static final String GROUP_NAME_INPUT_ID = "group-name-input";
    private static final String TARGET_DISPLAY_NAME_INPUT_ID = "target-display-name-input";
    private static final String PORTAL_DISPLAY_NAME_INPUT_ID = "portal-display-name-input";
    private static final int TARGET_STEP_KIND = 0;
    private static final int TARGET_STEP_DETAILS = 1;
    private static final int TARGET_STEP_REVIEW = 2;
    private static final int TARGET_LAST_STEP = TARGET_STEP_REVIEW;
    private static final int PORTAL_STEP_SELECT_SERVER = 0;
    private static final int PORTAL_STEP_SELECT_TARGET = 1;
    private static final int PORTAL_STEP_SELECT_PROFILE = 2;
    private static final int PORTAL_STEP_PORTAL_DETAILS = 3;
    private static final int PORTAL_STEP_REVIEW = 4;
    private static final int PORTAL_LAST_STEP = PORTAL_STEP_REVIEW;

    private static final Gson GSON = new Gson();

    private static final HyUIPatchStyle CARD_BG = new HyUIPatchStyle().setColor("#17273a");
    private static final HyUIPatchStyle ITEM_BG = new HyUIPatchStyle().setColor("#20354e");
    private static final HyUIPatchStyle STATUS_BG = new HyUIPatchStyle().setColor("#2d4f75");
    private static final HyUIPatchStyle SERVER_BUTTON_BG = new HyUIPatchStyle().setColor("#27405d");
    private static final HyUIPatchStyle SERVER_BUTTON_SELECTED_BG = new HyUIPatchStyle().setColor("#466f9f");
    private static final HyUIPatchStyle GOOD_BG = new HyUIPatchStyle().setColor("#1f5c35");
    private static final HyUIPatchStyle BAD_BG = new HyUIPatchStyle().setColor("#7b3742");

    private static final HyUIStyle TITLE = new HyUIStyle().setFontSize(17).setRenderBold(true).setTextColor("#f1f6ff");
    private static final HyUIStyle BODY = new HyUIStyle().setFontSize(14).setTextColor("#d6e5f7").setWrap(true);
    private static final HyUIStyle MUTED = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true);
    private static final HyUIStyle LABEL = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#adc3de");
    private static final HyUIStyle GOOD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#7de3a6");
    private static final HyUIStyle BAD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ff8b9a");
    private static final HyUIStyle INFO = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#8fc7ff");
    private static final ScrollbarStyle DEFAULT_SCROLLBAR = ScrollbarStyle.defaultExtraSpacingStyle()
        .withOnlyVisibleWhenHovered(false)
        .withSize(8)
        .withSpacing(4)
        .withBackground(new HyUIPatchStyle().setColor("#16283d"))
        .withHandle(new HyUIPatchStyle().setColor("#39577b"))
        .withHoveredHandle(new HyUIPatchStyle().setColor("#5d87bb"))
        .withDraggedHandle(new HyUIPatchStyle().setColor("#76a3dd"));

    private NexoriMenuHyUiPage() {
    }

    @Nonnull
    private static State defaultState() {
        return new State(
            Tab.SERVERS,
            "",
            "",
            "",
            "",
            ServersPanel.DETAILS,
            RulesPanel.DETAILS,
            TargetsPanel.DETAILS,
            "",
            "",
            0,
            "",
            "",
            0,
            "",
            "",
            "",
            "",
            ""
        );
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin
    ) {
        open(ref, store, playerRef, player, plugin, defaultState());
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull State state
    ) {
        if (player == null) {
            playerRef.sendMessage(Message.raw("nexorimenu: could not resolve the live player entity."));
            return;
        }

        List<ConfiguredPeer> peers = plugin.getConfiguredPeerService().list();
        List<ServerEntry> serverEntries = buildServerEntries(plugin, peers);
        String selectedPeerAddress = state.serversPanel() == ServersPanel.DETAILS
            ? normalizeSelectedPeerAddress(state.selectedPeerAddress(), serverEntries)
            : "";
        ServerEntry selectedServer = state.serversPanel() == ServersPanel.DETAILS
            ? findSelectedServer(serverEntries, selectedPeerAddress)
            : null;
        List<DestinationTargetDefinition> targets = plugin.getDestinationTargetService().list();
        String selectedTargetId = "";
        DestinationTargetDefinition selectedTarget = null;
        if (state.tab() == Tab.TARGETS) {
            selectedTargetId = state.selectedTargetId().isBlank()
                ? (state.targetsPanel() == TargetsPanel.DETAILS ? normalizeSelectedTargetId("", targets) : "")
                : normalizeSelectedTargetId(state.selectedTargetId(), targets);
            selectedTarget = selectedTargetId.isBlank() ? null : findSelectedTarget(targets, selectedTargetId);
        }
        List<ServerRuleGroupDefinition> ruleGroups = plugin.getServerRuleGroupService().list();
        String selectedRuleGroupId = state.rulesPanel() == RulesPanel.DETAILS
            ? normalizeSelectedRuleGroupId(state.selectedRuleGroupId(), ruleGroups)
            : "";
        ServerRuleGroupDefinition selectedRuleGroup = state.rulesPanel() == RulesPanel.DETAILS
            ? findSelectedRuleGroup(ruleGroups, selectedRuleGroupId)
            : null;
        State normalizedState = state
            .withSelectedPeer(selectedPeerAddress)
            .withSelectedTarget(selectedTargetId)
            .withSelectedRuleGroup(selectedRuleGroupId);

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI HYUI")
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(14));
        content.addChild(tabs(ref, store, playerRef, player, plugin, normalizedState));
        content.addChild(spacerY(12));
        content.addChild(body(ref, store, playerRef, player, plugin, normalizedState, peers, serverEntries, selectedServer, targets, selectedTarget, ruleGroups, selectedRuleGroup));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    public static void openPortalSetup(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String statusText
    ) {
        openPortalSetup(
            ref,
            store,
            playerRef,
            player,
            plugin,
            defaultState().withTab(Tab.TARGETS),
            portal,
            portal.autoDestinationTargetId(),
            statusText
        );
    }

    private static GroupBuilder tabs(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, Player player, NexoriPlugin plugin, State state) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(42));
        Tab[] tabs = Tab.values();
        int totalTabsWidth = tabs.length * TAB_W + Math.max(0, tabs.length - 1) * 8;
        int leadingSpace = Math.max(0, (BODY_W - totalTabsWidth) / 2);
        if (leadingSpace > 0) {
            row.addChild(spacerX(leadingSpace));
        }
        for (int index = 0; index < tabs.length; index++) {
            Tab tab = tabs[index];
            ButtonBuilder button = (state.tab() == tab ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText(tab.label)
                .withAnchor(new HyUIAnchor().setWidth(TAB_W).setHeight(42))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withTab(tab)
                        .withStatus("")
                        .withServersPanel(ServersPanel.DETAILS)
                        .withRulesPanel(RulesPanel.DETAILS)
                        .withTargetsPanel(TargetsPanel.DETAILS)
                ));
            row.addChild(button);
            if (index + 1 < tabs.length) row.addChild(spacerX(8));
        }
        return row;
    }

    private static GroupBuilder body(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, Player player, NexoriPlugin plugin, State state,
                                     List<ConfiguredPeer> peers, List<ServerEntry> serverEntries, ServerEntry selectedServer,
                                     List<DestinationTargetDefinition> targets, DestinationTargetDefinition selectedTarget, List<ServerRuleGroupDefinition> ruleGroups,
                                     ServerRuleGroupDefinition selectedRuleGroup) {
        return switch (state.tab()) {
            case SERVERS -> serversBody(ref, store, playerRef, player, plugin, state, peers, serverEntries, selectedServer);
            case TARGETS -> targetsBody(ref, store, playerRef, player, plugin, state, targets, selectedTarget);
            case RULES -> rulesBody(ref, store, playerRef, player, plugin, state, peers, serverEntries, ruleGroups, selectedRuleGroup);
        };
    }

    private static GroupBuilder serversBody(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        List<ConfiguredPeer> peers,
        List<ServerEntry> serverEntries,
        ServerEntry selectedServer
    ) {
        int panelHeight = bodyHeight(state);
        int leftListHeight = Math.max(220, panelHeight - 170);
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(panelHeight));

        GroupBuilder left = card(LEFT_W, panelHeight, CARD_BG);
        left.addChild(label("Trusted Network", TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        left.addChild(label(
            serverEntries.isEmpty()
                ? "No active trusted network is known on this server yet."
                : serverEntries.size() + " trusted server(s) in the active network.",
            MUTED,
            LEFT_W - 32
        ));
        left.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(38));
        actions.addChild(spacerX(((LEFT_W - 32) - 190) / 2));
        actions.addChild(
            (state.serversPanel() == ServersPanel.SETUP ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText("Initial Setup")
                .withAnchor(new HyUIAnchor().setWidth(190).setHeight(38))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedPeer("").withServersPanel(ServersPanel.SETUP).withStatus("")))
        );
        left.addChild(actions);
        left.addChild(spacerY(12));
        int serverListContentHeight = Math.max(leftListHeight, serverEntries.isEmpty() ? 120 : serverEntries.size() * 62 + 24);
        ReorderableListBuilder listHost = scrollList(LEFT_W - 32, leftListHeight, serverListContentHeight, "trusted-servers-list", true);
        if (!serverEntries.isEmpty()) {
            for (ServerEntry peer : serverEntries) {
                boolean selected = selectedServer != null && selectedServer.selectionKey().equals(peer.selectionKey());
                listHost.addChild(
                    (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                        .withText(peer.displayAddress())
                        .withBackground(selected ? SERVER_BUTTON_SELECTED_BG : SERVER_BUTTON_BG)
                        .withDisabled(selected)
                        .withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(54))
                        .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedPeer(peer.selectionKey()).withServersPanel(ServersPanel.DETAILS).withStatus("")))
                );
                listHost.addChild(spacerY(8));
            }
        } else {
            listHost.addChild(spacerY(12));
            listHost.addChild(label("Run Initial Setup to build the first trusted network for this server.", MUTED, LEFT_W - 48));
        }
        left.addChild(listHost);

        GroupBuilder right = card(RIGHT_W, panelHeight, CARD_BG);
        if (state.serversPanel() == ServersPanel.SETUP || state.serversPanel() == ServersPanel.ADD) {
            buildSetupPanel(ref, store, playerRef, player, plugin, state, peers, right);
        } else {
            buildServerDetailsPanel(ref, store, playerRef, player, plugin, state, selectedServer, serverEntries, right);
        }

        row.addChild(left);
        row.addChild(spacerX(16));
        row.addChild(right);
        return row;
    }

    private static void buildServerDetailsPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerEntry selectedPeer,
        List<ServerEntry> serverEntries,
        GroupBuilder right
    ) {
        right.addChild(label("Trusted Network Details", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        if (selectedPeer == null) {
            right.addChild(label("No active trusted server selected yet. Open Initial Setup to manage the local bootstrap peers that build this network.", MUTED, RIGHT_W - 32));
            return;
        }

        GroupBuilder connectBlock = card(RIGHT_W - 32, 176, ITEM_BG);
        connectBlock.addChild(label("How To Connect To This Server", TITLE, RIGHT_W - 64));
        connectBlock.addChild(spacerY(10));
        connectBlock.addChild(stat("Connection Address", selectedPeer.displayAddress(), RIGHT_W - 16));
        connectBlock.addChild(stat("Host", selectedPeer.host(), RIGHT_W - 16));
        connectBlock.addChild(stat("Port", selectedPeer.portText(), RIGHT_W - 16));
        connectBlock.addChild(stat(
            "Saved In Bootstrap Peers",
            selectedPeer.configured()
                ? "Yes, this server is also saved in this server's local bootstrap peer list."
                : "No, this server only appears in the active trusted network on this server.",
            RIGHT_W - 16
        ));
        right.addChild(connectBlock);
        right.addChild(spacerY(14));

        GroupBuilder secureBlock = card(RIGHT_W - 32, 280, ITEM_BG);
        secureBlock.addChild(label("Secure Travel With", TITLE, RIGHT_W - 64));
        secureBlock.addChild(spacerY(10));
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        boolean selectedServerTrusted = isTrustedEntry(plugin, bundle, selectedPeer);
        int secureRowCount = serverEntries.size();
        int secureListContentHeight = Math.max(200, Math.max(1, secureRowCount) * 32 + 32);
        ReorderableListBuilder secureList = scrollList(RIGHT_W - 64, 200, secureListContentHeight, "secure-travel-list", false);
        secureList.addChild(secureLinkRow(selectedPeer.displayAddress() + " (Selected)", selectedServerTrusted));
        secureList.addChild(spacerY(8));
        for (ServerEntry peer : serverEntries) {
            if (peer.selectionKey().equals(selectedPeer.selectionKey())) {
                continue;
            }
            secureList.addChild(secureLinkRow(peer.displayAddress(), selectedServerTrusted && isTrustedEntry(plugin, bundle, peer)));
            secureList.addChild(spacerY(8));
        }
        secureBlock.addChild(secureList);
        right.addChild(secureBlock);
    }

    private static void buildAddServerPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        GroupBuilder right
    ) {
        right.addChild(label("Add Server", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        GroupBuilder addBlock = card(RIGHT_W - 32, 190, ITEM_BG);
        GroupBuilder centeredHost = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(150));
        centeredHost.addChild(spacerX((RIGHT_W - 64 - 420) / 2));
        GroupBuilder centered = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(420).setHeight(150));
        centered.addChild(label("Connection Address", LABEL, 420));
        centered.addChild(spacerY(8));
        centered.addChild(
            TextFieldBuilder.textInput()
                .withId(SERVER_ADDRESS_INPUT_ID)
                .withValue(state.pendingServerAddress())
                .withPlaceholderText("host:port")
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(420).setHeight(42))
                .withBackground("#101926")
        );
        centered.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(420).setHeight(42));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("Save Server")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(42))
                .onClick((ignored, ctx) -> {
                    String rawAddress = ctx.getValue(SERVER_ADDRESS_INPUT_ID, String.class).orElse(state.pendingServerAddress()).trim();
                    try {
                        ConfiguredPeer added = plugin.getConfiguredPeerService().add(rawAddress);
                        String nextSelected = isLocalAddress(plugin, added.connectionAddress()) ? LOCAL_SERVER_KEY : added.connectionAddress();
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedPeer(nextSelected)
                                .withServersPanel(ServersPanel.DETAILS)
                                .withPendingServerAddress("")
                                .withStatus("Saved server " + added.connectionAddress() + ".")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withServersPanel(ServersPanel.ADD).withPendingServerAddress(rawAddress).withStatus("Could not add server: " + exception.getMessage()));
                    }
                })
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("Cancel")
                .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withServersPanel(ServersPanel.DETAILS).withPendingServerAddress("").withStatus("")))
        );
        centered.addChild(actions);
        centeredHost.addChild(centered);
        addBlock.addChild(centeredHost);
        right.addChild(addBlock);
    }

    private static void buildSetupPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        List<ConfiguredPeer> peers,
        GroupBuilder right
    ) {
        SetupReport report = buildSetupReport(plugin, peers);
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        boolean hasActiveTrustedNetwork = bundle.bundleVersion() > 0 && !bundle.bundleHash().isBlank() && !bundle.members().isEmpty();
        right.addChild(label("Initial Setup", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        int detailsHostHeight = bodyHeight(state) - 72;
        ReorderableListBuilder detailsHost = scrollList(
            RIGHT_W - 32,
            detailsHostHeight,
            Math.max(detailsHostHeight + 40, 860),
            "server-setup-details-scroll",
            false
        );

        if (!state.statusText().isBlank()) {
            detailsHost.addChild(label(state.statusText(), MUTED, RIGHT_W - 64));
            detailsHost.addChild(spacerY(10));
        }

        GroupBuilder setupBlock = card(RIGHT_W - 32, 248, ITEM_BG);
        setupBlock.addChild(label("Setup", TITLE, RIGHT_W - 64));
        setupBlock.addChild(spacerY(8));
        setupBlock.addChild(label(
            "The list on the left shows the active trusted network from the current Nexori trust bundle. Running Initial Setup uses only the Bootstrap Peers list below on this server, verifies those servers, then rebuilds and redistributes the trust bundle.",
            BODY,
            RIGHT_W - 64
        ));
        setupBlock.addChild(spacerY(12));
        setupBlock.addChild(label(
            "When you want to add or remove servers from the secure network, update the Bootstrap Peers list below and run Initial Setup again. Every server that should remain in the secure network must be in that list, including the server you are on now.",
            MUTED,
            RIGHT_W - 64
        ));
        setupBlock.addChild(spacerY(14));
        GroupBuilder setupActions = report.running()
            ? centeredActionRow(RIGHT_W - 64, 220, 12, 220)
            : centeredActionRow(RIGHT_W - 64, 220);
        setupActions.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("Run Initial Setup")
                .withDisabled(peers.isEmpty() || report.running())
                .withAnchor(new HyUIAnchor().setWidth(220).setHeight(42))
                .onClick((ignored, ctx) -> {
                    BootstrapCoordinator.StartResult result = plugin.getBootstrapCoordinator().start(playerRef);
                    if (result.started()) {
                        PageManager pages = player.getPageManager();
                        if (pages != null) {
                            pages.setPage(ref, store, Page.None);
                        }
                    } else {
                        open(ref, store, playerRef, player, plugin, state.withServersPanel(ServersPanel.SETUP).withStatus(result.message()));
                    }
                })
        );
        if (report.running()) {
            setupActions.addChild(spacerX(12));
            setupActions.addChild(
                ButtonBuilder.textButton()
                    .withText("Reset Active Run")
                    .withBackground(new HyUIPatchStyle().setColor("#a33f4d"))
                    .withAnchor(new HyUIAnchor().setWidth(220).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        BootstrapCoordinator.StartResult result = plugin.getBootstrapCoordinator().resetActiveRun(playerRef);
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withServersPanel(ServersPanel.SETUP).withStatus(result.message())
                        );
                    })
            );
        }
        setupBlock.addChild(setupActions);
        detailsHost.addChild(setupBlock);
        detailsHost.addChild(spacerY(14));

        GroupBuilder peersBlock = card(RIGHT_W - 32, 284, ITEM_BG);
        peersBlock.addChild(label("Bootstrap Peers", TITLE, RIGHT_W - 64));
        peersBlock.addChild(spacerY(8));
        peersBlock.addChild(label(
            hasActiveTrustedNetwork && peers.isEmpty()
                ? "This server already has an active trusted network, but its local Bootstrap Peers list is empty. You can still update the network from here, but add every server that should exist in the next secure network, including the server you are on now."
                : "These are the local peers this server will use as input for the next Initial Setup run. Add or remove servers here before you rebuild the trusted network.",
            MUTED,
            RIGHT_W - 64
        ));
        peersBlock.addChild(spacerY(12));
        GroupBuilder addRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
        addRow.addChild(
            TextFieldBuilder.textInput()
                .withId(SERVER_ADDRESS_INPUT_ID)
                .withValue(state.pendingServerAddress())
                .withPlaceholderText("host:port")
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(520).setHeight(42))
                .withBackground("#101926")
        );
        addRow.addChild(spacerX(12));
        addRow.addChild(
            ButtonBuilder.textButton()
                .withText("Add Server")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(42))
                .onClick((ignored, ctx) -> {
                    String rawAddress = ctx.getValue(SERVER_ADDRESS_INPUT_ID, String.class).orElse(state.pendingServerAddress()).trim();
                    try {
                        ConfiguredPeer added = plugin.getConfiguredPeerService().add(rawAddress);
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withServersPanel(ServersPanel.SETUP)
                                .withPendingServerAddress("")
                                .withStatus("Added bootstrap peer " + added.connectionAddress() + ".")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withServersPanel(ServersPanel.SETUP)
                                .withPendingServerAddress(rawAddress)
                                .withStatus("Could not add bootstrap peer: " + exception.getMessage())
                        );
                    }
                })
        );
        peersBlock.addChild(addRow);
        peersBlock.addChild(spacerY(12));
        ReorderableListBuilder bootstrapPeerList = scrollList(
            RIGHT_W - 64,
            128,
            Math.max(140, peers.isEmpty() ? 80 : peers.size() * 38 + 24),
            "bootstrap-peers-list",
            true
        );
        if (peers.isEmpty()) {
            bootstrapPeerList.addChild(spacerY(8));
            bootstrapPeerList.addChild(label("No bootstrap peers are saved on this server yet.", MUTED, RIGHT_W - 96));
        } else {
            for (ConfiguredPeer peer : peers) {
                GroupBuilder peerRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 96).setHeight(30));
                peerRow.addChild(LabelBuilder.label().withText(peer.connectionAddress()).withAnchor(new HyUIAnchor().setWidth(540)).withStyle(BODY));
                peerRow.addChild(spacerX(12));
                peerRow.addChild(
                    ButtonBuilder.smallSecondaryTextButton()
                        .withText("Remove")
                        .withAnchor(new HyUIAnchor().setWidth(120).setHeight(30))
                        .onClick((ignored, ctx) -> {
                            try {
                                boolean removed = plugin.getConfiguredPeerService().remove(peer.connectionAddress());
                                open(
                                    ref,
                                    store,
                                    playerRef,
                                    player,
                                    plugin,
                                    state.withServersPanel(ServersPanel.SETUP)
                                        .withPendingServerAddress("")
                                        .withStatus(
                                            removed
                                                ? "Removed bootstrap peer " + peer.connectionAddress() + ". The active trusted network on the left will not change until you run Initial Setup again."
                                                : "That bootstrap peer was already removed from this server."
                                        )
                                );
                            } catch (IOException | IllegalArgumentException exception) {
                                open(
                                    ref,
                                    store,
                                    playerRef,
                                    player,
                                    plugin,
                                    state.withServersPanel(ServersPanel.SETUP)
                                        .withStatus("Could not remove bootstrap peer: " + exception.getMessage())
                                );
                            }
                        })
                );
                bootstrapPeerList.addChild(peerRow);
                bootstrapPeerList.addChild(spacerY(8));
            }
        }
        peersBlock.addChild(bootstrapPeerList);
        detailsHost.addChild(peersBlock);
        detailsHost.addChild(spacerY(14));

        GroupBuilder reportBlock = card(RIGHT_W - 32, 220, ITEM_BG);
        reportBlock.addChild(label("Report", TITLE, RIGHT_W - 64));
        reportBlock.addChild(spacerY(8));
        reportBlock.addChild(coloredStat("Status", report.status(), RIGHT_W - 16, report.statusStyle()));
        if (!report.detail().isBlank()) {
            reportBlock.addChild(spacerY(6));
            reportBlock.addChild(label(report.detail(), BODY, RIGHT_W - 64));
        }
        if (!report.followUp().isBlank()) {
            reportBlock.addChild(spacerY(10));
            reportBlock.addChild(label(report.followUp(), MUTED, RIGHT_W - 64));
        }
        detailsHost.addChild(reportBlock);
        right.addChild(detailsHost);
    }

    private static GroupBuilder targetsBody(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        List<DestinationTargetDefinition> targets,
        DestinationTargetDefinition selectedTarget
    ) {
        int panelHeight = bodyHeight(state);
        int leftListHeight = Math.max(220, panelHeight - 170);
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(panelHeight));

        GroupBuilder left = card(LEFT_W, panelHeight, CARD_BG);
        left.addChild(label("Destination Targets", TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        left.addChild(label(
            targets.isEmpty()
                ? "No destination targets exist on this server yet."
                : targets.size() + " destination target(s) on this server.",
            MUTED,
            LEFT_W - 32
        ));
        left.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(38));
        TargetSetupDraft draft = plugin.getTargetSetupDraftService().find(playerRef.getUuid()).orElse(null);
        int createButtonWidth = draft == null ? 180 : 150;
        actions.addChild(spacerX(draft == null ? ((LEFT_W - 32) - createButtonWidth) / 2 : 6));
        actions.addChild(
            ButtonBuilder.secondaryTextButton().withText("Create Target")
                .withAnchor(new HyUIAnchor().setWidth(createButtonWidth).setHeight(38))
                .onClick((ignored, ctx) -> {
                    plugin.getTargetSetupDraftService().clear(playerRef.getUuid());
                    open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state
                            .withTargetsPanel(TargetsPanel.CREATE)
                            .withTargetStepIndex(TARGET_STEP_KIND)
                            .withPendingTargetId("")
                            .withPendingTargetDisplayName("")
                            .withSelectedTarget("")
                            .withStatus("")
                    );
                })
        );
        if (draft != null) {
            actions.addChild(spacerX(8));
            actions.addChild(
                ButtonBuilder.textButton().withText("Continue Draft")
                    .withAnchor(new HyUIAnchor().setWidth(180).setHeight(38))
                    .onClick((ignored, ctx) -> open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state
                            .withTargetsPanel(TargetsPanel.CREATE)
                            .withTargetStepIndex(clampTargetStep(draft.stepIndex()))
                            .withSelectedTarget("")
                            .withPendingTargetId(draft.targetId())
                            .withPendingTargetDisplayName(draft.displayName())
                            .withStatus("")
                    ))
            );
        }
        left.addChild(actions);
        left.addChild(spacerY(12));

        int targetListContentHeight = Math.max(leftListHeight, targets.isEmpty() ? 120 : targets.size() * 62 + 24);
        ReorderableListBuilder listHost = scrollList(LEFT_W - 32, leftListHeight, targetListContentHeight, "targets-list", true);
        if (!targets.isEmpty()) {
            for (DestinationTargetDefinition target : targets) {
                boolean selected = selectedTarget != null && selectedTarget.id().equals(target.id());
                listHost.addChild(
                    (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                        .withText(target.displayName())
                        .withBackground(selected ? SERVER_BUTTON_SELECTED_BG : SERVER_BUTTON_BG)
                        .withDisabled(selected)
                        .withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(54))
                        .onClick((ignored, ctx) -> open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedTarget(target.id())
                                .withTargetsPanel(TargetsPanel.DETAILS)
                                .withStatus("")
                        ))
                );
                listHost.addChild(spacerY(8));
            }
        } else {
            listHost.addChild(spacerY(12));
            listHost.addChild(label("Create coordinate targets from your current position, or place portals to generate portal targets automatically.", MUTED, LEFT_W - 48));
        }
        left.addChild(listHost);

        GroupBuilder right = card(RIGHT_W, panelHeight, CARD_BG);
        if (state.targetsPanel() == TargetsPanel.CREATE) {
            buildTargetCreatePanel(ref, store, playerRef, player, plugin, state, right);
        } else if (state.targetsPanel() == TargetsPanel.PORTAL_SETUP) {
            buildPortalSetupPanel(ref, store, playerRef, player, plugin, state, selectedTarget, right);
        } else {
            right.addChild(label("Target Details", TITLE, RIGHT_W - 32));
            right.addChild(spacerY(10));
            if (!state.statusText().isBlank()) {
                right.addChild(label(state.statusText(), MUTED, RIGHT_W - 32));
                right.addChild(spacerY(10));
            }

            if (selectedTarget == null) {
                right.addChild(label("Select a destination target on the left to inspect it or continue its setup.", MUTED, RIGHT_W - 32));
            } else {
                buildTargetDetailsPanel(ref, store, playerRef, player, plugin, state, selectedTarget, right);
            }
        }

        row.addChild(left);
        row.addChild(spacerX(16));
        row.addChild(right);
        return row;
    }

    private static void buildTargetDetailsPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        DestinationTargetDefinition selectedTarget,
        GroupBuilder right
    ) {
        GroupBuilder detailsBlock = card(RIGHT_W - 32, 190, ITEM_BG);
        detailsBlock.addChild(label("Current Target", TITLE, RIGHT_W - 64));
        detailsBlock.addChild(spacerY(10));
        detailsBlock.addChild(stat("Target ID", selectedTarget.id(), RIGHT_W - 16));
        detailsBlock.addChild(stat("Display Name", selectedTarget.displayName(), RIGHT_W - 16));
        detailsBlock.addChild(stat("Kind", selectedTarget.kind().displayName(), RIGHT_W - 16));
        detailsBlock.addChild(stat("World", selectedTarget.worldName(), RIGHT_W - 16));
        right.addChild(detailsBlock);
        right.addChild(spacerY(14));

        if (selectedTarget.kind() == DestinationTargetKind.COORDINATE) {
            GroupBuilder actionsBlock = card(RIGHT_W - 32, 120, ITEM_BG);
            actionsBlock.addChild(label("Coordinate Actions", TITLE, RIGHT_W - 64));
            actionsBlock.addChild(spacerY(12));
            GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
            actionRow.addChild(spacerX(((RIGHT_W - 64) - (170 + 12 + 190)) / 2));
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton().withText("Edit")
                    .withAnchor(new HyUIAnchor().setWidth(170).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        plugin.getTargetSetupDraftService().save(
                            playerRef.getUuid(),
                            new TargetSetupDraft(1, selectedTarget.id(), selectedTarget.displayName(), selectedTarget.arrivalPointId(), selectedTarget.kind().name())
                        );
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withTargetsPanel(TargetsPanel.CREATE)
                                .withTargetStepIndex(TARGET_STEP_DETAILS)
                                .withPendingTargetId(selectedTarget.id())
                                .withPendingTargetDisplayName(selectedTarget.displayName())
                                .withSelectedTarget(selectedTarget.id())
                                .withStatus("")
                        );
                    })
            );
            actionRow.addChild(spacerX(12));
            actionRow.addChild(
                ButtonBuilder.textButton().withText("Remove Target")
                    .withBackground(new HyUIPatchStyle().setColor("#a33f4d"))
                    .withAnchor(new HyUIAnchor().setWidth(190).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        try {
                            plugin.getDestinationTargetService().remove(selectedTarget.id());
                            open(ref, store, playerRef, player, plugin, state.withSelectedTarget("").withStatus("Removed destination target " + selectedTarget.id() + "."));
                        } catch (IOException exception) {
                            open(ref, store, playerRef, player, plugin, state.withStatus("Failed to remove that destination target: " + exception.getMessage()));
                        }
                    })
            );
            actionsBlock.addChild(actionRow);
            right.addChild(actionsBlock);
            return;
        }

        GroupBuilder infoBlock = card(RIGHT_W - 32, selectedTarget.kind() == DestinationTargetKind.PORTAL ? 170 : 140, ITEM_BG);
        infoBlock.addChild(label(selectedTarget.kind() == DestinationTargetKind.PORTAL ? "Portal Target" : "Natural Spawn Target", TITLE, RIGHT_W - 64));
        infoBlock.addChild(spacerY(10));
        infoBlock.addChild(label(
            selectedTarget.kind() == DestinationTargetKind.PORTAL
                ? "Portal targets are created automatically when you place a Nexori portal. Open the portal setup to rename it or change its travel binding."
                : "Natural spawn targets are generated automatically, one per world, and cannot be edited or removed from this screen.",
            MUTED,
            RIGHT_W - 64
        ));
        if (selectedTarget.kind() == DestinationTargetKind.PORTAL) {
            infoBlock.addChild(spacerY(14));
            GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
            actions.addChild(spacerX(((RIGHT_W - 64) - 230) / 2));
            actions.addChild(
                ButtonBuilder.textButton().withText("Open Portal Setup")
                    .withAnchor(new HyUIAnchor().setWidth(230).setHeight(42))
                    .onClick((ignored, ctx) -> openPortalSetupFromTarget(ref, store, playerRef, player, plugin, state, selectedTarget))
            );
            infoBlock.addChild(actions);
        }
        right.addChild(infoBlock);
    }

    private static void buildTargetCreatePanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        GroupBuilder right
    ) {
        int stepIndex = clampTargetStep(state.targetStepIndex());
        String displayName = state.pendingTargetDisplayName();
        boolean editingExistingTarget = !state.pendingTargetId().isBlank();

        right.addChild(label(editingExistingTarget ? "Edit Target" : "Create Target", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        right.addChild(label("Step " + (stepIndex + 1) + " of " + (TARGET_LAST_STEP + 1), INFO, RIGHT_W - 32));
        right.addChild(spacerY(8));

        if (!state.statusText().isBlank()) {
            right.addChild(label(state.statusText(), MUTED, RIGHT_W - 32));
            right.addChild(spacerY(10));
        }

        if (stepIndex == TARGET_STEP_KIND) {
            GroupBuilder typeBlock = card(RIGHT_W - 32, 190, ITEM_BG);
            typeBlock.addChild(label("Target Type", TITLE, RIGHT_W - 64));
            typeBlock.addChild(spacerY(10));
            typeBlock.addChild(stat("Kind", DestinationTargetKind.COORDINATE.displayName(), RIGHT_W - 16));
            typeBlock.addChild(spacerY(8));
            typeBlock.addChild(label(
                editingExistingTarget
                    ? "This target already exists. Saving here keeps the same target id and updates its display name plus the current live position and facing direction."
                    : "Manual target creation in this menu is for coordinate targets. Nexori captures your current live position and facing direction when you save. Portal targets still come from placing a portal in the world.",
                BODY,
                RIGHT_W - 64
            ));
            right.addChild(typeBlock);
        } else if (stepIndex == TARGET_STEP_DETAILS) {
            GroupBuilder detailsBlock = card(RIGHT_W - 32, 210, ITEM_BG);
            GroupBuilder centeredHost = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(170));
            centeredHost.addChild(spacerX((RIGHT_W - 64 - 520) / 2));
            GroupBuilder centered = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(520).setHeight(170));
            centered.addChild(label("Display Name", TITLE, 520));
            centered.addChild(spacerY(10));
            centered.addChild(
                TextFieldBuilder.textInput()
                    .withId(TARGET_DISPLAY_NAME_INPUT_ID)
                    .withValue(displayName)
                    .withPlaceholderText("Mountain overlook")
                    .withMaxLength(120)
                    .withAnchor(new HyUIAnchor().setWidth(520).setHeight(42))
                    .withBackground("#101926")
            );
            centered.addChild(spacerY(10));
            centered.addChild(label(
                "Nexori generates the internal target id automatically. Only the display name matters here.",
                MUTED,
                520
            ));
            centeredHost.addChild(centered);
            detailsBlock.addChild(centeredHost);
            right.addChild(detailsBlock);
        } else {
            GroupBuilder reviewBlock = card(RIGHT_W - 32, 240, ITEM_BG);
            reviewBlock.addChild(label("Review And Save", TITLE, RIGHT_W - 64));
            reviewBlock.addChild(spacerY(10));
            reviewBlock.addChild(stat("Kind", DestinationTargetKind.COORDINATE.displayName(), RIGHT_W - 16));
            if (editingExistingTarget) {
                reviewBlock.addChild(stat("Target ID", state.pendingTargetId(), RIGHT_W - 16));
            }
            reviewBlock.addChild(stat("Display Name", displayName.isBlank() ? "<use generated fallback>" : displayName, RIGHT_W - 16));
            reviewBlock.addChild(stat("World", player == null || player.getWorld() == null ? "<unknown>" : player.getWorld().getName(), RIGHT_W - 16));
            reviewBlock.addChild(spacerY(10));
            reviewBlock.addChild(label("Current live capture", LABEL, RIGHT_W - 64));
            reviewBlock.addChild(spacerY(6));
            reviewBlock.addChild(label(describeTargetCapture(store, ref), BODY, RIGHT_W - 64));
            right.addChild(reviewBlock);
        }

        right.addChild(spacerY(16));
        GroupBuilder actionsBlock = card(RIGHT_W - 32, 118, ITEM_BG);
        actionsBlock.addChild(label("Actions", TITLE, RIGHT_W - 64));
        actionsBlock.addChild(spacerY(12));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
        int actionCount = 2 + (stepIndex > TARGET_STEP_KIND ? 1 : 0);
        int totalWidth = 160 + 12 + (stepIndex == TARGET_LAST_STEP ? 180 : 140) + (stepIndex > TARGET_STEP_KIND ? 12 + 140 : 0);
        actionRow.addChild(spacerX(Math.max(0, ((RIGHT_W - 64) - totalWidth) / 2)));
        if (stepIndex > TARGET_STEP_KIND) {
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText("Back")
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        String currentDisplayName = ctx.getValue(TARGET_DISPLAY_NAME_INPUT_ID, String.class)
                            .orElse(state.pendingTargetDisplayName())
                            .trim();
                        reopenTargetCreate(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state,
                            stepIndex - 1,
                            currentDisplayName
                        );
                    })
            );
            actionRow.addChild(spacerX(12));
        }
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("Cancel")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(42))
                .onClick((ignored, ctx) -> {
                    plugin.getTargetSetupDraftService().clear(playerRef.getUuid());
                    open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state
                            .withTargetsPanel(TargetsPanel.DETAILS)
                            .withTargetStepIndex(0)
                            .withPendingTargetId("")
                            .withPendingTargetDisplayName("")
                            .withStatus("")
                    );
                })
        );
        actionRow.addChild(spacerX(12));
        if (stepIndex == TARGET_LAST_STEP) {
            actionRow.addChild(
                ButtonBuilder.textButton()
                    .withText(editingExistingTarget ? "Save Changes" : "Save Target")
                    .withAnchor(new HyUIAnchor().setWidth(180).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        String currentDisplayName = ctx.getValue(TARGET_DISPLAY_NAME_INPUT_ID, String.class)
                            .orElse(state.pendingTargetDisplayName())
                            .trim();
                        saveInlineTarget(ref, store, playerRef, player, plugin, state, currentDisplayName);
                    })
            );
        } else {
            actionRow.addChild(
                ButtonBuilder.textButton()
                    .withText("Next")
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        String currentDisplayName = ctx.getValue(TARGET_DISPLAY_NAME_INPUT_ID, String.class)
                            .orElse(state.pendingTargetDisplayName())
                            .trim();
                        reopenTargetCreate(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state,
                            stepIndex + 1,
                            currentDisplayName
                        );
                    })
            );
        }
        actionsBlock.addChild(actionRow);
        right.addChild(actionsBlock);
    }

    private static void reopenTargetCreate(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        int stepIndex,
        String displayName
    ) {
        plugin.getTargetSetupDraftService().save(
            playerRef.getUuid(),
            new TargetSetupDraft(clampTargetStep(stepIndex), state.pendingTargetId(), displayName, "", DestinationTargetKind.COORDINATE.name())
        );
        open(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state
                .withTargetsPanel(TargetsPanel.CREATE)
                .withTargetStepIndex(clampTargetStep(stepIndex))
                .withPendingTargetId(state.pendingTargetId())
                .withPendingTargetDisplayName(displayName)
                .withStatus("")
        );
    }

    private static void saveInlineTarget(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        String displayName
    ) {
        try {
            if (player == null || player.getWorld() == null) {
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state
                        .withTargetsPanel(TargetsPanel.CREATE)
                        .withTargetStepIndex(TARGET_STEP_REVIEW)
                        .withPendingTargetDisplayName(displayName)
                        .withStatus("Could not resolve the current world for this target.")
                );
                return;
            }

            String targetId = state.pendingTargetId().isBlank() ? generateCoordinateTargetId(plugin) : state.pendingTargetId();
            DestinationTargetDefinition target = plugin.getDestinationTargetService().upsert(new DestinationTargetDefinition(
                targetId,
                displayName,
                DestinationTargetKind.COORDINATE,
                player.getWorld().getName(),
                "",
                "",
                buildCoordinateTargetMetadata(store, ref)
            ));
            plugin.getTargetSetupDraftService().clear(playerRef.getUuid());
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state
                    .withTargetsPanel(TargetsPanel.DETAILS)
                    .withTargetStepIndex(0)
                    .withPendingTargetId("")
                    .withPendingTargetDisplayName("")
                    .withSelectedTarget(target.id())
                    .withStatus(
                        state.pendingTargetId().isBlank()
                            ? "Saved Nexori destination target " + target.id() + " in world " + target.worldName() + "."
                            : "Updated Nexori destination target " + target.id() + " in world " + target.worldName() + "."
                    )
            );
        } catch (IOException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state
                    .withTargetsPanel(TargetsPanel.CREATE)
                    .withTargetStepIndex(TARGET_STEP_REVIEW)
                    .withPendingTargetId(state.pendingTargetId())
                    .withPendingTargetDisplayName(displayName)
                    .withStatus("Failed to save the Nexori destination target: " + exception.getMessage())
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state
                    .withTargetsPanel(TargetsPanel.CREATE)
                    .withTargetStepIndex(TARGET_STEP_REVIEW)
                    .withPendingTargetId(state.pendingTargetId())
                    .withPendingTargetDisplayName(displayName)
                    .withStatus(exception.getMessage())
            );
        }
    }

    private static void openPortalSetupFromTarget(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        DestinationTargetDefinition selectedTarget
    ) {
        PortalInstanceDefinition portal = plugin.getPortalInstanceService().findByAutoDestinationTargetId(selectedTarget.id()).orElse(null);
        if (portal == null) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Could not resolve the owning portal for this target."));
            return;
        }

        openPortalSetup(ref, store, playerRef, player, plugin, state, portal, selectedTarget.id(), "");
    }

    private static void openPortalSetup(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull State state,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String selectedTargetId,
        @Nonnull String statusText
    ) {
        PortalSetupDraft draft = plugin.getPortalSetupDraftService().find(playerRef.getUuid(), portal.portalId())
            .orElse(new PortalSetupDraft(portal.portalId(), PORTAL_STEP_SELECT_SERVER, "", "", "", portal.displayName()));
        TriggerBindingDefinition binding = plugin.getTriggerBindingService().findPortalCollisionBinding(portal.portalId()).orElse(null);
        List<ConfiguredPeer> trustedPeers = trustedNetworkPeers(plugin);
        String destinationAddress = resolvePortalDestinationAddress(draft.selectedDestinationAddress(), binding, trustedPeers);
        DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(destinationAddress).orElse(null);
        String targetId = resolvePortalTargetId(draft.selectedTargetId(), discovery, destinationAddress, binding);
        TravelProfileType travelProfile = resolvePortalTravelProfile(draft.selectedTravelProfileId(), binding);
        String portalDisplayName = resolvePortalDisplayName(draft.portalDisplayName(), portal);

        open(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state
                .withTab(Tab.TARGETS)
                .withSelectedTarget(selectedTargetId)
                .withTargetsPanel(TargetsPanel.PORTAL_SETUP)
                .withPendingPortalId(portal.portalId())
                .withPortalStepIndex(clampPortalStep(draft.stepIndex()))
                .withPendingPortalDestinationAddress(destinationAddress)
                .withPendingPortalTargetId(targetId)
                .withPendingPortalTravelProfileId(travelProfile.id())
                .withPendingPortalDisplayName(portalDisplayName)
                .withStatus(statusText)
        );
    }

    private static void buildPortalSetupPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        DestinationTargetDefinition selectedTarget,
        GroupBuilder right
    ) {
        PortalInstanceDefinition portal = resolvePortalForSetup(plugin, state, selectedTarget);
        int stepIndex = clampPortalStep(state.portalStepIndex());
        right.addChild(label("Portal Setup", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));

        int detailsHostHeight = bodyHeight(state) - 72;
        int detailsContentHeight = switch (stepIndex) {
            case PORTAL_STEP_SELECT_SERVER -> 420;
            case PORTAL_STEP_SELECT_TARGET -> 520;
            case PORTAL_STEP_SELECT_PROFILE -> 430;
            case PORTAL_STEP_PORTAL_DETAILS -> 440;
            case PORTAL_STEP_REVIEW -> 760;
            default -> 520;
        };
        ReorderableListBuilder detailsHost = scrollList(
            RIGHT_W - 32,
            detailsHostHeight,
            Math.max(detailsHostHeight + 40, detailsContentHeight),
            "portal-setup-details-scroll",
            false
        );

        if (!state.statusText().isBlank()) {
            detailsHost.addChild(label(state.statusText(), MUTED, RIGHT_W - 64));
            detailsHost.addChild(spacerY(10));
        }

        if (portal == null) {
            detailsHost.addChild(label("Could not resolve the Nexori portal behind this target anymore.", MUTED, RIGHT_W - 64));
            right.addChild(detailsHost);
            return;
        }

        TriggerBindingDefinition binding = plugin.getTriggerBindingService().findPortalCollisionBinding(portal.portalId()).orElse(null);
        List<ConfiguredPeer> trustedPeers = trustedNetworkPeers(plugin);
        String destinationAddress = resolvePortalDestinationAddress(state.pendingPortalDestinationAddress(), binding, trustedPeers);
        DiscoveredDestinationTargetSet discovery = plugin.getDiscoveredDestinationTargetCacheService().find(destinationAddress).orElse(null);
        String targetId = resolvePortalTargetId(state.pendingPortalTargetId(), discovery, destinationAddress, binding);
        TravelProfileType travelProfile = resolvePortalTravelProfile(state.pendingPortalTravelProfileId(), binding);
        String portalDisplayName = resolvePortalDisplayName(state.pendingPortalDisplayName(), portal);
        boolean hasSavedBinding = binding != null;
        boolean hasUnsavedDraftChanges = hasSavedBinding && hasUnsavedPortalDraftChanges(
            binding,
            portal,
            destinationAddress,
            targetId,
            travelProfile.id(),
            portalDisplayName
        );

        GroupBuilder portalSummaryBlock = card(RIGHT_W - 32, 132, ITEM_BG);
        portalSummaryBlock.addChild(label("Current Portal", TITLE, RIGHT_W - 64));
        portalSummaryBlock.addChild(spacerY(10));
        portalSummaryBlock.addChild(stat("Display Name", portal.displayName(), RIGHT_W - 16));
        portalSummaryBlock.addChild(stat("World", portal.worldName(), RIGHT_W - 16));
        portalSummaryBlock.addChild(stat("Block", "(" + portal.blockX() + ", " + portal.blockY() + ", " + portal.blockZ() + ")", RIGHT_W - 16));
        portalSummaryBlock.addChild(stat("Travel", portal.enabled() ? "Enabled" : "Disabled", RIGHT_W - 16));
        detailsHost.addChild(portalSummaryBlock);
        detailsHost.addChild(spacerY(14));

        detailsHost.addChild(label("Step " + (stepIndex + 1) + " of " + (PORTAL_LAST_STEP + 1), INFO, RIGHT_W - 64));
        detailsHost.addChild(spacerY(8));
        if (hasUnsavedDraftChanges) {
            detailsHost.addChild(label(
                "Draft changes are not live yet. Players will keep using the saved portal setup until you press Save Portal Setup.",
                BAD,
                RIGHT_W - 64
            ));
            detailsHost.addChild(spacerY(10));
        }

        if (stepIndex == PORTAL_STEP_SELECT_SERVER) {
            GroupBuilder serverBlock = card(RIGHT_W - 32, 185, ITEM_BG);
            serverBlock.addChild(label("Select Destination Server", TITLE, RIGHT_W - 64));
            serverBlock.addChild(spacerY(10));
            serverBlock.addChild(stat("Selected Server", destinationAddress.isBlank() ? "<none>" : destinationAddress, RIGHT_W - 16));
            serverBlock.addChild(spacerY(8));
            serverBlock.addChild(label(
                trustedPeers.isEmpty()
                    ? "Run Initial Setup first so this server can see the trusted network before binding this portal."
                    : "Trusted destination servers in this network: " + trustedPeers.size(),
                MUTED,
                RIGHT_W - 64
            ));
            serverBlock.addChild(spacerY(14));
            GroupBuilder row = centeredActionRow(RIGHT_W - 64, 140, 12, 140);
            row.addChild(
                ButtonBuilder.secondaryTextButton().withText("Prev Server")
                    .withDisabled(trustedPeers.isEmpty())
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex,
                        cyclePortalDestination(trustedPeers, destinationAddress, -1), targetId, travelProfile.id(), portalDisplayName, ""))
            );
            row.addChild(spacerX(12));
            row.addChild(
                ButtonBuilder.textButton().withText("Next Server")
                    .withDisabled(trustedPeers.isEmpty())
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex,
                        cyclePortalDestination(trustedPeers, destinationAddress, 1), "", travelProfile.id(), portalDisplayName, ""))
            );
            serverBlock.addChild(row);
            detailsHost.addChild(serverBlock);
        } else if (stepIndex == PORTAL_STEP_SELECT_TARGET) {
            GroupBuilder targetBlock = card(RIGHT_W - 32, 240, ITEM_BG);
            targetBlock.addChild(label("Discover And Select Target", TITLE, RIGHT_W - 64));
            targetBlock.addChild(spacerY(10));
            targetBlock.addChild(stat("Destination Server", destinationAddress.isBlank() ? "<none>" : destinationAddress, RIGHT_W - 16));
            targetBlock.addChild(stat("Selected Target", targetId.isBlank() ? "<none>" : targetId, RIGHT_W - 16));
            targetBlock.addChild(spacerY(8));
            targetBlock.addChild(label(describePortalTarget(discovery, targetId), MUTED, RIGHT_W - 64));
            targetBlock.addChild(spacerY(8));
            targetBlock.addChild(label(
                discovery == null
                    ? "No discovery cache found yet for this server. Run discovery first."
                    : "Cached " + discovery.targets().size() + " remote target(s) from " + discovery.connectionAddress() + ".",
                MUTED,
                RIGHT_W - 64
            ));
            targetBlock.addChild(spacerY(14));
            GroupBuilder row = centeredActionRow(RIGHT_W - 64, 140, 12, 160, 12, 140);
            row.addChild(
                ButtonBuilder.secondaryTextButton().withText("Prev Target")
                    .withDisabled(discovery == null || discovery.targets().isEmpty())
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex,
                        destinationAddress, cyclePortalTarget(discovery, targetId, -1), travelProfile.id(), portalDisplayName, ""))
            );
            row.addChild(spacerX(12));
            row.addChild(
                ButtonBuilder.secondaryTextButton().withText("Discover Targets")
                    .withDisabled(destinationAddress.isBlank())
                    .withAnchor(new HyUIAnchor().setWidth(160).setHeight(42))
                    .onClick((ignored, ctx) -> discoverPortalTargets(ref, store, playerRef, player, plugin, state, portal, destinationAddress, targetId, travelProfile.id(), portalDisplayName))
            );
            row.addChild(spacerX(12));
            row.addChild(
                ButtonBuilder.textButton().withText("Next Target")
                    .withDisabled(discovery == null || discovery.targets().isEmpty())
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex,
                        destinationAddress, cyclePortalTarget(discovery, targetId, 1), travelProfile.id(), portalDisplayName, ""))
            );
            targetBlock.addChild(row);
            detailsHost.addChild(targetBlock);
        } else if (stepIndex == PORTAL_STEP_SELECT_PROFILE) {
            GroupBuilder profileBlock = card(RIGHT_W - 32, 200, ITEM_BG);
            profileBlock.addChild(label("Choose Travel Profile", TITLE, RIGHT_W - 64));
            profileBlock.addChild(spacerY(10));
            profileBlock.addChild(stat("Selected Profile", travelProfile.displayName(), RIGHT_W - 16));
            profileBlock.addChild(spacerY(8));
            profileBlock.addChild(label(travelProfile.description(), MUTED, RIGHT_W - 64));
            profileBlock.addChild(spacerY(14));
            GroupBuilder row = centeredActionRow(RIGHT_W - 64, 150, 12, 150);
            row.addChild(
                ButtonBuilder.secondaryTextButton().withText("Prev Profile")
                    .withAnchor(new HyUIAnchor().setWidth(150).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex,
                        destinationAddress, targetId, cyclePortalProfile(travelProfile, -1).id(), portalDisplayName, ""))
            );
            row.addChild(spacerX(12));
            row.addChild(
                ButtonBuilder.textButton().withText("Next Profile")
                    .withAnchor(new HyUIAnchor().setWidth(150).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex,
                        destinationAddress, targetId, cyclePortalProfile(travelProfile, 1).id(), portalDisplayName, ""))
            );
            profileBlock.addChild(row);
            detailsHost.addChild(profileBlock);
        } else if (stepIndex == PORTAL_STEP_PORTAL_DETAILS) {
            GroupBuilder detailsBlock = card(RIGHT_W - 32, 210, ITEM_BG);
            GroupBuilder centeredHost = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(170));
            centeredHost.addChild(spacerX((RIGHT_W - 64 - 520) / 2));
            GroupBuilder centered = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(520).setHeight(170));
            centered.addChild(label("Portal Display Name", TITLE, 520));
            centered.addChild(spacerY(10));
            centered.addChild(
                TextFieldBuilder.textInput()
                    .withId(PORTAL_DISPLAY_NAME_INPUT_ID)
                    .withValue(portalDisplayName)
                    .withPlaceholderText("North gate portal")
                    .withMaxLength(120)
                    .withAnchor(new HyUIAnchor().setWidth(520).setHeight(42))
                    .withBackground("#101926")
            );
            centered.addChild(spacerY(10));
            centered.addChild(label("This name is shown to owners in setup screens. Nexori keeps the technical portal target id internal.", MUTED, 520));
            centeredHost.addChild(centered);
            detailsBlock.addChild(centeredHost);
            detailsHost.addChild(detailsBlock);
        } else {
            GroupBuilder reviewBlock = card(RIGHT_W - 32, 240, ITEM_BG);
            reviewBlock.addChild(label("Review And Save", TITLE, RIGHT_W - 64));
            reviewBlock.addChild(spacerY(10));
            reviewBlock.addChild(stat("Destination Server", destinationAddress.isBlank() ? "<not selected>" : destinationAddress, RIGHT_W - 16));
            reviewBlock.addChild(stat("Remote Target", targetId.isBlank() ? "<not selected>" : targetId, RIGHT_W - 16));
            reviewBlock.addChild(stat("Travel Profile", travelProfile.displayName(), RIGHT_W - 16));
            reviewBlock.addChild(stat("Portal Name", portalDisplayName.isBlank() ? "<unnamed portal>" : portalDisplayName, RIGHT_W - 16));
            reviewBlock.addChild(spacerY(8));
            reviewBlock.addChild(label(
                binding == null
                    ? "This portal does not have a saved collision binding yet."
                    : "Current binding: " + binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId(),
                MUTED,
                RIGHT_W - 64
            ));
            detailsHost.addChild(reviewBlock);
            detailsHost.addChild(spacerY(14));

            GroupBuilder dangerBlock = card(RIGHT_W - 32, 120, ITEM_BG);
            dangerBlock.addChild(label("Portal Travel", TITLE, RIGHT_W - 64));
            dangerBlock.addChild(spacerY(12));
            GroupBuilder row = centeredActionRow(RIGHT_W - 64, 220, 12, 200, 12, 210);
            row.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText(portal.enabled() ? "Disable Portal Travel" : "Enable Portal Travel")
                    .withAnchor(new HyUIAnchor().setWidth(220).setHeight(42))
                    .onClick((ignored, ctx) -> togglePortalEnabled(ref, store, playerRef, player, plugin, state, portal, destinationAddress, targetId, travelProfile.id(), readPortalDisplayName(ctx, portalDisplayName)))
            );
            row.addChild(spacerX(12));
            row.addChild(
                ButtonBuilder.textButton()
                    .withText("Clear Binding")
                    .withBackground(new HyUIPatchStyle().setColor("#a33f4d"))
                    .withDisabled(binding == null)
                    .withAnchor(new HyUIAnchor().setWidth(200).setHeight(42))
                    .onClick((ignored, ctx) -> clearPortalBinding(ref, store, playerRef, player, plugin, state, portal, destinationAddress, targetId, travelProfile.id(), readPortalDisplayName(ctx, portalDisplayName)))
            );
            dangerBlock.addChild(row);
            detailsHost.addChild(dangerBlock);
        }

        detailsHost.addChild(spacerY(16));
        GroupBuilder actionsBlock = card(RIGHT_W - 32, 118, ITEM_BG);
        actionsBlock.addChild(label("Actions", TITLE, RIGHT_W - 64));
        actionsBlock.addChild(spacerY(12));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
        int totalWidth = 160 + 12 + (stepIndex == PORTAL_LAST_STEP ? 210 : 140) + (stepIndex > PORTAL_STEP_SELECT_SERVER ? 12 + 140 : 0);
        actionRow.addChild(spacerX(Math.max(0, ((RIGHT_W - 64) - totalWidth) / 2)));
        if (stepIndex > PORTAL_STEP_SELECT_SERVER) {
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText("Back")
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex - 1,
                        destinationAddress, targetId, travelProfile.id(), readPortalDisplayName(ctx, portalDisplayName), ""))
            );
            actionRow.addChild(spacerX(12));
        }
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("Back To Target")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(42))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state
                        .withTargetsPanel(TargetsPanel.DETAILS)
                        .withPendingPortalId("")
                        .withPortalStepIndex(0)
                        .withPendingPortalDestinationAddress("")
                        .withPendingPortalTargetId("")
                        .withPendingPortalTravelProfileId("")
                        .withPendingPortalDisplayName("")
                        .withStatus("")
                ))
        );
        actionRow.addChild(spacerX(12));
        if (stepIndex == PORTAL_LAST_STEP) {
            actionRow.addChild(
                ButtonBuilder.textButton()
                    .withText("Save Portal Setup")
                    .withAnchor(new HyUIAnchor().setWidth(210).setHeight(42))
                    .onClick((ignored, ctx) -> savePortalBinding(ref, store, playerRef, player, plugin, state, portal, destinationAddress, targetId, travelProfile.id(), readPortalDisplayName(ctx, portalDisplayName)))
            );
        } else {
            actionRow.addChild(
                ButtonBuilder.textButton()
                    .withText("Next")
                    .withAnchor(new HyUIAnchor().setWidth(140).setHeight(42))
                    .onClick((ignored, ctx) -> reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, stepIndex + 1,
                        destinationAddress, targetId, travelProfile.id(), readPortalDisplayName(ctx, portalDisplayName), ""))
            );
        }
        actionsBlock.addChild(actionRow);
        detailsHost.addChild(actionsBlock);
        right.addChild(detailsHost);
    }

    private static void reopenPortalSetup(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        PortalInstanceDefinition portal,
        int stepIndex,
        String destinationAddress,
        String targetId,
        String travelProfileId,
        String portalDisplayName,
        String statusText
    ) {
        plugin.getPortalSetupDraftService().save(
            playerRef.getUuid(),
            new PortalSetupDraft(
                portal.portalId(),
                clampPortalStep(stepIndex),
                destinationAddress,
                targetId,
                travelProfileId,
                portalDisplayName
            )
        );
        open(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state
                .withTargetsPanel(TargetsPanel.PORTAL_SETUP)
                .withPendingPortalId(portal.portalId())
                .withPortalStepIndex(clampPortalStep(stepIndex))
                .withPendingPortalDestinationAddress(destinationAddress)
                .withPendingPortalTargetId(targetId)
                .withPendingPortalTravelProfileId(travelProfileId)
                .withPendingPortalDisplayName(portalDisplayName)
                .withSelectedTarget(portal.autoDestinationTargetId())
                .withStatus(statusText)
        );
    }

    private static void discoverPortalTargets(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        PortalInstanceDefinition portal,
        String destinationAddress,
        String targetId,
        String travelProfileId,
        String portalDisplayName
    ) {
        try {
            if (destinationAddress.isBlank()) {
                reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_SELECT_TARGET, destinationAddress, targetId, travelProfileId, portalDisplayName, "Select a destination server first.");
                return;
            }
            Transform transform = captureCurrentTransform(store, ref);
            plugin.getPortalSetupDraftService().save(
                playerRef.getUuid(),
                new PortalSetupDraft(portal.portalId(), PORTAL_STEP_SELECT_TARGET, destinationAddress, targetId, travelProfileId, portalDisplayName)
            );
            plugin.getDestinationTargetDiscoveryService().discover(
                playerRef,
                ConfiguredPeer.parse(destinationAddress),
                player.getWorld().getName(),
                transform,
                (resumeRef, resumeStore, resumePlayerRef, resumePlayer) -> open(
                    resumeRef,
                    resumeStore,
                    resumePlayerRef,
                    resumePlayer,
                    plugin,
                    state
                        .withTab(Tab.TARGETS)
                        .withSelectedTarget(portal.autoDestinationTargetId())
                        .withTargetsPanel(TargetsPanel.PORTAL_SETUP)
                        .withPendingPortalId(portal.portalId())
                        .withPortalStepIndex(PORTAL_STEP_SELECT_TARGET)
                        .withPendingPortalDestinationAddress(destinationAddress)
                        .withPendingPortalTargetId(targetId)
                        .withPendingPortalTravelProfileId(travelProfileId)
                        .withPendingPortalDisplayName(portalDisplayName)
                        .withStatus("Destination targets refreshed from " + destinationAddress + ".")
                )
            );
            player.sendMessage(Message.raw("Nexori is discovering destination targets from " + destinationAddress + "..."));
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_SELECT_TARGET, destinationAddress, targetId, travelProfileId, portalDisplayName, "The Nexori portal discovery failed: " + exception.getMessage());
        }
    }

    private static void savePortalBinding(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        PortalInstanceDefinition portal,
        String destinationAddress,
        String targetId,
        String travelProfileId,
        String portalDisplayName
    ) {
        try {
            if (destinationAddress.isBlank()) {
                reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_REVIEW, destinationAddress, targetId, travelProfileId, portalDisplayName, "Select a destination server first.");
                return;
            }
            if (targetId.isBlank()) {
                reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_REVIEW, destinationAddress, targetId, travelProfileId, portalDisplayName, "Select a discovered destination target first.");
                return;
            }

            PortalInstanceDefinition renamedPortal = plugin.getPortalInstanceService().renamePortalAndTarget(portal.autoDestinationTargetId(), portalDisplayName);
            TriggerBindingDefinition saved = plugin.getTriggerBindingService().bindPortalCollision(
                renamedPortal.portalId(),
                destinationAddress,
                targetId,
                travelProfileId,
                "{}"
            );
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, renamedPortal, PORTAL_STEP_REVIEW,
                saved.destinationConnectionAddress(), saved.destinationTargetId(), saved.travelProfileId(), renamedPortal.displayName(),
                "Saved portal binding to " + saved.destinationConnectionAddress() + " -> " + saved.destinationTargetId() + "."
            );
        } catch (IOException | IllegalArgumentException exception) {
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_REVIEW, destinationAddress, targetId, travelProfileId, portalDisplayName, exception.getMessage());
        }
    }

    private static void clearPortalBinding(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        PortalInstanceDefinition portal,
        String destinationAddress,
        String targetId,
        String travelProfileId,
        String portalDisplayName
    ) {
        try {
            boolean removed = plugin.getTriggerBindingService().removePortalCollisionBinding(portal.portalId());
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_REVIEW,
                destinationAddress, targetId, travelProfileId, portalDisplayName,
                removed ? "Removed the portal binding." : "This portal did not have a saved binding."
            );
        } catch (IOException exception) {
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_REVIEW, destinationAddress, targetId, travelProfileId, portalDisplayName, "Failed to clear the portal binding: " + exception.getMessage());
        }
    }

    private static void togglePortalEnabled(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        PortalInstanceDefinition portal,
        String destinationAddress,
        String targetId,
        String travelProfileId,
        String portalDisplayName
    ) {
        try {
            PortalInstanceDefinition updated = plugin.getPortalInstanceService().setEnabled(portal.portalId(), !portal.enabled());
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, updated, PORTAL_STEP_REVIEW,
                destinationAddress, targetId, travelProfileId, portalDisplayName,
                portal.enabled() ? "Portal travel disabled. Admin setup still works." : "Portal travel enabled."
            );
        } catch (IOException exception) {
            reopenPortalSetup(ref, store, playerRef, player, plugin, state, portal, PORTAL_STEP_REVIEW, destinationAddress, targetId, travelProfileId, portalDisplayName, "Failed to update portal travel state: " + exception.getMessage());
        }
    }

    private static PortalInstanceDefinition resolvePortalForSetup(
        @Nonnull NexoriPlugin plugin,
        @Nonnull State state,
        DestinationTargetDefinition selectedTarget
    ) {
        if (!state.pendingPortalId().isBlank()) {
            PortalInstanceDefinition byId = plugin.getPortalInstanceService().findById(state.pendingPortalId()).orElse(null);
            if (byId != null) {
                return byId;
            }
        }
        if (selectedTarget == null || selectedTarget.kind() != DestinationTargetKind.PORTAL) {
            return null;
        }
        return plugin.getPortalInstanceService().findByAutoDestinationTargetId(selectedTarget.id()).orElse(null);
    }

    @Nonnull
    private static String resolvePortalDestinationAddress(
        @Nonnull String draftDestinationAddress,
        TriggerBindingDefinition binding,
        @Nonnull List<ConfiguredPeer> configuredPeers
    ) {
        if (!draftDestinationAddress.isBlank()) {
            return draftDestinationAddress;
        }
        if (binding != null && !binding.destinationConnectionAddress().isBlank()) {
            return binding.destinationConnectionAddress();
        }
        return configuredPeers.isEmpty() ? "" : configuredPeers.getFirst().connectionAddress();
    }

    @Nonnull
    private static String resolvePortalTargetId(
        @Nonnull String draftTargetId,
        DiscoveredDestinationTargetSet discovery,
        @Nonnull String destinationAddress,
        TriggerBindingDefinition binding
    ) {
        if (!draftTargetId.isBlank()) {
            return draftTargetId;
        }
        if (binding != null
            && !destinationAddress.isBlank()
            && binding.destinationConnectionAddress().equalsIgnoreCase(destinationAddress)
            && !binding.destinationTargetId().isBlank()) {
            return binding.destinationTargetId();
        }
        if (discovery == null || discovery.targets().isEmpty()) {
            return "";
        }
        return discovery.targets().getFirst().id();
    }

    @Nonnull
    private static TravelProfileType resolvePortalTravelProfile(
        @Nonnull String draftTravelProfileId,
        TriggerBindingDefinition binding
    ) {
        if (!draftTravelProfileId.isBlank()) {
            return TravelProfileType.parse(draftTravelProfileId);
        }
        if (binding != null && !binding.travelProfileId().isBlank()) {
            return TravelProfileType.parse(binding.travelProfileId());
        }
        return TravelProfileType.KEEP_INVENTORY;
    }

    @Nonnull
    private static String resolvePortalDisplayName(@Nonnull String draftDisplayName, @Nonnull PortalInstanceDefinition portal) {
        return draftDisplayName.isBlank() ? portal.displayName() : draftDisplayName.trim();
    }

    private static boolean hasUnsavedPortalDraftChanges(
        @Nonnull TriggerBindingDefinition binding,
        @Nonnull PortalInstanceDefinition portal,
        @Nonnull String destinationAddress,
        @Nonnull String targetId,
        @Nonnull String travelProfileId,
        @Nonnull String portalDisplayName
    ) {
        if (!binding.destinationConnectionAddress().equals(destinationAddress)) {
            return true;
        }
        if (!binding.destinationTargetId().equals(targetId)) {
            return true;
        }
        if (!binding.travelProfileId().equals(travelProfileId)) {
            return true;
        }
        return !portal.displayName().equals(portalDisplayName);
    }

    @Nonnull
    private static String describePortalTarget(DiscoveredDestinationTargetSet discovery, @Nonnull String targetId) {
        if (discovery == null || discovery.targets().isEmpty()) {
            return "Discover the destination server to browse its available arrival targets.";
        }
        for (DiscoveredDestinationTargetSummary target : discovery.targets()) {
            if (target.id().equals(targetId)) {
                return target.displayName() + " / " + target.kind() + " / " + target.worldName();
            }
        }
        return targetId.isBlank()
            ? "No discovered target selected yet."
            : targetId + " (not present in the current discovery cache)";
    }

    @Nonnull
    private static String cyclePortalDestination(@Nonnull List<ConfiguredPeer> configuredPeers, @Nonnull String current, int delta) {
        if (configuredPeers.isEmpty()) {
            return "";
        }
        int index = 0;
        for (int i = 0; i < configuredPeers.size(); i++) {
            if (configuredPeers.get(i).connectionAddress().equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        int nextIndex = Math.floorMod(index + delta, configuredPeers.size());
        return configuredPeers.get(nextIndex).connectionAddress();
    }

    @Nonnull
    private static String cyclePortalTarget(DiscoveredDestinationTargetSet discovery, @Nonnull String current, int delta) {
        if (discovery == null || discovery.targets().isEmpty()) {
            return "";
        }
        List<DiscoveredDestinationTargetSummary> targets = discovery.targets();
        int index = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).id().equalsIgnoreCase(current)) {
                index = i;
                break;
            }
        }
        int nextIndex = Math.floorMod(index + delta, targets.size());
        return targets.get(nextIndex).id();
    }

    @Nonnull
    private static TravelProfileType cyclePortalProfile(@Nonnull TravelProfileType current, int delta) {
        TravelProfileType[] values = TravelProfileType.values();
        int nextIndex = Math.floorMod(current.ordinal() + delta, values.length);
        return values[nextIndex];
    }

    private static int clampPortalStep(int stepIndex) {
        return Math.max(PORTAL_STEP_SELECT_SERVER, Math.min(PORTAL_LAST_STEP, stepIndex));
    }

    @Nonnull
    private static String readPortalDisplayName(@Nonnull au.ellie.hyui.events.UIContext ctx, @Nonnull String fallbackValue) {
        return ctx.getValue(PORTAL_DISPLAY_NAME_INPUT_ID, String.class)
            .orElse(fallbackValue)
            .trim();
    }

    @Nonnull
    private static GroupBuilder centeredActionRow(int width, int... segments) {
        int totalWidth = 0;
        for (int segment : segments) {
            totalWidth += segment;
        }
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(42));
        row.addChild(spacerX(Math.max(0, (width - totalWidth) / 2)));
        return row;
    }

    @Nonnull
    private static String describeTargetCapture(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            Transform transform = captureCurrentTransform(store, ref);
            return "Current capture preview: pos=("
                + formatDecimal(transform.getPosition().x) + ", "
                + formatDecimal(transform.getPosition().y) + ", "
                + formatDecimal(transform.getPosition().z) + ") rot=("
                + formatDecimal(transform.getRotation().x) + ", "
                + formatDecimal(transform.getRotation().y) + ", "
                + formatDecimal(transform.getRotation().z) + ")";
        } catch (IllegalStateException exception) {
            return "Could not read the live player position yet.";
        }
    }

    @Nonnull
    private static String buildCoordinateTargetMetadata(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
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

    private static int clampTargetStep(int stepIndex) {
        return Math.max(TARGET_STEP_KIND, Math.min(TARGET_LAST_STEP, stepIndex));
    }

    @Nonnull
    private static String generateCoordinateTargetId(@Nonnull NexoriPlugin plugin) {
        String candidate;
        do {
            candidate = "coordinate." + UUID.randomUUID().toString().substring(0, 8).toLowerCase();
        } while (plugin.getDestinationTargetService().find(candidate).isPresent());
        return candidate;
    }

    @Nonnull
    private static String formatDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private static GroupBuilder rulesBody(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        List<ConfiguredPeer> peers,
        List<ServerEntry> serverEntries,
        List<ServerRuleGroupDefinition> ruleGroups,
        ServerRuleGroupDefinition selectedRuleGroup
    ) {
        int panelHeight = bodyHeight(state);
        int leftListHeight = Math.max(220, panelHeight - 170);
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(panelHeight));

        GroupBuilder left = card(LEFT_W, panelHeight, CARD_BG);
        left.addChild(label("Groups Of Rules", TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        left.addChild(label(
            ruleGroups.isEmpty() ? "No rule groups configured yet." : ruleGroups.size() + " rule group(s) configured.",
            MUTED,
            LEFT_W - 32
        ));
        left.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(38));
        actions.addChild(spacerX(12));
        actions.addChild(
            (state.rulesPanel() == RulesPanel.ADD ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText("Add Group")
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(38))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedRuleGroup("").withRulesPanel(RulesPanel.ADD).withStatus("")))
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            (state.rulesPanel() == RulesPanel.VISUALIZE ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText("Visualize Groups")
                .withAnchor(new HyUIAnchor().setWidth(190).setHeight(38))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedRuleGroup("").withRulesPanel(RulesPanel.VISUALIZE).withStatus("")))
        );
        left.addChild(actions);
        left.addChild(spacerY(12));

        int groupListContentHeight = Math.max(leftListHeight, ruleGroups.isEmpty() ? 120 : ruleGroups.size() * 62 + 24);
        ReorderableListBuilder listHost = scrollList(LEFT_W - 32, leftListHeight, groupListContentHeight, "rule-groups-list", true);
        if (!ruleGroups.isEmpty()) {
            for (ServerRuleGroupDefinition group : ruleGroups) {
                boolean selected = selectedRuleGroup != null && selectedRuleGroup.groupId().equals(group.groupId());
                listHost.addChild(
                    (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                        .withText(group.displayName())
                        .withBackground(selected ? SERVER_BUTTON_SELECTED_BG : SERVER_BUTTON_BG)
                        .withDisabled(selected)
                        .withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(54))
                        .onClick((ignored, ctx) -> open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedRuleGroup(group.groupId()).withRulesPanel(RulesPanel.DETAILS).withStatus("")
                        ))
                );
                listHost.addChild(spacerY(8));
            }
        } else {
            listHost.addChild(spacerY(12));
            listHost.addChild(label("Create a group for adventure servers, minigame servers, or any other ruleset you want to reuse.", MUTED, LEFT_W - 48));
        }
        left.addChild(listHost);

        GroupBuilder right = card(RIGHT_W, panelHeight, CARD_BG);
        if (state.rulesPanel() == RulesPanel.ADD) {
            buildRuleGroupAddPanel(ref, store, playerRef, player, plugin, state, right);
        } else if (state.rulesPanel() == RulesPanel.VISUALIZE) {
            buildRuleGroupVisualizePanel(plugin, ruleGroups, serverEntries, right);
        } else {
            buildRuleGroupDetailsPanel(ref, store, playerRef, player, plugin, state, selectedRuleGroup, ruleGroups, peers, serverEntries, right);
        }

        row.addChild(left);
        row.addChild(spacerX(16));
        row.addChild(right);
        return row;
    }

    private static void buildRuleGroupAddPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        GroupBuilder right
    ) {
        right.addChild(label("Add Group", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        right.addChild(label("Create a reusable set of rules, then assign one or more servers to that group.", MUTED, RIGHT_W - 32));
        right.addChild(spacerY(12));

        GroupBuilder addBlock = card(RIGHT_W - 32, 190, ITEM_BG);
        GroupBuilder centeredHost = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(150));
        centeredHost.addChild(spacerX((RIGHT_W - 64 - 420) / 2));
        GroupBuilder centered = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(420).setHeight(150));
        centered.addChild(label("Group Name", LABEL, 420));
        centered.addChild(spacerY(8));
        centered.addChild(
            TextFieldBuilder.textInput()
                .withId(GROUP_NAME_INPUT_ID)
                .withValue(state.pendingRuleGroupName())
                .withPlaceholderText("Adventure Servers")
                .withMaxLength(80)
                .withAnchor(new HyUIAnchor().setWidth(420).setHeight(42))
                .withBackground("#101926")
        );
        centered.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(420).setHeight(42));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("Save Group")
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(42))
                .onClick((ignored, ctx) -> {
                    String rawGroupName = ctx.getValue(GROUP_NAME_INPUT_ID, String.class).orElse(state.pendingRuleGroupName()).trim();
                    try {
                        ServerRuleGroupDefinition created = plugin.getServerRuleGroupService().create(
                            rawGroupName,
                            plugin.getInventoryTransferService().isRecoveryEnabled(),
                            plugin.getInventoryTransferService().getMaxBackupsPerPlayer()
                        );
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedRuleGroup(created.groupId()).withRulesPanel(RulesPanel.DETAILS).withPendingRuleGroupName("").withStatus("")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withPendingRuleGroupName(rawGroupName).withStatus("Could not create that rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("Cancel")
                .withAnchor(new HyUIAnchor().setWidth(120).setHeight(42))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withRulesPanel(RulesPanel.DETAILS).withPendingRuleGroupName("").withStatus("")))
        );
        centered.addChild(actions);
        centeredHost.addChild(centered);
        addBlock.addChild(centeredHost);
        right.addChild(addBlock);
    }

    private static void buildRuleGroupVisualizePanel(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull List<ServerEntry> serverEntries,
        @Nonnull GroupBuilder right
    ) {
        right.addChild(label("Visualize Groups", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        right.addChild(label("Review how each reusable ruleset is configured and how many servers are currently assigned to it.", MUTED, RIGHT_W - 32));
        right.addChild(spacerY(12));

        int listHeight = 520;
        int contentHeight = Math.max(listHeight, ruleGroups.isEmpty() ? 120 : ruleGroups.size() * 150 + 24);
        ReorderableListBuilder list = scrollList(RIGHT_W - 32, listHeight, contentHeight, "rule-group-visualize-list", false);
        if (ruleGroups.isEmpty()) {
            list.addChild(spacerY(12));
            list.addChild(label("There are no rule groups yet. Create one from the left to start organizing server policies.", MUTED, RIGHT_W - 64));
        } else {
            for (ServerRuleGroupDefinition group : ruleGroups) {
                GroupStatusCounts counts = computeGroupStatusCounts(plugin, group, serverEntries);
                GroupBuilder card = card(RIGHT_W - 48, 132, ITEM_BG);
                card.addChild(label(group.displayName(), TITLE, RIGHT_W - 96));
                card.addChild(spacerY(8));
                card.addChild(label(
                    "Recovery: " + (group.recoveryEnabled() ? "Enabled" : "Disabled")
                        + " | Backup Limit: " + group.maxBackupsPerPlayer() + " | Assigned Servers: " + counts.assigned(),
                    BODY,
                    RIGHT_W - 96
                ));
                card.addChild(spacerY(6));
                card.addChild(label(
                    "Matching: " + counts.matching() + " | Needs Apply: " + counts.needsApply() + " | Unknown: " + counts.unknown(),
                    MUTED,
                    RIGHT_W - 96
                ));
                card.addChild(spacerY(8));
                card.addChild(label(
                    "Servers: " + group.assignedServerKeys().stream()
                        .map(assignedServerKey -> displayServerKey(serverEntries, assignedServerKey))
                        .reduce((left, rightLabel) -> left + ", " + rightLabel)
                        .orElse("<none assigned>"),
                    MUTED,
                    RIGHT_W - 96
                ));
                list.addChild(card);
                list.addChild(spacerY(10));
            }
        }
        right.addChild(list);
    }

    private static void buildRuleGroupDetailsPanel(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerRuleGroupDefinition selectedRuleGroup,
        List<ServerRuleGroupDefinition> ruleGroups,
        List<ConfiguredPeer> peers,
        List<ServerEntry> serverEntries,
        GroupBuilder right
    ) {
        right.addChild(label("Rules Details", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        int detailsHostHeight = 500;
        int detailsContentHeight = Math.max(700, 84 + 12 + 136 + 12 + 304 + 12 + 110 + Math.max(0, serverEntries.size() - 3) * 24);
        ReorderableListBuilder detailsHost = scrollList(RIGHT_W - 32, detailsHostHeight, detailsContentHeight, "rule-group-details-scroll", false);

        if (!state.statusText().isBlank()) {
            detailsHost.addChild(label(state.statusText(), MUTED, RIGHT_W - 64));
            detailsHost.addChild(spacerY(10));
        }

        if (selectedRuleGroup == null) {
            detailsHost.addChild(label("Select a rule group on the left or create one first.", MUTED, RIGHT_W - 64));
            right.addChild(detailsHost);
            return;
        }

        GroupBuilder identityBlock = card(RIGHT_W - 32, 84, ITEM_BG);
        identityBlock.addChild(label("Current Group", TITLE, RIGHT_W - 64));
        identityBlock.addChild(spacerY(10));
        identityBlock.addChild(coloredStat("Name", selectedRuleGroup.displayName(), RIGHT_W - 32, INFO));
        detailsHost.addChild(identityBlock);
        detailsHost.addChild(spacerY(12));

        GroupBuilder rulesBlock = card(RIGHT_W - 32, 132, ITEM_BG);
        rulesBlock.addChild(label("Rules In This Group", TITLE, RIGHT_W - 64));
        rulesBlock.addChild(spacerY(10));
        GroupBuilder recoveryRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(30));
        recoveryRow.addChild(LabelBuilder.label().withText("Recovery").withAnchor(new HyUIAnchor().setWidth(180)).withStyle(LABEL));
        recoveryRow.addChild(LabelBuilder.label().withText(selectedRuleGroup.recoveryEnabled() ? "Enabled" : "Disabled").withAnchor(new HyUIAnchor().setWidth(200)).withStyle(selectedRuleGroup.recoveryEnabled() ? GOOD : BAD));
        recoveryRow.addChild(
            ButtonBuilder.secondaryTextButton().withText("Toggle").withAnchor(new HyUIAnchor().setWidth(120).setHeight(30)).onClick((ignored, ctx) -> {
                try {
                    plugin.getServerRuleGroupService().setRecoveryEnabled(selectedRuleGroup.groupId(), !selectedRuleGroup.recoveryEnabled());
                    open(ref, store, playerRef, player, plugin, state.withStatus(""));
                } catch (IOException exception) {
                    open(ref, store, playerRef, player, plugin, state.withStatus("Could not save that group: " + exception.getMessage()));
                }
            })
        );
        rulesBlock.addChild(recoveryRow);
        rulesBlock.addChild(spacerY(10));
        GroupBuilder backupRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(30));
        backupRow.addChild(LabelBuilder.label().withText("Backup Limit").withAnchor(new HyUIAnchor().setWidth(180)).withStyle(LABEL));
        backupRow.addChild(LabelBuilder.label().withText(selectedRuleGroup.maxBackupsPerPlayer() + " backup(s) per player").withAnchor(new HyUIAnchor().setWidth(280)).withStyle(BODY));
        backupRow.addChild(
            ButtonBuilder.smallSecondaryTextButton().withText("-").withAnchor(new HyUIAnchor().setWidth(42).setHeight(30)).onClick((ignored, ctx) -> {
                try {
                    plugin.getServerRuleGroupService().setMaxBackupsPerPlayer(selectedRuleGroup.groupId(), Math.max(1, selectedRuleGroup.maxBackupsPerPlayer() - 1));
                    open(ref, store, playerRef, player, plugin, state.withStatus(""));
                } catch (IOException exception) {
                    open(ref, store, playerRef, player, plugin, state.withStatus("Could not save that group: " + exception.getMessage()));
                }
            })
        );
        backupRow.addChild(spacerX(8));
        backupRow.addChild(
            ButtonBuilder.smallSecondaryTextButton().withText("+").withAnchor(new HyUIAnchor().setWidth(42).setHeight(30)).onClick((ignored, ctx) -> {
                try {
                    plugin.getServerRuleGroupService().setMaxBackupsPerPlayer(selectedRuleGroup.groupId(), Math.min(50, selectedRuleGroup.maxBackupsPerPlayer() + 1));
                    open(ref, store, playerRef, player, plugin, state.withStatus(""));
                } catch (IOException exception) {
                    open(ref, store, playerRef, player, plugin, state.withStatus("Could not save that group: " + exception.getMessage()));
                }
            })
        );
        rulesBlock.addChild(backupRow);
        detailsHost.addChild(rulesBlock);
        detailsHost.addChild(spacerY(12));

        GroupBuilder assignmentBlock = card(RIGHT_W - 32, 304, ITEM_BG);
        assignmentBlock.addChild(label("Servers In This Group", TITLE, RIGHT_W - 64));
        assignmentBlock.addChild(spacerY(10));
        assignmentBlock.addChild(label("Assign servers here. A server can only belong to one rule group at a time.", MUTED, RIGHT_W - 64));
        assignmentBlock.addChild(spacerY(10));
        Map<String, ServerRuleGroupDefinition> assignments = buildRuleGroupAssignments(ruleGroups);
        int assignmentRows = Math.max(1, serverEntries.size());
        ReorderableListBuilder assignmentList = scrollList(RIGHT_W - 64, 130, Math.max(190, assignmentRows * 32 + 24), "rule-group-server-assignment-list", false);
        for (ServerEntry serverEntry : serverEntries) {
            assignmentList.addChild(ruleGroupAssignmentRow(ref, store, playerRef, player, plugin, state, selectedRuleGroup, serverEntry, assignments));
            assignmentList.addChild(spacerY(8));
        }
        assignmentBlock.addChild(assignmentList);
        assignmentBlock.addChild(spacerY(12));
        GroupBuilder applyActions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
        applyActions.addChild(spacerX(((RIGHT_W - 64) - 260) / 2));
        applyActions.addChild(
            ButtonBuilder.textButton().withText("Apply Group To Assigned").withAnchor(new HyUIAnchor().setWidth(260).setHeight(42)).onClick((ignored, ctx) ->
                applyRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedRuleGroup, peers, serverEntries)
            )
        );
        assignmentBlock.addChild(applyActions);
        detailsHost.addChild(assignmentBlock);
        detailsHost.addChild(spacerY(12));

        GroupStatusCounts counts = computeGroupStatusCounts(plugin, selectedRuleGroup, serverEntries);
        GroupBuilder actionsBlock = card(RIGHT_W - 32, 110, ITEM_BG);
        actionsBlock.addChild(label("Status Of Rules In Other Servers", TITLE, RIGHT_W - 64));
        actionsBlock.addChild(spacerY(10));
        actionsBlock.addChild(label(
            "Assigned: " + counts.assigned() + " | Matching: " + counts.matching() + " | Needs Apply: " + counts.needsApply() + " | Unknown: " + counts.unknown(),
            MUTED,
            RIGHT_W - 64
        ));
        actionsBlock.addChild(spacerY(12));
        GroupBuilder refreshActions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
        refreshActions.addChild(spacerX(((RIGHT_W - 64) - 210) / 2));
        refreshActions.addChild(
            ButtonBuilder.textButton().withText("Refresh Status").withAnchor(new HyUIAnchor().setWidth(210).setHeight(42)).onClick((ignored, ctx) ->
                refreshRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedRuleGroup, peers, serverEntries)
            )
        );
        actionsBlock.addChild(refreshActions);
        detailsHost.addChild(actionsBlock);
        right.addChild(detailsHost);
        right.addChild(spacerY(12));
        GroupBuilder removeRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 32).setHeight(42));
        removeRow.addChild(spacerX(((RIGHT_W - 32) - 170) / 2));
        removeRow.addChild(
            ButtonBuilder.textButton()
                .withText("Remove Group")
                .withBackground(new HyUIPatchStyle().setColor("#a33f4d"))
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(42))
                .onClick((ignored, ctx) -> {
                    try {
                        plugin.getServerRuleGroupService().remove(selectedRuleGroup.groupId());
                        open(ref, store, playerRef, player, plugin, state.withSelectedRuleGroup("").withRulesPanel(RulesPanel.VISUALIZE).withStatus(""));
                    } catch (IOException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatus("Could not remove that group: " + exception.getMessage()));
                    }
                })
        );
        right.addChild(removeRow);
    }

    private static GroupBuilder ruleGroupAssignmentRow(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerRuleGroupDefinition selectedRuleGroup,
        ServerEntry serverEntry,
        Map<String, ServerRuleGroupDefinition> assignments
    ) {
        ServerRuleGroupDefinition owningGroup = assignments.get(serverEntry.selectionKey());
        boolean assignedHere = owningGroup != null && owningGroup.groupId().equals(selectedRuleGroup.groupId());
        boolean assignedElsewhere = owningGroup != null && !owningGroup.groupId().equals(selectedRuleGroup.groupId());

        String statusText = assignedHere
            ? "In This Group"
            : assignedElsewhere
                ? "Used By " + owningGroup.displayName()
                : "Available";
        HyUIStyle statusStyle = assignedHere ? GOOD : assignedElsewhere ? BAD : MUTED;

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 80).setHeight(28));
        row.addChild(
            (assignedHere ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText(serverEntry.displayAddress())
                .withBackground(assignedHere ? SERVER_BUTTON_SELECTED_BG : SERVER_BUTTON_BG)
                .withDisabled(assignedElsewhere)
                .withAnchor(new HyUIAnchor().setWidth(520).setHeight(28))
                .onClick((ignored, ctx) -> {
                    try {
                        if (assignedHere) {
                            plugin.getServerRuleGroupService().unassignServer(selectedRuleGroup.groupId(), serverEntry.selectionKey());
                            open(
                                ref,
                                store,
                                playerRef,
                                player,
                                plugin,
                                state.withStatus("Removed " + serverEntry.displayAddress() + " from '" + selectedRuleGroup.displayName() + "'.")
                            );
                        } else {
                            plugin.getServerRuleGroupService().assignServer(selectedRuleGroup.groupId(), serverEntry.selectionKey());
                            open(
                                ref,
                                store,
                                playerRef,
                                player,
                                plugin,
                                state.withStatus("Assigned " + serverEntry.displayAddress() + " to '" + selectedRuleGroup.displayName() + "'.")
                            );
                        }
                    } catch (IOException | IllegalStateException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatus("Could not update that assignment: " + exception.getMessage()));
                    }
                })
        );
        row.addChild(spacerX(12));
        row.addChild(LabelBuilder.label().withText(statusText).withAnchor(new HyUIAnchor().setWidth(340)).withStyle(statusStyle));
        return row;
    }

    private static void applyRuleGroupAssignments(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerRuleGroupDefinition group,
        List<ConfiguredPeer> peers,
        List<ServerEntry> serverEntries
    ) {
        ServerRuleGroupDefinition currentGroup = resolveCurrentRuleGroup(plugin, state, group);
        List<ServerEntry> assignedServers = serverEntries.stream()
            .filter(serverEntry -> currentGroup.containsServer(serverEntry.selectionKey()))
            .toList();
        if (assignedServers.isEmpty()) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Assign at least one server to '" + currentGroup.displayName() + "' before you apply it."));
            return;
        }

        try {
            if (currentGroup.containsServer(LOCAL_SERVER_KEY)) {
                plugin.getInventoryTransferService().setRecoveryEnabled(currentGroup.recoveryEnabled());
                plugin.getInventoryTransferService().setMaxBackupsPerPlayer(currentGroup.maxBackupsPerPlayer());
            }
        } catch (IOException exception) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Could not apply the local server rules: " + exception.getMessage()));
            return;
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        List<String> unresolvedRemoteServers = new ArrayList<>();
        java.util.LinkedHashMap<String, ConfiguredPeer> trustedPeersByAddress = new java.util.LinkedHashMap<>();
        for (ConfiguredPeer trustedPeer : trustedNetworkPeers(plugin)) {
            trustedPeersByAddress.putIfAbsent(trustedPeer.connectionAddress(), trustedPeer);
        }
        for (ServerEntry serverEntry : assignedServers) {
            if (serverEntry.local() || serverEntry.connectionAddress().isBlank()) {
                continue;
            }
            ConfiguredPeer configuredPeer = trustedPeersByAddress.get(serverEntry.connectionAddress());
            if (configuredPeer != null) {
                remotePeers.add(configuredPeer);
            } else {
                unresolvedRemoteServers.add(serverEntry.displayAddress());
            }
        }

        if (!unresolvedRemoteServers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withStatus(
                    "These assigned servers are not in this server's trusted network yet: "
                        + String.join(", ", unresolvedRemoteServers)
                        + ". Run Initial Setup again if needed before applying this rule group remotely."
                )
            );
            return;
        }

        if (remotePeers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withStatus("Applied '" + currentGroup.displayName() + "' to the local server. No remote servers are assigned to this group yet.")
            );
            return;
        }

        player.sendMessage(Message.raw("Nexori is applying '" + currentGroup.displayName() + "' to " + remotePeers.size() + " remote server(s)..."));
        applyRuleGroupAssignmentAtIndex(ref, store, playerRef, player, plugin, state, currentGroup, remotePeers, 0, 0);
    }

    private static void applyRuleGroupAssignmentAtIndex(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerRuleGroupDefinition group,
        List<ConfiguredPeer> remotePeers,
        int index,
        int appliedCount
    ) {
        if (index >= remotePeers.size()) {
            open(ref, store, playerRef, player, plugin, state.withStatus(""));
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        try {
            plugin.getServerPolicySyncService().apply(
                playerRef,
                destination,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                group.recoveryEnabled(),
                group.maxBackupsPerPlayer(),
                (resumeRef, resumeStore, resumePlayerRef, resumePlayer) -> applyRuleGroupAssignmentAtIndex(
                    resumeRef,
                    resumeStore,
                    resumePlayerRef,
                    resumePlayer,
                    plugin,
                    state,
                    group,
                    remotePeers,
                    index + 1,
                    appliedCount + 1
                )
            );
        } catch (GeneralSecurityException | IOException exception) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Could not start secure apply for " + destination.connectionAddress() + ": " + exception.getMessage()));
        }
    }

    private static void refreshRuleGroupAssignments(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerRuleGroupDefinition group,
        List<ConfiguredPeer> peers,
        List<ServerEntry> serverEntries
    ) {
        ServerRuleGroupDefinition currentGroup = resolveCurrentRuleGroup(plugin, state, group);
        List<ServerEntry> assignedRemoteServers = serverEntries.stream()
            .filter(serverEntry -> currentGroup.containsServer(serverEntry.selectionKey()))
            .filter(serverEntry -> !serverEntry.local() && !serverEntry.connectionAddress().isBlank())
            .toList();
        if (assignedRemoteServers.isEmpty()) {
            open(ref, store, playerRef, player, plugin, state.withStatus("There are no remote servers assigned to '" + currentGroup.displayName() + "' yet."));
            return;
        }

        java.util.LinkedHashMap<String, ConfiguredPeer> trustedPeersByAddress = new java.util.LinkedHashMap<>();
        for (ConfiguredPeer trustedPeer : trustedNetworkPeers(plugin)) {
            trustedPeersByAddress.putIfAbsent(trustedPeer.connectionAddress(), trustedPeer);
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        List<String> unresolvedRemoteServers = new ArrayList<>();
        for (ServerEntry serverEntry : assignedRemoteServers) {
            ConfiguredPeer trustedPeer = trustedPeersByAddress.get(serverEntry.connectionAddress());
            if (trustedPeer != null) {
                remotePeers.add(trustedPeer);
            } else {
                unresolvedRemoteServers.add(serverEntry.displayAddress());
            }
        }

        if (!unresolvedRemoteServers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withStatus(
                    "These assigned remote servers are not in this server's trusted network yet: "
                        + String.join(", ", unresolvedRemoteServers)
                        + ". Run Initial Setup again if needed before refreshing this rule group."
                )
            );
            return;
        }

        player.sendMessage(Message.raw("Nexori is refreshing the cached rule status for '" + currentGroup.displayName() + "'..."));
        refreshRuleGroupAssignmentAtIndex(ref, store, playerRef, player, plugin, state, currentGroup, remotePeers, 0);
    }

    private static void refreshRuleGroupAssignmentAtIndex(
        Ref<EntityStore> ref,
        Store<EntityStore> store,
        PlayerRef playerRef,
        Player player,
        NexoriPlugin plugin,
        State state,
        ServerRuleGroupDefinition group,
        List<ConfiguredPeer> remotePeers,
        int index
    ) {
        if (index >= remotePeers.size()) {
            open(ref, store, playerRef, player, plugin, state.withStatus(""));
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        try {
            plugin.getServerPolicySyncService().refresh(
                playerRef,
                destination,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                (resumeRef, resumeStore, resumePlayerRef, resumePlayer) -> refreshRuleGroupAssignmentAtIndex(
                    resumeRef,
                    resumeStore,
                    resumePlayerRef,
                    resumePlayer,
                    plugin,
                    state,
                    group,
                    remotePeers,
                    index + 1
                )
            );
        } catch (GeneralSecurityException | IOException exception) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Could not refresh " + destination.connectionAddress() + ": " + exception.getMessage()));
        }
    }

    private static GroupStatusCounts computeGroupStatusCounts(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ServerEntry> serverEntries
    ) {
        int assigned = 0;
        int matching = 0;
        int needsApply = 0;
        int unknown = 0;
        for (String assignedServerKey : group.assignedServerKeys()) {
            assigned++;
            ServerEntry serverEntry = serverEntries.stream()
                .filter(entry -> entry.selectionKey().equals(assignedServerKey))
                .findFirst()
                .orElse(null);
            if (serverEntry == null) {
                unknown++;
                continue;
            }

            if (serverEntry.local()) {
                if (matchesPolicy(group, plugin.getInventoryTransferService().isRecoveryEnabled(), plugin.getInventoryTransferService().getMaxBackupsPerPlayer())) {
                    matching++;
                } else {
                    needsApply++;
                }
                continue;
            }

            ServerPolicySummary cached = plugin.getServerPolicyCacheService().find(serverEntry.connectionAddress()).orElse(null);
            if (cached == null) {
                unknown++;
            } else if (matchesPolicy(group, cached.recoveryEnabled(), cached.maxBackupsPerPlayer())) {
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
    private static Map<String, ServerRuleGroupDefinition> buildRuleGroupAssignments(@Nonnull List<ServerRuleGroupDefinition> ruleGroups) {
        java.util.LinkedHashMap<String, ServerRuleGroupDefinition> assignments = new java.util.LinkedHashMap<>();
        for (ServerRuleGroupDefinition group : ruleGroups) {
            for (String assignedServerKey : group.assignedServerKeys()) {
                assignments.put(assignedServerKey, group);
            }
        }
        return assignments;
    }

    @Nonnull
    private static String displayServerKey(@Nonnull List<ServerEntry> serverEntries, @Nonnull String assignedServerKey) {
        for (ServerEntry serverEntry : serverEntries) {
            if (serverEntry.selectionKey().equals(assignedServerKey)) {
                return serverEntry.displayAddress();
            }
        }
        return assignedServerKey;
    }

    private static GroupBuilder simpleTwoCol(String leftTitle, String leftText, String rightTitle, String rightText, ButtonBuilder button, int panelHeight) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(panelHeight));
        GroupBuilder left = card(LEFT_W, panelHeight, CARD_BG);
        left.addChild(label(leftTitle, TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        left.addChild(label(leftText, MUTED, LEFT_W - 32));
        GroupBuilder right = card(RIGHT_W, panelHeight, CARD_BG);
        right.addChild(label(rightTitle, TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        right.addChild(label(rightText, BODY, RIGHT_W - 32));
        right.addChild(spacerY(14));
        right.addChild(button);
        row.addChild(left);
        row.addChild(spacerX(16));
        row.addChild(right);
        return row;
    }

    private static GroupBuilder card(int width, int height, HyUIPatchStyle bg) {
        return GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(height)).withPadding(HyUIPadding.all(16)).withBackground(bg);
    }

    private static ReorderableListBuilder scrollList(int width, int height, int contentHeight, String id, boolean keepScrollPosition) {
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

    private static int bodyHeight(@Nonnull State state) {
        return 640;
    }

    private static LabelBuilder label(String text, HyUIStyle style, int width) {
        return LabelBuilder.label().withText(text).withAnchor(new HyUIAnchor().setWidth(width)).withStyle(style);
    }

    private static GroupBuilder stat(String label, String value, int width) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(24));
        row.addChild(LabelBuilder.label().withText(label).withAnchor(new HyUIAnchor().setWidth(180)).withStyle(LABEL));
        row.addChild(LabelBuilder.label().withText(value).withAnchor(new HyUIAnchor().setWidth(width - 214)).withStyle(BODY));
        return row;
    }

    private static GroupBuilder coloredStat(String label, String value, int width, HyUIStyle valueStyle) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(24));
        row.addChild(LabelBuilder.label().withText(label).withAnchor(new HyUIAnchor().setWidth(180)).withStyle(LABEL));
        row.addChild(LabelBuilder.label().withText(value).withAnchor(new HyUIAnchor().setWidth(width - 214)).withStyle(valueStyle));
        return row;
    }

    private static GroupBuilder secureLinkRow(String connectionLabel, boolean secure) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 80).setHeight(24));
        row.addChild(LabelBuilder.label().withText(connectionLabel).withAnchor(new HyUIAnchor().setWidth(470)).withStyle(BODY));
        row.addChild(LabelBuilder.label().withText(secure ? "Secure" : "Run Initial Setup Again").withAnchor(new HyUIAnchor().setWidth(220)).withStyle(secure ? GOOD : BAD));
        return row;
    }

    private static GroupBuilder spacerX(int width) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(width).setHeight(1));
    }

    private static GroupBuilder spacerY(int height) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(1).setHeight(height));
    }

    private static ConfiguredPeer findSelectedPeer(List<ConfiguredPeer> peers, String connectionAddress) {
        for (ConfiguredPeer peer : peers) {
            if (peer.connectionAddress().equals(connectionAddress)) {
                return peer;
            }
        }
        return null;
    }

    private static List<ServerEntry> buildServerEntries(@Nonnull NexoriPlugin plugin, @Nonnull List<ConfiguredPeer> peers) {
        List<ServerEntry> entries = new ArrayList<>();
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        String localServerId = localServerId(plugin);
        java.util.LinkedHashMap<String, ServerEntry> entriesBySelectionKey = new java.util.LinkedHashMap<>();
        for (BundleMember member : bundle.members()) {
            if (member.connectionAddress() == null || member.connectionAddress().isBlank()) {
                continue;
            }
            boolean local = localServerId.equals(member.serverId());
            boolean configured = peers.stream().anyMatch(peer -> peer.connectionAddress().equalsIgnoreCase(member.connectionAddress()));
            String selectionKey = local ? LOCAL_SERVER_KEY : member.connectionAddress();
            entriesBySelectionKey.putIfAbsent(selectionKey, new ServerEntry(
                selectionKey,
                member.connectionAddress(),
                parseHost(member.connectionAddress()),
                parsePortText(member.connectionAddress()),
                member.connectionAddress(),
                local,
                configured
            ));
        }
        entries.addAll(entriesBySelectionKey.values());
        return entries;
    }

    @Nonnull
    private static String parseHost(@Nonnull String connectionAddress) {
        int colon = connectionAddress.lastIndexOf(':');
        return colon <= 0 ? connectionAddress : connectionAddress.substring(0, colon);
    }

    @Nonnull
    private static String parsePortText(@Nonnull String connectionAddress) {
        int colon = connectionAddress.lastIndexOf(':');
        return colon <= 0 || colon + 1 >= connectionAddress.length()
            ? "<unknown>"
            : connectionAddress.substring(colon + 1);
    }

    private static String normalizeSelectedPeerAddress(@Nonnull String selectedPeerAddress, @Nonnull List<ServerEntry> serverEntries) {
        if (serverEntries.isEmpty()) {
            return "";
        }
        if (selectedPeerAddress.isBlank()) {
            return serverEntries.getFirst().selectionKey();
        }
        for (ServerEntry entry : serverEntries) {
            if (entry.selectionKey().equals(selectedPeerAddress) || (!entry.connectionAddress().isBlank() && entry.connectionAddress().equalsIgnoreCase(selectedPeerAddress))) {
                return entry.selectionKey();
            }
        }
        return serverEntries.getFirst().selectionKey();
    }

    private static String normalizeSelectedRuleGroupId(@Nonnull String selectedRuleGroupId, @Nonnull List<ServerRuleGroupDefinition> ruleGroups) {
        if (selectedRuleGroupId.isBlank()) {
            return ruleGroups.isEmpty() ? "" : ruleGroups.getFirst().groupId();
        }
        for (ServerRuleGroupDefinition group : ruleGroups) {
            if (group.groupId().equalsIgnoreCase(selectedRuleGroupId)) {
                return group.groupId();
            }
        }
        return ruleGroups.isEmpty() ? "" : ruleGroups.getFirst().groupId();
    }

    private static String normalizeSelectedTargetId(@Nonnull String selectedTargetId, @Nonnull List<DestinationTargetDefinition> targets) {
        if (selectedTargetId.isBlank()) {
            return targets.isEmpty() ? "" : targets.getFirst().id();
        }
        for (DestinationTargetDefinition target : targets) {
            if (target.id().equalsIgnoreCase(selectedTargetId)) {
                return target.id();
            }
        }
        return targets.isEmpty() ? "" : targets.getFirst().id();
    }

    private static ServerEntry findSelectedServer(@Nonnull List<ServerEntry> serverEntries, @Nonnull String selectionKey) {
        for (ServerEntry entry : serverEntries) {
            if (entry.selectionKey().equals(selectionKey)) {
                return entry;
            }
        }
        return serverEntries.isEmpty() ? null : serverEntries.getFirst();
    }

    private static ServerRuleGroupDefinition findSelectedRuleGroup(@Nonnull List<ServerRuleGroupDefinition> ruleGroups, @Nonnull String groupId) {
        for (ServerRuleGroupDefinition group : ruleGroups) {
            if (group.groupId().equalsIgnoreCase(groupId)) {
                return group;
            }
        }
        return ruleGroups.isEmpty() ? null : ruleGroups.getFirst();
    }

    @Nonnull
    private static ServerRuleGroupDefinition resolveCurrentRuleGroup(
        @Nonnull NexoriPlugin plugin,
        @Nonnull State state,
        @Nonnull ServerRuleGroupDefinition fallback
    ) {
        return plugin.getServerRuleGroupService()
            .find(state.selectedRuleGroupId())
            .orElse(fallback);
    }

    private static DestinationTargetDefinition findSelectedTarget(@Nonnull List<DestinationTargetDefinition> targets, @Nonnull String targetId) {
        for (DestinationTargetDefinition target : targets) {
            if (target.id().equalsIgnoreCase(targetId)) {
                return target;
            }
        }
        return targets.isEmpty() ? null : targets.getFirst();
    }

    private static boolean isLocalAddress(@Nonnull NexoriPlugin plugin, @Nonnull String connectionAddress) {
        String localAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();
        return !localAddress.isBlank() && localAddress.equalsIgnoreCase(connectionAddress);
    }

    private static Transform captureCurrentTransform(Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            throw new IllegalStateException("Could not read the live player position for Nexori rules sync.");
        }
        Vector3f rotation = transformComponent.getRotation();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            rotation = headRotation.getRotation();
        }
        return new Transform(transformComponent.getPosition(), rotation);
    }

    private static SetupReport buildSetupReport(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers
    ) {
        BootstrapState bootstrapState = plugin.getBootstrapStateStore().getCurrentState();
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        String localServerId = localServerId(plugin);
        boolean hasActiveTrustedNetwork = bundle.bundleVersion() > 0
            && !bundle.bundleHash().isBlank()
            && !bundle.members().isEmpty();
        String localConnectionAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();

        if (!hasActiveTrustedNetwork && peers.isEmpty()) {
            return new SetupReport(
                "Waiting For Bootstrap Peers",
                "This server does not have an active trusted network yet, and its local Bootstrap Peers list is empty.",
                "Add every server that should be part of the secure network here, including the server you are on now, then run Initial Setup.",
                false,
                BAD
            );
        }

        if (bootstrapState.hasActiveSession()) {
            return new SetupReport(
                "Running Now",
                "Nexori is currently verifying trusted servers and installing the active trust bundle.",
                "Wait for the run to finish, then reopen this page to confirm the result.",
                true,
                INFO
            );
        }

        if (bootstrapState.lastRunFailed() && !bootstrapState.lastRunMessage().isBlank()) {
            return new SetupReport(
                "Last Run Failed",
                bootstrapState.lastRunMessage(),
                hasActiveTrustedNetwork
                    ? "The active trusted network on this server was not replaced. Update Bootstrap Peers if needed, then run Initial Setup again from a verified server in that network."
                    : "No trusted network was installed on this server. Update Bootstrap Peers if needed, then run Initial Setup again.",
                false,
                BAD
            );
        }

        boolean localIncluded = bundle.members().stream().anyMatch(member -> localServerId.equals(member.serverId()));
        List<String> activeAddresses = bundle.members().stream()
            .map(BundleMember::connectionAddress)
            .filter(address -> address != null && !address.isBlank())
            .toList();
        List<String> bootstrapAddresses = peers.stream()
            .map(ConfiguredPeer::connectionAddress)
            .toList();
        List<String> missingFromActiveNetwork = peers.stream()
            .filter(peer -> activeAddresses.stream().noneMatch(address -> peer.connectionAddress().equalsIgnoreCase(address)))
            .map(ConfiguredPeer::connectionAddress)
            .toList();
        List<String> missingFromBootstrapPeers = activeAddresses.stream()
            .filter(address -> bootstrapAddresses.stream().noneMatch(peerAddress -> address.equalsIgnoreCase(peerAddress)))
            .toList();
        boolean currentServerMissing = !localConnectionAddress.isBlank()
            && peers.stream().noneMatch(peer -> localConnectionAddress.equalsIgnoreCase(peer.connectionAddress()));

        if (!hasActiveTrustedNetwork) {
            return new SetupReport(
                "First Setup Required",
                "This server does not have its first confirmed Nexori trust bundle yet. The Bootstrap Peers list on the right is only the setup input for this server, and the first successful run must include every server that should belong to the secure network, including this one.",
                "Nexori can contact the other servers first, but it cannot install the first bundle until this server is also listed in Bootstrap Peers. If the current server is missing, the run stops before the trusted network is created.",
                false,
                BAD
            );
        }

        if (!localIncluded) {
            return new SetupReport(
                "Not Completed",
                "This server does not have a confirmed Nexori trust bundle yet.",
                "Run Initial Setup after you finish adding every server that should belong to this secure network.",
                false,
                BAD
            );
        }

        if (peers.isEmpty()) {
            return new SetupReport(
                "Completed",
                "This server already has the active Nexori trust bundle installed, but its local Bootstrap Peers list is empty.",
                "Bundle v" + bundle.bundleVersion() + " was last updated at " + TIME_FORMAT.format(Instant.ofEpochMilli(bundle.updatedAtEpochMillis()))
                    + ". If you want to update the network from here, add every server for the next secure network again, including this one.",
                false,
                GOOD
            );
        }

        if (currentServerMissing) {
            return new SetupReport(
                "Bootstrap Peers Missing This Server",
                "The active trusted network still works, but this local Bootstrap Peers list does not include the current server address " + localConnectionAddress + ".",
                "Add this server here if you want this server's next setup input to fully mirror the network you expect to rebuild.",
                false,
                INFO
            );
        }

        if (!missingFromActiveNetwork.isEmpty() || !missingFromBootstrapPeers.isEmpty()) {
            StringBuilder followUp = new StringBuilder("Run Initial Setup again to rebuild the trusted network from this server's Bootstrap Peers list.");
            if (!missingFromActiveNetwork.isEmpty()) {
                followUp.append(" New here: ").append(String.join(", ", missingFromActiveNetwork)).append('.');
            }
            if (!missingFromBootstrapPeers.isEmpty()) {
                followUp.append(" Missing here: ").append(String.join(", ", missingFromBootstrapPeers)).append('.');
            }
            return new SetupReport(
                "Needs Re-run",
                "The local Bootstrap Peers list on this server does not match the active trusted network yet.",
                followUp.toString(),
                false,
                BAD
            );
        }

        return new SetupReport(
            "Completed",
            "The local Bootstrap Peers list on this server matches the active trusted network. Nexori is ready for secure travel.",
            "Bundle v" + bundle.bundleVersion() + " was last updated at " + TIME_FORMAT.format(Instant.ofEpochMilli(bundle.updatedAtEpochMillis())) + ".",
            false,
            GOOD
        );
    }

    private static boolean isTrustedMember(@Nonnull TrustBundle bundle, @Nonnull String connectionAddress) {
        return bundle.members().stream().anyMatch(member -> connectionAddress.equalsIgnoreCase(member.connectionAddress()));
    }

    private static boolean isTrustedEntry(@Nonnull NexoriPlugin plugin, @Nonnull TrustBundle bundle, @Nonnull ServerEntry entry) {
        if (entry.local()) {
            String localServerId = localServerId(plugin);
            return bundle.members().stream().anyMatch(member -> localServerId.equals(member.serverId()) && member.connectionAddress() != null && !member.connectionAddress().isBlank());
        }
        return !entry.connectionAddress().isBlank() && isTrustedMember(bundle, entry.connectionAddress());
    }

    @Nonnull
    private static List<ConfiguredPeer> trustedNetworkPeers(@Nonnull NexoriPlugin plugin) {
        String localServerId = localServerId(plugin);
        java.util.LinkedHashMap<String, ConfiguredPeer> peersByAddress = new java.util.LinkedHashMap<>();
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
    private static Optional<BundleMember> findLocalBundleMember(@Nonnull NexoriPlugin plugin, @Nonnull TrustBundle bundle) {
        String localServerId = localServerId(plugin);
        return bundle.members().stream()
            .filter(member -> localServerId.equals(member.serverId()))
            .findFirst();
    }

    @Nonnull
    private static String localServerId(@Nonnull NexoriPlugin plugin) {
        return plugin.getLocalIdentity().serverId().toString();
    }

    public record State(
        @Nonnull Tab tab,
        @Nonnull String selectedPeerAddress,
        @Nonnull String selectedRuleGroupId,
        @Nonnull String selectedTargetId,
        @Nonnull String statusText,
        @Nonnull ServersPanel serversPanel,
        @Nonnull RulesPanel rulesPanel,
        @Nonnull TargetsPanel targetsPanel,
        @Nonnull String pendingServerAddress,
        @Nonnull String pendingRuleGroupName,
        int targetStepIndex,
        @Nonnull String pendingTargetDisplayName,
        @Nonnull String pendingTargetId,
        int portalStepIndex,
        @Nonnull String pendingPortalId,
        @Nonnull String pendingPortalDestinationAddress,
        @Nonnull String pendingPortalTargetId,
        @Nonnull String pendingPortalTravelProfileId,
        @Nonnull String pendingPortalDisplayName
    ) {
        public State withTab(@Nonnull Tab tab) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withSelectedPeer(@Nonnull String selectedPeerAddress) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withSelectedRuleGroup(@Nonnull String selectedRuleGroupId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withSelectedTarget(@Nonnull String selectedTargetId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withStatus(@Nonnull String statusText) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withServersPanel(@Nonnull ServersPanel serversPanel) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withRulesPanel(@Nonnull RulesPanel rulesPanel) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withTargetsPanel(@Nonnull TargetsPanel targetsPanel) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingServerAddress(@Nonnull String pendingServerAddress) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingRuleGroupName(@Nonnull String pendingRuleGroupName) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withTargetStepIndex(int targetStepIndex) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingTargetDisplayName(@Nonnull String pendingTargetDisplayName) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingTargetId(@Nonnull String pendingTargetId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPortalStepIndex(int portalStepIndex) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingPortalId(@Nonnull String pendingPortalId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingPortalDestinationAddress(@Nonnull String pendingPortalDestinationAddress) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingPortalTargetId(@Nonnull String pendingPortalTargetId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingPortalTravelProfileId(@Nonnull String pendingPortalTravelProfileId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
        public State withPendingPortalDisplayName(@Nonnull String pendingPortalDisplayName) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, selectedTargetId, statusText, serversPanel, rulesPanel, targetsPanel, pendingServerAddress, pendingRuleGroupName, targetStepIndex, pendingTargetDisplayName, pendingTargetId, portalStepIndex, pendingPortalId, pendingPortalDestinationAddress, pendingPortalTargetId, pendingPortalTravelProfileId, pendingPortalDisplayName); }
    }

    public enum Tab {
        SERVERS("Servers"), RULES("Rules"), TARGETS("Targets");
        private final String label;
        Tab(String label) { this.label = label; }
    }

    public enum ServersPanel {
        DETAILS,
        SETUP,
        ADD
    }

    public enum RulesPanel {
        DETAILS,
        ADD,
        VISUALIZE
    }

    public enum TargetsPanel {
        DETAILS,
        CREATE,
        PORTAL_SETUP
    }

    private record SetupReport(@Nonnull String status, @Nonnull String detail, @Nonnull String followUp, boolean running, @Nonnull HyUIStyle statusStyle) {
    }

    private record GroupStatusCounts(int assigned, int matching, int needsApply, int unknown) {
    }

    private record ServerEntry(
        @Nonnull String selectionKey,
        @Nonnull String displayAddress,
        @Nonnull String host,
        @Nonnull String portText,
        @Nonnull String connectionAddress,
        boolean local,
        boolean configured
    ) {
    }
}
