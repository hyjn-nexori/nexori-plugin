package io.github.hyjn.nexori.plugin.hud;

import au.ellie.hyui.builders.Alignment;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.builders.ImageBuilder;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PanelBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class NexoriStatusHudService {

    // -------------------------------------------------------------------------
    // HUD kind — used to force remove+recreate when switching between queue
    // and return layouts, since they use different element ID trees.
    // -------------------------------------------------------------------------
    private enum HudRenderKind { QUEUE, RETURN }

    // -------------------------------------------------------------------------
    // Queue card colors
    // -------------------------------------------------------------------------
    private static final String QUEUE_CARD_BG          = "#051218BE";
    private static final String QUEUE_CARD_OUTLINE      = "#5FDEFFD2";
    private static final String QUEUE_LEFT_BG           = "#081E2669";
    private static final String QUEUE_LEFT_OUTLINE      = "#4BCDF546";
    private static final String QUEUE_TITLE_COLOR       = "#F5FCFF";
    private static final String QUEUE_COUNT_COLOR       = "#E1ECF2";
    private static final String QUEUE_DETAIL_FALLBACK   = "#5AEBFF";

    // Queue phase accent colors
    private static final String QUEUE_WAITING_COLOR    = "#8FDBFF";
    private static final String QUEUE_COUNTDOWN_COLOR  = "#FFD36E";
    private static final String QUEUE_READY_COLOR      = "#9AF4A8";

    // -------------------------------------------------------------------------
    // Return HUD colors (legacy, unchanged)
    // -------------------------------------------------------------------------
    private static final String MAIN_TEXT_COLOR         = "#FFFFFF";
    private static final String DETAIL_TEXT_COLOR       = "#C8D7EA";
    private static final String RETURN_VICTORY_COLOR    = "#F7D774";
    private static final String RETURN_ELIMINATED_COLOR = "#FF8B9A";
    private static final String RETURN_GENERIC_COLOR    = "#8FC7FF";
    private static final String RETURN_NO_CONTEST_COLOR = "#FFD36E";

    // -------------------------------------------------------------------------
    // State
    // -------------------------------------------------------------------------
    private final QueueCoordinatorService queueCoordinatorService;
    private final ArenaMatchService arenaMatchService;
    private final HytaleLogger logger;
    private final ConcurrentMap<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, HudRenderState> renderedStates = new ConcurrentHashMap<>();
    // Tracks which HUD kind is currently shown, so we can force remove+recreate on kind change.
    private final ConcurrentMap<UUID, HudRenderKind> activeHudKinds = new ConcurrentHashMap<>();

    public NexoriStatusHudService(
        @Nonnull QueueCoordinatorService queueCoordinatorService,
        @Nonnull ArenaMatchService arenaMatchService,
        @Nonnull HytaleLogger logger
    ) {
        this.queueCoordinatorService = queueCoordinatorService;
        this.arenaMatchService = arenaMatchService;
        this.logger = logger;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public void handlePlayerTick(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        long nowEpochMs
    ) {
        PlayerRef playerRef = store.getComponent(ref, Universe.get().getPlayerRefComponentType());
        if (playerRef == null) {
            return;
        }
        refresh(playerRef, nowEpochMs);
    }

    public void refresh(@Nonnull PlayerRef playerRef) {
        refresh(playerRef, System.currentTimeMillis());
    }

    public void remove(@Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }

        renderedStates.remove(playerUuid);
        activeHudKinds.remove(playerUuid);
        HyUIHud hud = activeHuds.remove(playerUuid);
        if (hud == null) {
            return;
        }

        try {
            hud.remove();
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to remove Nexori status HUD for " + playerUuid + ".");
        }
    }

    // -------------------------------------------------------------------------
    // Refresh logic
    // -------------------------------------------------------------------------

    private void refresh(@Nonnull PlayerRef playerRef, long nowEpochMs) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }

        Optional<HudRenderState> resolved = resolveState(playerUuid, nowEpochMs);
        if (resolved.isEmpty()) {
            remove(playerRef);
            return;
        }

        HudRenderState nextState = resolved.get();
        HudRenderState previousState = renderedStates.get(playerUuid);
        if (nextState.equals(previousState)) {
            return;
        }

        HudBuilder builder = buildHud(playerRef, nextState);
        HyUIHud existing = activeHuds.get(playerUuid);
        HudRenderKind existingKind = activeHudKinds.get(playerUuid);

        // QUEUE and RETURN have different element trees — force remove+recreate on kind change.
        boolean kindChanged = existing != null && existingKind != null && existingKind != nextState.kind();

        try {
            if (existing == null || kindChanged) {
                if (existing != null) {
                    existing.remove();
                }
                HyUIHud shownHud = builder.show(playerRef);
                activeHuds.put(playerUuid, shownHud);
                activeHudKinds.put(playerUuid, nextState.kind());
            } else {
                existing.update(builder);
            }
            renderedStates.put(playerUuid, nextState);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to update Nexori status HUD for " + playerUuid + ".");
        }
    }

    // -------------------------------------------------------------------------
    // State resolution
    // -------------------------------------------------------------------------

    @Nonnull
    private Optional<HudRenderState> resolveState(@Nonnull UUID playerUuid, long nowEpochMs) {
        Optional<ArenaMatchService.ReturnHudState> returnHudState = arenaMatchService.findReturnHudState(playerUuid, nowEpochMs);
        if (returnHudState.isPresent()) {
            return Optional.of(buildReturnState(returnHudState.get(), nowEpochMs));
        }

        Optional<QueueCoordinatorService.QueueHudState> queueHudState = queueCoordinatorService.findQueueHudState(playerUuid, nowEpochMs);
        if (queueHudState.isPresent()) {
            return Optional.of(buildQueueState(queueHudState.get(), nowEpochMs));
        }

        return Optional.empty();
    }

    @Nonnull
    private HudRenderState buildQueueState(@Nonnull QueueCoordinatorService.QueueHudState queueState, long nowEpochMs) {
        String accentColor;
        String statusText;

        switch (queueState.phase()) {
            case COUNTDOWN -> {
                accentColor = QUEUE_COUNTDOWN_COLOR;
                statusText = "Starting in " + secondsRemaining(queueState.countdownEndsAtEpochMs(), nowEpochMs) + "s";
            }
            case READY -> {
                accentColor = QUEUE_READY_COLOR;
                statusText = "Sending players now";
            }
            case WAITING -> {
                int needed = Math.max(0, queueState.minPlayers() - queueState.queuedPlayers());
                accentColor = QUEUE_WAITING_COLOR;
                statusText = needed > 0
                    ? "Need " + needed + " more player" + (needed == 1 ? "" : "s")
                    : "Waiting for players";
            }
            default -> throw new IllegalStateException("Unexpected queue phase: " + queueState.phase());
        }

        return new HudRenderState(
            HudRenderKind.QUEUE,
            "",                          // titleText — not shown in queue HUD
            queueState.displayName(),    // mainText — game display name shown as queue title
            queueState.queuedPlayers() + " / " + queueState.maxPlayers() + " players", // detailText — player count
            statusText,                  // statusText — phase status
            accentColor
        );
    }

    @Nonnull
    private HudRenderState buildReturnState(@Nonnull ArenaMatchService.ReturnHudState returnHudState, long nowEpochMs) {
        String normalizedOutcome = returnHudState.outcomeLabel() == null
            ? ""
            : returnHudState.outcomeLabel().trim().toLowerCase();
        String titleText;
        String accentColor;
        if ("victory".equals(normalizedOutcome)) {
            titleText = "VICTORY";
            accentColor = RETURN_VICTORY_COLOR;
        } else if ("eliminated".equals(normalizedOutcome)) {
            titleText = "ELIMINATED";
            accentColor = RETURN_ELIMINATED_COLOR;
        } else if ("no contest".equals(normalizedOutcome)) {
            titleText = "MATCH CANCELLED";
            accentColor = RETURN_NO_CONTEST_COLOR;
        } else {
            titleText = "MATCH COMPLETE";
            accentColor = RETURN_GENERIC_COLOR;
        }

        String detailText = returnHudState.arenaDisplayName() == null || returnHudState.arenaDisplayName().isBlank()
            ? "Get ready to queue again"
            : returnHudState.arenaDisplayName();

        return new HudRenderState(
            HudRenderKind.RETURN,
            titleText,
            "Returning to Origin Server",
            detailText,
            "Lobby in " + secondsRemaining(returnHudState.returnAtEpochMs(), nowEpochMs) + "s",
            accentColor
        );
    }

    // -------------------------------------------------------------------------
    // HUD construction dispatch
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildHud(@Nonnull PlayerRef playerRef, @Nonnull HudRenderState state) {
        return state.kind() == HudRenderKind.QUEUE
            ? buildQueueHud(playerRef, state)
            : buildReturnHud(playerRef, state);
    }

    // -------------------------------------------------------------------------
    // Queue HUD — card design translated from .ui spec
    //
    // All coordinates below are relative to the card panel (top=114, left=475
    // on a 1920×1080 screen), unless noted.
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildQueueHud(@Nonnull PlayerRef playerRef, @Nonnull HudRenderState state) {
        HudBuilder hud = HudBuilder.hudForPlayer(playerRef);

        // ── Main card ────────────────────────────────────────────────────────
        PanelBuilder card = PanelBuilder.panel()
            .withId("nexori-queue-card")
            .withAnchor(new HyUIAnchor().setLeft(475).setTop(114).setWidth(970).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(QUEUE_CARD_BG))
            .withOutlineColor(QUEUE_CARD_OUTLINE)
            .withOutlineSize(2f)
            .withHitTestVisible(false);

        // ── Left logo section ────────────────────────────────────────────────
        PanelBuilder logoSection = PanelBuilder.panel()
            .withId("nexori-queue-logo-section")
            .withAnchor(new HyUIAnchor().setLeft(0).setTop(0).setWidth(235).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(QUEUE_LEFT_BG))
            .withOutlineColor(QUEUE_LEFT_OUTLINE)
            .withOutlineSize(1f)
            .withHitTestVisible(false);

        // ── Nexori logo image ────────────────────────────────────────────────
        // Centered within the 235×205 left section:
        //   horizontal: (235 - 160) / 2 = 37  → left=37
        //   vertical:   (205 - 160) / 2 = 22  → top=22
        // ImageBuilder prepends "UI/Custom/" automatically, so
        // "HUD/Nexori_logo_fondo_transparente.png" resolves to
        // "UI/Custom/HUD/Nexori_logo_fondo_transparente.png" on the client.
        ImageBuilder logo = ImageBuilder.image()
            .withId("nexori-queue-logo")
            .withImage("HUD/Nexori_logo_fondo_transparente.png")
            .withAnchor(new HyUIAnchor().setLeft(37).setTop(22).setWidth(160).setHeight(160))
            .withHitTestVisible(false);

        // ── Queue title (game display name) ──────────────────────────────────
        // Width extended to 620px (left=320 → x=940) so that the framework
        // has enough room and does not clip/add ellipsis for typical names.
        LabelBuilder title = LabelBuilder.label()
            .withId("nexori-queue-title")
            .withText(state.mainText())
            .withAnchor(new HyUIAnchor().setLeft(320).setTop(30).setWidth(620).setHeight(80))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(42)
                .setRenderBold(true)
                .setTextColor(QUEUE_TITLE_COLOR)
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Start));

        // ── Multiplayer icon ─────────────────────────────────────────────────
        ImageBuilder playerIcon = ImageBuilder.image()
            .withId("nexori-queue-player-icon")
            .withImage("HUD/multiplayer.png")
            .withAnchor(new HyUIAnchor().setLeft(320).setTop(130).setWidth(30).setHeight(30))
            .withHitTestVisible(false);

        // ── Player count label ───────────────────────────────────────────────
        LabelBuilder playerCount = LabelBuilder.label()
            .withId("nexori-queue-player-count")
            .withText(state.detailText())
            .withAnchor(new HyUIAnchor().setLeft(360).setTop(130).setWidth(190).setHeight(40))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(25)
                .setTextColor(QUEUE_COUNT_COLOR)
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Start));

        // ── Queue detail label (phase status) ────────────────────────────────
        String detailColor = (state.accentColor() != null && !state.accentColor().isBlank())
            ? state.accentColor()
            : QUEUE_DETAIL_FALLBACK;
        LabelBuilder queueDetail = LabelBuilder.label()
            .withId("nexori-queue-detail")
            .withText(state.statusText())
            .withAnchor(new HyUIAnchor().setLeft(560).setTop(130).setWidth(300).setHeight(40))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(25)
                .setRenderBold(true)
                .setTextColor(detailColor)
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Start));

        // ── Assemble card children ────────────────────────────────────────────
        card.addChild(logoSection);
        card.addChild(logo);
        card.addChild(title);
        card.addChild(playerIcon);
        card.addChild(playerCount);
        card.addChild(queueDetail);

        hud.addElement(card);
        return hud;
    }

    // -------------------------------------------------------------------------
    // Return HUD — legacy layout, unchanged
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildReturnHud(@Nonnull PlayerRef playerRef, @Nonnull HudRenderState state) {
        HudBuilder hud = HudBuilder.hudForPlayer(playerRef);

        PanelBuilder root = PanelBuilder.panel()
            .withId("nexori-status-root")
            .withAnchor(new HyUIAnchor()
                .setLeft(0)
                .setRight(0)
                .setTop(200)
                .setHeight(250))
            .withHitTestVisible(false);

        LabelBuilder title = LabelBuilder.label()
            .withId("nexori-status-title")
            .withText(state.titleText())
            .withAnchor(new HyUIAnchor()
                .setLeft(0)
                .setRight(0)
                .setTop(0)
                .setHeight(44))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(36)
                .setRenderBold(true)
                .setTextColor(state.accentColor())
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        LabelBuilder main = LabelBuilder.label()
            .withId("nexori-status-main")
            .withText(state.mainText())
            .withAnchor(new HyUIAnchor()
                .setLeft(0)
                .setRight(0)
                .setTop(54)
                .setHeight(44))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(42)
                .setRenderBold(true)
                .setTextColor(MAIN_TEXT_COLOR)
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        LabelBuilder detail = LabelBuilder.label()
            .withId("nexori-status-detail")
            .withText(state.detailText())
            .withAnchor(new HyUIAnchor()
                .setLeft(0)
                .setRight(0)
                .setTop(110)
                .setHeight(34))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(26)
                .setTextColor(DETAIL_TEXT_COLOR)
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        LabelBuilder status = LabelBuilder.label()
            .withId("nexori-status-countdown")
            .withText(state.statusText())
            .withAnchor(new HyUIAnchor()
                .setLeft(0)
                .setRight(0)
                .setTop(156)
                .setHeight(38))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(30)
                .setRenderBold(true)
                .setTextColor(state.accentColor())
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        root.addChild(title);
        root.addChild(main);
        root.addChild(detail);
        root.addChild(status);

        hud.addElement(root);
        return hud;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int secondsRemaining(long targetEpochMs, long nowEpochMs) {
        long remainingMs = Math.max(0L, targetEpochMs - nowEpochMs);
        return (int) Math.max(0L, (remainingMs + 999L) / 1000L);
    }

    private record HudRenderState(
        HudRenderKind kind,
        String titleText,
        String mainText,
        String detailText,
        String statusText,
        String accentColor
    ) {
    }
}
