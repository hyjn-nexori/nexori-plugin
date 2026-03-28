package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.entity.entities.player.pages.choices.ChoiceElement;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;

public final class NexoriMenuPage extends InteractiveCustomUIPage<NexoriMenuPage.PageData> {

    private static final int TAB_INDEX_BASE = 1000;
    private static final int PEER_INDEX_BASE = 2000;
    private static final int ACTION_ADD_INDEX = 3000;
    private static final int ACTION_START_INDEX = 3001;
    private static final int ACTION_REMOVE_INDEX = 3002;

    private final PlayerRef playerRef;
    private final String adminPermission;
    private final ConfiguredPeerService configuredPeerService;
    private final BootstrapCoordinator bootstrapCoordinator;
    private final ChoiceElement[] tabElements;
    private final ConfiguredPeer[] peers;
    private final boolean canManageThisServer;
    private final String selectedPeerAddress;
    private final boolean addPeerMode;
    private final String addPeerConnectionAddress;
    private final String statusText;

    private NexoriMenuPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull String adminPermission,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull BootstrapCoordinator bootstrapCoordinator,
        @Nonnull ChoiceElement[] tabElements,
        @Nonnull ConfiguredPeer[] peers,
        boolean canManageThisServer,
        @Nonnull String selectedPeerAddress,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.adminPermission = adminPermission;
        this.configuredPeerService = configuredPeerService;
        this.bootstrapCoordinator = bootstrapCoordinator;
        this.tabElements = tabElements;
        this.peers = peers;
        this.canManageThisServer = canManageThisServer;
        this.selectedPeerAddress = selectedPeerAddress;
        this.addPeerMode = addPeerMode;
        this.addPeerConnectionAddress = addPeerConnectionAddress;
        this.statusText = statusText;
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull String adminPermission,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull BootstrapCoordinator bootstrapCoordinator
    ) {
        show(
            ref,
            store,
            playerRef,
            player,
            adminPermission,
            configuredPeerService,
            bootstrapCoordinator,
            null,
            false,
            "",
            ""
        );
    }

    private static void show(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull String adminPermission,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull BootstrapCoordinator bootstrapCoordinator,
        String selectedPeerAddress,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        PageManager pages = player == null ? null : player.getPageManager();
        if (pages == null) {
            playerRef.sendMessage(Message.raw("nexorimenu: PageManager not available."));
            return;
        }

        boolean canManageThisServer = NexoriOperatorAccess.canManage(playerRef, player, adminPermission + ".admin");
        pages.openCustomPage(
            ref,
            store,
            create(
                playerRef,
                adminPermission,
                configuredPeerService,
                bootstrapCoordinator,
                selectedPeerAddress,
                canManageThisServer,
                addPeerMode,
                addPeerConnectionAddress,
                statusText
            )
        );
    }

    @Nonnull
    private static NexoriMenuPage create(
        @Nonnull PlayerRef playerRef,
        @Nonnull String adminPermission,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull BootstrapCoordinator bootstrapCoordinator,
        String selectedPeerAddress,
        boolean canManageThisServer,
        boolean addPeerMode,
        @Nonnull String addPeerConnectionAddress,
        @Nonnull String statusText
    ) {
        List<ConfiguredPeer> savedPeers = configuredPeerService.list();
        String resolvedSelectedPeerAddress = resolveSelectedPeerAddress(savedPeers, selectedPeerAddress);

        ChoiceElement[] tabElements = new ChoiceElement[] {
            new NexoriTabElement("Peers", true)
        };

        return new NexoriMenuPage(
            playerRef,
            adminPermission,
            configuredPeerService,
            bootstrapCoordinator,
            tabElements,
            savedPeers.toArray(ConfiguredPeer[]::new),
            canManageThisServer,
            resolvedSelectedPeerAddress,
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
        commands.append("#BodyHost", "Pages/Nexori/NexoriPeersBody.ui");

        for (int i = 0; i < tabElements.length; i++) {
            String selector = "#TabsList[" + i + "]";
            tabElements[i].addButton(commands, events, selector, playerRef);
            bindIndex(events, selector, TAB_INDEX_BASE + i);
        }

        buildPeersBody(commands, events);
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
        if (index == null || index == TAB_INDEX_BASE) {
            return;
        }

        if (index == ACTION_ADD_INDEX) {
            if (!canManageThisServer) {
                return;
            }
            show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, selectedPeerAddress, true, "", "");
            return;
        }

        if (index == ACTION_START_INDEX) {
            if (!canManageThisServer) {
                return;
            }

            BootstrapCoordinator.StartResult result = bootstrapCoordinator.start(playerRef);
            if (result.started()) {
                PageManager pages = player.getPageManager();
                if (pages != null) {
                    pages.setPage(ref, store, Page.None);
                }
            } else {
                show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, selectedPeerAddress, false, "", result.message());
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
                configuredPeerService.remove(selectedPeer.connectionAddress());
                show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, null, false, "", "Removed " + selectedPeer.connectionAddress() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, selectedPeerAddress, false, "", "Failed to remove peer: " + exception.getMessage());
            }
            return;
        }

        if (index >= PEER_INDEX_BASE && index < PEER_INDEX_BASE + peers.length) {
            ConfiguredPeer selectedPeer = peers[index - PEER_INDEX_BASE];
            show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, selectedPeer.connectionAddress(), false, "", statusText);
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
            show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, selectedPeerAddress, false, "", "");
            return;
        }

        if (action == Action.SAVE_PEER) {
            try {
                ConfiguredPeer savedPeer = configuredPeerService.add(safe(data.connectionAddress).trim());
                show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, savedPeer.connectionAddress(), false, "", "Saved " + savedPeer.connectionAddress() + ".");
            } catch (IOException | IllegalArgumentException exception) {
                show(ref, store, playerRef, player, adminPermission, configuredPeerService, bootstrapCoordinator, selectedPeerAddress, true, safe(data.connectionAddress).trim(), exception.getMessage());
            }
        }
    }

    private void buildPeersBody(@Nonnull UICommandBuilder commands, @Nonnull UIEventBuilder events) {
        commands.set("#AddPeerButton.Visible", canManageThisServer);
        commands.set("#RunBootstrapButton.Visible", canManageThisServer && peers.length > 0);
        commands.set("#PeerCountText.Text", peers.length + " saved peer(s)");

        bindIndex(events, "#AddPeerButton", ACTION_ADD_INDEX);
        bindIndex(events, "#RunBootstrapButton", ACTION_START_INDEX);
        bindIndex(events, "#RemovePeerButton", ACTION_REMOVE_INDEX);

        for (int i = 0; i < peers.length; i++) {
            ConfiguredPeer peer = peers[i];
            BundleMember verifiedMember = findVerifiedMember(peer.connectionAddress());
            String description = verifiedMember == null
                ? "Awaiting proof verification"
                : "Verified as " + verifiedMember.serverId();
            ChoiceElement peerElement = new NexoriPeerEntryElement(
                peer.connectionAddress(),
                description,
                peer.connectionAddress().equals(selectedPeerAddress)
            );
            String selector = "#PeerList[" + i + "]";
            peerElement.addButton(commands, events, selector, playerRef);
            bindIndex(events, selector, PEER_INDEX_BASE + i);
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

        applyRightPanel(commands);
    }

    private void applyRightPanel(@Nonnull UICommandBuilder commands) {
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
            + bootstrapCoordinator.getTrustBundle().bundleVersion()
            + " / members "
            + bootstrapCoordinator.getTrustBundle().members().size());
        commands.set("#RemovePeerButton.Visible", canManageThisServer);
    }

    private BundleMember findVerifiedMember(@Nonnull String connectionAddress) {
        for (BundleMember member : bootstrapCoordinator.getTrustBundle().members()) {
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
