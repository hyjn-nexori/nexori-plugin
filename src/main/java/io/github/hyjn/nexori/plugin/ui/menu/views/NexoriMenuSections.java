package io.github.hyjn.nexori.plugin.ui.menu.views;

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
import io.github.hyjn.nexori.plugin.ui.menu.state.AfkDetectionPolicyDraft;
import io.github.hyjn.nexori.plugin.ui.menu.state.MinigameWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;
import io.github.hyjn.nexori.plugin.ui.menu.state.PortalWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.state.QueueBackfillDraft;
import io.github.hyjn.nexori.plugin.ui.menu.state.RulesWorkspaceTab;
import io.github.hyjn.nexori.plugin.ui.menu.views.portal.NexoriPortalSections;

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

public class NexoriMenuSections {

    protected static final DateTimeFormatter SYNC_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    protected static final Gson GSON = new Gson();

    protected static final int PAGE_W = 1900;
    protected static final int PAGE_H = 1040;
    protected static final int BODY_W = PAGE_W - 52;
    protected static final int BODY_H = PAGE_H - 98;
    protected static final int SIDEBAR_W = 320;
    protected static final int STATUS_H = 96;
    protected static final int CONTENT_W = BODY_W - SIDEBAR_W - 20;
    protected static final int CONTENT_H = BODY_H - STATUS_H - 12;
    protected static final int HOME_SERVER_CARD_H = 64;
    protected static final int SERVER_ACTION_CURRENT_W = 150;
    protected static final int SERVER_ACTION_EDIT_W = 90;
    protected static final int SERVER_ACTION_REMOVE_W = 120;
    protected static final int SERVER_ACTION_EXTRA_LEFT_SHIFT = SERVER_ACTION_REMOVE_W / 2;
    protected static final int TRAVEL_SERVER_GROUP_GAP = 64;
    protected static final int TRAVEL_SECTION_INSET = 12;
    protected static final int HOME_INPUT_BLOCK_H = 76;
    protected static final int HOME_INPUT_LABEL_H = 18;
    protected static final int HOME_INPUT_LABEL_GAP = 8;
    protected static final int HOME_INPUT_FIELD_H = 42;
    protected static final int HOME_INPUT_CONTENT_H = HOME_INPUT_LABEL_H + HOME_INPUT_LABEL_GAP + HOME_INPUT_FIELD_H;
    protected static final int HOME_INPUT_TOP_PADDING = Math.max(0, (HOME_INPUT_BLOCK_H - HOME_INPUT_CONTENT_H) / 2);
    protected static final int HOME_ACTION_BUTTON_BOTTOM_PADDING = 2;
    protected static final int HOME_ACTION_BUTTON_TOP = HOME_INPUT_BLOCK_H - HOME_INPUT_FIELD_H - HOME_ACTION_BUTTON_BOTTOM_PADDING;
    protected static final String HOME_DISPLAY_NAME_INPUT_ID = "nexori-v2-home-display-name";
    protected static final String HOME_SERVER_ADDRESS_INPUT_ID = "nexori-v2-home-server-address";
    protected static final String DESTINATION_DISPLAY_NAME_INPUT_ID = "nexori-v2-destination-display-name";
    protected static final String DESTINATION_RULES_ENGINE_INPUT_ID = "nexori-v2-destination-rules-engine";
    protected static final String DESTINATION_AFK_TIMEOUT_INPUT_ID = "nexori-v2-destination-afk-timeout";
    protected static final String QUEUE_DISPLAY_NAME_INPUT_ID = "nexori-v2-queue-display-name";
    protected static final String QUEUE_MIN_PLAYERS_INPUT_ID = "nexori-v2-queue-min-players";
    protected static final String QUEUE_MAX_PLAYERS_INPUT_ID = "nexori-v2-queue-max-players";
    protected static final String QUEUE_COUNTDOWN_INPUT_ID = "nexori-v2-queue-countdown";
    protected static final String QUEUE_BACKFILL_WINDOW_INPUT_ID = "nexori-v2-queue-backfill-window";
    protected static final String RULE_GROUP_NAME_INPUT_ID = "nexori-v2-rule-group-name";
    protected static final String TARGET_DISPLAY_NAME_INPUT_ID = "nexori-v2-target-display-name";
    protected static final String ACCESS_GATE_MAX_PLAYERS_INPUT_ID = "nexori-v2-access-gate-max-players";
    protected static final String ACCESS_GATE_RESERVED_SLOTS_INPUT_ID = "nexori-v2-access-gate-reserved-slots";
    protected static final String ACCESS_GATE_FULL_MESSAGE_INPUT_ID = "nexori-v2-access-gate-full-message";
    protected static final String ACCESS_GATE_ADD_UUID_INPUT_ID = "nexori-v2-access-gate-add-uuid";
    protected static final String BACKEND_BASE_URL_INPUT_ID = "nexori-v2-backend-base-url";
    protected static final String BACKEND_SERVER_TOKEN_INPUT_ID = "nexori-v2-backend-server-token";
    protected static final String BACKEND_SYNC_INTERVAL_INPUT_ID = "nexori-v2-backend-sync-interval";
    protected static final String BACKEND_REGION_INPUT_ID = "nexori-v2-backend-region";
    protected static final String BACKEND_TIMEOUT_INPUT_ID = "nexori-v2-backend-timeout";
    protected static final String BACKEND_RESULT_RETRY_INTERVAL_INPUT_ID = "nexori-v2-backend-result-retry-interval";
    protected static final String BACKEND_MATCH_STATE_DEBOUNCE_INPUT_ID = "nexori-v2-backend-match-state-debounce";
    protected static final String BACKEND_MATCH_STATE_MAX_COALESCE_INPUT_ID = "nexori-v2-backend-match-state-max-coalesce";
    protected static final String BACKEND_MATCH_STATE_RETRY_INTERVAL_INPUT_ID = "nexori-v2-backend-match-state-retry-interval";
    protected static final String BACKEND_MATCH_STATE_STALE_AFTER_INPUT_ID = "nexori-v2-backend-match-state-stale-after";
    protected static final String DEFAULT_MINIGAME_QUEUE_TRAVEL_PROFILE_ID = "keep_inventory";
    protected static final int DEFAULT_DESTINATION_MAX_SUPPORTED_PLAYERS = 9999;
    protected static final String NEW_RULE_GROUP_ID = "__new__";
    protected static final String LOCAL_SERVER_KEY = ServerRuleGroupDefinition.LOCAL_SERVER_KEY;

