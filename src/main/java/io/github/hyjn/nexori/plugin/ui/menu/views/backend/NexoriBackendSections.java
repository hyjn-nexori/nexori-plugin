package io.github.hyjn.nexori.plugin.ui.menu.views.backend;

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
public final class NexoriBackendSections extends NexoriMenuSections {

    private NexoriBackendSections() {
    }

    public static GroupBuilder backendTabs(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state
    ) {
        int tabWidth = 180;
        int gap = 8;
        BackendWorkspaceTab[] tabs = BackendWorkspaceTab.values();
        int totalWidth = tabs.length * tabWidth + Math.max(0, tabs.length - 1) * gap;
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(CONTENT_W - 32).setHeight(42));
        row.addChild(spacerX(Math.max(0, ((CONTENT_W - 32) - totalWidth) / 2)));
        BackendWorkspaceTab current = currentBackendTab(playerRef);
        for (int index = 0; index < tabs.length; index++) {
            BackendWorkspaceTab tab = tabs[index];
            ButtonBuilder button = ButtonBuilder.secondaryTextButton()
                .withText(tab.label())
                .withAnchor(new HyUIAnchor().setWidth(tabWidth).setHeight(42))
                .withDisabled(current == tab)
                .onClick((ignored, ctx) -> {
                    preserveBackendDraftFromInputs(playerRef, plugin, ctx);
                    BACKEND_WORKSPACE_TABS.put(playerRef.getUuid(), tab);
                    open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText(""));
                });
            row.addChild(button);
            if (index + 1 < tabs.length) {
                row.addChild(spacerX(gap));
            }
        }
        return row;
    }


    public static ReorderableListBuilder buildBackendScroll(
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
        BackendWorkspaceTab tab = currentBackendTab(playerRef);
        int setupHeight = tab == BackendWorkspaceTab.TERMINAL ? 625 : 1560;
        int contentHeight = 16 + setupHeight + 20;
        ReorderableListBuilder scroll = scrollList(width, viewportHeight, Math.max(viewportHeight, contentHeight), scrollId, true);
        scroll.addChild(spacerY(16));
        try {
            BackendMatchmakingConfig config = plugin.getBackendMatchmakingConfigStore().loadOrCreate();
            switch (tab) {
                case CONFIG -> scroll.addChild(backendConfigCard(ref, store, playerRef, player, plugin, state, config, innerWidth, setupHeight));
                case TERMINAL -> scroll.addChild(backendTerminalCard(ref, store, playerRef, player, plugin, state, config, innerWidth, setupHeight, scrollId + "-terminal"));
            }
        } catch (IOException exception) {
            GroupBuilder card = card(innerWidth, 150, PANEL_BG);
            card.addChild(label("Backend Matchmaking", TITLE, innerWidth - 32));
            card.addChild(spacerY(8));
            card.addChild(label("Could not load backend-matchmaking.json: " + exception.getMessage(), BAD, innerWidth - 32));
            scroll.addChild(card);
        }
        return scroll;
    }

    @Nonnull
    static GroupBuilder backendConfigCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull BackendMatchmakingConfig config,
        int width,
        int height
    ) {
        BackendConfigDraft draft = backendDraft(playerRef);
        boolean enabled = draft != null && draft.enabled() != null ? draft.enabled() : config.syncEnabled();
        boolean resultReportingEnabled = draft != null && draft.resultReportingEnabled() != null ? draft.resultReportingEnabled() : config.resultReportingEnabled();
        boolean matchStateReportingEnabled = draft != null && draft.matchStateReportingEnabled() != null ? draft.matchStateReportingEnabled() : config.matchStateReportingEnabled();
        String baseUrl = draft != null && draft.baseUrl() != null ? draft.baseUrl() : config.baseUrl();
        String tokenValue = draft != null && draft.serverToken() != null ? draft.serverToken() : "";
        String syncInterval = draft != null && draft.syncIntervalMs() != null ? draft.syncIntervalMs() : Long.toString(config.syncIntervalMs());
        String region = draft != null && draft.region() != null ? draft.region() : config.region();
        String timeout = draft != null && draft.requestTimeoutMs() != null ? draft.requestTimeoutMs() : Long.toString(config.requestTimeoutMs());
        String resultRetryInterval = draft != null && draft.resultRetryIntervalMs() != null ? draft.resultRetryIntervalMs() : Long.toString(config.resultRetryIntervalMs());
        String matchStateDebounceMs = draft != null && draft.matchStateDebounceMs() != null ? draft.matchStateDebounceMs() : Long.toString(config.matchStateDebounceMs());
        String matchStateMaxCoalesceWindowMs = draft != null && draft.matchStateMaxCoalesceWindowMs() != null ? draft.matchStateMaxCoalesceWindowMs() : Long.toString(config.matchStateMaxCoalesceWindowMs());
        String matchStateRetryIntervalMs = draft != null && draft.matchStateRetryIntervalMs() != null ? draft.matchStateRetryIntervalMs() : Long.toString(config.matchStateRetryIntervalMs());
        String matchStateStaleAfterMs = draft != null && draft.matchStateStaleAfterMs() != null ? draft.matchStateStaleAfterMs() : Long.toString(config.matchStateStaleAfterMs());
        String tokenPlaceholder = config.maskedToken().isBlank() ? "Paste API key / server token" : "Paste new API key / server token";

        int innerWidth = width - 32;
        int sectionInnerWidth = innerWidth - 32;
        int sectionThirdWidth = (sectionInnerWidth - 24) / 3;

        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Backend Setup", TITLE, width - 32));
        card.addChild(spacerY(8));
        card.addChild(label("Configure the shared backend origin and Bearer token used by POST /nexori/sync for BACKEND_DRIVEN queues and POST /nexori/results for completed match reports.", MUTED, width - 32));
        card.addChild(spacerY(4));
        card.addChild(label("Save writes config/backend-matchmaking.json and applies it to the live backend services. LOCAL_FIFO queues continue to work standalone.", MUTED, width - 32));
        card.addChild(spacerY(12));

        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(innerWidth).setHeight(HOME_INPUT_FIELD_H));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText("SAVE CHANGES")
                .withAnchor(new HyUIAnchor().setWidth(160).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> saveBackendConfig(ref, store, playerRef, player, plugin, state, config, enabled, resultReportingEnabled, matchStateReportingEnabled, baseUrl, syncInterval, region, timeout, resultRetryInterval, matchStateDebounceMs, matchStateMaxCoalesceWindowMs, matchStateRetryIntervalMs, matchStateStaleAfterMs, ctx))
        );
        actionRow.addChild(spacerX(10));
        actionRow.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText("DISCARD CHANGES")
                .withAnchor(new HyUIAnchor().setWidth(180).setHeight(HOME_INPUT_FIELD_H))
                .onClick((ignored, ctx) -> {
                    BACKEND_CONFIG_DRAFTS.remove(playerRef.getUuid());
                    open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Discarded backend config draft."));
                })
        );
        card.addChild(actionRow);
        card.addChild(spacerY(12));

        int backendFieldHeight = 146;
        int sectionHeaderHeight = 58;
        int sectionGap = 12;
        int sectionOffset = 20;
        int shortSectionHeight = 32 + sectionHeaderHeight + 8 + backendFieldHeight;
        int tallSectionHeight = shortSectionHeight + sectionGap + backendFieldHeight;

        GroupBuilder generalSection = backendSectionContainer("General", "Shared backend connection settings used by sync, result reporting, and admission state reporting.", innerWidth, tallSectionHeight);
        GroupBuilder rowOne = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(sectionInnerWidth).setHeight(backendFieldHeight));
        rowOne.addChild(backendInputCard("Base URL", "Backend HTTP origin shared by sync and result reporting endpoints.", BACKEND_BASE_URL_INPUT_ID, baseUrl, "http://127.0.0.1:8000", "Current: " + (config.baseUrl().isBlank() ? "<blank>" : config.baseUrl()), sectionThirdWidth, backendFieldHeight, 255));
        rowOne.addChild(spacerX(12));
        rowOne.addChild(backendInputCard("API Key / Server Token", "Sent as Authorization: Bearer <token>. Existing token is masked and never printed. Leave blank to keep the current token.", BACKEND_SERVER_TOKEN_INPUT_ID, tokenValue, tokenPlaceholder, "Current: " + (config.maskedToken().isBlank() ? "NOT SET" : config.maskedToken()), sectionThirdWidth, backendFieldHeight, 255));
        rowOne.addChild(spacerX(12));
        rowOne.addChild(backendInputCard("Region", "Manual routing hint for your backend. Leave blank if this server has no region label yet.", BACKEND_REGION_INPUT_ID, region, "us-east", "Current: " + (config.region().isBlank() ? "<blank>" : config.region()), sectionThirdWidth, backendFieldHeight, 64));
        generalSection.addChild(rowOne);
        generalSection.addChild(spacerY(sectionGap));

        GroupBuilder rowTwo = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(sectionInnerWidth).setHeight(backendFieldHeight));
        rowTwo.addChild(backendInputCard("Request Timeout Ms", "HTTP timeout before Nexori marks sync or result reporting failed and backs off.", BACKEND_TIMEOUT_INPUT_ID, timeout, "3000", "Current: " + config.requestTimeoutMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        generalSection.addChild(rowTwo);
        card.addChild(generalSection);
        card.addChild(spacerY(sectionOffset));

        GroupBuilder catalogSection = backendSectionContainer("Catalog Sync", "Controls POST /nexori/sync catalog updates for backend-driven queues.", innerWidth, shortSectionHeight);
        GroupBuilder rowThree = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(sectionInnerWidth).setHeight(backendFieldHeight));
        rowThree.addChild(backendSyncToggleField(ref, store, playerRef, player, plugin, state, enabled, resultReportingEnabled, matchStateReportingEnabled, config, sectionThirdWidth, backendFieldHeight));
        rowThree.addChild(spacerX(12));
        rowThree.addChild(backendInputCard("Sync Interval Ms", "Global throttle. One server process sends at most one sync per interval.", BACKEND_SYNC_INTERVAL_INPUT_ID, syncInterval, "1000", "Current: " + config.syncIntervalMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        catalogSection.addChild(rowThree);
        card.addChild(catalogSection);
        card.addChild(spacerY(sectionOffset));

        GroupBuilder resultSection = backendSectionContainer("Result Reporting", "Controls POST /nexori/results for completed match outcomes.", innerWidth, shortSectionHeight);
        GroupBuilder rowFour = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(sectionInnerWidth).setHeight(backendFieldHeight));
        rowFour.addChild(backendResultToggleField(ref, store, playerRef, player, plugin, state, enabled, resultReportingEnabled, matchStateReportingEnabled, config, sectionThirdWidth, backendFieldHeight));
        rowFour.addChild(spacerX(12));
        rowFour.addChild(backendInputCard("Result Retry Interval Ms", "Delay before retrying failed POST /nexori/results attempts.", BACKEND_RESULT_RETRY_INTERVAL_INPUT_ID, resultRetryInterval, "5000", "Current: " + config.resultRetryIntervalMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        resultSection.addChild(rowFour);
        card.addChild(resultSection);
        card.addChild(spacerY(sectionOffset));

        GroupBuilder admissionSection = backendSectionContainer("Admission State Reporting", "Reports open match admission/backfill visibility to POST /nexori/matches/state. Used by backend-driven backfill.", innerWidth, tallSectionHeight);
        GroupBuilder rowFive = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(sectionInnerWidth).setHeight(backendFieldHeight));
        rowFive.addChild(backendMatchStateReportingToggleField(ref, store, playerRef, player, plugin, state, enabled, resultReportingEnabled, matchStateReportingEnabled, config, sectionThirdWidth, backendFieldHeight));
        rowFive.addChild(spacerX(12));
        rowFive.addChild(backendInputCard("Debounce Ms", "Delay before sending a dirty admission state snapshot.", BACKEND_MATCH_STATE_DEBOUNCE_INPUT_ID, matchStateDebounceMs, "250", "Current: " + config.matchStateDebounceMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        rowFive.addChild(spacerX(12));
        rowFive.addChild(backendInputCard("Max Coalesce Window Ms", "Maximum time to coalesce admission state changes before forcing a send.", BACKEND_MATCH_STATE_MAX_COALESCE_INPUT_ID, matchStateMaxCoalesceWindowMs, "1000", "Current: " + config.matchStateMaxCoalesceWindowMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        admissionSection.addChild(rowFive);
        admissionSection.addChild(spacerY(sectionGap));

        GroupBuilder rowSix = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(sectionInnerWidth).setHeight(backendFieldHeight));
        rowSix.addChild(backendInputCard("Retry Interval Ms", "Delay before retrying failed POST /nexori/matches/state attempts.", BACKEND_MATCH_STATE_RETRY_INTERVAL_INPUT_ID, matchStateRetryIntervalMs, "5000", "Current: " + config.matchStateRetryIntervalMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        rowSix.addChild(spacerX(12));
        rowSix.addChild(backendInputCard("Stale After Ms", "How long a match admission snapshot remains fresh for backend selection.", BACKEND_MATCH_STATE_STALE_AFTER_INPUT_ID, matchStateStaleAfterMs, "10000", "Current: " + config.matchStateStaleAfterMs() + " ms", sectionThirdWidth, backendFieldHeight, 16));
        admissionSection.addChild(rowSix);
        card.addChild(admissionSection);
        return card;
    }

    @Nonnull
    static GroupBuilder backendSyncToggleField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        boolean enabled,
        boolean resultReportingEnabled,
        boolean matchStateReportingEnabled,
        @Nonnull BackendMatchmakingConfig config,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.withBackground(PANEL_BG);
        card.addChild(label("Backend Sync", SUBTITLE, width - 28));
        card.addChild(spacerY(4));
        card.addChild(label("Turn on only when your backend is ready with the matching Bearer token.", MUTED, width - 28));
        card.addChild(spacerY(8));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(enabled ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42))
                .onClick((ignored, ctx) -> {
                    BACKEND_CONFIG_DRAFTS.put(playerRef.getUuid(), backendDraftFromInputs(
                        config,
                        !enabled,
                        resultReportingEnabled,
                        matchStateReportingEnabled,
                        ctx
                    ));
                    open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Backend sync draft is now " + (!enabled ? "enabled" : "disabled") + ". Save changes to apply."));
                })
        );
        card.addChild(row);
        card.addChild(spacerY(6));
        card.addChild(label("Current: " + (config.syncEnabled() ? "ENABLED" : "DISABLED"), INFO, width - 28));
        return card;
    }

    @Nonnull
    static GroupBuilder backendResultToggleField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        boolean enabled,
        boolean resultReportingEnabled,
        boolean matchStateReportingEnabled,
        @Nonnull BackendMatchmakingConfig config,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.withBackground(PANEL_BG);
        card.addChild(label("Result Reporting", SUBTITLE, width - 28));
        card.addChild(spacerY(4));
        card.addChild(label("Allows rules mods to send completed match results to POST /nexori/results.", MUTED, width - 28));
        card.addChild(spacerY(8));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(resultReportingEnabled ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42))
                .onClick((ignored, ctx) -> {
                    BACKEND_CONFIG_DRAFTS.put(playerRef.getUuid(), backendDraftFromInputs(
                        config,
                        enabled,
                        !resultReportingEnabled,
                        matchStateReportingEnabled,
                        ctx
                    ));
                    open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Result reporting draft is now " + (!resultReportingEnabled ? "enabled" : "disabled") + ". Save changes to apply."));
                })
        );
        card.addChild(row);
        card.addChild(spacerY(6));
        card.addChild(label("Current: " + (config.resultReportingEnabled() ? "ENABLED" : "DISABLED"), INFO, width - 28));
        return card;
    }

    @Nonnull
    static GroupBuilder backendMatchStateReportingToggleField(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        boolean enabled,
        boolean resultReportingEnabled,
        boolean matchStateReportingEnabled,
        @Nonnull BackendMatchmakingConfig config,
        int width,
        int height
    ) {
        GroupBuilder card = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);
        card.withBackground(PANEL_BG);
        card.addChild(label("Admission State Reporting", SUBTITLE, width - 28));
        card.addChild(spacerY(4));
        card.addChild(label("Controls POST /nexori/matches/state for backend-driven backfill visibility.", MUTED, width - 28));
        card.addChild(spacerY(8));

        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42));
        row.addChild(
            ButtonBuilder.secondaryTextButton()
                .withText(matchStateReportingEnabled ? "TURN OFF" : "TURN ON")
                .withAnchor(new HyUIAnchor().setWidth(width - 28).setHeight(42))
                .onClick((ignored, ctx) -> {
                    BACKEND_CONFIG_DRAFTS.put(playerRef.getUuid(), backendDraftFromInputs(
                        config,
                        enabled,
                        resultReportingEnabled,
                        !matchStateReportingEnabled,
                        ctx
                    ));
                    open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Admission state reporting draft is now " + (!matchStateReportingEnabled ? "enabled" : "disabled") + ". Save changes to apply."));
                })
        );
        card.addChild(row);
        card.addChild(spacerY(6));
        card.addChild(label("Current: " + (config.matchStateReportingEnabled() ? "ENABLED" : "DISABLED"), INFO, width - 28));
        return card;
    }

    @Nonnull
    protected static GroupBuilder backendSectionHeader(
        @Nonnull String title,
        @Nonnull String description,
        int width
    ) {
        GroupBuilder header = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(50));
        header.addChild(centeredLabel(title, TITLE, width, width, 12));
        header.addChild(spacerY(2));
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
    static BackendConfigDraft backendDraftFromInputs(
        @Nonnull BackendMatchmakingConfig config,
        boolean enabled,
        boolean resultReportingEnabled,
        boolean matchStateReportingEnabled,
        @Nonnull au.ellie.hyui.events.UIContext ctx
    ) {
        return new BackendConfigDraft(
            enabled,
            resultReportingEnabled,
            matchStateReportingEnabled,
            ctx.getValue(BACKEND_BASE_URL_INPUT_ID, String.class).orElse(config.baseUrl()).trim(),
            ctx.getValue(BACKEND_SERVER_TOKEN_INPUT_ID, String.class).orElse("").trim(),
            ctx.getValue(BACKEND_SYNC_INTERVAL_INPUT_ID, String.class).orElse(Long.toString(config.syncIntervalMs())).trim(),
            ctx.getValue(BACKEND_REGION_INPUT_ID, String.class).orElse(config.region()).trim(),
            ctx.getValue(BACKEND_TIMEOUT_INPUT_ID, String.class).orElse(Long.toString(config.requestTimeoutMs())).trim(),
            ctx.getValue(BACKEND_RESULT_RETRY_INTERVAL_INPUT_ID, String.class).orElse(Long.toString(config.resultRetryIntervalMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_DEBOUNCE_INPUT_ID, String.class).orElse(Long.toString(config.matchStateDebounceMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_MAX_COALESCE_INPUT_ID, String.class).orElse(Long.toString(config.matchStateMaxCoalesceWindowMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_RETRY_INTERVAL_INPUT_ID, String.class).orElse(Long.toString(config.matchStateRetryIntervalMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_STALE_AFTER_INPUT_ID, String.class).orElse(Long.toString(config.matchStateStaleAfterMs())).trim()
        );
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
    static GroupBuilder backendTerminalCard(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull BackendMatchmakingConfig config,
        int width,
        int height,
        @Nonnull String scrollId
    ) {
        GroupBuilder terminal = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(14))
            .withBackground(PANEL_ALT_BG);

        String syncHealth = plugin.getBackendSyncService().healthState().status();
        String syncMessage = plugin.getBackendSyncService().healthState().lastMessage();
        String resultHealth = plugin.getBackendResultReportingService().healthState().status();
        String resultMessage = plugin.getBackendResultReportingService().healthState().lastMessage();
        String syncLine = config.syncEnabled()
            ? "Sync: " + syncHealth + (syncMessage.isBlank() ? "" : " - " + syncMessage)
            : "Sync: DISABLED";
        String resultLine = config.resultReportingEnabled()
            ? "Results: " + resultHealth + (resultMessage.isBlank() ? "" : " - " + resultMessage)
            : "Results: DISABLED";

        terminal.addChild(label("Backend Terminal", SUBTITLE, width - 28));
        terminal.addChild(spacerY(6));

        int headerWidth = width - 28;
        int refreshButtonWidth = 180;
        int refreshButtonGapPx = 10;
        int refreshButtonRightInsetPx = 40;
        int healthLabelWidth = Math.max(220, headerWidth - refreshButtonWidth - refreshButtonGapPx - refreshButtonRightInsetPx);
        GroupBuilder header = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(headerWidth).setHeight(34));
        header.addChild(label(syncLine + " | " + resultLine + " | Newest first.", MUTED, healthLabelWidth));
        header.addChild(spacerX(refreshButtonGapPx));
        header.addChild(
            ButtonBuilder.smallSecondaryTextButton()
                .withText("REFRESH TERMINAL")
                .withAnchor(new HyUIAnchor().setWidth(refreshButtonWidth).setHeight(32))
                .onClick((ignored, ctx) -> {
                    open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Refreshed backend terminal."));
                })
        );
        terminal.addChild(header);
        terminal.addChild(spacerY(8));

        int bodyWidth = width - 28;
        int bodyHeight = Math.max(96, height - 86);
        List<BackendTerminalEntry> entries = backendTerminalEntries(plugin);
        int rowHeight = 50;
        int bodyContentHeight = entries.isEmpty() ? bodyHeight : 8 + entries.size() * (rowHeight + 6) + 8;
        ReorderableListBuilder body = scrollList(bodyWidth, bodyHeight, Math.max(bodyHeight, bodyContentHeight), scrollId, true);
        body.addChild(spacerY(8));
        if (entries.isEmpty()) {
            body.addChild(label("No backend requests recorded yet. Completed POST /nexori/sync and POST /nexori/results attempts will appear here.", MUTED, bodyWidth - 16));
        } else {
            int index = 0;
            for (BackendTerminalEntry entry : entries) {
                body.addChild(backendTerminalRow(entry, index, bodyWidth - 16, rowHeight));
                body.addChild(spacerY(6));
                index++;
            }
        }
        terminal.addChild(body);
        return terminal;
    }

    @Nonnull
    static List<BackendTerminalEntry> backendTerminalEntries(@Nonnull NexoriPlugin plugin) {
        List<BackendTerminalEntry> entries = new ArrayList<>();
        for (BackendSyncService.BackendSyncLogEntry entry : plugin.getBackendSyncService().recentSyncLogEntries()) {
            String syncId = entry.syncId().isBlank() ? "-" : shortRequestId(entry.syncId());
            String detail = "SYNC  " + entry.method() + " " + entry.path()
                + "  seq=" + entry.sequence()
                + "  syncId=" + syncId
                + "  assignments=" + entry.assignmentCount()
                + "  acked=" + entry.acknowledgedAckCount();
            if (!entry.outcome().isBlank() && !"OK".equalsIgnoreCase(entry.outcome())) {
                detail = detail + "  " + entry.outcome();
            }
            entries.add(new BackendTerminalEntry(
                entry.completedAtEpochMs(),
                entry.statusCode(),
                detail
            ));
        }
        for (BackendResultReportingService.BackendResultLogEntry entry : plugin.getBackendResultReportingService().recentResultLogEntries()) {
            String resultId = entry.resultId().isBlank() ? "-" : shortRequestId(entry.resultId());
            String detail = "RESULT  " + entry.method() + " " + entry.path()
                + "  resultId=" + resultId
                + "  players=" + entry.playerCount();
            if (!entry.backendStatus().isBlank()) {
                detail = detail + "  backend=" + entry.backendStatus();
            }
            if (!entry.outcome().isBlank()
                && !"OK".equalsIgnoreCase(entry.outcome())
                && !"ACCEPTED".equalsIgnoreCase(entry.outcome())
                && !"DUPLICATE".equalsIgnoreCase(entry.outcome())) {
                detail = detail + "  " + entry.outcome();
            }
            entries.add(new BackendTerminalEntry(
                entry.completedAtEpochMs(),
                entry.statusCode(),
                detail
            ));
        }
        entries.sort(Comparator.comparingLong(BackendTerminalEntry::completedAtEpochMs).reversed());
        return entries.size() <= 80 ? List.copyOf(entries) : List.copyOf(entries.subList(0, 80));
    }

    @Nonnull
    static GroupBuilder backendTerminalRow(
        @Nonnull BackendTerminalEntry entry,
        int index,
        int width,
        int height
    ) {
        int offset = 10;
        GroupBuilder wrapper = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height));
        if (offset > 0) {
            wrapper.addChild(spacerX(offset));
        }

        int rowWidth = width - offset;
        GroupBuilder row = GroupBuilder.group()
            .withLayoutMode("Left")
            .withAnchor(new HyUIAnchor().setWidth(rowWidth).setHeight(height))
            .withPadding(HyUIPadding.all(8))
            .withBackground(index % 2 == 0 ? SERVER_CARD_BG : PANEL_BG);

        row.addChild(backendSyncStatusPill(entry.statusCode(), 90, 32));
        row.addChild(spacerX(10));

        row.addChild(centeredTerminalText(entry.detail(), INFO, Math.max(320, rowWidth - 360), 32));
        row.addChild(spacerX(10));
        row.addChild(centeredTerminalText(SYNC_TIME_FORMAT.format(Instant.ofEpochMilli(entry.completedAtEpochMs())), MUTED, 220, 32));

        wrapper.addChild(row);
        return wrapper;
    }

    @Nonnull
    static GroupBuilder backendSyncStatusPill(int statusCode, int width, int height) {
        String status = statusCode <= 0 ? "NO HTTP" : Integer.toString(statusCode);
        GroupBuilder pill = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withBackground(backendSyncStatusBackground(statusCode));
        pill.addChild(spacerY(8));
        pill.addChild(label(status, backendSyncStatusStyle(statusCode), width));
        return pill;
    }

    @Nonnull
    static GroupBuilder centeredTerminalText(@Nonnull String text, @Nonnull HyUIStyle style, int width, int height) {
        GroupBuilder box = GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height));
        box.addChild(spacerY(8));
        box.addChild(label(text, style, width));
        return box;
    }

    @Nonnull
    static HyUIPatchStyle backendSyncStatusBackground(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return GOOD_BG;
        }
        if (statusCode <= 0 || statusCode >= 500) {
            return BAD_BG;
        }
        if (statusCode >= 300 && statusCode < 500) {
            return WARN_BG;
        }
        return INFO_BG;
    }

    @Nonnull
    static HyUIStyle backendSyncStatusStyle(int statusCode) {
        if (statusCode >= 200 && statusCode < 300) {
            return GOOD_CENTER;
        }
        if (statusCode <= 0 || statusCode >= 500) {
            return BAD_CENTER;
        }
        if (statusCode >= 300 && statusCode < 500) {
            return WARN_CENTER;
        }
        return INFO_CENTER;
    }

    @Nonnull
    static String shortRequestId(@Nonnull String requestId) {
        return requestId.length() <= 8 ? requestId : requestId.substring(0, 8);
    }

    private record BackendTerminalEntry(
        long completedAtEpochMs,
        int statusCode,
        String detail
    ) {
    }

    static void saveBackendConfig(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State state,
        @Nonnull BackendMatchmakingConfig current,
        boolean enabled,
        boolean resultReportingEnabled,
        boolean matchStateReportingEnabled,
        @Nonnull String baseUrl,
        @Nonnull String syncInterval,
        @Nonnull String region,
        @Nonnull String timeout,
        @Nonnull String resultRetryInterval,
        @Nonnull String matchStateDebounceMs,
        @Nonnull String matchStateMaxCoalesceWindowMs,
        @Nonnull String matchStateRetryIntervalMs,
        @Nonnull String matchStateStaleAfterMs,
        @Nonnull au.ellie.hyui.events.UIContext ctx
    ) {
        String rawBaseUrl = ctx.getValue(BACKEND_BASE_URL_INPUT_ID, String.class).orElse(baseUrl).trim();
        String rawToken = ctx.getValue(BACKEND_SERVER_TOKEN_INPUT_ID, String.class).orElse("").trim();
        String rawSyncInterval = ctx.getValue(BACKEND_SYNC_INTERVAL_INPUT_ID, String.class).orElse(syncInterval).trim();
        String rawRegion = ctx.getValue(BACKEND_REGION_INPUT_ID, String.class).orElse(region).trim();
        String rawTimeout = ctx.getValue(BACKEND_TIMEOUT_INPUT_ID, String.class).orElse(timeout).trim();
        String rawResultRetryInterval = ctx.getValue(BACKEND_RESULT_RETRY_INTERVAL_INPUT_ID, String.class).orElse(resultRetryInterval).trim();
        String rawMatchStateDebounceMs = ctx.getValue(BACKEND_MATCH_STATE_DEBOUNCE_INPUT_ID, String.class).orElse(matchStateDebounceMs).trim();
        String rawMatchStateMaxCoalesceWindowMs = ctx.getValue(BACKEND_MATCH_STATE_MAX_COALESCE_INPUT_ID, String.class).orElse(matchStateMaxCoalesceWindowMs).trim();
        String rawMatchStateRetryIntervalMs = ctx.getValue(BACKEND_MATCH_STATE_RETRY_INTERVAL_INPUT_ID, String.class).orElse(matchStateRetryIntervalMs).trim();
        String rawMatchStateStaleAfterMs = ctx.getValue(BACKEND_MATCH_STATE_STALE_AFTER_INPUT_ID, String.class).orElse(matchStateStaleAfterMs).trim();
        try {
            long syncIntervalMs = Long.parseLong(rawSyncInterval);
            long requestTimeoutMs = Long.parseLong(rawTimeout);
            long resultRetryIntervalMs = Long.parseLong(rawResultRetryInterval);
            long parsedMatchStateDebounceMs = Long.parseLong(rawMatchStateDebounceMs);
            long parsedMatchStateMaxCoalesceWindowMs = Long.parseLong(rawMatchStateMaxCoalesceWindowMs);
            long parsedMatchStateRetryIntervalMs = Long.parseLong(rawMatchStateRetryIntervalMs);
            long parsedMatchStateStaleAfterMs = Long.parseLong(rawMatchStateStaleAfterMs);
            String token = rawToken.isBlank() ? current.serverToken() : rawToken;
            BackendMatchmakingConfig updated = new BackendMatchmakingConfig(
                BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
                enabled,
                rawBaseUrl,
                token,
                syncIntervalMs,
                rawRegion,
                requestTimeoutMs,
                resultReportingEnabled,
                resultRetryIntervalMs,
                matchStateReportingEnabled,
                parsedMatchStateDebounceMs,
                parsedMatchStateMaxCoalesceWindowMs,
                parsedMatchStateRetryIntervalMs,
                parsedMatchStateStaleAfterMs
            ).normalized();
            plugin.getBackendMatchmakingConfigStore().save(updated);
            plugin.getBackendSyncService().updateConfig(updated);
            plugin.getBackendMatchAdmissionStateReportingService().updateConfig(updated);
            plugin.getBackendResultReportingService().updateConfig(updated);
            BACKEND_CONFIG_DRAFTS.remove(playerRef.getUuid());
            open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Saved backend matchmaking config and applied it live."));
        } catch (NumberFormatException exception) {
            BACKEND_CONFIG_DRAFTS.put(playerRef.getUuid(), new BackendConfigDraft(enabled, resultReportingEnabled, matchStateReportingEnabled, rawBaseUrl, rawToken, rawSyncInterval, rawRegion, rawTimeout, rawResultRetryInterval, rawMatchStateDebounceMs, rawMatchStateMaxCoalesceWindowMs, rawMatchStateRetryIntervalMs, rawMatchStateStaleAfterMs));
            open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Backend numeric fields must be whole numbers."));
        } catch (IOException | IllegalArgumentException exception) {
            BACKEND_CONFIG_DRAFTS.put(playerRef.getUuid(), new BackendConfigDraft(enabled, resultReportingEnabled, matchStateReportingEnabled, rawBaseUrl, rawToken, rawSyncInterval, rawRegion, rawTimeout, rawResultRetryInterval, rawMatchStateDebounceMs, rawMatchStateMaxCoalesceWindowMs, rawMatchStateRetryIntervalMs, rawMatchStateStaleAfterMs));
            open(ref, store, playerRef, player, plugin, state.withSelectedView(NexoriMenuV2View.BACKEND).withStatusText("Could not save backend config: " + exception.getMessage()));
        }
    }

    static void preserveBackendDraftFromInputs(
        @Nonnull PlayerRef playerRef,
        @Nonnull NexoriPlugin plugin,
        @Nonnull au.ellie.hyui.events.UIContext ctx
    ) { 
        BackendMatchmakingConfig config = plugin.getBackendSyncService().config();
        BackendConfigDraft current = backendDraft(playerRef);
        if (current == null
            && ctx.getValue(BACKEND_BASE_URL_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_SERVER_TOKEN_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_SYNC_INTERVAL_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_REGION_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_TIMEOUT_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_RESULT_RETRY_INTERVAL_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_MATCH_STATE_DEBOUNCE_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_MATCH_STATE_MAX_COALESCE_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_MATCH_STATE_RETRY_INTERVAL_INPUT_ID, String.class).isEmpty()
            && ctx.getValue(BACKEND_MATCH_STATE_STALE_AFTER_INPUT_ID, String.class).isEmpty()) {
            return;
        }
        BACKEND_CONFIG_DRAFTS.put(playerRef.getUuid(), new BackendConfigDraft(
            current != null && current.enabled() != null ? current.enabled() : config.syncEnabled(),
            current != null && current.resultReportingEnabled() != null ? current.resultReportingEnabled() : config.resultReportingEnabled(),
            current != null && current.matchStateReportingEnabled() != null ? current.matchStateReportingEnabled() : config.matchStateReportingEnabled(),
            ctx.getValue(BACKEND_BASE_URL_INPUT_ID, String.class).orElse(current != null && current.baseUrl() != null ? current.baseUrl() : config.baseUrl()).trim(),
            ctx.getValue(BACKEND_SERVER_TOKEN_INPUT_ID, String.class).orElse(current != null && current.serverToken() != null ? current.serverToken() : "").trim(),
            ctx.getValue(BACKEND_SYNC_INTERVAL_INPUT_ID, String.class).orElse(current != null && current.syncIntervalMs() != null ? current.syncIntervalMs() : Long.toString(config.syncIntervalMs())).trim(),
            ctx.getValue(BACKEND_REGION_INPUT_ID, String.class).orElse(current != null && current.region() != null ? current.region() : config.region()).trim(),
            ctx.getValue(BACKEND_TIMEOUT_INPUT_ID, String.class).orElse(current != null && current.requestTimeoutMs() != null ? current.requestTimeoutMs() : Long.toString(config.requestTimeoutMs())).trim(),
            ctx.getValue(BACKEND_RESULT_RETRY_INTERVAL_INPUT_ID, String.class).orElse(current != null && current.resultRetryIntervalMs() != null ? current.resultRetryIntervalMs() : Long.toString(config.resultRetryIntervalMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_DEBOUNCE_INPUT_ID, String.class).orElse(current != null && current.matchStateDebounceMs() != null ? current.matchStateDebounceMs() : Long.toString(config.matchStateDebounceMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_MAX_COALESCE_INPUT_ID, String.class).orElse(current != null && current.matchStateMaxCoalesceWindowMs() != null ? current.matchStateMaxCoalesceWindowMs() : Long.toString(config.matchStateMaxCoalesceWindowMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_RETRY_INTERVAL_INPUT_ID, String.class).orElse(current != null && current.matchStateRetryIntervalMs() != null ? current.matchStateRetryIntervalMs() : Long.toString(config.matchStateRetryIntervalMs())).trim(),
            ctx.getValue(BACKEND_MATCH_STATE_STALE_AFTER_INPUT_ID, String.class).orElse(current != null && current.matchStateStaleAfterMs() != null ? current.matchStateStaleAfterMs() : Long.toString(config.matchStateStaleAfterMs())).trim()
        ));
    }

    @Nonnull
    static GroupBuilder backendHealthCard(@Nonnull NexoriPlugin plugin, @Nonnull BackendMatchmakingConfig config, int width, int height) {
        GroupBuilder card = card(width, height, PANEL_BG);
        card.addChild(label("Runtime Health", TITLE, width - 32));
        card.addChild(spacerY(8));
        String status = plugin.getBackendSyncService().healthState().status();
        String detail = plugin.getBackendSyncService().healthState().lastMessage();
        if (detail.isBlank()) {
            detail = config.syncEnabled() ? "No heartbeat error recorded." : "Backend sync is disabled. BACKEND_DRIVEN queues will wait until it is enabled and usable.";
        }
        card.addChild(label(detail, MUTED, width - 32));
        card.addChild(spacerY(12));
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(34));
        row.addChild(statusBadgeRow("Status", status, (width - 44) / 2, "HEALTHY".equalsIgnoreCase(status) ? GOOD : BAD, "HEALTHY".equalsIgnoreCase(status) ? GOOD_BG : BAD_BG));
        row.addChild(spacerX(12));
        row.addChild(statusBadgeRow("Token", config.maskedToken().isBlank() ? "NOT SET" : config.maskedToken(), (width - 44) / 2, config.maskedToken().isBlank() ? BAD : INFO, config.maskedToken().isBlank() ? BAD_BG : INFO_BG));
        card.addChild(row);
        return card;
    }

    static BackendConfigDraft backendDraft(@Nonnull PlayerRef playerRef) {
        return BACKEND_CONFIG_DRAFTS.get(playerRef.getUuid());
    }

    @Nonnull
    public static BackendWorkspaceTab currentBackendTab(@Nonnull PlayerRef playerRef) {
        return BACKEND_WORKSPACE_TABS.getOrDefault(playerRef.getUuid(), BackendWorkspaceTab.CONFIG);
    }


}

