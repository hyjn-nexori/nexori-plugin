package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.policy.ServerPolicySummary;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class NexoriMenuPage extends InteractiveCustomUIPage<NexoriMenuPage.PageData> {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final int TAB_INDEX_BASE = 1000;
    private static final int PEER_INDEX_BASE = 2000;
    private static final int PORTAL_INDEX_BASE = 3000;

    private static final int ACTION_ADD_INDEX = 4000;
    private static final int ACTION_START_INDEX = 4001;
    private static final int ACTION_REMOVE_INDEX = 4002;

    private static final int ACTION_RULES_TOGGLE_RECOVERY_INDEX = 5000;
    private static final int ACTION_RULES_LIMIT_DECREASE_INDEX = 5001;
    private static final int ACTION_RULES_LIMIT_INCREASE_INDEX = 5002;
    private static final int ACTION_RULES_REFRESH_SELECTED_INDEX = 5003;
    private static final int ACTION_RULES_APPLY_SELECTED_INDEX = 5004;

    private static final int ACTION_OPEN_TARGET_MANAGER_INDEX = 6000;

    private final NexoriPlugin plugin;
    private final PlayerRef playerRef;
    private final String adminPermission;
    private final ChoiceElement[] tabElements;
    private final List<ConfiguredPeer> peers;
    private final List<PortalInstanceDefinition> portals;
    private final boolean canManageThisServer;
    private final MenuTab activeTab;
    private final String selectedPeerAddress;
    private final String selectedPortalId;
    private final boolean addPeerMode;
    private final String addPeerConnectionAddress;
    private final String statusText;

    private NexoriMenuPage(
        @Nonnull NexoriPlugin plugin,
        @Nonnull PlayerRef playerRef,
        @Nonnull String adminPermission,
        @Nonnull ChoiceElement[] tabElements,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull List<PortalInstanceDefinition> portals,
        boolean canManageThisServer,
        @Nonnull MenuTab activeTab,
        @Nonnull String selectedPeerAddress,
        @Nonnull String selectedPortalId,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
        this.adminPermission = adminPermission;
        this.tabElements = tabElements;
        this.peers = peers;
        this.portals = portals;
        this.canManageThisServer = canManageThisServer;
        this.activeTab = activeTab;
        this.selectedPeerAddress = selectedPeerAddress;
        this.selectedPortalId = selectedPortalId;
        this.addPeerMode = addPeerMode;
        this.addPeerConnectionAddress = addPeerConnectionAddress;
        this.statusText = statusText;
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin
    ) {
        show(ref, store, playerRef, player, plugin, MenuTab.SERVERS, null, null, false, "", "");
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull MenuTab activeTab,
        String selectedPeerAddress,
        String selectedPortalId,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        show(ref, store, playerRef, player, plugin, activeTab, selectedPeerAddress, selectedPortalId, addPeerMode, addPeerConnectionAddress, statusText);
    }

    private static void show(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull MenuTab activeTab,
        String selectedPeerAddress,
        String selectedPortalId,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        PageManager pages = player == null ? null : player.getPageManager();
        if (pages == null) {
            playerRef.sendMessage(Message.raw("nexorimenu: PageManager not available."));
            return;
        }

        boolean canManageThisServer = NexoriOperatorAccess.canManage(playerRef, player, plugin.getBasePermission() + ".admin");
        pages.openCustomPage(
            ref,
            store,
            create(plugin, playerRef, canManageThisServer, activeTab, selectedPeerAddress, selectedPortalId, addPeerMode && activeTab == MenuTab.SERVERS, addPeerConnectionAddress, statusText)
        );
    }

    @Nonnull
    private static NexoriMenuPage create(
        @Nonnull NexoriPlugin plugin,
        @Nonnull PlayerRef playerRef,
        boolean canManageThisServer,
        @Nonnull MenuTab activeTab,
        String selectedPeerAddress,
        String selectedPortalId,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        List<ConfiguredPeer> savedPeers = plugin.getConfiguredPeerService().list();
        List<PortalInstanceDefinition> savedPortals = plugin.getPortalInstanceService().list();
        String resolvedSelectedPeerAddress = resolveSelectedPeerAddress(savedPeers, selectedPeerAddress);
        String resolvedSelectedPortalId = resolveSelectedPortalId(savedPortals, selectedPortalId);

        MenuTab[] tabs = MenuTab.values();
        ChoiceElement[] tabElements = new ChoiceElement[tabs.length];
        for (int index = 0; index < tabs.length; index++) {
            tabElements[index] = new NexoriTabElement(tabs[index].label, tabs[index] == activeTab);
        }

        return new NexoriMenuPage(
            plugin,
            playerRef,
            plugin.getBasePermission(),
            tabElements,
            savedPeers,
            savedPortals,
            canManageThisServer,
            activeTab,
            resolvedSelectedPeerAddress,
            resolvedSelectedPortalId,
            addPeerMode,
            addPeerConnectionAddress,
            statusText
        );
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        commands.append("Pages/Nexori/NexoriHome.ui");

        switch (activeTab) {
            case SERVERS -> commands.append("#BodyHost", "Pages/Nexori/NexoriPeersBody.ui");
            case RULES -> commands.append("#BodyHost", "Pages/Nexori/NexoriRulesBody.ui");
            case TARGETS -> commands.append("#BodyHost", "Pages/Nexori/NexoriTargetsBody.ui");
            case PORTALS -> commands.append("#BodyHost", "Pages/Nexori/NexoriPortalsBody.ui");
        }

        for (int i = 0; i < tabElements.length; i++) {
            String selector = "#TabsList[" + i + "]";
            tabElements[i].addButton(commands, events, selector, playerRef);
            bindIndex(events, selector, TAB_INDEX_BASE + i);
        }

        switch (activeTab) {
            case SERVERS -> buildServersBody(commands, events);
            case RULES -> buildRulesBody(commands, events);
            case TARGETS -> buildTargetsBody(commands, events);
            case PORTALS -> buildPortalsBody(commands, events);
        }
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        Action action = parseAction(data.actionRaw);
        if (action != null) {
            handleAction(ref, store, player, action, data);
            return;
        }

        Integer index = parseIndex(data.indexRaw);
        if (index == null) {
            return;
        }

        if (index >= TAB_INDEX_BASE && index < TAB_INDEX_BASE + MenuTab.values().length) {
            MenuTab nextTab = MenuTab.values()[index - TAB_INDEX_BASE];
            reopen(ref, store, player, nextTab, selectedPeerAddress, selectedPortalId, false, "", "");
            return;
        }

        switch (activeTab) {
            case SERVERS -> handleServersIndex(ref, store, player, index);
            case RULES -> handleRulesIndex(ref, store, player, index);
            case TARGETS -> handleTargetsIndex(ref, store, player, index);
            case PORTALS -> handlePortalsIndex(ref, store, player, index);
        }
    }

    private void handleServersIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        int index
    ) {
        if (index == ACTION_ADD_INDEX) {
            if (!canManageThisServer) {
                return;
            }
            reopen(ref, store, player, MenuTab.SERVERS, selectedPeerAddress, selectedPortalId, true, "", "");
            return;
        }

        if (index == ACTION_START_INDEX) {
            if (!canManageThisServer) {
                return;
            }

            BootstrapCoordinator.StartResult result = plugin.getBootstrapCoordinator().start(playerRef);
            if (result.started()) {
                PageManager pages = player.getPageManager();
                if (pages != null) {
                    pages.setPage(ref, store, Page.None);
                }
            } else {
                reopen(ref, store, player, MenuTab.SERVERS, selectedPeerAddress, selectedPortalId, false, "", result.message());
            }
            return;
        }

        if (index == ACTION_REMOVE_INDEX) {
            if (!canManageThisServer) {
                return;
            }

            ConfiguredPeer selectedPeer = findSelectedPeer();
            if (selectedPeer == null) {
                return;
            }

            try {
                plugin.getConfiguredPeerService().remove(selectedPeer.connectionAddress());
                reopen(ref, store, player, MenuTab.SERVERS, null, selectedPortalId, false, "", "Removed " + selectedPeer.connectionAddress() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                reopen(ref, store, player, MenuTab.SERVERS, selectedPeerAddress, selectedPortalId, false, "", "Failed to remove peer: " + exception.getMessage());
            }
            return;
        }

        if (index >= PEER_INDEX_BASE && index < PEER_INDEX_BASE + peers.size()) {
            ConfiguredPeer selectedPeer = peers.get(index - PEER_INDEX_BASE);
            reopen(ref, store, player, MenuTab.SERVERS, selectedPeer.connectionAddress(), selectedPortalId, false, "", statusText);
        }
    }

    private void handleRulesIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        int index
    ) {
        if (index >= PEER_INDEX_BASE && index < PEER_INDEX_BASE + peers.size()) {
            ConfiguredPeer selectedPeer = peers.get(index - PEER_INDEX_BASE);
            reopen(ref, store, player, MenuTab.RULES, selectedPeer.connectionAddress(), selectedPortalId, false, "", "");
            return;
        }

        if (!canManageThisServer) {
            return;
        }

        InventoryTransferService inventoryTransferService = plugin.getInventoryTransferService();
        try {
            switch (index) {
                case ACTION_RULES_TOGGLE_RECOVERY_INDEX -> {
                    inventoryTransferService.setRecoveryEnabled(!inventoryTransferService.isRecoveryEnabled());
                    reopen(ref, store, player, MenuTab.RULES, selectedPeerAddress, selectedPortalId, false, "", "Updated this server's recovery rule.");
                }
                case ACTION_RULES_LIMIT_DECREASE_INDEX -> {
                    inventoryTransferService.setMaxBackupsPerPlayer(Math.max(1, inventoryTransferService.getMaxBackupsPerPlayer() - 1));
                    reopen(ref, store, player, MenuTab.RULES, selectedPeerAddress, selectedPortalId, false, "", "Lowered this server's per-player backup limit.");
                }
                case ACTION_RULES_LIMIT_INCREASE_INDEX -> {
                    inventoryTransferService.setMaxBackupsPerPlayer(Math.min(50, inventoryTransferService.getMaxBackupsPerPlayer() + 1));
                    reopen(ref, store, player, MenuTab.RULES, selectedPeerAddress, selectedPortalId, false, "", "Raised this server's per-player backup limit.");
                }
                case ACTION_RULES_REFRESH_SELECTED_INDEX -> startPolicyRefresh(ref, store, player);
                case ACTION_RULES_APPLY_SELECTED_INDEX -> startPolicyApply(ref, store, player);
                default -> {
                }
            }
        } catch (IOException exception) {
            reopen(ref, store, player, MenuTab.RULES, selectedPeerAddress, selectedPortalId, false, "", "Could not save the local Nexori rules: " + exception.getMessage());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            reopen(ref, store, player, MenuTab.RULES, selectedPeerAddress, selectedPortalId, false, "", exception.getMessage());
        } catch (GeneralSecurityException exception) {
            reopen(ref, store, player, MenuTab.RULES, selectedPeerAddress, selectedPortalId, false, "", "The secure Nexori policy sync could not start: " + exception.getMessage());
        }
    }

    private void handleTargetsIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        int index
    ) {
        if (index != ACTION_OPEN_TARGET_MANAGER_INDEX) {
            return;
        }

        NexoriTargetManagerPage.open(
            ref,
            store,
            playerRef,
            player,
            plugin.getDestinationTargetService(),
            plugin.getPortalInstanceService(),
            plugin.getPortalInteractionService(),
            plugin.getTargetSetupDraftService(),
            "",
            ""
        );
    }

    private void handlePortalsIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        int index
    ) {
        if (index >= PORTAL_INDEX_BASE && index < PORTAL_INDEX_BASE + portals.size()) {
            PortalInstanceDefinition portal = portals.get(index - PORTAL_INDEX_BASE);
            reopen(ref, store, player, MenuTab.PORTALS, selectedPeerAddress, portal.portalId(), false, "", "");
            return;
        }
    }

    private void handleAction(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        @Nonnull Action action,
        @Nonnull PageData data
    ) {
        if (action == Action.CANCEL_ADD_PEER) {
            reopen(ref, store, player, MenuTab.SERVERS, selectedPeerAddress, selectedPortalId, false, "", "");
            return;
        }

        if (action == Action.SAVE_PEER) {
            try {
                ConfiguredPeer savedPeer = plugin.getConfiguredPeerService().add(safe(data.connectionAddress).trim());
                reopen(ref, store, player, MenuTab.SERVERS, savedPeer.connectionAddress(), selectedPortalId, false, "", "Saved " + savedPeer.connectionAddress() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                reopen(ref, store, player, MenuTab.SERVERS, selectedPeerAddress, selectedPortalId, true, safe(data.connectionAddress).trim(), exception.getMessage());
            }
        }
    }

    private void buildServersBody(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        commands.set("#AddPeerButton.Visible", canManageThisServer);
        commands.set("#RunBootstrapButton.Visible", canManageThisServer && !peers.isEmpty());
        commands.set("#PeerCountText.Text", peers.size() + " saved peer(s)");

        bindIndex(events, "#AddPeerButton", ACTION_ADD_INDEX);
        bindIndex(events, "#RunBootstrapButton", ACTION_START_INDEX);
        bindIndex(events, "#RemovePeerButton", ACTION_REMOVE_INDEX);

        for (int i = 0; i < peers.size(); i++) {
            ConfiguredPeer peer = peers.get(i);
            BundleMember verifiedMember = findVerifiedMember(peer.connectionAddress());
            String description = verifiedMember == null
                ? "Awaiting proof verification"
                : "Verified as " + verifiedMember.serverId();
            new NexoriPeerEntryElement(
                peer.connectionAddress(),
                description,
                peer.connectionAddress().equals(selectedPeerAddress)
            ).addButton(commands, events, "#PeerList[" + i + "]", playerRef);
            bindIndex(events, "#PeerList[" + i + "]", PEER_INDEX_BASE + i);
        }

        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#SavePeerButton",
            new EventData()
                .append("Action", Action.SAVE_PEER.name())
                .append("@ConnectionAddress", "#ConnectionAddressField #Input.Value")
        );
        events.addEventBinding(
            CustomUIEventBindingType.Activating,
            "#CancelAddPeerButton",
            new EventData().append("Action", Action.CANCEL_ADD_PEER.name())
        );

        applyServersRightPanel(commands);
    }

    private void buildRulesBody(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        InventoryTransferService inventoryTransferService = plugin.getInventoryTransferService();
        ServerPolicySummary cachedPolicy = plugin.getServerPolicyCacheService().find(selectedPeerAddress).orElse(null);

        commands.set("#PeerCountText.Text", peers.size() + " configured server(s)");
        commands.set("#StatusText.Text", statusText);
        commands.set("#StatusText.Visible", !statusText.isBlank());

        for (int i = 0; i < peers.size(); i++) {
            ConfiguredPeer peer = peers.get(i);
            ServerPolicySummary summary = plugin.getServerPolicyCacheService().find(peer.connectionAddress()).orElse(null);
            String description = summary == null
                ? "No confirmed rules cached yet"
                : "Confirmed " + TIME_FORMAT.format(Instant.ofEpochMilli(summary.confirmedAtEpochMillis()));
            new NexoriPeerEntryElement(
                peer.connectionAddress(),
                description,
                peer.connectionAddress().equals(selectedPeerAddress)
            ).addButton(commands, events, "#PeerList[" + i + "]", playerRef);
            bindIndex(events, "#PeerList[" + i + "]", PEER_INDEX_BASE + i);
        }

        commands.set("#LocalRecoveryModeText.Text", inventoryTransferService.isRecoveryEnabled() ? "Enabled" : "Disabled");
        commands.set("#LocalBackupLimitText.Text", inventoryTransferService.getMaxBackupsPerPlayer() + " backup(s) per player");
        commands.set("#ToggleRecoveryButton.Visible", canManageThisServer);
        commands.set("#DecreaseBackupLimitButton.Visible", canManageThisServer);
        commands.set("#IncreaseBackupLimitButton.Visible", canManageThisServer);
        commands.set("#RefreshSelectedRulesButton.Visible", canManageThisServer && !selectedPeerAddress.isBlank());
        commands.set("#ApplyLocalRulesButton.Visible", canManageThisServer && !selectedPeerAddress.isBlank());

        commands.set("#SelectedRulePeerAddress.Text", selectedPeerAddress.isBlank() ? "<no server selected>" : selectedPeerAddress);
        commands.set("#SelectedRulePeerConfirmation.Text", cachedPolicy == null
            ? "No confirmed rules cached yet for this server."
            : "Confirmed from " + cachedPolicy.remoteServerId() + " at " + TIME_FORMAT.format(Instant.ofEpochMilli(cachedPolicy.confirmedAtEpochMillis())));
        commands.set("#SelectedRulePeerRecovery.Text", cachedPolicy == null ? "<unknown>" : cachedPolicy.recoveryEnabled() ? "Enabled" : "Disabled");
        commands.set("#SelectedRulePeerBackupLimit.Text", cachedPolicy == null ? "<unknown>" : Integer.toString(cachedPolicy.maxBackupsPerPlayer()));

        bindIndex(events, "#ToggleRecoveryButton", ACTION_RULES_TOGGLE_RECOVERY_INDEX);
        bindIndex(events, "#DecreaseBackupLimitButton", ACTION_RULES_LIMIT_DECREASE_INDEX);
        bindIndex(events, "#IncreaseBackupLimitButton", ACTION_RULES_LIMIT_INCREASE_INDEX);
        bindIndex(events, "#RefreshSelectedRulesButton", ACTION_RULES_REFRESH_SELECTED_INDEX);
        bindIndex(events, "#ApplyLocalRulesButton", ACTION_RULES_APPLY_SELECTED_INDEX);
    }

    private void buildTargetsBody(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        List<DestinationTargetDefinition> targets = plugin.getDestinationTargetService().list();
        long naturalSpawnCount = targets.stream().filter(target -> target.kind() == DestinationTargetKind.NATURAL_SPAWN).count();
        long coordinateCount = targets.stream().filter(target -> target.kind() == DestinationTargetKind.COORDINATE).count();
        long portalCount = targets.stream().filter(target -> target.kind() == DestinationTargetKind.PORTAL).count();

        commands.set("#TargetsSummaryText.Text", targets.size() + " total destination target(s) on this server.");
        commands.set("#TargetsDetailText.Text", "Natural spawn: " + naturalSpawnCount
            + " / Coordinate: " + coordinateCount
            + " / Portal: " + portalCount
            + ". Use the target manager for create/edit/remove flows.");
        bindIndex(events, "#OpenTargetManagerButton", ACTION_OPEN_TARGET_MANAGER_INDEX);
    }

    private void buildPortalsBody(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        commands.set("#PortalCountText.Text", portals.size() + " portal(s)");
        for (int i = 0; i < portals.size(); i++) {
            PortalInstanceDefinition portal = portals.get(i);
            String description = portal.worldName()
                + " @ (" + portal.blockPosition().getX() + ", " + portal.blockPosition().getY() + ", " + portal.blockPosition().getZ() + ")";
            new NexoriPortalEntryElement(
                portal.displayName(),
                description,
                portal.portalId().equals(selectedPortalId)
            ).addButton(commands, events, "#PortalList[" + i + "]", playerRef);
            bindIndex(events, "#PortalList[" + i + "]", PORTAL_INDEX_BASE + i);
        }

        PortalInstanceDefinition selectedPortal = findSelectedPortal();
        TriggerBindingDefinition binding = selectedPortal == null
            ? null
            : plugin.getTriggerBindingService().findPortalCollisionBinding(selectedPortal.portalId()).orElse(null);

        commands.set("#SelectedPortalEmpty.Visible", selectedPortal == null);
        commands.set("#SelectedPortalDetail.Visible", selectedPortal != null);
        commands.set("#SelectedPortalName.Text", selectedPortal == null ? "" : selectedPortal.displayName());
        commands.set("#SelectedPortalWorld.Text", selectedPortal == null ? "" : selectedPortal.worldName());
        commands.set("#SelectedPortalPosition.Text", selectedPortal == null
            ? ""
            : "(" + selectedPortal.blockPosition().getX() + ", " + selectedPortal.blockPosition().getY() + ", " + selectedPortal.blockPosition().getZ() + ")");
        commands.set("#SelectedPortalEnabled.Text", selectedPortal == null ? "" : selectedPortal.enabled() ? "Enabled" : "Disabled");
        commands.set("#SelectedPortalTarget.Text", selectedPortal == null ? "" : selectedPortal.autoDestinationTargetId());
        commands.set("#SelectedPortalBinding.Text", binding == null
            ? "This portal does not have a saved collision binding yet."
            : "Binding: " + binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId()
                + " / profile " + binding.travelProfileId());
    }

    private void applyServersRightPanel(@Nonnull UICommandBuilder commands) {
        commands.set("#StatusText.Text", statusText);
        commands.set("#StatusText.Visible", !statusText.isBlank());
        commands.set("#DetailsHeader.Text", addPeerMode ? "ADD PEER" : "PEER DETAILS");

        if (addPeerMode) {
            commands.set("#SelectedDetail.Visible", false);
            commands.set("#SelectedEmpty.Visible", false);
            commands.set("#AddPeerForm.Visible", true);
            commands.set("#ConnectionAddressField #Input.Value", addPeerConnectionAddress);
            return;
        }

        commands.set("#AddPeerForm.Visible", false);

        ConfiguredPeer selectedPeer = findSelectedPeer();
        if (selectedPeer == null) {
            commands.set("#SelectedDetail.Visible", false);
            commands.set("#SelectedEmpty.Visible", true);
            commands.set("#RemovePeerButton.Visible", false);
            return;
        }

        BundleMember verifiedMember = findVerifiedMember(selectedPeer.connectionAddress());
        commands.set("#SelectedDetail.Visible", true);
        commands.set("#SelectedEmpty.Visible", false);
        commands.set("#SelectedPeerAddress.Text", selectedPeer.connectionAddress());
        commands.set("#SelectedPeerHost.Text", selectedPeer.host());
        commands.set("#SelectedPeerPort.Text", Integer.toString(selectedPeer.port()));
        commands.set("#SelectedPeerVerification.Text", verifiedMember == null
            ? "Not verified yet"
            : verifiedMember.serverId() + " / " + shorten(verifiedMember.fingerprint()));
        commands.set("#BundleStatusText.Text", "Bundle v"
            + plugin.getBootstrapCoordinator().getTrustBundle().bundleVersion()
            + " / members "
            + plugin.getBootstrapCoordinator().getTrustBundle().members().size());
        commands.set("#RemovePeerButton.Visible", canManageThisServer);
    }

    private void startPolicyRefresh(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player
    ) throws IOException, GeneralSecurityException {
        ConfiguredPeer selectedPeer = requireSelectedPeer();
        plugin.getServerPolicySyncService().refresh(
            playerRef,
            selectedPeer,
            player.getWorld().getName(),
            captureCurrentTransform(store, ref),
            resumeMenu(MenuTab.RULES, selectedPeer.connectionAddress(), selectedPortalId)
        );
        player.sendMessage(Message.raw("Nexori is refreshing rules from " + selectedPeer.connectionAddress() + "..."));
    }

    private void startPolicyApply(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player
    ) throws IOException, GeneralSecurityException {
        ConfiguredPeer selectedPeer = requireSelectedPeer();
        InventoryTransferService inventoryTransferService = plugin.getInventoryTransferService();
        plugin.getServerPolicySyncService().apply(
            playerRef,
            selectedPeer,
            player.getWorld().getName(),
            captureCurrentTransform(store, ref),
            inventoryTransferService.isRecoveryEnabled(),
            inventoryTransferService.getMaxBackupsPerPlayer(),
            resumeMenu(MenuTab.RULES, selectedPeer.connectionAddress(), selectedPortalId)
        );
        player.sendMessage(Message.raw("Nexori is applying this server's rules to " + selectedPeer.connectionAddress() + "..."));
    }

    @Nonnull
    private UiResumeAction resumeMenu(
        @Nonnull MenuTab activeTab,
        @Nonnull String selectedPeerAddress,
        @Nonnull String selectedPortalId
    ) {
        return (resumeRef, resumeStore, resumePlayerRef, resumePlayer) -> NexoriMenuPage.open(
            resumeRef,
            resumeStore,
            resumePlayerRef,
            resumePlayer,
            plugin,
            activeTab,
            selectedPeerAddress,
            selectedPortalId,
            false,
            "",
            ""
        );
    }

    @Nonnull
    private ConfiguredPeer requireSelectedPeer() {
        ConfiguredPeer selectedPeer = findSelectedPeer();
        if (selectedPeer == null) {
            throw new IllegalStateException("Select a trusted server first.");
        }
        return selectedPeer;
    }

    private BundleMember findVerifiedMember(@Nonnull String connectionAddress) {
        for (BundleMember member : plugin.getBootstrapCoordinator().getTrustBundle().members()) {
            if (connectionAddress.equals(member.connectionAddress())) {
                return member;
            }
        }
        return null;
    }

    private ConfiguredPeer findSelectedPeer() {
        for (ConfiguredPeer peer : peers) {
            if (peer.connectionAddress().equals(selectedPeerAddress)) {
                return peer;
            }
        }
        return null;
    }

    private PortalInstanceDefinition findSelectedPortal() {
        for (PortalInstanceDefinition portal : portals) {
            if (portal.portalId().equals(selectedPortalId)) {
                return portal;
            }
        }
        return null;
    }

    private void bindIndex(@Nonnull UIEventBuilder events, @Nonnull String selector, int index) {
        EventData data = EventData.of("Index", Integer.toString(index));
        String[] selectorCandidates = new String[] {selector, selector + " #Button"};

        for (String candidate : selectorCandidates) {
            try {
                events.addEventBinding(CustomUIEventBindingType.Activating, candidate, data, false);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private void reopen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        Player player,
        @Nonnull MenuTab activeTab,
        String selectedPeerAddress,
        String selectedPortalId,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        open(
            ref,
            store,
            playerRef,
            player,
            plugin,
            activeTab,
            selectedPeerAddress,
            selectedPortalId,
            addPeerMode,
            addPeerConnectionAddress,
            statusText
        );
    }

    @Nonnull
    private static String resolveSelectedPeerAddress(@Nonnull List<ConfiguredPeer> peers, String requestedPeerAddress) {
        String normalizedRequestedPeerAddress = safe(requestedPeerAddress).trim().toLowerCase();
        if (!normalizedRequestedPeerAddress.isBlank()) {
            for (ConfiguredPeer peer : peers) {
                if (peer.connectionAddress().equals(normalizedRequestedPeerAddress)) {
                    return normalizedRequestedPeerAddress;
                }
            }
        }
        return peers.isEmpty() ? "" : peers.getFirst().connectionAddress();
    }

    @Nonnull
    private static String resolveSelectedPortalId(@Nonnull List<PortalInstanceDefinition> portals, String requestedPortalId) {
        String normalizedRequestedPortalId = safe(requestedPortalId).trim().toLowerCase();
        if (!normalizedRequestedPortalId.isBlank()) {
            for (PortalInstanceDefinition portal : portals) {
                if (portal.portalId().equals(normalizedRequestedPortalId)) {
                    return normalizedRequestedPortalId;
                }
            }
        }
        return portals.isEmpty() ? "" : portals.getFirst().portalId();
    }

    @Nonnull
    private Transform captureCurrentTransform(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
        if (transformComponent == null) {
            throw new IllegalStateException("Could not read the live player position for Nexori server rules sync.");
        }

        Vector3f rotation = transformComponent.getRotation();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            rotation = headRotation.getRotation();
        }
        return new Transform(transformComponent.getPosition(), rotation);
    }

    private static Integer parseIndex(String rawIndex) {
        String normalized = safe(rawIndex).trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Action parseAction(String rawAction) {
        String normalized = safe(rawAction).trim();
        if (normalized.isBlank()) {
            return null;
        }
        try {
            return Action.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Nonnull
    private static String shorten(@Nonnull String value) {
        return value.length() <= 12 ? value : value.substring(0, 12) + "...";
    }

    @Nonnull
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public enum MenuTab {
        SERVERS("Servers"),
        RULES("Rules"),
        TARGETS("Targets"),
        PORTALS("Portals");

        private final String label;

        MenuTab(String label) {
            this.label = label;
        }
    }

    private enum Action {
        SAVE_PEER,
        CANCEL_ADD_PEER
    }

    public static final class PageData {

        private static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(
                new KeyedCodec<>("Index", Codec.STRING),
                (pageData, indexRaw) -> pageData.indexRaw = indexRaw,
                pageData -> pageData.indexRaw
            )
            .add()
            .append(
                new KeyedCodec<>("Action", Codec.STRING),
                (pageData, actionRaw) -> pageData.actionRaw = actionRaw,
                pageData -> pageData.actionRaw
            )
            .add()
            .append(
                new KeyedCodec<>("@ConnectionAddress", Codec.STRING),
                (pageData, connectionAddress) -> pageData.connectionAddress = connectionAddress,
                pageData -> pageData.connectionAddress
            )
            .add()
            .build();

        private String indexRaw = "";
        private String actionRaw = "";
        private String connectionAddress = "";
    }
}