    protected static final HyUIPatchStyle ROOT_BG = new HyUIPatchStyle().setColor("#0e1824");
    protected static final HyUIPatchStyle PANEL_BG = new HyUIPatchStyle().setColor("#17273a");
    protected static final HyUIPatchStyle PANEL_ALT_BG = new HyUIPatchStyle().setColor("#20354e");
    protected static final HyUIPatchStyle SERVER_CARD_BG = new HyUIPatchStyle().setColor("#23374f");
    protected static final HyUIPatchStyle BUTTON_BG = new HyUIPatchStyle().setColor("#243a54");
    protected static final HyUIPatchStyle BUTTON_SELECTED_BG = new HyUIPatchStyle().setColor("#486f9f");
    protected static final HyUIPatchStyle BUTTON_ABOUT_BG = new HyUIPatchStyle().setColor("#31496d");
    protected static final HyUIPatchStyle BUTTON_ABOUT_SELECTED_BG = new HyUIPatchStyle().setColor("#5f7fab");
    protected static final HyUIPatchStyle STATUS_BG = new HyUIPatchStyle().setColor("#1a3149");
    protected static final HyUIPatchStyle GOOD_BG = new HyUIPatchStyle().setColor("#1f5c35");
    protected static final HyUIPatchStyle WARN_BG = new HyUIPatchStyle().setColor("#8a5c24");
    protected static final HyUIPatchStyle BAD_BG = new HyUIPatchStyle().setColor("#7b3742");
    protected static final HyUIPatchStyle INFO_BG = new HyUIPatchStyle().setColor("#2d4f75");
    protected static final ScrollbarStyle DEFAULT_SCROLLBAR = ScrollbarStyle.defaultExtraSpacingStyle()
        .withOnlyVisibleWhenHovered(false)
        .withSize(8)
        .withSpacing(4)
        .withBackground(new HyUIPatchStyle().setColor("#132235"))
        .withHandle(new HyUIPatchStyle().setColor("#39577b"))
        .withHoveredHandle(new HyUIPatchStyle().setColor("#5d87bb"))
        .withDraggedHandle(new HyUIPatchStyle().setColor("#76a3dd"));

