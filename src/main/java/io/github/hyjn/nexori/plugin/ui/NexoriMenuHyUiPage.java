package io.github.hyjn.nexori.plugin.ui;

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
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
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

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin
    ) {
        open(ref, store, playerRef, player, plugin, new State(
            Tab.SERVERS,
            "",
            "",
            "",
            ServersPanel.DETAILS,
            RulesPanel.DETAILS,
            "",
            ""
        ));
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
            playerRef.sendMessage(Message.raw("nexorimenuhyui: could not resolve the live player entity."));
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
        List<ServerRuleGroupDefinition> ruleGroups = plugin.getServerRuleGroupService().list();
        String selectedRuleGroupId = state.rulesPanel() == RulesPanel.DETAILS
            ? normalizeSelectedRuleGroupId(state.selectedRuleGroupId(), ruleGroups)
            : "";
        ServerRuleGroupDefinition selectedRuleGroup = state.rulesPanel() == RulesPanel.DETAILS
            ? findSelectedRuleGroup(ruleGroups, selectedRuleGroupId)
            : null;
        State normalizedState = state
            .withSelectedPeer(selectedPeerAddress)
            .withSelectedRuleGroup(selectedRuleGroupId);

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI HYUI")
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(14));
        content.addChild(tabs(ref, store, playerRef, player, plugin, normalizedState));
        content.addChild(spacerY(12));
        content.addChild(body(ref, store, playerRef, player, plugin, normalizedState, peers, serverEntries, selectedServer, targets, ruleGroups, selectedRuleGroup));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    private static GroupBuilder tabs(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, Player player, NexoriPlugin plugin, State state) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(42));
        for (Tab tab : Tab.values()) {
            ButtonBuilder button = (state.tab() == tab ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText(tab.label)
                .withAnchor(new HyUIAnchor().setWidth(TAB_W).setHeight(42))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withTab(tab).withStatus("").withServersPanel(ServersPanel.DETAILS)));
            row.addChild(button);
            if (tab != Tab.PORTALS) row.addChild(spacerX(8));
        }
        return row;
    }

    private static GroupBuilder body(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, Player player, NexoriPlugin plugin, State state,
                                     List<ConfiguredPeer> peers, List<ServerEntry> serverEntries, ServerEntry selectedServer,
                                     List<DestinationTargetDefinition> targets, List<ServerRuleGroupDefinition> ruleGroups,
                                     ServerRuleGroupDefinition selectedRuleGroup) {
        return switch (state.tab()) {
            case SERVERS -> serversBody(ref, store, playerRef, player, plugin, state, peers, serverEntries, selectedServer);
            case TARGETS -> {
                long natural = targets.stream().filter(t -> t.kind() == DestinationTargetKind.NATURAL_SPAWN).count();
                long coordinate = targets.stream().filter(t -> t.kind() == DestinationTargetKind.COORDINATE).count();
                long portal = targets.stream().filter(t -> t.kind() == DestinationTargetKind.PORTAL).count();
                yield simpleTwoCol(
                    "Target Summary", "Total: " + targets.size() + "\nNatural Spawn: " + natural + "\nCoordinate: " + coordinate + "\nPortal: " + portal,
                    "Target Manager", "Use the current target manager while we prove the HyUI shell.",
                    ButtonBuilder.secondaryTextButton().withText("Open Target Manager").withAnchor(new HyUIAnchor().setWidth(250).setHeight(42))
                        .onClick((ignored, ctx) -> NexoriTargetManagerPage.open(ref, store, playerRef, player, plugin.getDestinationTargetService(), plugin.getPortalInstanceService(), plugin.getPortalInteractionService(), plugin.getTargetSetupDraftService(), "", ""))
                , bodyHeight(state));
            }
            case PORTALS -> simpleTwoCol(
                "Portals", plugin.getPortalInstanceService().list().size() + " registered portal(s) on this server.",
                "Portal Setup", "Portal-by-portal setup still lives in the proven page opened from placed portals.",
                ButtonBuilder.secondaryTextButton().withText("Open Current Portal Overview").withAnchor(new HyUIAnchor().setWidth(300).setHeight(42))
                    .onClick((ignored, ctx) -> NexoriMenuPage.open(ref, store, playerRef, player, plugin, NexoriMenuPage.MenuTab.PORTALS, state.selectedPeerAddress(), "", false, "", ""))
            , bodyHeight(state));
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
        left.addChild(label("Trusted Servers", TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        long knownTrustedServers = serverEntries.stream()
            .filter(entry -> !entry.connectionAddress().isBlank())
            .count();
        left.addChild(label(
            knownTrustedServers == 0
                ? "No trusted servers are known yet."
                : knownTrustedServers + " trusted server(s) known for this network.",
            MUTED,
            LEFT_W - 32
        ));
        left.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(38));
        actions.addChild(spacerX(20));
        actions.addChild(
            ButtonBuilder.secondaryTextButton().withText("Add Server")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(38))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedPeer("").withServersPanel(ServersPanel.ADD).withStatus("")))
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            (state.serversPanel() == ServersPanel.SETUP ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText("Initial Setup")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(38))
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
            listHost.addChild(label("Add the servers that belong to this experience, then run Initial Setup.", MUTED, LEFT_W - 48));
        }
        left.addChild(listHost);

        GroupBuilder right = card(RIGHT_W, panelHeight, CARD_BG);
        if (state.serversPanel() == ServersPanel.ADD) {
            buildAddServerPanel(ref, store, playerRef, player, plugin, state, right);
        } else if (state.serversPanel() == ServersPanel.SETUP) {
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
        right.addChild(label("Server Details", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        if (selectedPeer == null) {
            right.addChild(label("No trusted server selected yet.", MUTED, RIGHT_W - 32));
            return;
        }

        GroupBuilder connectBlock = card(RIGHT_W - 32, 150, ITEM_BG);
        connectBlock.addChild(label("How To Connect To This Server", TITLE, RIGHT_W - 64));
        connectBlock.addChild(spacerY(10));
        connectBlock.addChild(stat("Connection Address", selectedPeer.displayAddress(), RIGHT_W - 16));
        connectBlock.addChild(stat("Host", selectedPeer.host(), RIGHT_W - 16));
        connectBlock.addChild(stat("Port", selectedPeer.portText(), RIGHT_W - 16));
        right.addChild(connectBlock);
        right.addChild(spacerY(14));

        GroupBuilder secureBlock = card(RIGHT_W - 32, 280, ITEM_BG);
        secureBlock.addChild(label("Secure Travel With", TITLE, RIGHT_W - 64));
        secureBlock.addChild(spacerY(10));
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        boolean selectedServerTrusted = isTrustedEntry(bundle, selectedPeer);
        int secureRowCount = serverEntries.size();
        int secureListContentHeight = Math.max(200, Math.max(1, secureRowCount) * 32 + 32);
        ReorderableListBuilder secureList = scrollList(RIGHT_W - 64, 200, secureListContentHeight, "secure-travel-list", false);
        secureList.addChild(secureLinkRow(selectedPeer.displayAddress() + " (Selected)", selectedServerTrusted));
        secureList.addChild(spacerY(8));
        for (ServerEntry peer : serverEntries) {
            if (peer.selectionKey().equals(selectedPeer.selectionKey())) {
                continue;
            }
            secureList.addChild(secureLinkRow(peer.displayAddress(), selectedServerTrusted && isTrustedEntry(bundle, peer)));
            secureList.addChild(spacerY(8));
        }
        secureBlock.addChild(secureList);
        right.addChild(secureBlock);
        right.addChild(spacerY(16));
        if (selectedPeer.configured() && !selectedPeer.connectionAddress().isBlank()) {
            right.addChild(
                ButtonBuilder.textButton()
                    .withText("Remove Server")
                    .withBackground(new HyUIPatchStyle().setColor("#a33f4d"))
                    .withAnchor(new HyUIAnchor().setWidth(220).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        try {
                            plugin.getConfiguredPeerService().remove(selectedPeer.connectionAddress());
                            plugin.getServerRuleGroupService().removeServerAssignments(selectedPeer.selectionKey());
                            open(ref, store, playerRef, player, plugin, state.withSelectedPeer("").withStatus(""));
                        } catch (IOException | IllegalArgumentException exception) {
                            open(ref, store, playerRef, player, plugin, state.withStatus("Failed to remove server: " + exception.getMessage()));
                        }
                    })
            );
        }
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
                        open(ref, store, playerRef, player, plugin, state.withSelectedPeer(nextSelected).withServersPanel(ServersPanel.DETAILS).withPendingServerAddress("").withStatus(""));
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
        right.addChild(label("Initial Setup", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        GroupBuilder setupBlock = card(RIGHT_W - 32, 220, ITEM_BG);
        setupBlock.addChild(label("Setup", TITLE, RIGHT_W - 64));
        setupBlock.addChild(spacerY(8));
        setupBlock.addChild(label(
            "When Nexori starts on a server, it creates that server's key pair. For secure travel to work, every trusted server in this experience needs the public keys of the others. After you add the servers that belong to this experience under Trusted Servers, run Initial Setup so Nexori can verify them and install the trust bundle they need.",
            BODY,
            RIGHT_W - 64
        ));
        setupBlock.addChild(spacerY(14));
        setupBlock.addChild(
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
                        open(ref, store, playerRef, player, plugin, state.withStatus(result.message()));
                    }
                })
        );
        right.addChild(setupBlock);
        right.addChild(spacerY(14));
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
        right.addChild(reportBlock);
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
                            open(ref, store, playerRef, player, plugin, state.withStatus(""));
                        } else {
                            plugin.getServerRuleGroupService().assignServer(selectedRuleGroup.groupId(), serverEntry.selectionKey());
                            open(ref, store, playerRef, player, plugin, state.withStatus(""));
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
        List<ServerEntry> assignedServers = serverEntries.stream()
            .filter(serverEntry -> group.containsServer(serverEntry.selectionKey()))
            .toList();
        if (assignedServers.isEmpty()) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Assign at least one server to '" + group.displayName() + "' before you apply it."));
            return;
        }

        try {
            if (group.containsServer(LOCAL_SERVER_KEY)) {
                plugin.getInventoryTransferService().setRecoveryEnabled(group.recoveryEnabled());
                plugin.getInventoryTransferService().setMaxBackupsPerPlayer(group.maxBackupsPerPlayer());
            }
        } catch (IOException exception) {
            open(ref, store, playerRef, player, plugin, state.withStatus("Could not apply the local server rules: " + exception.getMessage()));
            return;
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        for (ServerEntry serverEntry : assignedServers) {
            if (serverEntry.local() || serverEntry.connectionAddress().isBlank()) {
                continue;
            }
            ConfiguredPeer configuredPeer = findSelectedPeer(peers, serverEntry.connectionAddress());
            if (configuredPeer != null) {
                remotePeers.add(configuredPeer);
            }
        }

        if (remotePeers.isEmpty()) {
            open(ref, store, playerRef, player, plugin, state.withStatus(""));
            return;
        }

        player.sendMessage(Message.raw("Nexori is applying '" + group.displayName() + "' to " + remotePeers.size() + " remote server(s)..."));
        applyRuleGroupAssignmentAtIndex(ref, store, playerRef, player, plugin, state, group, remotePeers, 0, 0);
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
        List<ConfiguredPeer> remotePeers = serverEntries.stream()
            .filter(serverEntry -> group.containsServer(serverEntry.selectionKey()))
            .filter(serverEntry -> !serverEntry.local() && !serverEntry.connectionAddress().isBlank())
            .map(serverEntry -> findSelectedPeer(peers, serverEntry.connectionAddress()))
            .filter(java.util.Objects::nonNull)
            .toList();
        if (remotePeers.isEmpty()) {
            open(ref, store, playerRef, player, plugin, state.withStatus("There are no remote servers assigned to '" + group.displayName() + "' yet."));
            return;
        }

        player.sendMessage(Message.raw("Nexori is refreshing the cached rule status for '" + group.displayName() + "'..."));
        refreshRuleGroupAssignmentAtIndex(ref, store, playerRef, player, plugin, state, group, remotePeers, 0);
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
        Optional<ConfiguredPeer> localPeer = plugin.getLocalConnectionAddressService().getConfiguredPeer();
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        java.util.LinkedHashMap<String, ServerEntry> remoteEntriesByAddress = new java.util.LinkedHashMap<>();
        boolean localTrusted = false;

        if (localPeer.isPresent()) {
            localTrusted = bundle.members().stream()
                .anyMatch(member -> member.connectionAddress() != null
                    && member.connectionAddress().equalsIgnoreCase(localPeer.get().connectionAddress()));
        }

        if (localPeer.isPresent()) {
            ConfiguredPeer peer = localPeer.get();
            entries.add(new ServerEntry(
                LOCAL_SERVER_KEY,
                peer.connectionAddress(),
                peer.host(),
                Integer.toString(peer.port()),
                peer.connectionAddress(),
                true,
                localTrusted
            ));
        } else {
            entries.add(new ServerEntry(
                LOCAL_SERVER_KEY,
                "Current Server (address not learned yet)",
                "<unknown>",
                "<unknown>",
                "",
                true,
                false
            ));
        }

        for (ConfiguredPeer peer : peers) {
            if (localPeer.isPresent() && peer.connectionAddress().equalsIgnoreCase(localPeer.get().connectionAddress())) {
                continue;
            }
            remoteEntriesByAddress.put(peer.connectionAddress(), new ServerEntry(
                peer.connectionAddress(),
                peer.connectionAddress(),
                peer.host(),
                Integer.toString(peer.port()),
                peer.connectionAddress(),
                false,
                true
            ));
        }

        for (BundleMember member : bundle.members()) {
            if (member.local() || member.connectionAddress() == null || member.connectionAddress().isBlank()) {
                continue;
            }
            if (localPeer.isPresent() && member.connectionAddress().equalsIgnoreCase(localPeer.get().connectionAddress())) {
                continue;
            }
            remoteEntriesByAddress.putIfAbsent(member.connectionAddress(), new ServerEntry(
                member.connectionAddress(),
                member.connectionAddress(),
                parseHost(member.connectionAddress()),
                parsePortText(member.connectionAddress()),
                member.connectionAddress(),
                false,
                true
            ));
        }
        entries.addAll(remoteEntriesByAddress.values());
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
        if (selectedPeerAddress.isBlank()) {
            return LOCAL_SERVER_KEY;
        }
        for (ServerEntry entry : serverEntries) {
            if (entry.selectionKey().equals(selectedPeerAddress) || (!entry.connectionAddress().isBlank() && entry.connectionAddress().equalsIgnoreCase(selectedPeerAddress))) {
                return entry.selectionKey();
            }
        }
        return LOCAL_SERVER_KEY;
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

    private static SetupReport buildSetupReport(@Nonnull NexoriPlugin plugin, @Nonnull List<ConfiguredPeer> peers) {
        BootstrapState bootstrapState = plugin.getBootstrapStateStore().getCurrentState();
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();

        if (peers.isEmpty()) {
            return new SetupReport(
                "Waiting For Servers",
                "Add the trusted servers for this experience first. Nexori cannot build the secure trust bundle until at least one remote server has been configured.",
                "",
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

        boolean localIncluded = bundle.members().stream().anyMatch(BundleMember::local);
        List<String> missingPeers = peers.stream()
            .filter(peer -> bundle.members().stream().noneMatch(member -> peer.connectionAddress().equalsIgnoreCase(member.connectionAddress())))
            .map(ConfiguredPeer::connectionAddress)
            .toList();

        if (bundle.bundleVersion() <= 0 || bundle.bundleHash().isBlank() || bundle.members().isEmpty() || !localIncluded) {
            return new SetupReport(
                "Not Completed",
                "This server does not have a confirmed Nexori trust bundle yet.",
                "Run Initial Setup after you finish adding the trusted servers that belong to this experience.",
                false,
                BAD
            );
        }

        if (!missingPeers.isEmpty()) {
            return new SetupReport(
                "Needs Re-run",
                "The current trust bundle is missing one or more trusted servers: " + String.join(", ", missingPeers),
                "Run Initial Setup again so every trusted server gets the public keys it needs.",
                false,
                BAD
            );
        }

        return new SetupReport(
            "Completed",
            "Initial setup completed successfully for the currently configured trusted servers. Nexori has a confirmed trust bundle ready for secure travel.",
            "Bundle v" + bundle.bundleVersion() + " was last updated at " + TIME_FORMAT.format(Instant.ofEpochMilli(bundle.updatedAtEpochMillis())) + ".",
            false,
            GOOD
        );
    }

    private static boolean isTrustedMember(@Nonnull TrustBundle bundle, @Nonnull String connectionAddress) {
        return bundle.members().stream().anyMatch(member -> connectionAddress.equalsIgnoreCase(member.connectionAddress()));
    }

    private static boolean isTrustedEntry(@Nonnull TrustBundle bundle, @Nonnull ServerEntry entry) {
        if (entry.local()) {
            if (entry.connectionAddress().isBlank()) {
                return bundle.members().stream().anyMatch(member -> member.local() && member.connectionAddress() != null && !member.connectionAddress().isBlank());
            }
            return bundle.members().stream().anyMatch(member -> member.local() && entry.connectionAddress().equalsIgnoreCase(member.connectionAddress()));
        }
        return !entry.connectionAddress().isBlank() && isTrustedMember(bundle, entry.connectionAddress());
    }

    public record State(
        @Nonnull Tab tab,
        @Nonnull String selectedPeerAddress,
        @Nonnull String selectedRuleGroupId,
        @Nonnull String statusText,
        @Nonnull ServersPanel serversPanel,
        @Nonnull RulesPanel rulesPanel,
        @Nonnull String pendingServerAddress,
        @Nonnull String pendingRuleGroupName
    ) {
        public State withTab(@Nonnull Tab tab) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withSelectedPeer(@Nonnull String selectedPeerAddress) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withSelectedRuleGroup(@Nonnull String selectedRuleGroupId) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withStatus(@Nonnull String statusText) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withServersPanel(@Nonnull ServersPanel serversPanel) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withRulesPanel(@Nonnull RulesPanel rulesPanel) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withPendingServerAddress(@Nonnull String pendingServerAddress) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
        public State withPendingRuleGroupName(@Nonnull String pendingRuleGroupName) { return new State(tab, selectedPeerAddress, selectedRuleGroupId, statusText, serversPanel, rulesPanel, pendingServerAddress, pendingRuleGroupName); }
    }

    public enum Tab {
        SERVERS("Servers"), RULES("Rules"), TARGETS("Targets"), PORTALS("Portals");
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
