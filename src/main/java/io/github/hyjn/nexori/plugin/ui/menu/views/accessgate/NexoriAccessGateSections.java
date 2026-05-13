package io.github.hyjn.nexori.plugin.ui.menu.views.accessgate;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.Alignment;
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
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingKind;
import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateConfigDocument;
import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateBypassPlayer;
import io.github.hyjn.nexori.plugin.backend.BackendMatchmakingConfig;
import io.github.hyjn.nexori.plugin.backend.BackendResultReportingService;
import io.github.hyjn.nexori.plugin.backend.BackendSyncService;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotDefinition;
import io.github.hyjn.nexori.plugin.minigame.LastPlayerAliveArenaMatchResolutionTrigger;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.policy.ServerPolicySummary;
import io.github.hyjn.nexori.plugin.policy.ServerRuleGroupDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2Page;
import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2View;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuSetupState;
import io.github.hyjn.nexori.plugin.ui.menu.state.BackendConfigDraft;
import io.github.hyjn.nexori.plugin.ui.menu.state.BackendWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.AccessGateWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.MinigameWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;
import io.github.hyjn.nexori.plugin.ui.menu.state.PortalWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.QueueBackfillDraft;
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
import java.util.concurrent.ConcurrentHashMap;

import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriMenuSections;

/**
 * Section implementation extracted from the Nexori V2 menu for this feature area.
 */
public final class NexoriAccessGateSections extends NexoriMenuSections {

    private NexoriAccessGateSections() {
    }

    public static GroupBuilder accessGateTabs(
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


    public static ReorderableListBuilder buildAccessGateScroll(
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
    static ReorderableListBuilder buildAccessGateMainScroll(
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
    static GroupBuilder accessGateSetupCard(
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
    static ReorderableListBuilder buildAccessGateManualConnectionsScroll(
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
    static GroupBuilder accessGateManualConnectionsSetupCard(
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
    static GroupBuilder accessGateTrustedServersTable(
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
    static GroupBuilder accessGateTrustedServerRow(
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

    static void saveAccessGateManualRedirectTarget(
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
    static GroupBuilder accessGateToggleField(
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
    static GroupBuilder accessGateToggleField(
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
    static GroupBuilder accessGateConnectedPlayersTable(
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
    static GroupBuilder accessGateConnectedPlayerRow(
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
    static GroupBuilder accessGateBypassPlayersTable(
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
    static GroupBuilder accessGateBypassRow(
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
    static List<AccessGateConnectedPlayer> buildAccessGateConnectedPlayers(@Nonnull NexoriPlugin plugin) {
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

    static Integer parseInteger(@Nonnull String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    @Nonnull
    static GroupBuilder accessGateMaxPlayersCard(
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
    static GroupBuilder accessGateReservedSlotsCard(
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
    static GroupBuilder accessGateFullMessageCard(
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
    static GroupBuilder accessGateSavableInputCard(
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


}
