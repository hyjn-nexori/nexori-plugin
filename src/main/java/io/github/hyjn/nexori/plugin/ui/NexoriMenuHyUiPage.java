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
    private static final String LOCAL_SERVER_KEY = "__local__";
    private static final String SERVER_ADDRESS_INPUT_ID = "server-address-input";

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
        open(ref, store, playerRef, player, plugin, new State(Tab.SERVERS, "", "", ServersPanel.DETAILS, ""));
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
        String selectedPeerAddress = normalizeSelectedPeerAddress(state.selectedPeerAddress(), serverEntries);
        ServerEntry selectedServer = findSelectedServer(serverEntries, selectedPeerAddress);
        ConfiguredPeer selectedPeer = selectedServer == null || selectedServer.local() || selectedServer.connectionAddress().isBlank()
            ? null
            : findSelectedPeer(peers, selectedServer.connectionAddress());
        ServerPolicySummary cachedPolicy = selectedPeer == null
            ? null
            : plugin.getServerPolicyCacheService().find(selectedPeer.connectionAddress()).orElse(null);
        List<DestinationTargetDefinition> targets = plugin.getDestinationTargetService().list();

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI HYUI")
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(14));
        content.addChild(tabs(ref, store, playerRef, player, plugin, state.withSelectedPeer(selectedPeerAddress)));
        content.addChild(spacerY(12));
        content.addChild(body(ref, store, playerRef, player, plugin, state.withSelectedPeer(selectedPeerAddress), peers, serverEntries, selectedServer, selectedPeer, cachedPolicy, targets));
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
                                     List<ConfiguredPeer> peers, List<ServerEntry> serverEntries, ServerEntry selectedServer, ConfiguredPeer selectedPeer,
                                     ServerPolicySummary cachedPolicy, List<DestinationTargetDefinition> targets) {
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
            case RULES -> rulesBody(ref, store, playerRef, player, plugin, state, peers, selectedPeer, cachedPolicy);
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
        left.addChild(label(peers.isEmpty() ? "No configured peers yet." : peers.size() + " trusted server(s) configured.", MUTED, LEFT_W - 32));
        left.addChild(spacerY(12));
        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(38));
        actions.addChild(spacerX(20));
        actions.addChild(
            ButtonBuilder.secondaryTextButton().withText("Add Server")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(38))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withServersPanel(ServersPanel.ADD).withStatus("")))
        );
        actions.addChild(spacerX(8));
        actions.addChild(
            (state.serversPanel() == ServersPanel.SETUP ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText("Initial Setup")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(38))
                .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withServersPanel(ServersPanel.SETUP).withStatus("")))
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
                ButtonBuilder.secondaryTextButton()
                    .withText("Remove Server")
                    .withBackground(new HyUIPatchStyle().setColor("#a33f4d"))
                    .withAnchor(new HyUIAnchor().setWidth(220).setHeight(42))
                    .onClick((ignored, ctx) -> {
                        try {
                            plugin.getConfiguredPeerService().remove(selectedPeer.connectionAddress());
                            open(ref, store, playerRef, player, plugin, state.withSelectedPeer(LOCAL_SERVER_KEY).withStatus("Removed " + selectedPeer.displayAddress() + "."));
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
                        open(ref, store, playerRef, player, plugin, state.withSelectedPeer(nextSelected).withServersPanel(ServersPanel.DETAILS).withPendingServerAddress("").withStatus("Added " + added.connectionAddress() + "."));
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

    private static GroupBuilder rulesBody(Ref<EntityStore> ref, Store<EntityStore> store, PlayerRef playerRef, Player player, NexoriPlugin plugin,
                                          State state, List<ConfiguredPeer> peers, ConfiguredPeer selectedPeer, ServerPolicySummary cachedPolicy) {
        int panelHeight = bodyHeight(state);
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(panelHeight));
        GroupBuilder left = card(LEFT_W, panelHeight, CARD_BG);
        left.addChild(label("Trusted Servers", TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        if (peers.isEmpty()) {
            left.addChild(label("No configured peers yet.", MUTED, LEFT_W - 32));
        } else {
            for (ConfiguredPeer peer : peers) {
                boolean selected = selectedPeer != null && selectedPeer.connectionAddress().equals(peer.connectionAddress());
                GroupBuilder peerCard = card(LEFT_W - 2, 94, selected ? STATUS_BG : ITEM_BG);
                peerCard.addChild(label(peer.connectionAddress(), new HyUIStyle().setFontSize(14).setRenderBold(true).setTextColor("#f1f6ff"), LEFT_W - 34));
                peerCard.addChild(spacerY(6));
                peerCard.addChild(label("Select which trusted server to inspect or update.", MUTED, LEFT_W - 34));
                peerCard.addChild(spacerY(10));
                peerCard.addChild(
                    ButtonBuilder.secondaryTextButton().withText(selected ? "Selected" : "Select").withDisabled(selected)
                        .withAnchor(new HyUIAnchor().setWidth(140).setHeight(36))
                        .onClick((ignored, ctx) -> open(ref, store, playerRef, player, plugin, state.withSelectedPeer(peer.connectionAddress()).withStatus("")))
                );
                left.addChild(peerCard);
                left.addChild(spacerY(8));
            }
        }

        GroupBuilder right = card(RIGHT_W, panelHeight, CARD_BG);
        right.addChild(label("Local Server Rules", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        right.addChild(stat("Recovery", plugin.getInventoryTransferService().isRecoveryEnabled() ? "Enabled" : "Disabled", RIGHT_W));
        right.addChild(stat("Backup Limit", plugin.getInventoryTransferService().getMaxBackupsPerPlayer() + " backup(s) per player", RIGHT_W));
        right.addChild(spacerY(10));
        GroupBuilder localActions = GroupBuilder.group().withLayoutMode("Left");
        localActions.addChild(ButtonBuilder.secondaryTextButton().withText("Toggle Recovery").withAnchor(new HyUIAnchor().setWidth(210).setHeight(42)).onClick((ignored, ctx) -> {
            try {
                plugin.getInventoryTransferService().setRecoveryEnabled(!plugin.getInventoryTransferService().isRecoveryEnabled());
                open(ref, store, playerRef, player, plugin, state.withStatus("Updated this server's recovery rule."));
            } catch (IOException exception) {
                open(ref, store, playerRef, player, plugin, state.withStatus("Could not save recovery mode: " + exception.getMessage()));
            }
        }));
        localActions.addChild(spacerX(8));
        localActions.addChild(ButtonBuilder.smallSecondaryTextButton().withText("-").withAnchor(new HyUIAnchor().setWidth(52).setHeight(42)).onClick((ignored, ctx) -> {
            try {
                plugin.getInventoryTransferService().setMaxBackupsPerPlayer(Math.max(1, plugin.getInventoryTransferService().getMaxBackupsPerPlayer() - 1));
                open(ref, store, playerRef, player, plugin, state.withStatus("Lowered this server's per-player backup limit."));
            } catch (IOException exception) {
                open(ref, store, playerRef, player, plugin, state.withStatus("Could not save backup limit: " + exception.getMessage()));
            }
        }));
        localActions.addChild(spacerX(8));
        localActions.addChild(ButtonBuilder.smallSecondaryTextButton().withText("+").withAnchor(new HyUIAnchor().setWidth(52).setHeight(42)).onClick((ignored, ctx) -> {
            try {
                plugin.getInventoryTransferService().setMaxBackupsPerPlayer(Math.min(50, plugin.getInventoryTransferService().getMaxBackupsPerPlayer() + 1));
                open(ref, store, playerRef, player, plugin, state.withStatus("Raised this server's per-player backup limit."));
            } catch (IOException exception) {
                open(ref, store, playerRef, player, plugin, state.withStatus("Could not save backup limit: " + exception.getMessage()));
            }
        }));
        right.addChild(localActions);
        right.addChild(spacerY(18));
        right.addChild(label("Selected Remote Server", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));
        if (selectedPeer == null) {
            right.addChild(label("No trusted server selected yet.", MUTED, RIGHT_W - 32));
        } else {
            right.addChild(stat("Address", selectedPeer.connectionAddress(), RIGHT_W));
            right.addChild(label(cachedPolicy == null ? "No confirmed rules cached yet for this server." : "Confirmed from " + cachedPolicy.remoteServerId() + " at " + TIME_FORMAT.format(Instant.ofEpochMilli(cachedPolicy.confirmedAtEpochMillis())), MUTED, RIGHT_W - 32));
            right.addChild(spacerY(10));
            right.addChild(stat("Recovery", cachedPolicy == null ? "<unknown>" : cachedPolicy.recoveryEnabled() ? "Enabled" : "Disabled", RIGHT_W));
            right.addChild(stat("Backup Limit", cachedPolicy == null ? "<unknown>" : Integer.toString(cachedPolicy.maxBackupsPerPlayer()), RIGHT_W));
            right.addChild(spacerY(10));
            GroupBuilder remoteActions = GroupBuilder.group().withLayoutMode("Left");
            remoteActions.addChild(ButtonBuilder.secondaryTextButton().withText("Refresh Selected").withAnchor(new HyUIAnchor().setWidth(210).setHeight(42)).onClick((ignored, ctx) -> {
                try {
                    plugin.getServerPolicySyncService().refresh(playerRef, selectedPeer, player.getWorld().getName(), captureCurrentTransform(store, ref),
                        (resumeRef, resumeStore, resumePlayerRef, resumePlayer) -> open(resumeRef, resumeStore, resumePlayerRef, resumePlayer, plugin, state.withStatus("Refreshed rules from " + selectedPeer.connectionAddress() + ".")));
                    player.sendMessage(Message.raw("Nexori is refreshing rules from " + selectedPeer.connectionAddress() + "..."));
                } catch (GeneralSecurityException | IOException exception) {
                    open(ref, store, playerRef, player, plugin, state.withStatus("Could not start secure rules refresh: " + exception.getMessage()));
                }
            }));
            remoteActions.addChild(spacerX(8));
            remoteActions.addChild(ButtonBuilder.secondaryTextButton().withText("Apply Local To Selected").withAnchor(new HyUIAnchor().setWidth(250).setHeight(42)).onClick((ignored, ctx) -> {
                try {
                    plugin.getServerPolicySyncService().apply(playerRef, selectedPeer, player.getWorld().getName(), captureCurrentTransform(store, ref),
                        plugin.getInventoryTransferService().isRecoveryEnabled(), plugin.getInventoryTransferService().getMaxBackupsPerPlayer(),
                        (resumeRef, resumeStore, resumePlayerRef, resumePlayer) -> open(resumeRef, resumeStore, resumePlayerRef, resumePlayer, plugin, state.withStatus("Applied local rules to " + selectedPeer.connectionAddress() + ".")));
                    player.sendMessage(Message.raw("Nexori is applying local rules to " + selectedPeer.connectionAddress() + "..."));
                } catch (GeneralSecurityException | IOException exception) {
                    open(ref, store, playerRef, player, plugin, state.withStatus("Could not start secure rules apply: " + exception.getMessage()));
                }
            }));
            right.addChild(remoteActions);
        }

        row.addChild(left);
        row.addChild(spacerX(16));
        row.addChild(right);
        return row;
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
        boolean localConfigured = localPeer.isPresent() && peers.stream()
            .anyMatch(peer -> peer.connectionAddress().equalsIgnoreCase(localPeer.get().connectionAddress()));

        if (localPeer.isPresent()) {
            ConfiguredPeer peer = localPeer.get();
            entries.add(new ServerEntry(
                LOCAL_SERVER_KEY,
                peer.connectionAddress(),
                peer.host(),
                Integer.toString(peer.port()),
                peer.connectionAddress(),
                true,
                localConfigured
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
            entries.add(new ServerEntry(
                peer.connectionAddress(),
                peer.connectionAddress(),
                peer.host(),
                Integer.toString(peer.port()),
                peer.connectionAddress(),
                false,
                true
            ));
        }
        return entries;
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

    private static ServerEntry findSelectedServer(@Nonnull List<ServerEntry> serverEntries, @Nonnull String selectionKey) {
        for (ServerEntry entry : serverEntries) {
            if (entry.selectionKey().equals(selectionKey)) {
                return entry;
            }
        }
        return serverEntries.isEmpty() ? null : serverEntries.getFirst();
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

    public record State(@Nonnull Tab tab, @Nonnull String selectedPeerAddress, @Nonnull String statusText, @Nonnull ServersPanel serversPanel,
                        @Nonnull String pendingServerAddress) {
        public State withTab(@Nonnull Tab tab) { return new State(tab, selectedPeerAddress, statusText, serversPanel, pendingServerAddress); }
        public State withSelectedPeer(@Nonnull String selectedPeerAddress) { return new State(tab, selectedPeerAddress, statusText, serversPanel, pendingServerAddress); }
        public State withStatus(@Nonnull String statusText) { return new State(tab, selectedPeerAddress, statusText, serversPanel, pendingServerAddress); }
        public State withServersPanel(@Nonnull ServersPanel serversPanel) { return new State(tab, selectedPeerAddress, statusText, serversPanel, pendingServerAddress); }
        public State withPendingServerAddress(@Nonnull String pendingServerAddress) { return new State(tab, selectedPeerAddress, statusText, serversPanel, pendingServerAddress); }
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

    private record SetupReport(@Nonnull String status, @Nonnull String detail, @Nonnull String followUp, boolean running, @Nonnull HyUIStyle statusStyle) {
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
