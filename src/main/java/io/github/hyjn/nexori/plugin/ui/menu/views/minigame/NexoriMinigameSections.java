package io.github.hyjn.nexori.plugin.ui.menu.views.minigame;

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
import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
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
import io.github.hyjn.nexori.plugin.ui.menu.state.AfkDetectionPolicyDraft;
import io.github.hyjn.nexori.plugin.ui.menu.state.AfkTimeoutDraftValidator;
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
public final class NexoriMinigameSections extends NexoriMenuSections {

    private NexoriMinigameSections() {
    }

    public static GroupBuilder minigameTabs(
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
                .onClick((ignored, ctx) -> {
                    if (tab == MinigameWorkspaceTab.SYNC && hasActiveMinigameEdit(state)) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withStatusText("Save or cancel the current edit before syncing.")
                        );
                        return;
                    }
                    open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withSelectedMinigameTab(tab).clearedCatalogSyncConfirmation().withStatusText("")
                    );
                });
            row.addChild(button);
            if (index + 1 < tabs.length) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }


    public static ReorderableListBuilder buildMinigameWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull NexoriMenuSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        return switch (state.selectedMinigameTab()) {
            case DESTINATIONS -> buildDestinationWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case QUEUES -> buildQueueWorkspaceScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
            case SYNC -> buildCatalogSyncWorkspaceScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case SPAWNS -> buildSpawnWorkspaceScroll(ref, store, playerRef, player, plugin, state, viewportHeight, scrollId);
        };
    }

    @Nonnull
    static ReorderableListBuilder buildSpawnWorkspaceScroll(
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

        boolean manualResolution = ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equalsIgnoreCase(state.pendingDestinationTriggerId());
        int setupHeight = manualResolution ? 228 : 156;
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
    static GroupBuilder spawnSlotSetupCard(
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
    static GroupBuilder spawnSlotTemplateSelectionContainer(
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
    static GroupBuilder spawnSlotListContainer(
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
    static GroupBuilder spawnSlotListRow(
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
    static String buildSpawnSlotDetail(@Nonnull InstanceSpawnSlotDefinition slot, int ordinal) {
        return "Slot " + ordinal
            + "  X " + formatCoordinate(slot.x())
            + " Y " + formatCoordinate(slot.y())
            + " Z " + formatCoordinate(slot.z())
            + "  Pitch " + formatCoordinate(slot.pitch())
            + " Yaw " + formatCoordinate(slot.yaw());
    }

    @Nonnull
    static String formatCoordinate(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }


    static GroupBuilder destinationSetupCard(
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
        boolean manualResolution = ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equalsIgnoreCase(state.pendingDestinationTriggerId());
        String rulesEngineValue = state.pendingDestinationRulesEngineId().isBlank()
            ? (editing == null ? "" : editing.rulesEngineId())
            : state.pendingDestinationRulesEngineId();
        AfkDetectionPolicyDraft afkDraft = arenaAfkPolicyDraft(playerRef, editing);

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
        if (manualResolution) {
            topRow.addChild(inputField("Rules Engine ID", DESTINATION_RULES_ENGINE_INPUT_ID, rulesEngineValue, "skywars", 260));
            topRow.addChild(spacerX(12));
        }
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
                            String rulesEngineId = manualResolution
                                ? ctx.getValue(DESTINATION_RULES_ENGINE_INPUT_ID, String.class).orElse(rulesEngineValue).trim()
                                : "";
                            AfkDetectionPolicyDraft currentAfkDraft = arenaAfkPolicyDraft(playerRef, editing)
                                .withInactivityTimeoutSeconds(ctx.getValue(DESTINATION_AFK_TIMEOUT_INPUT_ID, String.class).orElse(afkDraft.inactivityTimeoutSeconds()).trim());
                            if (manualResolution && rulesEngineId.isBlank()) {
                                ARENA_AFK_POLICY_DRAFTS.put(playerRef.getUuid(), currentAfkDraft);
                                open(
                                    ref,
                                    store,
                                    playerRef,
                                    player,
                                    plugin,
                                    state.withDestinationDraft(
                                        displayName,
                                        state.pendingDestinationConnectionAddress(),
                                        "",
                                        state.pendingDestinationInstanceTemplateId(),
                                        state.pendingDestinationTriggerId(),
                                        rulesEngineId,
                                        state.pendingDestinationMaxPlayers()
                                    ).withStatusText("Rules Engine ID is required for Manual games. Example: skywars, bedwars, capture_the_zone.")
                                );
                                return;
                            }
                            try {
                                int afkTimeoutSeconds = AfkTimeoutDraftValidator.parseTimeoutSeconds(currentAfkDraft.inactivityTimeoutSeconds());
                                String destinationId = editing == null ? deriveId(displayName, "destination") : editing.arenaId();
                                ArenaDefinition saved = plugin.getArenaService().upsert(new ArenaDefinition(
                                        destinationId,
                                        displayName,
                                        state.pendingDestinationConnectionAddress(),
                                        "",
                                        state.pendingDestinationInstanceTemplateId(),
                                        state.pendingDestinationTriggerId(),
                                        rulesEngineId,
                                        DEFAULT_DESTINATION_MAX_SUPPORTED_PLAYERS,
                                        true,
                                        new AfkDetectionPolicy(currentAfkDraft.enabled(), afkTimeoutSeconds)
                                ));
                                ARENA_AFK_POLICY_DRAFTS.remove(playerRef.getUuid());
                                open(ref, store, playerRef, player, plugin, state.clearedDestinationDraft().withStatusText("Saved game " + saved.displayName() + "."));
                            } catch (IOException | IllegalArgumentException exception) {
                                ARENA_AFK_POLICY_DRAFTS.put(playerRef.getUuid(), currentAfkDraft);
                                open(ref, store, playerRef, player, plugin, state.withDestinationDraft(displayName, state.pendingDestinationConnectionAddress(), "", state.pendingDestinationInstanceTemplateId(), state.pendingDestinationTriggerId(), rulesEngineId, state.pendingDestinationMaxPlayers()).withStatusText("Could not save game: " + exception.getMessage()));
                            }
                        })
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
                ButtonBuilder.secondaryTextButton()
                        .withText("CANCEL")
                        .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                        .onClick((ignored, ctx) -> {
                            ARENA_AFK_POLICY_DRAFTS.remove(playerRef.getUuid());
                            open(ref, store, playerRef, player, plugin, state.clearedDestinationDraft().withStatusText("Game edit cleared."));
                        })
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
        card.addChild(spacerY(12));

        GroupBuilder afkRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(HOME_INPUT_BLOCK_H));
        GroupBuilder afkToggleColumn = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(170).setHeight(HOME_INPUT_BLOCK_H));
        afkToggleColumn.addChild(label("AFK Detection", MUTED, 170));
        afkToggleColumn.addChild(spacerY(8));
        afkToggleColumn.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(afkDraft.enabled() ? "ON" : "OFF")
                .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String timeout = ctx.getValue(DESTINATION_AFK_TIMEOUT_INPUT_ID, String.class).orElse(afkDraft.inactivityTimeoutSeconds()).trim();
                    String displayName = ctx.getValue(DESTINATION_DISPLAY_NAME_INPUT_ID, String.class).orElse(displayValue).trim();
                    String rulesEngineId = manualResolution
                        ? ctx.getValue(DESTINATION_RULES_ENGINE_INPUT_ID, String.class).orElse(rulesEngineValue).trim()
                        : rulesEngineValue;
                    ARENA_AFK_POLICY_DRAFTS.put(playerRef.getUuid(), new AfkDetectionPolicyDraft(!afkDraft.enabled(), timeout));
                    open(
                        ref,
                        store,
                        playerRef,
                        player,
                        plugin,
                        state.withDestinationDraft(
                            displayName,
                            state.pendingDestinationConnectionAddress(),
                            state.pendingDestinationTargetId(),
                            state.pendingDestinationInstanceTemplateId(),
                            state.pendingDestinationTriggerId(),
                            rulesEngineId,
                            state.pendingDestinationMaxPlayers()
                        ).withStatusText("")
                    );
                })
        );
        afkRow.addChild(afkToggleColumn);
        afkRow.addChild(spacerX(12));
        afkRow.addChild(inputField("Timeout Seconds", DESTINATION_AFK_TIMEOUT_INPUT_ID, afkDraft.inactivityTimeoutSeconds(), Integer.toString(AfkDetectionPolicy.DEFAULT_INACTIVITY_TIMEOUT_SECONDS), 180));
        afkRow.addChild(spacerX(12));
        afkRow.addChild(label("Default is OFF with 30 seconds. Valid range: 5 to 3600 seconds.", MUTED, width - 406));
        card.addChild(afkRow);
        return card;
    }

    @Nonnull
    static ReorderableListBuilder buildDestinationWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull NexoriMenuSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        String localSelectorAddress = localSelectorAddress(setup.localConnectionAddress());
        List<TravelServerGroup> groups = buildTravelServerGroups(plugin, peers, setup.localConnectionAddress(), localSelectorAddress);
        List<RemoteServerOption> remoteServers = buildRemoteDestinationServerOptions(plugin, peers, localSelectorAddress);
        List<String> instanceIds = buildInstanceTemplateIds();
        List<ArenaDefinition> destinations = plugin.getArenaService().list();
        ArenaDefinition editing = currentEditedDestination(plugin, state);

        int setupHeight = 280;
        int selectorsHeight = Math.max(320, viewportHeight - setupHeight - 420);
        int savedHeight = Math.max(360, viewportHeight - setupHeight - selectorsHeight - 56);
        int contentHeight = 16 + setupHeight + 12 + selectorsHeight + 12 + savedHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(destinationSetupCard(
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
        ));
        scroll.addChild(spacerY(12));
        scroll.addChild(destinationSelectionTableCard(
            ref,
            store,
            playerRef,
            player,
            plugin,
            state,
            remoteServers,
            List.of(),
            instanceIds,
            innerWidth,
            selectorsHeight,
            selectorsHeight - 96,
            scrollId + "-game-selectors"
        ));
        scroll.addChild(spacerY(12));
        scroll.addChild(destinationListContainer(ref, store, playerRef, player, plugin, state, destinations, innerWidth, savedHeight, scrollId + "-saved-destinations"));
        return scroll;
    }

    @Nonnull
    static GroupBuilder destinationSelectionTableCard(
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
    static ReorderableListBuilder destinationServerColumnScroll(
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
    static ReorderableListBuilder destinationTargetColumnScroll(
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
    static ReorderableListBuilder destinationInstanceColumnScroll(
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
    static ReorderableListBuilder destinationTriggerColumnScroll(
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
    static GroupBuilder selectorRowCard(
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
    static GroupBuilder remoteServerSelectionContainer(
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
    static GroupBuilder remoteServerRow(
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
    static GroupBuilder remoteTargetSelectionContainer(
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
    static GroupBuilder remoteTargetRow(
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
    static GroupBuilder instanceTemplateSelectionContainer(
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
    static GroupBuilder instanceTemplateRow(
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
    static GroupBuilder destinationListContainer(
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
    static GroupBuilder destinationListRow(
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
            destination.destinationConnectionAddress()
                + " -> " + destination.destinationTargetId()
                + "  " + destination.instanceTemplateId()
                + "  AFK " + (AfkDetectionPolicy.normalize(destination.afkDetectionPolicy()).enabled()
                    ? "ON " + AfkDetectionPolicy.normalize(destination.afkDetectionPolicy()).inactivityTimeoutSeconds() + "s"
                    : "OFF"),
            MUTED,
            width - 250
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> {
                    AfkDetectionPolicy policy = AfkDetectionPolicy.normalize(destination.afkDetectionPolicy());
                    ARENA_AFK_POLICY_DRAFTS.put(
                        playerRef.getUuid(),
                        new AfkDetectionPolicyDraft(policy.enabled(), Integer.toString(policy.inactivityTimeoutSeconds()))
                    );
                    open(
                        ref, store, playerRef, player, plugin,
                        state.withEditingDestinationId(destination.arenaId())
                            .withDestinationDraft(destination.displayName(), destination.destinationConnectionAddress(), destination.destinationTargetId(), destination.instanceTemplateId(), destination.matchResolutionTriggerId(), destination.rulesEngineId(), Integer.toString(DEFAULT_DESTINATION_MAX_SUPPORTED_PLAYERS))
                            .withStatusText("Editing game " + destination.displayName() + ".")
                    );
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getArenaService().remove(destination.arenaId());
                        ARENA_AFK_POLICY_DRAFTS.remove(playerRef.getUuid());
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
    static ReorderableListBuilder buildQueueWorkspaceScroll(
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
        QueueDefinition editing = currentEditedQueue(plugin, state);
        List<ArenaDefinition> destinations = plugin.getArenaService().list();
        String selectedDestinationId = state.pendingQueueDestinationId().isBlank()
            ? (editing == null || editing.arenaIds().isEmpty() ? "" : editing.arenaIds().getFirst())
            : state.pendingQueueDestinationId();
        ArenaDefinition selectedDestination = findDestination(destinations, selectedDestinationId);

        int setupHeight = 470;
        int destinationViewportHeight = 300;
        int destinationSelectionHeight = 72 + 12 + destinationViewportHeight + 56;
        List<QueueDefinition> queues = plugin.getQueueService().list();
        int existingViewportHeight = 300;
        int existingHeight = 64 + 12 + existingViewportHeight + 56;
        int contentHeight = 16 + setupHeight + 12 + destinationSelectionHeight + 12 + existingHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(queueSetupCard(ref, store, playerRef, player, plugin, state, editing, selectedDestination, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(queueDestinationSelectionContainer(ref, store, playerRef, player, plugin, state, destinations, selectedDestination, innerWidth, destinationSelectionHeight, destinationViewportHeight, scrollId + "-queue-games"));
        scroll.addChild(spacerY(12));
        scroll.addChild(queueListContainer(ref, store, playerRef, player, plugin, state, destinations, queues, innerWidth, existingHeight, existingViewportHeight, scrollId + "-saved-queues"));
        return scroll;
    }

    @Nonnull
    static GroupBuilder queueSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
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
        QueueMatchmakingMode matchmakingMode = queueModeDraft(playerRef, editing);
        boolean backendDriven = matchmakingMode == QueueMatchmakingMode.BACKEND_DRIVEN;
        QueueBackfillDraft backfillDraft = queueBackfillDraft(playerRef, editing);

        boolean canSave = selectedDestination != null;

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Queues", TITLE, width - 32));
        card.addChild(spacerY(8));
        GroupBuilder description = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(48));
        description.addChild(label(
            backendDriven
                ? "BACKEND_DRIVEN queues keep players waiting in Nexori, then launch only when the configured backend returns a CREATE_MATCH assignment. No local countdown is shown or used."
                : "LOCAL_FIFO queues start their local countdown once the minimum player count is reached, and reset if the queue falls below minimum.",
            MUTED,
            width - 32
        ));
        card.addChild(description);
        card.addChild(spacerY(12));

        GroupBuilder topRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(HOME_INPUT_BLOCK_H));
        topRow.addChild(inputField("Display Name", QUEUE_DISPLAY_NAME_INPUT_ID, displayValue, "New Queue", 280));
        topRow.addChild(spacerX(12));
        topRow.addChild(queueModeField(ref, store, playerRef, player, plugin, state, matchmakingMode, editing, 240));
        topRow.addChild(spacerX(12));
        topRow.addChild(inputField("Min Players", QUEUE_MIN_PLAYERS_INPUT_ID, minValue, "2", 110));
        topRow.addChild(spacerX(12));
        topRow.addChild(inputField("Max Players", QUEUE_MAX_PLAYERS_INPUT_ID, maxValue, "8", 110));
        topRow.addChild(spacerX(12));
        if (!backendDriven) {
            topRow.addChild(inputField("Countdown", QUEUE_COUNTDOWN_INPUT_ID, countdownValue, "15", 120));
            topRow.addChild(spacerX(12));
        }

        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(330).setHeight(HOME_INPUT_BLOCK_H));
        actions.addChild(spacerY(HOME_ACTION_BUTTON_TOP));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(330).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText(editing == null ? "SAVE QUEUE" : "UPDATE QUEUE")
                .withDisabled(!canSave)
                .withAnchor(new HyUIAnchor().setWidth(170).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    if (selectedDestination == null) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Pick a game first."));
                        return;
                    }
                    String displayName = ctx.getValue(QUEUE_DISPLAY_NAME_INPUT_ID, String.class).orElse(displayValue).trim();
                    String rawMin = ctx.getValue(QUEUE_MIN_PLAYERS_INPUT_ID, String.class).orElse(minValue).trim();
                    String rawMax = ctx.getValue(QUEUE_MAX_PLAYERS_INPUT_ID, String.class).orElse(maxValue).trim();
                    String rawCountdown = backendDriven ? countdownValue : ctx.getValue(QUEUE_COUNTDOWN_INPUT_ID, String.class).orElse(countdownValue).trim();
                    String rawBackfillWindow = ctx.getValue(QUEUE_BACKFILL_WINDOW_INPUT_ID, String.class).orElse(backfillDraft.windowSeconds()).trim();
                    try {
                        int minPlayers = Integer.parseInt(rawMin);
                        int maxPlayers = Integer.parseInt(rawMax);
                        int countdown = Integer.parseInt(rawCountdown);
                        int backfillWindowSeconds = backendDriven ? Integer.parseInt(rawBackfillWindow) : 0;
                        String queueId = editing == null ? deriveId(displayName, "queue") : editing.queueId();
                        boolean backfillEnabled = backendDriven && backfillDraft.enabled();
                        String backfillMode = backfillEnabled ? backfillDraft.mode().id() : QueueBackfillMode.NONE.id();
                        QueueDefinition saved = plugin.getQueueService().upsert(new QueueDefinition(
                            queueId,
                            displayName,
                            List.of(selectedDestination.arenaId()),
                            minPlayers,
                            maxPlayers,
                            countdown,
                            DEFAULT_MINIGAME_QUEUE_TRAVEL_PROFILE_ID,
                            matchmakingMode.id(),
                            true,
                            backfillEnabled,
                            backfillMode,
                            backfillWindowSeconds
                        ));
                        QUEUE_MODE_DRAFTS.remove(playerRef.getUuid());
                        QUEUE_BACKFILL_DRAFTS.remove(playerRef.getUuid());
                        open(ref, store, playerRef, player, plugin, state.clearedQueueDraft().withStatusText("Saved queue " + saved.displayName() + "."));
                    } catch (NumberFormatException exception) {
                        QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), backfillDraft.withWindowSeconds(rawBackfillWindow));
                        open(ref, store, playerRef, player, plugin, state.withQueueDraft(displayName, state.pendingQueueDestinationId(), rawMin, rawMax, rawCountdown).withStatusText("Queue numeric fields must be whole numbers."));
                    } catch (IOException | IllegalArgumentException exception) {
                        QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), backfillDraft.withWindowSeconds(rawBackfillWindow));
                        open(ref, store, playerRef, player, plugin, state.withQueueDraft(displayName, state.pendingQueueDestinationId(), rawMin, rawMax, rawCountdown).withStatusText("Could not save queue: " + exception.getMessage()));
                    }
                })
        );
        actionRow.addChild(spacerX(12));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("CANCEL")
                .withAnchor(new HyUIAnchor().setWidth(120).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    QUEUE_MODE_DRAFTS.remove(playerRef.getUuid());
                    QUEUE_BACKFILL_DRAFTS.remove(playerRef.getUuid());
                    open(ref, store, playerRef, player, plugin, state.clearedQueueDraft().withStatusText("Queue edit cleared."));
                })
        );
        actions.addChild(actionRow);
        topRow.addChild(actions);
        card.addChild(topRow);
        if (backendDriven) {
            card.addChild(spacerY(16));
            card.addChild(queueBackfillPolicySection(ref, store, playerRef, player, plugin, state, editing, backfillDraft, width - 32, 236));
        }
        return card;
    }

    @Nonnull
    static GroupBuilder queueModeField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull QueueMatchmakingMode mode,
        QueueDefinition editing,
        int width
    ) {
        GroupBuilder field = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_BLOCK_H));
        field.addChild(spacerY(HOME_INPUT_TOP_PADDING));
        GroupBuilder labelSlot = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_LABEL_H));
        labelSlot.addChild(label("Matchmaking", SUBTITLE, width));
        field.addChild(labelSlot);
        field.addChild(spacerY(HOME_INPUT_LABEL_GAP));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_FIELD_H));
        row.addChild(queueModeButton(ref, store, playerRef, player, plugin, state, editing, mode, QueueMatchmakingMode.LOCAL_FIFO, "LOCAL", (width - 8) / 2));
        row.addChild(spacerX(8));
        row.addChild(queueModeButton(ref, store, playerRef, player, plugin, state, editing, mode, QueueMatchmakingMode.BACKEND_DRIVEN, "BACKEND", (width - 8) / 2));
        field.addChild(row);
        return field;
    }

    @Nonnull
    static ButtonBuilder queueModeButton(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        QueueDefinition editing,
        @Nonnull QueueMatchmakingMode current,
        @Nonnull QueueMatchmakingMode option,
        @Nonnull String label,
        int width
    ) {
        boolean selected = current == option;
        return ButtonBuilder.smallSecondaryTextButton()
            .withText(label)
            .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
            .withDisabled(selected)
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(HOME_INPUT_FIELD_H))
            .onClick((ignored, ctx) -> {
                QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), queueBackfillDraftFromInputs(playerRef, editing, ctx));
                QUEUE_MODE_DRAFTS.put(playerRef.getUuid(), option);
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withQueueDraft(
                        ctx.getValue(QUEUE_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingQueueDisplayName().isBlank() ? (editing == null ? "New Queue" : editing.displayName()) : state.pendingQueueDisplayName()).trim(),
                        state.pendingQueueDestinationId().isBlank() && editing != null && !editing.arenaIds().isEmpty() ? editing.arenaIds().getFirst() : state.pendingQueueDestinationId(),
                        ctx.getValue(QUEUE_MIN_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMinPlayers()).trim(),
                        ctx.getValue(QUEUE_MAX_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMaxPlayers()).trim(),
                        ctx.getValue(QUEUE_COUNTDOWN_INPUT_ID, String.class).orElse(state.pendingQueueCountdownSeconds()).trim()
                    ).withStatusText("Queue matchmaking draft set to " + option.id() + ".")
                );
            });
    }

    @Nonnull
    static GroupBuilder queueBackfillPolicySection(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        QueueDefinition editing,
        @Nonnull QueueBackfillDraft draft,
        int width,
        int height
    ) {
        int fieldHeight = 146;
        int fieldWidth = (width - 32 - 24) / 3;
        GroupBuilder section = backendSectionContainer(
            "Backfill Policy",
            "Controls whether backend-driven matches can keep admission open for placement or active-window backfill.",
            width,
            height
        );
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(fieldHeight));
        row.addChild(queueBackfillToggleField(ref, store, playerRef, player, plugin, state, editing, draft, fieldWidth, fieldHeight));
        row.addChild(spacerX(12));
        row.addChild(queueBackfillModeField(ref, store, playerRef, player, plugin, state, editing, draft, fieldWidth, fieldHeight));
        row.addChild(spacerX(12));
        row.addChild(backendInputCard(
            "Backfill Window Seconds",
            "Used by ACTIVE_WINDOW to keep admission open after the match becomes active.",
            QUEUE_BACKFILL_WINDOW_INPUT_ID,
            draft.windowSeconds(),
            "60",
            "Current: " + (editing == null ? "0" : editing.backfillWindowSeconds()) + " seconds",
            fieldWidth,
            fieldHeight,
            16
        ));
        section.addChild(row);
        return section;
    }

    @Nonnull
    static GroupBuilder queueBackfillToggleField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        QueueDefinition editing,
        @Nonnull QueueBackfillDraft draft,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_BG);
        card.addChild(label("Backfill Enabled", SUBTITLE, width - 28));
        card.addChild(spacerY(4));
        card.addChild(label("Allows this backend-driven queue to report open admission for backfill.", MUTED, width - 28));
        card.addChild(spacerY(8));
        card.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(draft.enabled() ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42))
                .onClick((ignored, ctx) -> {
                    QueueBackfillDraft next = queueBackfillDraftFromInputs(playerRef, editing, ctx).withEnabled(!draft.enabled());
                    QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), next);
                    open(ref, store, playerRef, player, plugin, preserveQueueDraftFromInputs(state, editing, ctx).withStatusText("Queue backfill draft is now " + (next.enabled() ? "enabled" : "disabled") + ". Save queue to apply."));
                })
        );
        card.addChild(spacerY(6));
        card.addChild(label("Current: " + (editing != null && editing.backfillEnabled() ? "ENABLED" : "DISABLED"), INFO, width - 28));
        return card;
    }

    @Nonnull
    static GroupBuilder queueBackfillModeField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        QueueDefinition editing,
        @Nonnull QueueBackfillDraft draft,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_BG);
        card.addChild(label("Backfill Mode", SUBTITLE, width - 28));
        card.addChild(spacerY(4));
        card.addChild(label("NONE closes at active, PLACEMENT_ONLY closes after placement, ACTIVE_WINDOW closes by time.", MUTED, width - 28));
        card.addChild(spacerY(8));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        int buttonWidth = (width - 28 - 16) / 3;
        row.addChild(queueBackfillModeButton(ref, store, playerRef, player, plugin, state, editing, draft, QueueBackfillMode.NONE, "NONE", buttonWidth));
        row.addChild(spacerX(8));
        row.addChild(queueBackfillModeButton(ref, store, playerRef, player, plugin, state, editing, draft, QueueBackfillMode.PLACEMENT_ONLY, "PLACE", buttonWidth));
        row.addChild(spacerX(8));
        row.addChild(queueBackfillModeButton(ref, store, playerRef, player, plugin, state, editing, draft, QueueBackfillMode.ACTIVE_WINDOW, "ACTIVE", buttonWidth));
        card.addChild(row);
        card.addChild(spacerY(6));
        card.addChild(label("Current: " + (editing == null ? QueueBackfillMode.NONE.id() : editing.effectiveBackfillMode().id()), INFO, width - 28));
        return card;
    }

    @Nonnull
    static ButtonBuilder queueBackfillModeButton(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        QueueDefinition editing,
        @Nonnull QueueBackfillDraft draft,
        @Nonnull QueueBackfillMode option,
        @Nonnull String label,
        int width
    ) {
        boolean selected = draft.mode() == option;
        return ButtonBuilder.smallSecondaryTextButton()
            .withText(label)
            .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
            .withDisabled(selected)
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(42))
            .onClick((ignored, ctx) -> {
                QueueBackfillDraft next = queueBackfillDraftFromInputs(playerRef, editing, ctx).withMode(option);
                QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), next);
                open(ref, store, playerRef, player, plugin, preserveQueueDraftFromInputs(state, editing, ctx).withStatusText("Queue backfill mode draft set to " + option.id() + ". Save queue to apply."));
            });
    }

    @Nonnull
    static GroupBuilder queueDestinationSelectionContainer(
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
    static GroupBuilder queueDestinationRow(
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
                .onClick((ignored, ctx) -> {
                    QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), queueBackfillDraftFromInputs(playerRef, currentEditedQueue(plugin, state), ctx));
                    open(
                        ref, store, playerRef, player, plugin,
                        state.withQueueDraft(
                        ctx.getValue(QUEUE_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingQueueDisplayName().isBlank() ? "New Queue" : state.pendingQueueDisplayName()).trim(),
                        destination.arenaId(),
                        ctx.getValue(QUEUE_MIN_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMinPlayers()).trim(),
                        ctx.getValue(QUEUE_MAX_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMaxPlayers()).trim(),
                        ctx.getValue(QUEUE_COUNTDOWN_INPUT_ID, String.class).orElse(state.pendingQueueCountdownSeconds()).trim()
                        ).withStatusText("Selected game " + destination.displayName() + " for this queue.")
                    );
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder queueListContainer(
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
    static GroupBuilder queueListRow(
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
        QueueMatchmakingMode mode = queue.effectiveMatchmakingMode();
        String modeDetail = mode == QueueMatchmakingMode.BACKEND_DRIVEN
            ? "BACKEND_DRIVEN  backfill=" + (queue.backfillEnabled() ? queue.effectiveBackfillMode().id() + " " + queue.backfillWindowSeconds() + "s" : "DISABLED")
            : "LOCAL_FIFO  " + queue.countdownSeconds() + "s";
        identity.addChild(label((destination == null ? "<missing destination>" : destination.displayName()) + "  " + queue.minPlayers() + "-" + queue.maxPlayers() + "  " + modeDetail, MUTED, width - 250));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("EDIT")
                .withAnchor(new HyUIAnchor().setWidth(90).setHeight(30))
                .onClick((ignored, ctx) -> {
                    QUEUE_MODE_DRAFTS.put(playerRef.getUuid(), queue.effectiveMatchmakingMode());
                    QUEUE_BACKFILL_DRAFTS.put(playerRef.getUuid(), new QueueBackfillDraft(queue.backfillEnabled(), queue.effectiveBackfillMode(), Integer.toString(queue.backfillWindowSeconds())));
                    open(
                        ref, store, playerRef, player, plugin,
                        state.withEditingQueueId(queue.queueId())
                            .withQueueDraft(
                                queue.displayName(),
                                queue.arenaIds().isEmpty() ? "" : queue.arenaIds().getFirst(),
                                Integer.toString(queue.minPlayers()),
                                Integer.toString(queue.maxPlayers()),
                                Integer.toString(queue.countdownSeconds())
                            ).withStatusText("Editing queue " + queue.displayName() + ".")
                    );
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REMOVE")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getQueueService().remove(queue.queueId());
                        QUEUE_MODE_DRAFTS.remove(playerRef.getUuid());
                        QUEUE_BACKFILL_DRAFTS.remove(playerRef.getUuid());
                        open(ref, store, playerRef, player, plugin, state.clearedQueueDraft().withStatusText(removed ? "Removed queue " + queue.displayName() + "." : queue.displayName() + " was already removed."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withStatusText("Could not remove queue: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    static boolean hasActiveMinigameEdit(@Nonnull NexoriMenuV2State state) {
        boolean destinationDraftDirty =
            !state.editingDestinationId().isBlank()
                || !state.pendingDestinationDisplayName().isBlank()
                || !state.pendingDestinationConnectionAddress().isBlank()
                || !state.pendingDestinationTargetId().isBlank()
                || !state.pendingDestinationInstanceTemplateId().isBlank()
                || !state.pendingDestinationRulesEngineId().isBlank()
                || !"last_player_alive".equalsIgnoreCase(state.pendingDestinationTriggerId())
                || !"8".equals(state.pendingDestinationMaxPlayers());
        boolean queueDraftDirty =
            !state.editingQueueId().isBlank()
                || !state.pendingQueueDisplayName().isBlank()
                || !state.pendingQueueDestinationId().isBlank()
                || !"2".equals(state.pendingQueueMinPlayers())
                || !"8".equals(state.pendingQueueMaxPlayers())
                || !"15".equals(state.pendingQueueCountdownSeconds());
        return destinationDraftDirty || queueDraftDirty;
    }

    @Nonnull
    static ReorderableListBuilder buildCatalogSyncWorkspaceScroll(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull NexoriMenuSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        int width = CONTENT_W - 32;
        int innerWidth = width - 16;
        String localSelectorAddress = localSelectorAddress(setup.localConnectionAddress());
        List<RemoteServerOption> remoteServers = buildRemoteDestinationServerOptions(plugin, peers, localSelectorAddress);
        List<ArenaDefinition> arenas = plugin.getArenaService().list();
        List<QueueDefinition> queues = plugin.getQueueService().list();
        int introHeight = 174;
        int panelsHeight = Math.max(520, viewportHeight - introHeight - 76);
        int contentHeight = 16 + introHeight + 12 + panelsHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(catalogSyncIntroCard(ref, store, playerRef, player, plugin, state, innerWidth, introHeight));
        scroll.addChild(spacerY(12));

        GroupBuilder panelsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(panelsHeight));
        int panelWidth = (innerWidth - 12) / 2;
        if (state.selectedCatalogSyncEntityType() == CatalogSyncEntityType.GAME) {
            panelsRow.addChild(catalogSyncGameListContainer(ref, store, playerRef, player, plugin, state, arenas, panelWidth, panelsHeight, scrollId + "-catalog-games"));
        } else {
            panelsRow.addChild(catalogSyncQueueListContainer(ref, store, playerRef, player, plugin, state, arenas, queues, panelWidth, panelsHeight, scrollId + "-catalog-queues"));
        }
        panelsRow.addChild(spacerX(12));
        panelsRow.addChild(catalogSyncTargetServersContainer(ref, store, playerRef, player, plugin, state, peers, remoteServers, panelWidth, panelsHeight, scrollId + "-catalog-targets"));
        scroll.addChild(panelsRow);
        return scroll;
    }

    @Nonnull
    static GroupBuilder catalogSyncIntroCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int width,
        int height
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Catalog Sync", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Copy one saved game or one saved queue to one trusted target server per operation. This sync creates or updates only the selected entity and does not copy portals, spawns, backend config, local server identity, or local UI state.", MUTED, width - 32));
        card.addChild(label(
            "Use this sync to keep backend driven multi lobby queues aligned across entry servers. If multiple lobby servers should send players into the same minigame, the queue and game definitions referenced by those portals need to match on each server.",
            MUTED,
            width - 32
        ));
        card.addChild(spacerY(12));
        card.addChild(catalogSyncModeTabs(ref, store, playerRef, player, plugin, state, width - 32));
        return card;
    }

    @Nonnull
    static GroupBuilder catalogSyncModeTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        int width
    ) {
        int buttonWidth = 180;
        int gap = 8;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(42));
        row.addChild(spacerX(Math.max(0, (width - (buttonWidth * 2) - gap) / 2)));
        for (CatalogSyncEntityType option : CatalogSyncEntityType.values()) {
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(option.label())
                .withAnchor(new HyUIAnchor().setWidth(buttonWidth).setHeight(42))
                .withDisabled(state.selectedCatalogSyncEntityType() == option)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withCatalogSyncSelection(option, "").withCatalogSyncTargetConnectionAddress("").clearedCatalogSyncConfirmation().withSelectedMinigameTab(MinigameWorkspaceTab.SYNC).withStatusText("")
                ));
            row.addChild(button);
            if (option != CatalogSyncEntityType.QUEUE) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }

    @Nonnull
    static GroupBuilder catalogSyncGameListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ArenaDefinition> arenas,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int headerHeight = 64;
        int outerGap = 12;
        int bodyViewportHeight = height - headerHeight - 12 - 32 - (outerGap * 2);
        int bodyContentHeight = arenas.isEmpty()
            ? bodyViewportHeight
            : 8 + arenas.size() * HOME_SERVER_CARD_H + Math.max(0, arenas.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Games", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (arenas.isEmpty()) {
            body.addChild(label("No games saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < arenas.size(); index++) {
                ArenaDefinition arena = arenas.get(index);
                body.addChild(catalogSyncGameRow(ref, store, playerRef, player, plugin, state, arena, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < arenas.size()) {
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
    static GroupBuilder catalogSyncGameRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ArenaDefinition arena,
        int width,
        int height
    ) {
        boolean selected = state.selectedCatalogSyncEntityType() == CatalogSyncEntityType.GAME
            && state.selectedCatalogSyncEntityId().equalsIgnoreCase(arena.arenaId());
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(arena.displayName(), SUBTITLE, width - 170));
        identity.addChild(spacerY(2));
        identity.addChild(label(
            arena.destinationConnectionAddress() + " -> " + arena.destinationTargetId() + "  " + arena.instanceTemplateId(),
            MUTED,
            width - 170
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(selected ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withCatalogSyncSelection(CatalogSyncEntityType.GAME, arena.arenaId()).clearedCatalogSyncConfirmation().withStatusText("Selected game '" + arena.arenaId() + "' for sync.")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder catalogSyncQueueListContainer(
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
        @Nonnull String scrollId
    ) {
        int headerHeight = 64;
        int outerGap = 12;
        int bodyViewportHeight = height - headerHeight - 12 - 32 - (outerGap * 2);
        int bodyContentHeight = queues.isEmpty()
            ? bodyViewportHeight
            : 8 + queues.size() * HOME_SERVER_CARD_H + Math.max(0, queues.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Saved Queues", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (queues.isEmpty()) {
            body.addChild(label("No queues saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < queues.size(); index++) {
                QueueDefinition queue = queues.get(index);
                body.addChild(catalogSyncQueueRow(ref, store, playerRef, player, plugin, state, destinations, queue, width - 48, HOME_SERVER_CARD_H));
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
    static GroupBuilder catalogSyncQueueRow(
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
        boolean selected = state.selectedCatalogSyncEntityType() == CatalogSyncEntityType.QUEUE
            && state.selectedCatalogSyncEntityId().equalsIgnoreCase(queue.queueId());
        ArenaDefinition destination = findDestination(destinations, queue.arenaIds().isEmpty() ? "" : queue.arenaIds().getFirst());
        QueueMatchmakingMode mode = queue.effectiveMatchmakingMode();
        String modeDetail = mode == QueueMatchmakingMode.BACKEND_DRIVEN
            ? "BACKEND_DRIVEN"
            : "LOCAL_FIFO  " + queue.countdownSeconds() + "s";
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(queue.displayName(), SUBTITLE, width - 170));
        identity.addChild(spacerY(2));
        identity.addChild(label((destination == null ? "<missing game>" : destination.displayName()) + "  " + queue.minPlayers() + "-" + queue.maxPlayers() + "  " + modeDetail, MUTED, width - 170));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(selected ? "SELECTED" : "SELECT")
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withCatalogSyncSelection(CatalogSyncEntityType.QUEUE, queue.queueId()).clearedCatalogSyncConfirmation().withStatusText("Selected queue '" + queue.queueId() + "' for sync.")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder catalogSyncTargetServersContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull List<RemoteServerOption> remoteServers,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int headerHeight = 64;
        int outerGap = 12;
        int bodyViewportHeight = height - headerHeight - 12 - 32 - (outerGap * 2);
        int bodyContentHeight = remoteServers.isEmpty()
            ? bodyViewportHeight
            : 8 + remoteServers.size() * HOME_SERVER_CARD_H + Math.max(0, remoteServers.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Target Servers", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (remoteServers.isEmpty()) {
            body.addChild(label("No trusted remote servers available.", MUTED, width - 48));
        } else {
            for (int index = 0; index < remoteServers.size(); index++) {
                RemoteServerOption option = remoteServers.get(index);
                body.addChild(catalogSyncTargetRow(ref, store, playerRef, player, plugin, state, peers, option, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < remoteServers.size()) {
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
    static GroupBuilder catalogSyncTargetRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull RemoteServerOption option,
        int width,
        int height
    ) {
        String confirmationKey = catalogSyncConfirmationKey(state.selectedCatalogSyncEntityType(), state.selectedCatalogSyncEntityId(), option.connectionAddress());
        boolean confirming = !state.pendingCatalogSyncConfirmationKey().isBlank() && state.pendingCatalogSyncConfirmationKey().equals(confirmationKey);
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 210).setHeight(rowHeight));
        identity.addChild(label(option.displayName(), SUBTITLE, width - 210));
        identity.addChild(spacerY(2));
        identity.addChild(label((option.serverId().isBlank() ? "<unknown server id>" : option.serverId()) + "  " + option.connectionAddress(), MUTED, width - 210));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(confirming ? "CONFIRM" : "SYNC")
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(30))
                .withDisabled(state.selectedCatalogSyncEntityId().isBlank())
                .onClick((ignored, ctx) -> triggerCatalogSync(ref, store, playerRef, player, plugin, state, peers, option))
        );
        card.addChild(row);
        return card;
    }

    static void triggerCatalogSync(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ConfiguredPeer> peers,
        @Nonnull RemoteServerOption target
    ) {
        if (hasActiveMinigameEdit(state)) {
            open(ref, store, playerRef, player, plugin, state.withStatusText("Save or cancel the current edit before syncing."));
            return;
        }
        if (state.selectedCatalogSyncEntityId().isBlank()) {
            open(ref, store, playerRef, player, plugin, state.withStatusText("Select a saved " + state.selectedCatalogSyncEntityType().singularLabel() + " first."));
            return;
        }
        ConfiguredPeer destination = findConfiguredPeer(peers, target.connectionAddress());
        if (destination == null) {
            open(ref, store, playerRef, player, plugin, state.withStatusText("Could not resolve the selected target server."));
            return;
        }

        String confirmationKey = catalogSyncConfirmationKey(state.selectedCatalogSyncEntityType(), state.selectedCatalogSyncEntityId(), target.connectionAddress());
        if (!state.pendingCatalogSyncConfirmationKey().equals(confirmationKey)) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withCatalogSyncTargetConnectionAddress(target.connectionAddress())
                    .withPendingCatalogSyncConfirmationKey(confirmationKey)
                    .withStatusText(catalogSyncConfirmationMessage(state.selectedCatalogSyncEntityType(), state.selectedCatalogSyncEntityId(), target))
            );
            return;
        }

        String originWorldName = player.getWorld().getName();
        Transform originTransform = captureCurrentTransform(store, ref);
        UiResumeAction resumeAction = catalogSyncResumeAction(
            plugin,
            state,
            state.selectedCatalogSyncEntityType(),
            state.selectedCatalogSyncEntityId(),
            target.connectionAddress()
        );
        try {
            switch (state.selectedCatalogSyncEntityType()) {
                case GAME -> {
                    ArenaDefinition arena = plugin.getArenaService().find(state.selectedCatalogSyncEntityId()).orElse(null);
                    if (arena == null) {
                        open(ref, store, playerRef, player, plugin, state.clearedCatalogSyncConfirmation().withStatusText("The selected game no longer exists."));
                        return;
                    }
                    plugin.getNetworkCatalogSyncService().syncArena(
                        playerRef,
                        destination,
                        originWorldName,
                        originTransform,
                        arena,
                        resumeAction
                    );
                }
                case QUEUE -> {
                    QueueDefinition queue = plugin.getQueueService().find(state.selectedCatalogSyncEntityId()).orElse(null);
                    if (queue == null) {
                        open(ref, store, playerRef, player, plugin, state.clearedCatalogSyncConfirmation().withStatusText("The selected queue no longer exists."));
                        return;
                    }
                    plugin.getNetworkCatalogSyncService().syncQueue(
                        playerRef,
                        destination,
                        originWorldName,
                        originTransform,
                        queue,
                        resumeAction
                    );
                }
            }
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.clearedCatalogSyncConfirmation().withCatalogSyncTargetConnectionAddress(target.connectionAddress()).withStatusText("Failed to start " + state.selectedCatalogSyncEntityType().singularLabel() + " sync: " + exception.getMessage())
            );
        }
    }

    @Nonnull
    static UiResumeAction catalogSyncResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull CatalogSyncEntityType entityType,
        @Nonnull String entityId,
        @Nonnull String targetConnectionAddress
    ) {
        NexoriMenuV2State resumeState = state
            .withSelectedView(NexoriMenuV2View.QUEUES)
            .withSelectedMinigameTab(MinigameWorkspaceTab.SYNC)
            .withCatalogSyncSelection(entityType, entityId)
            .withCatalogSyncTargetConnectionAddress(targetConnectionAddress)
            .clearedCatalogSyncConfirmation();
        return new UiResumeAction() {
            @Override
            public void reopen(
                @Nonnull Ref<EntityStore> ref,
                @Nonnull Store<EntityStore> store,
                @Nonnull PlayerRef playerRef,
                @Nonnull Player player
            ) {
                open(ref, store, playerRef, player, plugin, resumeState);
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
                open(ref, store, playerRef, player, plugin, resumeState.withStatusText(status));
            }
        };
    }

    @Nonnull
    static String catalogSyncConfirmationMessage(
        @Nonnull CatalogSyncEntityType entityType,
        @Nonnull String entityId,
        @Nonnull RemoteServerOption target
    ) {
        String targetLabel = target.serverId().isBlank() ? target.connectionAddress() : target.serverId();
        return switch (entityType) {
            case GAME ->
                "This will create or update game '" + entityId + "' on server '" + targetLabel + "'. It will not copy portals, spawns, backend config, or local server identity. Click CONFIRM to continue.";
            case QUEUE ->
                "This will create or update queue '" + entityId + "' on server '" + targetLabel + "'. It will not copy portals, spawns, backend config, or local server identity. Click CONFIRM to continue.";
        };
    }

    @Nonnull
    static String catalogSyncConfirmationKey(
        @Nonnull CatalogSyncEntityType entityType,
        @Nonnull String entityId,
        @Nonnull String connectionAddress
    ) {
        return entityType.name() + "|" + entityId.trim().toLowerCase(Locale.ROOT) + "|" + connectionAddress.trim().toLowerCase(Locale.ROOT);
    }

    static ConfiguredPeer findConfiguredPeer(@Nonnull List<ConfiguredPeer> peers, @Nonnull String connectionAddress) {
        for (ConfiguredPeer peer : peers) {
            if (peer.connectionAddress().equalsIgnoreCase(connectionAddress)) {
                return peer;
            }
        }
        return null;
    }

    static QueueMatchmakingMode queueModeDraft(@Nonnull PlayerRef playerRef, QueueDefinition editing) {
        QueueMatchmakingMode draft = QUEUE_MODE_DRAFTS.get(playerRef.getUuid());
        if (draft != null) {
            return draft;
        }
        return editing == null ? QueueMatchmakingMode.defaultMode() : editing.effectiveMatchmakingMode();
    }

    @Nonnull
    static AfkDetectionPolicyDraft arenaAfkPolicyDraft(@Nonnull PlayerRef playerRef, ArenaDefinition editing) {
        AfkDetectionPolicyDraft draft = ARENA_AFK_POLICY_DRAFTS.get(playerRef.getUuid());
        if (draft != null) {
            return draft.normalized(editing);
        }
        AfkDetectionPolicy policy = editing == null
            ? AfkDetectionPolicy.defaults()
            : AfkDetectionPolicy.normalize(editing.afkDetectionPolicy());
        return new AfkDetectionPolicyDraft(policy.enabled(), Integer.toString(policy.inactivityTimeoutSeconds()));
    }

    @Nonnull
    static QueueBackfillDraft queueBackfillDraft(@Nonnull PlayerRef playerRef, QueueDefinition editing) {
        QueueBackfillDraft draft = QUEUE_BACKFILL_DRAFTS.get(playerRef.getUuid());
        if (draft != null) {
            return draft.normalized(editing);
        }
        return new QueueBackfillDraft(
            editing != null && editing.backfillEnabled(),
            editing == null ? QueueBackfillMode.defaultMode() : editing.effectiveBackfillMode(),
            editing == null ? "0" : Integer.toString(editing.backfillWindowSeconds())
        );
    }

    @Nonnull
    static QueueBackfillDraft queueBackfillDraftFromInputs(
        @Nonnull PlayerRef playerRef,
        QueueDefinition editing,
        @Nonnull au.ellie.hyui.events.UIContext ctx
    ) {
        QueueBackfillDraft draft = queueBackfillDraft(playerRef, editing);
        return draft.withWindowSeconds(ctx.getValue(QUEUE_BACKFILL_WINDOW_INPUT_ID, String.class).orElse(draft.windowSeconds()).trim());
    }

    @Nonnull
    static NexoriMenuV2State preserveQueueDraftFromInputs(
        @Nonnull NexoriMenuV2State state,
        QueueDefinition editing,
        @Nonnull au.ellie.hyui.events.UIContext ctx
    ) {
        return state.withQueueDraft(
            ctx.getValue(QUEUE_DISPLAY_NAME_INPUT_ID, String.class).orElse(state.pendingQueueDisplayName().isBlank() ? (editing == null ? "New Queue" : editing.displayName()) : state.pendingQueueDisplayName()).trim(),
            state.pendingQueueDestinationId().isBlank() && editing != null && !editing.arenaIds().isEmpty() ? editing.arenaIds().getFirst() : state.pendingQueueDestinationId(),
            ctx.getValue(QUEUE_MIN_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMinPlayers()).trim(),
            ctx.getValue(QUEUE_MAX_PLAYERS_INPUT_ID, String.class).orElse(state.pendingQueueMaxPlayers()).trim(),
            ctx.getValue(QUEUE_COUNTDOWN_INPUT_ID, String.class).orElse(state.pendingQueueCountdownSeconds()).trim()
        );
    }


}

