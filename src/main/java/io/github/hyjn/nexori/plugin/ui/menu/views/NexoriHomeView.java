package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIPadding;
import au.ellie.hyui.builders.ReorderableListBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2Page;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuSetupState;

import javax.annotation.Nonnull;
import java.security.GeneralSecurityException;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.card;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.dismissPage;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.inputField;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.label;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.scrollList;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.selectionSummaryCard;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.spacerX;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.spacerY;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuInputIds.HOME_DISPLAY_NAME_INPUT_ID;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuInputIds.HOME_SERVER_ADDRESS_INPUT_ID;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.CONTENT_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_ACTION_BUTTON_TOP;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_BLOCK_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_INPUT_FIELD_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.HOME_SERVER_CARD_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.SERVER_ACTION_EDIT_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.SERVER_ACTION_REMOVE_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BAD;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BAD_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.GOOD;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.GOOD_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.INFO;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.INFO_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.MUTED;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.PANEL_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.SERVER_CARD_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.SUBTITLE;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.TITLE;

/**
 * Renders the Nexori home dashboard and server setup workspace.
 *
 * <p>This view owns the UI specific to the home workspace, including secure server setup,
 * trust bundle status, and local server management. NexoriMenuV2Page should only route
 * into this view rather than reabsorbing this section's UI. Reusable widgets belong in
 * {@code ui.menu.components}.
 */
public final class NexoriHomeView {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private NexoriHomeView() {
    }