    protected static final HyUIStyle TITLE = new HyUIStyle().setFontSize(20).setRenderBold(true).setTextColor("#f1f6ff").setWrap(true);
    protected static final HyUIStyle SUBTITLE = new HyUIStyle().setFontSize(14).setRenderBold(true).setTextColor("#bcd2eb").setWrap(true);
    protected static final HyUIStyle BODY = new HyUIStyle().setFontSize(14).setTextColor("#d6e5f7").setWrap(true);
    protected static final HyUIStyle MUTED = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true);
    protected static final HyUIStyle MUTED_CENTER = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true).setAlignment(Alignment.Center);
    protected static final HyUIStyle GOOD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#7de3a6").setWrap(true);
    protected static final HyUIStyle WARN = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ffc66d").setWrap(true);
    protected static final HyUIStyle BAD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ff8b9a").setWrap(true);
    protected static final HyUIStyle INFO = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#8fc7ff").setWrap(true);
    protected static final HyUIStyle GOOD_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#d5ffe2").setWrap(true).setAlignment(Alignment.Center);
    protected static final HyUIStyle WARN_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ffe4ae").setWrap(true).setAlignment(Alignment.Center);
    protected static final HyUIStyle BAD_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ffd7dc").setWrap(true).setAlignment(Alignment.Center);
    protected static final HyUIStyle INFO_CENTER = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#d7ecff").setWrap(true).setAlignment(Alignment.Center);
    protected static final Map<UUID, BackendConfigDraft> BACKEND_CONFIG_DRAFTS = new ConcurrentHashMap<>();
    protected static final Map<UUID, BackendWorkspaceTab> BACKEND_WORKSPACE_TABS = new ConcurrentHashMap<>();
    protected static final Map<UUID, QueueMatchmakingMode> QUEUE_MODE_DRAFTS = new ConcurrentHashMap<>();
    protected static final Map<UUID, QueueBackfillDraft> QUEUE_BACKFILL_DRAFTS = new ConcurrentHashMap<>();
    protected static final Map<UUID, AfkDetectionPolicyDraft> ARENA_AFK_POLICY_DRAFTS = new ConcurrentHashMap<>();

    protected NexoriMenuSections() {
    }

    protected static void open(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef, Player player, @Nonnull NexoriPlugin plugin) {
        NexoriMenuV2Page.open(ref, store, playerRef, player, plugin);
    }

    protected static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State rawState
    ) {
        NexoriMenuV2Page.open(ref, store, playerRef, player, plugin, rawState);
    }

    @Nonnull
    protected static List<ConfiguredPeer> trustedNetworkPeers(@Nonnull NexoriPlugin plugin) {
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
    protected static LocalServerSummary currentLocalServerSummary(@Nonnull NexoriPlugin plugin) {
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
    protected static String localServerId(@Nonnull NexoriPlugin plugin) {
        return plugin.getLocalIdentity().serverId().toString();
    }

    @Nonnull
    protected static ArenaDefinition currentEditedDestination(@Nonnull NexoriPlugin plugin, @Nonnull NexoriMenuV2State state) {
        if (state.editingDestinationId().isBlank()) {
            return null;
        }
        return plugin.getArenaService().find(state.editingDestinationId()).orElse(null);
    }

    protected static QueueDefinition currentEditedQueue(@Nonnull NexoriPlugin plugin, @Nonnull NexoriMenuV2State state) {
        if (state.editingQueueId().isBlank()) {
            return null;
        }
        return plugin.getQueueService().find(state.editingQueueId()).orElse(null);
    }

    protected static InstanceSpawnSlotDefinition currentEditedSpawnSlot(@Nonnull NexoriPlugin plugin, @Nonnull NexoriMenuV2State state) {
        if (state.editingDestinationId().isBlank()) {
            return null;
        }
        return plugin.getInstanceSpawnSlotService().find(state.editingDestinationId()).orElse(null);
    }

    @Nonnull
    protected static List<RemoteServerOption> buildRemoteDestinationServerOptions(
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
            String remoteServerId = discovery == null ? "" : discovery.remoteServerId();
            if (remoteServerId.isBlank()) {
                TrustBundle trustBundle = plugin.getTrustBundleStore().getCurrentBundle();
                remoteServerId = trustBundle.members().stream()
                    .filter(member -> peer.connectionAddress().equalsIgnoreCase(member.connectionAddress()))
                    .map(BundleMember::serverId)
                    .findFirst()
                    .orElse("");
            }
            options.add(new RemoteServerOption(
                peer.displayName(),
                peer.connectionAddress(),
                remoteServerId,
                discovery == null ? List.of() : discovery.targets()
            ));
        }
        return options;
    }

    @Nonnull
    protected static List<RemoteTargetOption> buildRemoteTargetOptions(@Nonnull NexoriPlugin plugin, @Nonnull String connectionAddress) {
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

    protected static void synchronizeTravelInfo(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull List<TravelServerGroup> groups,
        @Nonnull String statusPrefix,
        @Nonnull String scrollId
    ) {
        NexoriPortalSections.synchronizeTravelInfo(ref, store, playerRef, player, plugin, state, peers, groups, statusPrefix, scrollId);
    }

    @Nonnull
    protected static List<TravelServerGroup> buildTravelServerGroups(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull String rawLocalConnectionAddress,
        @Nonnull String localSelectorAddress
    ) {
        return NexoriPortalSections.buildTravelServerGroups(plugin, peers, rawLocalConnectionAddress, localSelectorAddress);
    }

    @Nonnull
    protected static List<String> buildInstanceTemplateIds() {
        return InstancesPlugin.get().getInstanceAssets().stream()
            .filter(id -> id != null && !id.isBlank() && !ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(id))
            .distinct()
            .sorted(Comparator.naturalOrder())
            .toList();
    }

    protected static ArenaDefinition findDestination(@Nonnull List<ArenaDefinition> destinations, @Nonnull String arenaId) {
        for (ArenaDefinition destination : destinations) {
            if (destination.arenaId().equalsIgnoreCase(arenaId)) {
                return destination;
            }
        }
        return null;
    }

    @Nonnull
    protected static String deriveId(String rawValue, @Nonnull String fallback) {
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
    protected static String localSelectorAddress(@Nonnull String rawLocalConnectionAddress) {
        String normalized = rawLocalConnectionAddress == null ? "" : rawLocalConnectionAddress.trim().toLowerCase();
        return normalized.isBlank() ? "__local__" : normalized;
    }

    @Nonnull
    protected static Transform captureCurrentTransform(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
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
    protected static GroupBuilder sectionHeaderCard(@Nonnull String title, @Nonnull String detail, int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label(title, SUBTITLE, width - 32));
        if (!detail.isBlank()) {
            card.addChild(spacerY(8));
            card.addChild(label(detail, MUTED, width - 32));
        }
        return card;
    }

    @Nonnull
    protected static GroupBuilder selectionSummaryCard(@Nonnull String labelText, @Nonnull String value, int width, boolean selected) {
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
    protected static GroupBuilder centeredLabel(@Nonnull String text, @Nonnull HyUIStyle style, int width, int labelWidth, int estimatedCharWidth) {
        int estimatedWidth = Math.min(width, Math.max(48, text.length() * estimatedCharWidth));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(24));
        row.addChild(spacerX(Math.max(0, (width - estimatedWidth) / 2)));
        row.addChild(label(text, style, labelWidth));
        return row;
    }

    @Nonnull
    protected static GroupBuilder singleColumnHeaderCard(@Nonnull String titleText, int width) {
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
    protected static GroupBuilder backendSectionHeader(@Nonnull String title, @Nonnull String description, int width) {
        GroupBuilder header = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(58));
        header.addChild(centeredLabel(title, TITLE, width, width, 12));
        header.addChild(spacerY(4));
        header.addChild(label(description, MUTED_CENTER, width));
        return header;
    }

    @Nonnull
    protected static GroupBuilder backendSectionContainer(
        @Nonnull String title,
        @Nonnull String description,
        int width,
        int height
    ) {
        int innerWidth = width - 32;
        GroupBuilder section = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(PANEL_BG);
        section.withBackground(SERVER_CARD_BG);
        section.addChild(backendSectionHeader(title, description, innerWidth));
        section.addChild(spacerY(8));
        return section;
    }

    @Nonnull
    protected static GroupBuilder backendInputCard(
        @Nonnull String title,
        @Nonnull String detail,
        @Nonnull String fieldId,
        @Nonnull String currentValue,
        @Nonnull String placeholder,
        @Nonnull String footerText,
        int width,
        int height,
        int maxLength
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.withBackground(PANEL_BG);
        card.addChild(label(title, SUBTITLE, width - 28));
        card.addChild(spacerY(4));
        card.addChild(label(detail, MUTED, width - 28));
        card.addChild(spacerY(8));
        card.addChild(
            TextFieldBuilder.textInput()
                .withId(fieldId)
                .withValue(currentValue)
                .withPlaceholderText(placeholder)
                .withMaxLength(maxLength)
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(HOME_INPUT_FIELD_H))
                .withBackground("#101926")
        );
        card.addChild(spacerY(6));
        card.addChild(label(footerText, INFO, width - 28));
        return card;
    }

    @Nonnull
    protected static ReorderableListBuilder buildAboutScroll(int viewportHeight, @Nonnull String scrollId) {
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
    protected static ReorderableListBuilder buildPlaceholderScroll(@Nonnull NexoriMenuV2View view, int viewportHeight, @Nonnull String scrollId) {
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
    protected static String viewSubtitle(@Nonnull NexoriMenuV2View view) {
        return switch (view) {
            case HOME -> "Minimal server setup, trust bundle status, and local server list.";
            case PORTALS -> "";
            case TARGETS -> "Targets are now folded into the Portals travel bind workspace.";
            case RULES -> "";
            case QUEUES -> "Configure reusable games, queues, spawn slots, and cross server catalog sync.";
            case BACKEND -> "Configure backend sync, result reporting, request health, and endpoint reference details.";
            case ACCESS_GATE -> "Control server join caps, reserved slots, and bypass player access for the server you are currently on. To configure another server, move to that server first and then open this view there.";
            case OPERATIONS -> "Live runtime, diagnostics, and recovery belong here.";
            case ABOUT -> "Detailed context for what Nexori does and how this workspace is organized.";
        };
    }

    @Nonnull
    protected static String placeholderDetail(@Nonnull NexoriMenuV2View view) {
        return switch (view) {
            case PORTALS -> "This will become the star visualizer: server/world groups, portal nodes, bindings, and arrows.";
            case TARGETS -> "Targets are now grouped inside the travel bind workspace.";
            case RULES -> "Rules keep their own mental model: define policies once, then attach servers under them.";
            case QUEUES -> "Create games locally, create queues locally, manage spawn slots, and sync saved catalog entities to trusted servers one operation at a time.";
            case BACKEND -> "Backend config can send authenticated matchmaking sync requests and event-driven match result reports.";
            case ACCESS_GATE -> "";
            case OPERATIONS -> "Operations will show active queues, active matches, handoffs, diagnostics, and repair flows.";
            case HOME, ABOUT -> "";
        };
    }

    @Nonnull
    protected static GroupBuilder conceptItem(@Nonnull String title, @Nonnull String detail, int width) {
        GroupBuilder item = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(48));
        item.addChild(label(title, SUBTITLE, width));
        item.addChild(spacerY(2));
        item.addChild(label(detail, MUTED, width));
        return item;
    }

    @Nonnull
    protected static GroupBuilder statusBar(@Nonnull NexoriMenuV2State state) {
        GroupBuilder bar = card(BODY_W, STATUS_H, STATUS_BG);
        bar.addChild(label("Action / Status", SUBTITLE, BODY_W - 32));
        bar.addChild(spacerY(6));
        bar.addChild(label(state.statusText().isBlank() ? "No action yet. This footer is reserved for confirmations, errors, guided actions, and next-step hints." : state.statusText(), BODY, BODY_W - 32));
        return bar;
    }

    @Nonnull
    protected static GroupBuilder inputField(@Nonnull String labelText, @Nonnull String fieldId, @Nonnull String currentValue, @Nonnull String placeholder, int width) {
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
    protected static GroupBuilder statusBadgeRow(@Nonnull String prefix, @Nonnull String value, int width, @Nonnull HyUIStyle valueStyle, @Nonnull HyUIPatchStyle background) {
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
    protected static GroupBuilder card(int width, int height, @Nonnull HyUIPatchStyle background) {
        return GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(background);
    }

    @Nonnull
    protected static ReorderableListBuilder scrollList(int width, int height, int contentHeight, @Nonnull String id, boolean keepScrollPosition) {
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
    protected static GroupBuilder spacerY(int height) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(1).setHeight(height));
    }

    @Nonnull
    protected static GroupBuilder spacerX(int width) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(width).setHeight(1));
    }

    @Nonnull
    protected static LabelBuilder label(@Nonnull String text, @Nonnull HyUIStyle style, int width) {
        return LabelBuilder.label().withText(text).withAnchor(new HyUIAnchor().setWidth(width)).withStyle(style);
    }

    protected static void dismissPage(@Nonnull Player player, @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        PageManager pages = player.getPageManager();
        if (pages != null) {
            pages.setPage(ref, store, Page.None);
        }
    }

    @Nonnull
    public record TravelServerGroup(
        String displayName,
        String connectionAddress,
        boolean local,
        List<TravelPortalEndpoint> portals,
        List<TravelTargetEndpoint> coordinateTargets
    ) {
    }

    public record TravelPortalEndpoint(
        String connectionAddress,
        String serverDisplayName,
        String portalId,
        String targetId,
        String displayName
    ) {
    }

    public record TravelTargetEndpoint(
        String connectionAddress,
        String serverDisplayName,
        String targetId,
        String displayName
    ) {
    }

    public record RemoteServerOption(
        String displayName,
        String connectionAddress,
        String serverId,
        List<DiscoveredDestinationTargetSummary> discoveredTargets
    ) {
    }

    public record RemoteTargetOption(
        String targetId,
        String displayName,
        String detail
    ) {
    }

    public record TriggerOption(
        String displayName,
        String detail,
        String triggerId
    ) {
    }

    public record RuleServerEntry(
        String selectionKey,
        String connectionAddress,
        String displayName,
        String displayAddress,
        boolean local
    ) {
    }

    public record GroupStatusCounts(
        int assigned,
        int matching,
        int needsApply,
        int unknown
    ) {
    }

    public record LocalServerSummary(
        @Nonnull String displayName,
        @Nonnull String connectionAddress
    ) {
    }

    public record TravelBindOperation(
        String sourceConnectionAddress,
        String sourcePortalId,
        String destinationConnectionAddress,
        String destinationTargetId,
        String travelProfileId
    ) {
    }

    public record RuleMatchIndicator(
        @Nonnull String label,
        @Nonnull HyUIPatchStyle background
    ) {
    }


    public enum TravelBindColumnMode {
        A,
        B
    }
}





