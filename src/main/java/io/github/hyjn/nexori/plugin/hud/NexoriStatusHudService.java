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
    // Intro animation — short entry animation played once when a HUD is first
    // shown (or re-shown after kind change).  After INTRO_DURATION_MS the
    // animation is marked complete and normal dedup resumes.
    // -------------------------------------------------------------------------
    private static final long INTRO_DURATION_MS = 600L;

    /**
     * Snapshot of the intro animation at a single point in time.
     * {@code easedProgress} is 0.0 at the first frame and 1.0 when the
     * animation is complete.  {@code complete} is true once progress reaches 1.
     */
    private record HudIntroFrame(float easedProgress, boolean complete) {

        /** Build a frame from raw (linear) progress in [0, 1]. */
        static HudIntroFrame compute(long startedAtEpochMs, long nowEpochMs) {
            float raw = (float) (nowEpochMs - startedAtEpochMs) / INTRO_DURATION_MS;
            float clamped = Math.max(0f, Math.min(1f, raw));
            return new HudIntroFrame(easeOutQuad(clamped), clamped >= 1f);
        }

        /** A fully-completed frame — used when no animation is tracked. */
        static HudIntroFrame completed() {
            return new HudIntroFrame(1f, true);
        }

        /** Quadratic ease-out: fast start, gentle finish. */
        private static float easeOutQuad(float t) {
            return 1f - (1f - t) * (1f - t);
        }
    }

    // -------------------------------------------------------------------------
    // Queue card colors  (format: #RRGGBBAA)
    // -------------------------------------------------------------------------
    private static final String QUEUE_CARD_BG         = "#051218BE";
    private static final String QUEUE_CARD_OUTLINE     = "#5FDEFFD2";
    private static final String QUEUE_LEFT_BG          = "#081E2669";
    private static final String QUEUE_LEFT_OUTLINE     = "#4BCDF546";
    private static final String QUEUE_TITLE_COLOR      = "#F5FCFF";
    private static final String QUEUE_COUNT_COLOR      = "#E1ECF2";
    private static final String QUEUE_DETAIL_FALLBACK  = "#5AEBFF";

    // Queue phase accent colors
    private static final String QUEUE_WAITING_COLOR   = "#8FDBFF";
    private static final String QUEUE_COUNTDOWN_COLOR = "#FFD36E";
    private static final String QUEUE_READY_COLOR     = "#9AF4A8";

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
    // Per-player state
    // -------------------------------------------------------------------------
    private final QueueCoordinatorService queueCoordinatorService;
    private final ArenaMatchService arenaMatchService;
    private final HytaleLogger logger;
    private final ConcurrentMap<UUID, HyUIHud>        activeHuds      = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, HudRenderState> renderedStates  = new ConcurrentHashMap<>();
    /** Which kind of HUD is currently shown — used to detect kind changes. */
    private final ConcurrentMap<UUID, HudRenderKind>  activeHudKinds  = new ConcurrentHashMap<>();
    /** Epoch-ms when the active HUD's intro animation started. Absent when no animation is tracked. */
    private final ConcurrentMap<UUID, Long>           introStartedAt  = new ConcurrentHashMap<>();

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
        introStartedAt.remove(playerUuid);   // clear animation state so next appearance replays intro
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

        HyUIHud existing = activeHuds.get(playerUuid);
        HudRenderKind existingKind = activeHudKinds.get(playerUuid);

        // QUEUE and RETURN have different element trees — force remove+recreate on kind change.
        boolean kindChanged = existing != null && existingKind != null && existingKind != nextState.kind();
        boolean isNewHud = existing == null || kindChanged;

        // IMPORTANT: register the animation start time BEFORE resolving the intro frame.
        // If we're about to create a new HUD, the first frame must have p=0 (fully at
        // the start position). If we set introStartedAt after show(), the first call to
        // resolveIntroFrame() returns completed() → p=1 → the card flashes at its final
        // position for one frame, then jumps back to the start of the slide.
        if (isNewHud) {
            introStartedAt.put(playerUuid, nowEpochMs);
        }

        HudIntroFrame introFrame = resolveIntroFrame(playerUuid, nowEpochMs);

        // Skip update only when:
        //   (a) the logical state is unchanged  AND
        //   (b) the intro animation has already finished.
        // During the animation window we must keep sending updates even if the
        // queue state itself hasn't changed, because every tick the anchors and
        // alpha values are different.
        if (introFrame.complete() && nextState.equals(previousState)) {
            return;
        }

        HudBuilder builder = buildHud(playerRef, nextState, introFrame);

        try {
            if (isNewHud) {
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
    // Intro animation resolution
    // -------------------------------------------------------------------------

    @Nonnull
    private HudIntroFrame resolveIntroFrame(@Nonnull UUID playerUuid, long nowEpochMs) {
        Long startedAt = introStartedAt.get(playerUuid);
        if (startedAt == null) {
            // No animation tracked for this player — treat as fully complete so
            // we don't force unnecessary re-renders.
            return HudIntroFrame.completed();
        }
        return HudIntroFrame.compute(startedAt, nowEpochMs);
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
            queueState.displayName(),    // mainText  — game display name (queue title)
            queueState.queuedPlayers() + " / " + queueState.maxPlayers() + " players",
            statusText,
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
    private HudBuilder buildHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull HudRenderState state,
        @Nonnull HudIntroFrame intro
    ) {
        return state.kind() == HudRenderKind.QUEUE
            ? buildQueueHud(playerRef, state, intro)
            : buildReturnHud(playerRef, state, intro);
    }

    // -------------------------------------------------------------------------
    // Queue HUD — card design with intro animation
    //
    // All coordinates are relative to the card panel (top=114, left=475 on a
    // 1920×1080 screen) unless noted.
    //
    // Intro animation (first 600 ms):
    //   • Card slides up 30 px — all children follow automatically.
    //   • Card background + outline alpha fade from 0 → full target value.
    //   • Logo section background + outline alpha fade in the same way.
    //   • Text labels and images appear at full opacity from the first frame
    //     (they look natural as part of the sliding card).
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildQueueHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull HudRenderState state,
        @Nonnull HudIntroFrame intro
    ) {
        HudBuilder hud = HudBuilder.hudForPlayer(playerRef);

        float p = intro.easedProgress();

        // Vertical slide: 30 px offset at t=0, 0 px offset at t=1.
        // All card children inherit this slide because they are positioned
        // relative to the card.
        int slideOffset = Math.round(30f * (1f - p));

        // ── Main card ─────────────────────────────────────────────────────────
        PanelBuilder card = PanelBuilder.panel()
            .withId("nexori-queue-card")
            .withAnchor(new HyUIAnchor().setLeft(475).setTop(114 + slideOffset).setWidth(970).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(lerpAlpha(QUEUE_CARD_BG, p)))
            .withOutlineColor(lerpAlpha(QUEUE_CARD_OUTLINE, p))
            .withOutlineSize(2f)
            .withHitTestVisible(false);

        // ── Left logo section ─────────────────────────────────────────────────
        PanelBuilder logoSection = PanelBuilder.panel()
            .withId("nexori-queue-logo-section")
            .withAnchor(new HyUIAnchor().setLeft(0).setTop(0).setWidth(235).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(lerpAlpha(QUEUE_LEFT_BG, p)))
            .withOutlineColor(lerpAlpha(QUEUE_LEFT_OUTLINE, p))
            .withOutlineSize(1f)
            .withHitTestVisible(false);

        // ── Nexori logo image ─────────────────────────────────────────────────
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

        // ── Queue title (game display name) ───────────────────────────────────
        // Width 620 px (left=320 → x=940) prevents the framework from
        // adding ellipsis for typical game display names.
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

        // ── Multiplayer icon ──────────────────────────────────────────────────
        ImageBuilder playerIcon = ImageBuilder.image()
            .withId("nexori-queue-player-icon")
            .withImage("HUD/multiplayer.png")
            .withAnchor(new HyUIAnchor().setLeft(320).setTop(130).setWidth(30).setHeight(30))
            .withHitTestVisible(false);

        // ── Player count label ────────────────────────────────────────────────
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

        // ── Queue detail label (phase status) ─────────────────────────────────
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

        // ── Assemble ──────────────────────────────────────────────────────────
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
    // Return HUD — legacy layout
    //
    // intro: accepted for API consistency with buildQueueHud; entry animation
    // for the return HUD is not yet implemented and can be added here later.
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildReturnHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull HudRenderState state,
        @Nonnull HudIntroFrame intro   // reserved — not yet applied
    ) {
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

    /**
     * Interpolates the alpha channel of a {@code #RRGGBBAA} hex color string.
     * At {@code t=0} the color is fully transparent; at {@code t=1} it equals
     * the original color exactly.
     *
     * @param hexColor a 9-character string in {@code #RRGGBBAA} format
     * @param t        progress in [0, 1]
     * @return interpolated color string in {@code #RRGGBBAA} format
     */
    @Nonnull
    private static String lerpAlpha(@Nonnull String hexColor, float t) {
        int targetAlpha = Integer.parseInt(hexColor.substring(7, 9), 16);
        int alpha = Math.round(targetAlpha * t);
        return hexColor.substring(0, 7) + String.format("%02X", alpha);
    }

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