    @Nonnull
    public static NexoriMenuSetupState buildSetupState(@Nonnull NexoriPlugin plugin, @Nonnull List<ConfiguredPeer> peers) {
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
            return new NexoriMenuSetupState(true, false, true, localConnectionAddress, trusted, "Initial Setup running", "Nexori is verifying peers and rebuilding the active trust bundle.", "Wait for the run to finish, then reopen Servers.", INFO, INFO_BG);
        }
        if (!hasBundle) {
            String detail = peers.isEmpty()
                ? "Add every server that should belong to this network here, including the current server, before running the first Initial Setup."
                : "The first trust bundle does not exist yet. Review the servers below and run Initial Setup.";
            if (localMissing) {
                detail += " Include this server as well (" + localConnectionAddress + ").";
            }
            return new NexoriMenuSetupState(true, !peers.isEmpty(), true, localConnectionAddress, trusted, peers.isEmpty() ? "Waiting for servers" : "Ready for first bundle", detail, "Until the first trust bundle exists, Servers is the only unlocked view.", peers.isEmpty() ? BAD : INFO, peers.isEmpty() ? BAD_BG : INFO_BG);
        }
        if (dirty) {
            String detail = "The saved server list on this machine no longer matches the active trust bundle.";
            if (localMissing) {
                detail += " The current server is also missing from this local list (" + localConnectionAddress + ").";
            }
            return new NexoriMenuSetupState(false, !peers.isEmpty(), true, localConnectionAddress, trusted, "Bundle needs re-run", detail, "Run Initial Setup again after every add/remove.", BAD, BAD_BG);
        }
        String detail = peers.isEmpty()
            ? "This server is using the active trust bundle to render the secure network list."
            : "The local server list matches the active trust bundle. Nexori is ready for secure travel.";
        return new NexoriMenuSetupState(false, false, false, localConnectionAddress, trusted, "Trusted network ready", detail, "Bundle v" + bundle.bundleVersion() + " updated at " + TIME_FORMAT.format(Instant.ofEpochMilli(bundle.updatedAtEpochMillis())) + ".", GOOD, GOOD_BG);
    }

    @Nonnull
    public static ReorderableListBuilder render(
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
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
        scroll.addChild(homeSetupCard(context, setup, innerWidth, setupCardHeight));
        scroll.addChild(spacerY(16));
        scroll.addChild(homeServersTablesCard(context, setup, innerWidth, serversTablesHeight, scrollId + "-servers"));
        return scroll;
    }

    @Nonnull
    private static GroupBuilder homeSetupCard(
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
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

        boolean editingServer = !context.state().editingServerAddress().isBlank();
        String displayNameValue = context.state().pendingServerDisplayName().isBlank() ? "My Server" : context.state().pendingServerDisplayName();
        String addressValue = context.state().pendingServerAddress().isBlank() ? "127.0.0.1" : context.state().pendingServerAddress();

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
                            ConfiguredPeer updated = context.plugin().getConfiguredPeerService().update(context.state().editingServerAddress(), displayName, address);
                            NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().clearedPendingServerDraft().withStatusText("Updated " + updated.displayName() + " (" + updated.connectionAddress() + ")."));
                        } else {
                            ConfiguredPeer added = context.plugin().getConfiguredPeerService().add(displayName, address);
                            NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().clearedPendingServerDraft().withStatusText("Added " + added.displayName() + " (" + added.connectionAddress() + ")."));
                        }
                    } catch (IOException | IllegalArgumentException exception) {
                        NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().withPendingServerDisplayName(displayName).withPendingServerAddress(address).withStatusText((editingServer ? "Could not rename server: " : "Could not add server: ") + exception.getMessage()));
                    }
                })
        );
        if (editingServer) {
            actionRow.addChild(spacerX(12));
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText("CANCEL")
                    .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                    .onClick((ignored, ctx) -> NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().clearedPendingServerDraft().withStatusText("Edit cancelled.")))
            );
        } else if (setup.showBootstrapAction()) {
            actionRow.addChild(spacerX(12));
            actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                    .withText("RUN INITIAL SETUP")
                    .withAnchor(new HyUIAnchor().setWidth(230).setHeight(HOME_INPUT_FIELD_H))
                    .withDisabled(!setup.canRunBootstrap())
                    .onClick((ignored, ctx) -> {
                        BootstrapCoordinator.StartResult result = context.plugin().getBootstrapCoordinator().start(context.playerRef());
                        if (result.started()) {
                            context.plugin().getBootstrapCoordinator().requestMenuResume(context.playerRef().getUuid());
                            dismissPage(context.player(), context.ref(), context.store());
                            return;
                        }
                        NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().withStatusText(result.message()));
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
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
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

        List<HomeServerEntry> bundleServers = buildBundleHomeServers(context.plugin(), context.peers(), setup);

        container.addChild(spacerY(outerGap));
        GroupBuilder headerRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(availableWidth).setHeight(72));
        headerRow.addChild(selectionSummaryCard("LOCAL PEERS", "Source for Initial Setup.", columnWidth, !context.peers().isEmpty()));
        headerRow.addChild(spacerX(columnGap));
        headerRow.addChild(selectionSummaryCard("TRUST BUNDLE", "Travel across the active network.", columnWidth, !bundleServers.isEmpty()));
        container.addChild(headerRow);
        container.addChild(spacerY(12));

        GroupBuilder columnsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(availableWidth).setHeight(viewportHeight));
        columnsRow.addChild(localPeersColumnScroll(context, columnWidth, viewportHeight, scrollId + "-local"));
        columnsRow.addChild(spacerX(columnGap));
        columnsRow.addChild(bundleServersColumnScroll(context, setup, bundleServers, columnWidth, viewportHeight, scrollId + "-bundle"));
        container.addChild(columnsRow);
        container.addChild(spacerY(outerGap));
        return container;
    }

    @Nonnull
    private static ReorderableListBuilder localPeersColumnScroll(
        @Nonnull NexoriMenuRenderContext context,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int rowHeight = 64;
        int contentHeight = context.peers().isEmpty()
            ? height
            : 8 + context.peers().size() * rowHeight + Math.max(0, context.peers().size() - 1) * 8 + 8;
        ReorderableListBuilder scroll = scrollList(width, height, Math.max(height, contentHeight), scrollId, true);
        scroll.addChild(spacerY(8));
        if (context.peers().isEmpty()) {
            scroll.addChild(label("No local peers saved yet.", MUTED, width - 16));
            return scroll;
        }
        for (int index = 0; index < context.peers().size(); index++) {
            ConfiguredPeer peer = context.peers().get(index);
            scroll.addChild(localPeerRowCard(context, peer, width - 16, rowHeight));
            if (index + 1 < context.peers().size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static GroupBuilder localPeerRowCard(
        @Nonnull NexoriMenuRenderContext context,
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
                .onClick((ignored, ctx) -> NexoriMenuV2Page.open(
                    context.ref(),
                    context.store(),
                    context.playerRef(),
                    context.player(),
                    context.plugin(),
                    context.state().withPendingServerDisplayName(peer.displayName())
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
                        boolean removed = context.plugin().getConfiguredPeerService().remove(peer.connectionAddress());
                        NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().clearedPendingServerDraft().withStatusText(removed ? "Removed " + peer.displayName() + "." : peer.displayName() + " was already removed."));
                    } catch (IOException | IllegalArgumentException exception) {
                        NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().withStatusText("Could not remove " + peer.displayName() + ": " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static ReorderableListBuilder bundleServersColumnScroll(
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
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
            scroll.addChild(bundlePeerRowCard(context, setup, servers.get(index), width - 16, rowHeight));
            if (index + 1 < servers.size()) {
                scroll.addChild(spacerY(8));
            }
        }
        return scroll;
    }

    @Nonnull
    private static GroupBuilder bundlePeerRowCard(
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
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
                        context.plugin().getSecureTravelService().travelToServer(context.playerRef(), ConfiguredPeer.parse(peer.connectionAddress()), TravelProfileType.KEEP_INVENTORY.id(), "");
                        dismissPage(context.player(), context.ref(), context.store());
                    } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
                        NexoriMenuV2Page.open(context.ref(), context.store(), context.playerRef(), context.player(), context.plugin(), context.state().withStatusText("Could not start travel to " + peer.displayName() + ": " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    private static List<HomeServerEntry> buildBundleHomeServers(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull NexoriMenuSetupState setup
    ) {
        java.util.Map<String, ConfiguredPeer> localPeersByAddress = new java.util.LinkedHashMap<>();
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
                entries.add(new HomeServerEntry(displayName, connectionAddress, local));
            }
        }

        return entries;
    }

    private record HomeServerEntry(
        @Nonnull String displayName,
        @Nonnull String connectionAddress,
        boolean local
    ) {
    }
}
