package io.github.hyjn.nexori.plugin.ui.menu.views.rules;

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
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
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
public final class NexoriRulesSections extends NexoriMenuSections {

    private NexoriRulesSections() {
    }

    public static GroupBuilder rulesTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        int tabWidth = 180;
        int gap = 8;
        RulesWorkspaceTab[] tabs = RulesWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        for (int index = 0; index < tabs.length; index++) {
            RulesWorkspaceTab tab = tabs[index];
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(state.selectedRulesTab() == tab)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedRulesTab(tab).withStatusText("")
                ));
            row.addChild(button);
            if (index + 1 < tabs.length) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }


    public static ReorderableListBuilder buildRulesWorkspaceScroll(
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
        return buildRulesGroupsScroll(ref, store, playerRef, player, plugin, state, peers, setup, viewportHeight, scrollId);
    }

    @Nonnull
    static ReorderableListBuilder buildRulesGroupsScroll(
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
        List<ServerRuleGroupDefinition> ruleGroups = plugin.getServerRuleGroupService().list();
        List<RuleServerEntry> serverEntries = buildRuleServerEntries(plugin, peers);
        ServerRuleGroupDefinition selectedGroup = selectedRuleGroupForDisplay(plugin, state, ruleGroups);
        int setupHeight = 248;
        int tablesHeight = Math.max(360, viewportHeight - setupHeight - 60);
        int tableWidth = (innerWidth - 12) / 2;
        int contentHeight = 16 + setupHeight + 12 + tablesHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(ruleGroupSetupCard(ref, store, playerRef, player, plugin, state, selectedGroup, innerWidth, setupHeight));
        scroll.addChild(spacerY(12));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(tablesHeight));
        row.addChild(ruleGroupsTableContainer(ref, store, playerRef, player, plugin, state, ruleGroups, selectedGroup, tableWidth, tablesHeight, scrollId + "-groups"));
        row.addChild(spacerX(12));
        row.addChild(ruleAssignmentsTableContainer(ref, store, playerRef, player, plugin, state, ruleGroups, serverEntries, selectedGroup, innerWidth - tableWidth - 12, tablesHeight, scrollId + "-servers"));
        scroll.addChild(row);
        return scroll;
    }

    @Nonnull
    static ReorderableListBuilder buildRulesSyncScroll(
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
        List<ServerRuleGroupDefinition> ruleGroups = plugin.getServerRuleGroupService().list();
        List<RuleServerEntry> serverEntries = buildRuleServerEntries(plugin, peers);
        ServerRuleGroupDefinition selectedGroup = selectedRuleGroupForDisplay(plugin, state, ruleGroups);
        ServerRuleGroupDefinition syncableGroup = isPersistedRuleGroup(selectedGroup) ? selectedGroup : null;
        int summaryHeight = 188;
        int statusHeight = Math.max(340, viewportHeight - summaryHeight - 44);
        int contentHeight = 16 + summaryHeight + 12 + statusHeight + 20;

        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        scroll.addChild(ruleSyncSummaryCard(ref, store, playerRef, player, plugin, state, syncableGroup, serverEntries, innerWidth, summaryHeight));
        scroll.addChild(spacerY(12));
        scroll.addChild(ruleStatusListContainer(ref, store, playerRef, player, plugin, state, syncableGroup, serverEntries, innerWidth, statusHeight, scrollId + "-status"));
        return scroll;
    }


    static GroupBuilder ruleGroupSetupCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height
    ) {
        boolean persisted = isPersistedRuleGroup(selectedGroup);
        String displayValue = state.pendingRuleGroupName().isBlank()
            ? selectedGroup.displayName()
            : state.pendingRuleGroupName();
        int innerWidth = width - 32;
        int groupInputWidth = (innerWidth - 24) / 3;

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Rule Group Setup", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Here you can create server configuration templates and choose which servers to apply them to.", MUTED, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("NEW GROUP")
                .withAnchor(new HyUIAnchor().setWidth(130).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(NEW_RULE_GROUP_ID).withPendingRuleGroupName("").withStatusText("Started a new rule group draft.")
                ))
        );
        actionRow.addChild(spacerX(10));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText("SAVE GROUP")
                .withAnchor(new HyUIAnchor().setWidth(150).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    String displayName = ctx.getValue(RULE_GROUP_NAME_INPUT_ID, String.class).orElse(displayValue).trim();
                    try {
                        ServerRuleGroupDefinition saved = persisted
                            ? plugin.getServerRuleGroupService().rename(selectedGroup.groupId(), displayName)
                            : plugin.getServerRuleGroupService().create(displayName, selectedGroup.recoveryEnabled(), selectedGroup.maxBackupsPerPlayer());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(saved.groupId()).withPendingRuleGroupName("").withStatusText("Saved rule group " + saved.displayName() + ".")
                        );
                    } catch (IOException | IllegalArgumentException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withPendingRuleGroupName(displayName).withStatusText("Could not save rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        actionRow.addChild(spacerX(10));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("APPLY TO ASSIGNED")
                .withAnchor(new HyUIAnchor().setWidth(190).setHeight(HOME_INPUT_FIELD_H))
                .withDisabled(!persisted)
                .onClick((ignored, ctx) -> applyRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedGroup, buildRuleServerEntries(plugin, plugin.getConfiguredPeerService().list())))
        );
        card.addChild(actionRow);
        card.addChild(spacerY(12));

        GroupBuilder inputsRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(96));
        inputsRow.addChild(ruleGroupNameField(displayValue, groupInputWidth, 96));
        inputsRow.addChild(spacerX(12));
        inputsRow.addChild(ruleRecoveryField(ref, store, playerRef, player, plugin, state, selectedGroup, persisted, groupInputWidth, 96));
        inputsRow.addChild(spacerX(12));
        inputsRow.addChild(ruleBackupsField(ref, store, playerRef, player, plugin, state, selectedGroup, persisted, groupInputWidth, 96));
        card.addChild(inputsRow);
        return card;
    }

    @Nonnull
    static GroupBuilder ruleGroupNameField(
        @Nonnull String displayValue,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label("Group Name", SUBTITLE, width - 28));
        card.addChild(spacerY(10));
        card.addChild(
            TextFieldBuilder.textInput()
                .withId(RULE_GROUP_NAME_INPUT_ID)
                .withValue(displayValue)
                .withPlaceholderText("New Rule Group")
                .withMaxLength(255)
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(HOME_INPUT_FIELD_H))
                .withBackground("#101926")
        );
        return card;
    }

    @Nonnull
    static GroupBuilder ruleGroupsTableContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = ruleGroups.isEmpty()
            ? bodyViewportHeight
            : 8 + ruleGroups.size() * HOME_SERVER_CARD_H + Math.max(0, ruleGroups.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Rule Groups", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (ruleGroups.isEmpty()) {
            body.addChild(label("No rule groups saved yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < ruleGroups.size(); index++) {
                ServerRuleGroupDefinition group = ruleGroups.get(index);
                body.addChild(ruleGroupListRowCard(ref, store, playerRef, player, plugin, state, group, selectedGroup, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < ruleGroups.size()) {
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
    static GroupBuilder ruleRecoveryField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        boolean persisted,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label("Recovery", SUBTITLE, width - 28));
        card.addChild(spacerY(10));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(selectedGroup.recoveryEnabled() ? "ON" : "OFF")
                .withBackground(selectedGroup.recoveryEnabled() ? GOOD_BG : PANEL_BG)
                .withAnchor(new HyUIAnchor().setWidth(96).setHeight(42))
                .withDisabled(true)
        );
        row.addChild(spacerX(10));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(selectedGroup.recoveryEnabled() ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 134).setHeight(42))
                .withDisabled(!persisted)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = plugin.getServerRuleGroupService().setRecoveryEnabled(selectedGroup.groupId(), !selectedGroup.recoveryEnabled());
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(updated.groupId()).withStatusText("Recovery is now " + (updated.recoveryEnabled() ? "enabled" : "disabled") + " for " + updated.displayName() + "."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withStatusText("Could not update recovery: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder ruleBackupsField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        boolean persisted,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.addChild(label("Max Backups", SUBTITLE, width - 28));
        card.addChild(spacerY(10));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("-")
                .withAnchor(new HyUIAnchor().setWidth(42).setHeight(42))
                .withDisabled(!persisted || selectedGroup.maxBackupsPerPlayer() <= 1)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = plugin.getServerRuleGroupService().setMaxBackupsPerPlayer(selectedGroup.groupId(), selectedGroup.maxBackupsPerPlayer() - 1);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(updated.groupId()).withStatusText("Max backups is now " + updated.maxBackupsPerPlayer() + " for " + updated.displayName() + "."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withStatusText("Could not lower max backups: " + exception.getMessage()));
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(Integer.toString(selectedGroup.maxBackupsPerPlayer()))
                .withBackground(INFO_BG)
                .withAnchor(new HyUIAnchor().setWidth(92).setHeight(42))
                .withDisabled(true)
        );
        row.addChild(spacerX(8));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("+")
                .withAnchor(new HyUIAnchor().setWidth(42).setHeight(42))
                .withDisabled(!persisted)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = plugin.getServerRuleGroupService().setMaxBackupsPerPlayer(selectedGroup.groupId(), selectedGroup.maxBackupsPerPlayer() + 1);
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRuleGroupId(updated.groupId()).withStatusText("Max backups is now " + updated.maxBackupsPerPlayer() + " for " + updated.displayName() + "."));
                    } catch (IOException | IllegalArgumentException exception) {
                        open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.RULES).withStatusText("Could not increase max backups: " + exception.getMessage()));
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder ruleAssignmentsTableContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull List<RuleServerEntry> serverEntries,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = serverEntries.isEmpty()
            ? bodyViewportHeight
            : 8 + serverEntries.size() * HOME_SERVER_CARD_H + Math.max(0, serverEntries.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Servers In This Group", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (serverEntries.isEmpty()) {
            body.addChild(label("No servers are available yet.", MUTED, width - 48));
        } else {
            Map<String, ServerRuleGroupDefinition> assignments = buildRuleGroupAssignments(ruleGroups);
            for (int index = 0; index < serverEntries.size(); index++) {
                body.addChild(ruleAssignmentRowCard(ref, store, playerRef, player, plugin, state, selectedGroup, serverEntries.get(index), assignments, width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < serverEntries.size()) {
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
    static GroupBuilder ruleSyncSummaryCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        ServerRuleGroupDefinition selectedGroup,
        @Nonnull List<RuleServerEntry> serverEntries,
        int width,
        int height
    ) {
        GroupBuilder card = card(width, height, PANEL_BG);
        if (selectedGroup == null) {
            card.addChild(label("Sync", TITLE, width - 32));
            card.addChild(spacerY(8));
            card.addChild(label("Select a saved rule group in GROUPS first.", MUTED, width - 32));
            return card;
        }

        GroupStatusCounts counts = computeGroupStatusCounts(plugin, selectedGroup, serverEntries);
        card.addChild(label(selectedGroup.displayName(), TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label(
            "Recovery " + (selectedGroup.recoveryEnabled() ? "ON" : "OFF")
                + " · Max Backups " + selectedGroup.maxBackupsPerPlayer()
                + " · Assigned " + counts.assigned(),
            MUTED,
            width - 32
        ));
        card.addChild(spacerY(12));

        GroupBuilder topStatus = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(32));
        topStatus.addChild(statusBadgeRow("Matching", Integer.toString(counts.matching()), 200, GOOD, GOOD_BG));
        topStatus.addChild(spacerX(12));
        topStatus.addChild(statusBadgeRow("Needs Apply", Integer.toString(counts.needsApply()), 220, BAD, BAD_BG));
        topStatus.addChild(spacerX(12));
        topStatus.addChild(statusBadgeRow("Unknown", Integer.toString(counts.unknown()), 180, INFO, INFO_BG));
        card.addChild(topStatus);
        card.addChild(spacerY(16));

        GroupBuilder actions = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(42));
        int totalWidth = 230 + 12 + 190;
        actions.addChild(spacerX(Math.max(0, ((width - 32) - totalWidth) / 2)));
        actions.addChild(
            ButtonBuilder.textButton()
                .withText("APPLY TO ASSIGNED")
                .withAnchor(new HyUIAnchor().setWidth(230).setHeight(42))
                .onClick((ignored, ctx) -> applyRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedGroup, serverEntries))
        );
        actions.addChild(spacerX(12));
        actions.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("REFRESH STATUS")
                .withAnchor(new HyUIAnchor().setWidth(190).setHeight(42))
                .onClick((ignored, ctx) -> refreshRuleGroupAssignments(ref, store, playerRef, player, plugin, state, selectedGroup, serverEntries))
        );
        card.addChild(actions);
        return card;
    }

    @Nonnull
    static GroupBuilder ruleStatusListContainer(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        ServerRuleGroupDefinition selectedGroup,
        @Nonnull List<RuleServerEntry> serverEntries,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        int outerGap = 12;
        List<RuleServerEntry> assignedServers = selectedGroup == null
            ? List.of()
            : serverEntries.stream().filter(entry -> selectedGroup.containsServer(entry.selectionKey())).toList();
        int bodyViewportHeight = height - 64 - 12 - (outerGap * 2);
        int bodyContentHeight = assignedServers.isEmpty()
            ? bodyViewportHeight
            : 8 + assignedServers.size() * HOME_SERVER_CARD_H + Math.max(0, assignedServers.size() - 1) * 8 + 8;
        GroupBuilder container = card(width, height, PANEL_BG);
        container.addChild(spacerY(outerGap));
        container.addChild(singleColumnHeaderCard("Server Status", width - 32));
        container.addChild(spacerY(12));

        ReorderableListBuilder body = scrollList(width - 32, bodyViewportHeight, Math.max(bodyViewportHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (selectedGroup == null) {
            body.addChild(label("Select a saved rule group first.", MUTED, width - 48));
        } else if (assignedServers.isEmpty()) {
            body.addChild(label("This rule group has no assigned servers yet.", MUTED, width - 48));
        } else {
            for (int index = 0; index < assignedServers.size(); index++) {
                body.addChild(ruleStatusRow(plugin, selectedGroup, assignedServers.get(index), width - 48, HOME_SERVER_CARD_H));
                if (index + 1 < assignedServers.size()) {
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
    static List<RuleServerEntry> buildRuleServerEntries(
        @Nonnull NexoriPlugin plugin,
        @Nonnull List<ConfiguredPeer> peers
    ) {
        List<RuleServerEntry> entries = new ArrayList<>();
        String localConnectionAddress = plugin.getLocalConnectionAddressService().getConnectionAddressOrBlank();
        entries.add(new RuleServerEntry(
            LOCAL_SERVER_KEY,
            localConnectionAddress,
            "Current Server",
            localConnectionAddress.isBlank() ? "This server" : localConnectionAddress,
            true
        ));

        Map<String, String> displayNamesByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : peers) {
            if (!peer.connectionAddress().isBlank()) {
                displayNamesByAddress.putIfAbsent(
                    peer.connectionAddress(),
                    peer.displayName().isBlank() ? peer.connectionAddress() : peer.displayName()
                );
            }
        }

        Set<String> seenAddresses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (ConfiguredPeer trustedPeer : trustedNetworkPeers(plugin)) {
            if (!seenAddresses.add(trustedPeer.connectionAddress())) {
                continue;
            }
            String displayName = displayNamesByAddress.getOrDefault(
                trustedPeer.connectionAddress(),
                trustedPeer.displayName().isBlank() ? trustedPeer.connectionAddress() : trustedPeer.displayName()
            );
            entries.add(new RuleServerEntry(
                trustedPeer.connectionAddress(),
                trustedPeer.connectionAddress(),
                displayName,
                trustedPeer.connectionAddress(),
                false
            ));
        }
        return entries;
    }

    @Nonnull
    static ServerRuleGroupDefinition selectedRuleGroupForDisplay(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups
    ) {
        if (NEW_RULE_GROUP_ID.equalsIgnoreCase(state.selectedRuleGroupId())) {
            return newRuleGroupDraft(plugin, state);
        }
        for (ServerRuleGroupDefinition group : ruleGroups) {
            if (group.groupId().equalsIgnoreCase(state.selectedRuleGroupId())) {
                return group;
            }
        }
        return ruleGroups.isEmpty() ? newRuleGroupDraft(plugin, state) : ruleGroups.getFirst();
    }

    @Nonnull
    static ServerRuleGroupDefinition newRuleGroupDraft(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        String displayName = state.pendingRuleGroupName().isBlank()
            ? "New Rule Group"
            : state.pendingRuleGroupName();
        return new ServerRuleGroupDefinition(
            NEW_RULE_GROUP_ID,
            displayName,
            plugin.getInventoryTransferService().isRecoveryEnabled(),
            plugin.getInventoryTransferService().getMaxBackupsPerPlayer(),
            List.of()
        ).normalized();
    }

    static boolean isPersistedRuleGroup(ServerRuleGroupDefinition group) {
        return group != null && !NEW_RULE_GROUP_ID.equalsIgnoreCase(group.groupId());
    }

    @Nonnull
    static GroupBuilder ruleGroupListRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull List<ServerRuleGroupDefinition> ruleGroups,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height
    ) {
        boolean selected = selectedGroup.groupId().equalsIgnoreCase(group.groupId());
        int deleteWidth = 104;
        int selectWidth = 110;
        int actionWidth = deleteWidth + 8 + selectWidth;
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int identityWidth = width - 28 - actionWidth - 12;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(identityWidth).setHeight(rowHeight));
        identity.addChild(label(group.displayName(), SUBTITLE, identityWidth));
        identity.addChild(spacerY(2));
        identity.addChild(label(
            "Recovery " + (group.recoveryEnabled() ? "ON" : "OFF")
                + " · Backups " + group.maxBackupsPerPlayer()
                + " · " + group.assignedServerKeys().size() + " server(s)",
            MUTED,
            identityWidth
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("DELETE")
                .withAnchor(new HyUIAnchor().setWidth(deleteWidth).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getServerRuleGroupService().remove(group.groupId());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId("")
                                .withPendingRuleGroupName("")
                                .withStatusText(removed ? "Deleted rule group " + group.displayName() + "." : "Rule group was already removed.")
                        );
                    } catch (IOException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not delete rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            (selected ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(selected ? "SELECTED" : "SELECT")
                .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(selectWidth).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state
                        .withSelectedView(NexoriMenuV2View.RULES)
                        .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                        .withSelectedRuleGroupId(group.groupId())
                        .withPendingRuleGroupName("")
                        .withStatusText("Selected rule group " + group.displayName() + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder ruleGroupListRowCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        int width,
        int height
    ) {
        boolean selected = selectedGroup.groupId().equalsIgnoreCase(group.groupId());
        int deleteWidth = 104;
        int selectWidth = 110;
        int actionWidth = deleteWidth + 8 + selectWidth;
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int identityWidth = width - 28 - actionWidth - 12;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(identityWidth).setHeight(rowHeight));
        identity.addChild(label(group.displayName(), SUBTITLE, identityWidth));
        identity.addChild(spacerY(2));
        identity.addChild(label(
            group.assignedServerKeys().size() + " server(s)",
            MUTED,
            identityWidth
        ));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("DELETE")
                .withAnchor(new HyUIAnchor().setWidth(deleteWidth).setHeight(30))
                .onClick((ignored, ctx) -> {
                    try {
                        boolean removed = plugin.getServerRuleGroupService().remove(group.groupId());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId("")
                                .withPendingRuleGroupName("")
                                .withStatusText(removed ? "Deleted rule group " + group.displayName() + "." : "Rule group was already removed.")
                        );
                    } catch (IOException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not delete rule group: " + exception.getMessage())
                        );
                    }
                })
        );
        row.addChild(spacerX(8));
        row.addChild(
            (selected ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(selected ? "SELECTED" : "SELECT")
                .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(selectWidth).setHeight(30))
                .withDisabled(selected)
                .onClick((ignored, ctx) -> open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state
                        .withSelectedView(NexoriMenuV2View.RULES)
                        .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                        .withSelectedRuleGroupId(group.groupId())
                        .withPendingRuleGroupName("")
                        .withStatusText("Selected rule group " + group.displayName() + ".")
                ))
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static Map<String, ServerRuleGroupDefinition> buildRuleGroupAssignments(@Nonnull List<ServerRuleGroupDefinition> ruleGroups) {
        Map<String, ServerRuleGroupDefinition> assignments = new LinkedHashMap<>();
        for (ServerRuleGroupDefinition group : ruleGroups) {
            for (String assignedServerKey : group.assignedServerKeys()) {
                assignments.put(assignedServerKey, group);
            }
        }
        return assignments;
    }

    @Nonnull
    static GroupBuilder ruleAssignmentRow(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry,
        @Nonnull Map<String, ServerRuleGroupDefinition> assignments,
        int width,
        int height
    ) {
        ServerRuleGroupDefinition owningGroup = assignments.get(entry.selectionKey());
        boolean assignedHere = owningGroup != null && owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean assignedElsewhere = owningGroup != null && !owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean canChange = isPersistedRuleGroup(selectedGroup) && !assignedElsewhere;

        String detail = assignedHere
            ? "In this group · " + entry.displayAddress()
            : assignedElsewhere
                ? "Used by " + owningGroup.displayName() + " · " + entry.displayAddress()
                : "Available · " + entry.displayAddress();
        String buttonText = !isPersistedRuleGroup(selectedGroup)
            ? "SAVE FIRST"
            : assignedHere
                ? "REMOVE"
                : assignedElsewhere
                    ? "IN USE"
                    : "ADD";

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 40;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 150).setHeight(rowHeight));
        identity.addChild(label(entry.displayName(), SUBTITLE, width - 150));
        identity.addChild(spacerY(2));
        identity.addChild(label(detail, MUTED, width - 150));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            (assignedHere ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(buttonText)
                .withBackground(assignedHere ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(110).setHeight(30))
                .withDisabled(!canChange)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = assignedHere
                            ? plugin.getServerRuleGroupService().unassignServer(selectedGroup.groupId(), entry.selectionKey())
                            : plugin.getServerRuleGroupService().assignServer(selectedGroup.groupId(), entry.selectionKey());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId(updated.groupId())
                                .withStatusText(
                                    assignedHere
                                        ? "Removed " + entry.displayName() + " from " + updated.displayName() + "."
                                        : "Assigned " + entry.displayName() + " to " + updated.displayName() + "."
                                )
                        );
                    } catch (IOException | IllegalStateException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not update that assignment: " + exception.getMessage())
                        );
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static GroupBuilder ruleAssignmentRowCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry,
        @Nonnull Map<String, ServerRuleGroupDefinition> assignments,
        int width,
        int height
    ) {
        ServerRuleGroupDefinition owningGroup = assignments.get(entry.selectionKey());
        boolean assignedHere = owningGroup != null && owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean assignedElsewhere = owningGroup != null && !owningGroup.groupId().equalsIgnoreCase(selectedGroup.groupId());
        boolean canChange = isPersistedRuleGroup(selectedGroup) && !assignedElsewhere;
        RuleMatchIndicator indicator = ruleMatchIndicator(plugin, selectedGroup, entry);

        String buttonText = !isPersistedRuleGroup(selectedGroup)
            ? "SAVE FIRST"
            : assignedHere
                ? "REMOVE"
                : assignedElsewhere
                    ? "IN USE"
                    : "ADD";

        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.symmetric(14, 0))
            .withBackground(SERVER_CARD_BG);
        int rowHeight = 30;
        int statusWidth = 126;
        int actionWidth = 110;
        card.addChild(spacerY(Math.max(0, (height - rowHeight) / 2)));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(rowHeight));
        int identityWidth = width - 28 - statusWidth - actionWidth - 24;
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(identityWidth).setHeight(rowHeight));
        identity.addChild(label(entry.displayName(), SUBTITLE, identityWidth));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(indicator.label())
                .withBackground(indicator.background())
                .withAnchor(new HyUIAnchor().setWidth(statusWidth).setHeight(30))
                .withDisabled(true)
        );
        row.addChild(spacerX(12));
        row.addChild(
            (assignedHere ? ButtonBuilder.textButton() : ButtonBuilder.smallSecondaryTextButton())
                .withText(buttonText)
                .withBackground(assignedHere ? BUTTON_SELECTED_BG : BUTTON_BG)
                .withAnchor(new HyUIAnchor().setWidth(actionWidth).setHeight(30))
                .withDisabled(!canChange)
                .onClick((ignored, ctx) -> {
                    try {
                        ServerRuleGroupDefinition updated = assignedHere
                            ? plugin.getServerRuleGroupService().unassignServer(selectedGroup.groupId(), entry.selectionKey())
                            : plugin.getServerRuleGroupService().assignServer(selectedGroup.groupId(), entry.selectionKey());
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state
                                .withSelectedView(NexoriMenuV2View.RULES)
                                .withSelectedRulesTab(RulesWorkspaceTab.GROUPS)
                                .withSelectedRuleGroupId(updated.groupId())
                                .withStatusText(
                                    assignedHere
                                        ? "Removed " + entry.displayName() + " from " + updated.displayName() + "."
                                        : "Assigned " + entry.displayName() + " to " + updated.displayName() + "."
                                )
                        );
                    } catch (IOException | IllegalStateException exception) {
                        open(
                            ref,
                            store,
                            playerRef,
                            player,
                            plugin,
                            state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withStatusText("Could not update that assignment: " + exception.getMessage())
                        );
                    }
                })
        );
        card.addChild(row);
        return card;
    }

    @Nonnull
    static RuleMatchIndicator ruleMatchIndicator(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry
    ) {
        if (entry.local()) {
            boolean matching = matchesPolicy(
                selectedGroup,
                plugin.getInventoryTransferService().isRecoveryEnabled(),
                plugin.getInventoryTransferService().getMaxBackupsPerPlayer()
            );
            return matching
                ? new RuleMatchIndicator("MATCHING", GOOD_BG)
                : new RuleMatchIndicator("NOT MATCHING", BAD_BG);
        }

        ServerPolicySummary cached = plugin.getServerPolicyCacheService().find(entry.connectionAddress()).orElse(null);
        if (cached == null) {
            return new RuleMatchIndicator("UNKNOWN", INFO_BG);
        }

        return matchesPolicy(selectedGroup, cached.recoveryEnabled(), cached.maxBackupsPerPlayer())
            ? new RuleMatchIndicator("MATCHING", GOOD_BG)
            : new RuleMatchIndicator("NOT MATCHING", BAD_BG);
    }

    @Nonnull
    static GroupStatusCounts computeGroupStatusCounts(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<RuleServerEntry> serverEntries
    ) {
        int assigned = 0;
        int matching = 0;
        int needsApply = 0;
        int unknown = 0;
        for (String assignedServerKey : group.assignedServerKeys()) {
            assigned++;
            RuleServerEntry entry = null;
            for (RuleServerEntry serverEntry : serverEntries) {
                if (serverEntry.selectionKey().equalsIgnoreCase(assignedServerKey)) {
                    entry = serverEntry;
                    break;
                }
            }
            if (entry == null) {
                unknown++;
                continue;
            }

            if (entry.local()) {
                if (matchesPolicy(group, plugin.getInventoryTransferService().isRecoveryEnabled(), plugin.getInventoryTransferService().getMaxBackupsPerPlayer())) {
                    matching++;
                } else {
                    needsApply++;
                }
                continue;
            }

            ServerPolicySummary summary = plugin.getServerPolicyCacheService().find(entry.connectionAddress()).orElse(null);
            if (summary == null) {
                unknown++;
            } else if (matchesPolicy(group, summary.recoveryEnabled(), summary.maxBackupsPerPlayer())) {
                matching++;
            } else {
                needsApply++;
            }
        }
        return new GroupStatusCounts(assigned, matching, needsApply, unknown);
    }

    static boolean matchesPolicy(
        @Nonnull ServerRuleGroupDefinition group,
        boolean recoveryEnabled,
        int maxBackupsPerPlayer
    ) {
        return group.recoveryEnabled() == recoveryEnabled
            && group.maxBackupsPerPlayer() == Math.max(1, maxBackupsPerPlayer);
    }

    @Nonnull
    static GroupBuilder ruleStatusRow(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ServerRuleGroupDefinition selectedGroup,
        @Nonnull RuleServerEntry entry,
        int width,
        int height
    ) {
        String statusText;
        HyUIPatchStyle statusBackground;
        if (entry.local()) {
            boolean matching = matchesPolicy(
                selectedGroup,
                plugin.getInventoryTransferService().isRecoveryEnabled(),
                plugin.getInventoryTransferService().getMaxBackupsPerPlayer()
            );
            statusText = matching ? "MATCHING" : "NEEDS APPLY";
            statusBackground = matching ? GOOD_BG : BAD_BG;
        } else {
            ServerPolicySummary cached = plugin.getServerPolicyCacheService().find(entry.connectionAddress()).orElse(null);
            if (cached == null) {
                statusText = "UNKNOWN";
                statusBackground = INFO_BG;
            } else if (matchesPolicy(selectedGroup, cached.recoveryEnabled(), cached.maxBackupsPerPlayer())) {
                statusText = "MATCHING";
                statusBackground = GOOD_BG;
            } else {
                statusText = "NEEDS APPLY";
                statusBackground = BAD_BG;
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
        GroupBuilder identity = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(width - 170).setHeight(rowHeight));
        identity.addChild(label(entry.displayName(), SUBTITLE, width - 170));
        identity.addChild(spacerY(2));
        identity.addChild(label(entry.displayAddress(), MUTED, width - 170));
        row.addChild(identity);
        row.addChild(spacerX(12));
        row.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText(statusText)
                .withBackground(statusBackground)
                .withAnchor(new HyUIAnchor().setWidth(130).setHeight(30))
                .withDisabled(true)
        );
        card.addChild(row);
        return card;
    }

    static void applyRuleGroupAssignments(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<RuleServerEntry> serverEntries
    ) {
        ServerRuleGroupDefinition currentGroup = plugin.getServerRuleGroupService().find(group.groupId()).orElse(group);
        List<RuleServerEntry> assignedServers = serverEntries.stream()
            .filter(entry -> currentGroup.containsServer(entry.selectionKey()))
            .toList();
        if (assignedServers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Assign at least one server before applying this rule group.")
            );
            return;
        }

        boolean localApplied = false;
        if (currentGroup.containsServer(LOCAL_SERVER_KEY)) {
            try {
                plugin.getInventoryTransferService().setRecoveryEnabled(currentGroup.recoveryEnabled());
                plugin.getInventoryTransferService().setMaxBackupsPerPlayer(currentGroup.maxBackupsPerPlayer());
                localApplied = true;
            } catch (IOException exception) {
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Could not apply local server rules: " + exception.getMessage())
                );
                return;
            }
        }

        Map<String, ConfiguredPeer> trustedPeersByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : trustedNetworkPeers(plugin)) {
            trustedPeersByAddress.putIfAbsent(peer.connectionAddress(), peer);
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (RuleServerEntry entry : assignedServers) {
            if (entry.local() || entry.connectionAddress().isBlank()) {
                continue;
            }
            ConfiguredPeer peer = trustedPeersByAddress.get(entry.connectionAddress());
            if (peer != null) {
                remotePeers.add(peer);
            } else {
                unresolved.add(entry.displayAddress());
            }
        }

        if (!unresolved.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("These assigned servers are not in the trusted network yet: " + String.join(", ", unresolved) + ".")
            );
            return;
        }

        if (remotePeers.isEmpty()) {
            String finalStatus = localApplied
                ? "Applied " + currentGroup.displayName() + " to the local server."
                : "There are no remote servers assigned to " + currentGroup.displayName() + ".";
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText(finalStatus)
            );
            return;
        }

        String successMessage = localApplied
            ? "Applied " + currentGroup.displayName() + " to the local server and " + remotePeers.size() + " remote server(s)."
            : "Applied " + currentGroup.displayName() + " to " + remotePeers.size() + " remote server(s).";
        try {
            applyRuleGroupAssignmentAtIndex(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state,
                currentGroup,
                remotePeers,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                0,
                successMessage,
                localApplied ? 1 : 0
            );
        } catch (IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Could not start rules apply: " + exception.getMessage())
            );
        }
    }

    static void applyRuleGroupAssignmentAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index,
        @Nonnull String successMessage,
        int localAppliedCount
    ) throws IllegalStateException {
        if (index >= remotePeers.size()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(successMessage)
            );
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        try {
            plugin.getServerPolicySyncService().apply(
                playerRef,
                destination,
                originWorldName,
                originTransform.clone(),
                group.recoveryEnabled(),
                group.maxBackupsPerPlayer(),
                chainedRuleApplyResumeAction(
                    plugin,
                    state,
                    group,
                    remotePeers,
                    originWorldName,
                    originTransform,
                    index + 1,
                    successMessage,
                    localAppliedCount
                )
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException(partialRuleApplyFailurePrefix(localAppliedCount) + exception.getMessage(), exception);
        }
    }

    static void refreshRuleGroupAssignments(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<RuleServerEntry> serverEntries
    ) {
        ServerRuleGroupDefinition currentGroup = plugin.getServerRuleGroupService().find(group.groupId()).orElse(group);
        List<RuleServerEntry> remoteAssignedServers = serverEntries.stream()
            .filter(entry -> currentGroup.containsServer(entry.selectionKey()))
            .filter(entry -> !entry.local() && !entry.connectionAddress().isBlank())
            .toList();
        if (remoteAssignedServers.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("There are no remote servers assigned to " + currentGroup.displayName() + ".")
            );
            return;
        }

        Map<String, ConfiguredPeer> trustedPeersByAddress = new LinkedHashMap<>();
        for (ConfiguredPeer peer : trustedNetworkPeers(plugin)) {
            trustedPeersByAddress.putIfAbsent(peer.connectionAddress(), peer);
        }

        List<ConfiguredPeer> remotePeers = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (RuleServerEntry entry : remoteAssignedServers) {
            ConfiguredPeer peer = trustedPeersByAddress.get(entry.connectionAddress());
            if (peer != null) {
                remotePeers.add(peer);
            } else {
                unresolved.add(entry.displayAddress());
            }
        }

        if (!unresolved.isEmpty()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("These assigned servers are not in the trusted network yet: " + String.join(", ", unresolved) + ".")
            );
            return;
        }

        try {
            refreshRuleGroupAssignmentAtIndex(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state,
                currentGroup,
                remotePeers,
                player.getWorld().getName(),
                captureCurrentTransform(store, ref),
                0
            );
        } catch (IllegalStateException exception) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(currentGroup.groupId()).withStatusText("Could not start rules refresh: " + exception.getMessage())
            );
        }
    }

    static void refreshRuleGroupAssignmentAtIndex(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int index
    ) throws IllegalStateException {
        if (index >= remotePeers.size()) {
            open(
                ref,
                store,
                playerRef,
                player,
                plugin,
                state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText("Refreshed status for " + remotePeers.size() + " remote server(s).")
            );
            return;
        }

        ConfiguredPeer destination = remotePeers.get(index);
        try {
            plugin.getServerPolicySyncService().refresh(
                playerRef,
                destination,
                originWorldName,
                originTransform.clone(),
                chainedRuleRefreshResumeAction(
                    plugin,
                    state,
                    group,
                    remotePeers,
                    originWorldName,
                    originTransform,
                    index + 1
                )
            );
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    @Nonnull
    static UiResumeAction chainedRuleApplyResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        int nextIndex,
        @Nonnull String successMessage,
        int localAppliedCount
    ) {
        return new UiResumeAction() {
            @Override
            public boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
                if (nextIndex >= remotePeers.size()) {
                    return false;
                }
                ConfiguredPeer nextDestination = remotePeers.get(nextIndex);
                plugin.getServerPolicySyncService().apply(
                    event,
                    nextDestination,
                    originWorldName,
                    originTransform.clone(),
                    group.recoveryEnabled(),
                    group.maxBackupsPerPlayer(),
                    chainedRuleApplyResumeAction(
                        plugin,
                        state,
                        group,
                        remotePeers,
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
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(successMessage)
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
                String finalStatus = success ? successMessage : partialRuleApplyFailurePrefix(localAppliedCount) + status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(finalStatus)
                );
            }
        };
    }

    @Nonnull
    static UiResumeAction chainedRuleRefreshResumeAction(
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull List<ConfiguredPeer> remotePeers,
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
                plugin.getServerPolicySyncService().refresh(
                    event,
                    nextDestination,
                    originWorldName,
                    originTransform.clone(),
                    chainedRuleRefreshResumeAction(
                        plugin,
                        state,
                        group,
                        remotePeers,
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
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText("Refreshed status for " + remotePeers.size() + " remote server(s).")
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
                String finalStatus = success ? "Refreshed status for " + remotePeers.size() + " remote server(s)." : status;
                open(
                    ref,
                    store,
                    playerRef,
                    player,
                    plugin,
                    state.withSelectedView(NexoriMenuV2View.RULES).withSelectedRulesTab(RulesWorkspaceTab.GROUPS).withSelectedRuleGroupId(group.groupId()).withStatusText(finalStatus)
                );
            }
        };
    }

    @Nonnull
    static String partialRuleApplyFailurePrefix(int localAppliedCount) {
        if (localAppliedCount <= 0) {
            return "";
        }
        return "Applied local rules, but ";
    }


}
