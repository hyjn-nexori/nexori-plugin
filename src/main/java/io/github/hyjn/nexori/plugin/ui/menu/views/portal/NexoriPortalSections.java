package io.github.hyjn.nexori.plugin.ui.menu.views.portal;

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
public final class NexoriPortalSections extends NexoriMenuSections {

    private NexoriPortalSections() {
    }

    public static GroupBuilder portalTabs(
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
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            PortalWorkspaceTab tab = tabs[index];
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(state.selectedPortalTab() == tab)
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


    public static ReorderableListBuilder buildPortalWorkspaceScroll(
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
        return switch (state.selectedPortalTab()) {
            case BIND -> buildTravelBindScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case SETTINGS -> buildPortalQueueBindingsScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
            case TARGETS -> buildPortalTargetsScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
        };
    }


    static ReorderableListBuilder buildTravelBindScroll(
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
    static GroupBuilder travelBindCard(
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
    static ButtonBuilder travelProfileToggleButton(
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
    static ReorderableListBuilder travelBindListScroll(
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
    static GroupBuilder travelBindServerDividerRow(@Nonnull TravelServerGroup group, int width, int height) {
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
    static GroupBuilder travelBindEmptyGroupRow(int width, int height) {
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
    static GroupBuilder travelBindPortalRow(
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
    static GroupBuilder travelBindTargetRow(
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
    static GroupBuilder travelBindSelectorRowCard(
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
    static GroupBuilder portalBindingListContainer(
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
    static GroupBuilder portalBindingListRow(
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
    static NexoriMenuV2State editTravelBindingState(
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
    static String resolveBindingDestinationDisplayName(
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

    static void executeTravelBindingPlan(
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

    public static void synchronizeTravelInfo(
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

    static void discoverRemoteEndpointsAtIndex(
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
    static UiResumeAction chainedTravelDiscoveryResumeAction(
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
    static UiResumeAction chainedTravelDiscoverySyncResumeAction(
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
    static List<ConfiguredPeer> trustedDiscoveryPeers(
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

    static void applyRemoteBindingOperationAtIndex(
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
    static UiResumeAction chainedPortalBindingResumeAction(
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

    static void applyBindingOperationLocally(
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
    static String partialBindingFailurePrefix(int localAppliedCount) {
        if (localAppliedCount <= 0) {
            return "";
        }
        return "Saved " + localAppliedCount + " local portal binding" + (localAppliedCount == 1 ? "" : "s") + ", but ";
    }

    @Nonnull
    static TravelProfileType currentTravelProfile(@Nonnull NexoriMenuV2State state) {
        return TravelProfileType.parse(state.selectedTravelProfileId());
    }

    @Nonnull
    static TravelProfileType toggleTravelProfile(@Nonnull TravelProfileType profile) {
        return profile == TravelProfileType.APPLY_INVENTORY
            ? TravelProfileType.KEEP_INVENTORY
            : TravelProfileType.APPLY_INVENTORY;
    }

    static TravelBindOperation forwardBindingOperation(@Nonnull NexoriMenuV2State state) {
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

    static TravelBindOperation reverseBindingOperation(@Nonnull NexoriMenuV2State state) {
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
    protected static GroupBuilder centeredLabel(@Nonnull String text, @Nonnull HyUIStyle style, int width, int labelWidth, int estimatedCharWidth) {
        int estimatedWidth = Math.min(width, Math.max(48, text.length() * estimatedCharWidth));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width).setHeight(24));
        row.addChild(spacerX(Math.max(0, (width - estimatedWidth) / 2)));
        row.addChild(label(text, style, labelWidth));
        return row;
    }

    @Nonnull
    static GroupBuilder serverEndpointsContainer(
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
    static GroupBuilder serverEndpointsGroupCard(
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
    static GroupBuilder endpointSectionCard(
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
    static GroupBuilder buildPortalSectionContent(
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
    static GroupBuilder buildTargetSectionContent(
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
    static GroupBuilder portalEndpointRow(
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
    static GroupBuilder targetEndpointRow(
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
    static ButtonBuilder endpointSelectionButton(
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
    public static List<TravelServerGroup> buildTravelServerGroups(
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
    static String resolveServerDisplayName(@Nonnull Map<String, ConfiguredPeer> configuredByAddress, @Nonnull String connectionAddress, boolean local) {
        ConfiguredPeer peer = configuredByAddress.get(connectionAddress.toLowerCase());
        if (peer != null) {
            return peer.displayName();
        }
        return local ? "Current Server" : connectionAddress;
    }

    static int serverEndpointsGroupHeight(@Nonnull TravelServerGroup group) {
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

    static int sectionHeight(int rows) {
        return sectionContentHeight(rows) + 32 + (TRAVEL_SECTION_INSET * 2);
    }

    static int sectionContentHeight(int rows) {
        return rows * HOME_SERVER_CARD_H + Math.max(0, rows - 1) * 8;
    }


    static ReorderableListBuilder buildPortalQueueBindingsScroll(
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
    static ReorderableListBuilder buildPortalTargetsScroll(
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
    static GroupBuilder portalTargetsSetupCard(
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
    static ReorderableListBuilder targetServerListScroll(
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
    static GroupBuilder targetServerRow(
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
    static ReorderableListBuilder targetListScroll(
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
    static GroupBuilder targetManagementRow(
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
    static List<TravelServerGroup> buildCoordinateTargetServerGroups(
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
    static String buildCoordinateTargetMetadataJson(
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
    static GroupBuilder portalQueueBindingSetupCard(
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
    static ReorderableListBuilder portalQueuePortalListScroll(
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
    static GroupBuilder portalQueuePortalRow(
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
    static ReorderableListBuilder portalQueueListScroll(
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
    static GroupBuilder portalQueueQueueRow(
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
    static GroupBuilder portalQueueActionListContainer(
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
    static GroupBuilder portalQueueActionRow(
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


}

