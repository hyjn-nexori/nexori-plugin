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
    // HUD kind — QUEUE and RETURN use different element ID trees, so we force
    // remove+recreate whenever the kind changes.
    // -------------------------------------------------------------------------
    private enum HudRenderKind { QUEUE, RETURN }

    // -------------------------------------------------------------------------
    // Animation phase
    // -------------------------------------------------------------------------
    private enum HudAnimationPhase { ENTERING, EXITING }

    // -------------------------------------------------------------------------
    // Per-player animation state — stores which phase is active and when it started.
    // -------------------------------------------------------------------------
    private record HudAnimationState(HudAnimationPhase phase, long startedAtEpochMs) {}

    // -------------------------------------------------------------------------
    // Animation frame — snapshot for one render call during an animation.
    //
    // easedProgress is always in [0, 1]:
    //   ENTERING:  0 = start of entry  →  1 = fully visible, animation done.
    //   EXITING:   0 = start of exit   →  1 = fully gone, ready to remove.
    //
    // Easing:
    //   ENTERING uses ease-out-quad (fast rise, gentle settle).
    //   EXITING  uses ease-in-quad  (gentle start, fast disappear).
    // -------------------------------------------------------------------------
    private record HudAnimationFrame(HudAnimationPhase phase, float easedProgress, boolean complete) {

        static HudAnimationFrame compute(@Nonnull HudAnimationState state, long nowEpochMs) {
            long duration = state.phase() == HudAnimationPhase.ENTERING
                ? ENTER_DURATION_MS
                : EXIT_DURATION_MS;
            float raw = (float) (nowEpochMs - state.startedAtEpochMs()) / duration;
            float clamped = Math.max(0f, Math.min(1f, raw));
            float eased = state.phase() == HudAnimationPhase.ENTERING
                ? easeOutQuad(clamped)
                : easeInQuad(clamped);
            return new HudAnimationFrame(state.phase(), eased, clamped >= 1f);
        }

        /**
         * Steady-state frame — no animation active, HUD is fully visible.
         * Phase is ENTERING so builder logic reads alpha=1, slideOffset=0.
         */
        static HudAnimationFrame steady() {
            return new HudAnimationFrame(HudAnimationPhase.ENTERING, 1f, true);
        }

        private static float easeOutQuad(float t) { return 1f - (1f - t) * (1f - t); }
        private static float easeInQuad(float t)  { return t * t; }
    }

    // -------------------------------------------------------------------------
    // Animation durations
    // -------------------------------------------------------------------------
    private static final long ENTER_DURATION_MS = 600L;
    private static final long EXIT_DURATION_MS  = 375L;

    // -------------------------------------------------------------------------
    // Queue card colors  (#RRGGBBAA — lerpAlpha interpolates the last two digits)
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
    private final ConcurrentMap<UUID, HyUIHud>           activeHuds       = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, HudRenderState>    renderedStates   = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, HudRenderKind>     activeHudKinds   = new ConcurrentHashMap<>();
    /**
     * Active animation per player.  Absent when the HUD is in its steady
     * (fully visible, no animation) state.  Kept alive during EXITING so we
     * can drive the exit frames before removing the HUD.
     */
    private final ConcurrentMap<UUID, HudAnimationState> activeAnimations = new ConcurrentHashMap<>();

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

    /**
     * Removes the HUD immediately with no exit animation.
     * Use for cleanup, error handling, and player unload — not for normal
     * disappearance, which goes through {@link #refresh} and plays the exit animation.
     */
    public void remove(@Nonnull PlayerRef playerRef) {
        UUID playerUuid = playerRef.getUuid();
        if (playerUuid == null) {
            return;
        }
        removeImmediately(playerUuid);
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

        // ── No active queue/return state → drive or start exit animation ──────
        if (resolved.isEmpty()) {
            HyUIHud existing = activeHuds.get(playerUuid);
            if (existing == null) {
                return;  // nothing on screen, nothing to do
            }

            HudAnimationState currentAnim = activeAnimations.get(playerUuid);
            if (currentAnim == null || currentAnim.phase() != HudAnimationPhase.EXITING) {
                // Begin exit animation.  We need the last visible state so we can
                // keep rendering the card during the fade-out.
                if (renderedStates.get(playerUuid) == null) {
                    // No state to animate — remove immediately.
                    removeImmediately(playerUuid);
                    return;
                }
                // Return HUD skips exit animation — players are teleported to lobby
                // before it would finish, so there is no point playing it.
                if (activeHudKinds.get(playerUuid) == HudRenderKind.RETURN) {
                    removeImmediately(playerUuid);
                    return;
                }
                activeAnimations.put(playerUuid, new HudAnimationState(HudAnimationPhase.EXITING, nowEpochMs));
            }

            HudAnimationFrame frame = resolveFrame(playerUuid, nowEpochMs);
            if (frame.complete()) {
                removeImmediately(playerUuid);
                return;
            }

            // Render the exit frame using the last known state (renderedStates is
            // NOT cleared during EXITING — we need it to build the HUD).
            HudRenderState lastState = renderedStates.get(playerUuid);
            if (lastState == null) {
                removeImmediately(playerUuid);
                return;
            }

            HudBuilder builder = buildHud(playerRef, lastState, frame);
            try {
                existing.update(builder);
            } catch (Exception exception) {
                logger.atWarning().withCause(exception).log("Failed to update Nexori status HUD exit for " + playerUuid + ".");
                removeImmediately(playerUuid);
            }
            return;
        }

        // ── State is present → enter or update ───────────────────────────────
        HudRenderState nextState = resolved.get();
        HudRenderState previousState = renderedStates.get(playerUuid);

        HyUIHud existing = activeHuds.get(playerUuid);
        HudRenderKind existingKind = activeHudKinds.get(playerUuid);

        boolean kindChanged = existing != null && existingKind != null && existingKind != nextState.kind();
        boolean isNewHud = existing == null || kindChanged;

        if (isNewHud) {
            // Register ENTERING animation BEFORE resolveFrame() so the very first
            // frame is built at p=0 (card invisible, 30 px below final position).
            // If we registered after show(), resolveFrame() would see no animation
            // and return steady() → p=1 → one-frame flash at the final position.
            activeAnimations.put(playerUuid, new HudAnimationState(HudAnimationPhase.ENTERING, nowEpochMs));
        }

        HudAnimationFrame frame = resolveFrame(playerUuid, nowEpochMs);

        // Skip only when animation is done AND state hasn't changed.
        if (frame.complete() && nextState.equals(previousState)) {
            return;
        }

        HudBuilder builder = buildHud(playerRef, nextState, frame);

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

            // Once the entry animation finishes, remove the animation record so
            // deduplication works normally and we stop re-rendering every tick.
            if (frame.complete()) {
                activeAnimations.remove(playerUuid);
            }
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to update Nexori status HUD for " + playerUuid + ".");
        }
    }

    // -------------------------------------------------------------------------
    // Immediate removal — clears all maps and calls hud.remove()
    // -------------------------------------------------------------------------

    private void removeImmediately(@Nonnull UUID playerUuid) {
        renderedStates.remove(playerUuid);
        activeHudKinds.remove(playerUuid);
        activeAnimations.remove(playerUuid);
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
    // Animation frame resolution
    // -------------------------------------------------------------------------

    @Nonnull
    private HudAnimationFrame resolveFrame(@Nonnull UUID playerUuid, long nowEpochMs) {
        HudAnimationState state = activeAnimations.get(playerUuid);
        if (state == null) {
            return HudAnimationFrame.steady();
        }
        return HudAnimationFrame.compute(state, nowEpochMs);
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
            "",
            queueState.displayName(),
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
            titleText = "MATCH CANCELLED DO TO AFK PLAYER";
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
        @Nonnull HudAnimationFrame frame
    ) {
        return state.kind() == HudRenderKind.QUEUE
            ? buildQueueHud(playerRef, state, frame)
            : buildReturnHud(playerRef, state, frame);
    }

    // -------------------------------------------------------------------------
    // Queue HUD — card design with enter/exit animation
    //
    // Coordinates are relative to the card panel (top=114, left=475 on 1920×1080)
    // unless noted.
    //
    // ENTERING (ease-out-quad):
    //   alpha = easedProgress          (0→1, fades in)
    //   slideOffset = 30*(1-eased)     (card starts 30px below, rises to final)
    //
    // EXITING (ease-in-quad):
    //   alpha = 1 - easedProgress      (1→0, fades out)
    //   slideOffset = 30*eased         (card drifts 30px below from final)
    //
    // All card children inherit the slide because their anchors are relative
    // to the card panel.
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildQueueHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull HudRenderState state,
        @Nonnull HudAnimationFrame frame
    ) {
        HudBuilder hud = HudBuilder.hudForPlayer(playerRef);

        float p = frame.easedProgress();
        float alpha;
        int slideOffset;

        if (frame.phase() == HudAnimationPhase.ENTERING) {
            alpha = p;
            slideOffset = Math.round(30f * (1f - p));
        } else {
            alpha = Math.max(0f, 1f - p);
            slideOffset = Math.round(30f * p);
        }

        // ── Main card ─────────────────────────────────────────────────────────
        PanelBuilder card = PanelBuilder.panel()
            .withId("nexori-queue-card")
            .withAnchor(new HyUIAnchor().setLeft(475).setTop(114 + slideOffset).setWidth(970).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(lerpAlpha(QUEUE_CARD_BG, alpha)))
            .withOutlineColor(lerpAlpha(QUEUE_CARD_OUTLINE, alpha))
            .withOutlineSize(2f)
            .withHitTestVisible(false);

        // ── Left logo section ─────────────────────────────────────────────────
        PanelBuilder logoSection = PanelBuilder.panel()
            .withId("nexori-queue-logo-section")
            .withAnchor(new HyUIAnchor().setLeft(0).setTop(0).setWidth(235).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(lerpAlpha(QUEUE_LEFT_BG, alpha)))
            .withOutlineColor(lerpAlpha(QUEUE_LEFT_OUTLINE, alpha))
            .withOutlineSize(1f)
            .withHitTestVisible(false);

        // ── Nexori logo image ─────────────────────────────────────────────────
        // Centered in the 235×205 left section: left=(235-160)/2=37, top=(205-160)/2=22.
        // ImageBuilder prepends "UI/Custom/" so the path resolves to
        // "UI/Custom/HUD/Nexori_logo_fondo_transparente.png" on the client.
        ImageBuilder logo = ImageBuilder.image()
            .withId("nexori-queue-logo")
            .withImage("HUD/Nexori_logo_fondo_transparente.png")
            .withAnchor(new HyUIAnchor().setLeft(37).setTop(22).setWidth(160).setHeight(160))
            .withHitTestVisible(false);

        // ── Queue title (game display name) ───────────────────────────────────
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
    // Return HUD — card design with entry animation only
    //
    // Left section (235px): Nexori logo, same as queue HUD.
    // Right section (centered): "Returning to Origin Server" / outcome label /
    //                           "Lobby in Xs" countdown — all centered.
    //
    // ENTERING (ease-out-quad):
    //   alpha = easedProgress          (0→1, fades in)
    //   slideOffset = 30*(1-eased)     (card starts 30px below, rises to final)
    //
    // No EXITING animation — players are teleported away before exit would
    // be visible, so removeImmediately() is called from the refresh() exit path.
    // -------------------------------------------------------------------------

    @Nonnull
    private HudBuilder buildReturnHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull HudRenderState state,
        @Nonnull HudAnimationFrame frame
    ) {
        HudBuilder hud = HudBuilder.hudForPlayer(playerRef);

        float p = frame.easedProgress();
        float alpha;
        int slideOffset;

        if (frame.phase() == HudAnimationPhase.ENTERING) {
            alpha = p;
            slideOffset = Math.round(30f * (1f - p));
        } else {
            alpha = Math.max(0f, 1f - p);
            slideOffset = Math.round(30f * p);
        }

        // ── Main card ─────────────────────────────────────────────────────────
        PanelBuilder card = PanelBuilder.panel()
            .withId("nexori-return-card")
            .withAnchor(new HyUIAnchor().setLeft(475).setTop(114 + slideOffset).setWidth(970).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(lerpAlpha(QUEUE_CARD_BG, alpha)))
            .withOutlineColor(lerpAlpha(QUEUE_CARD_OUTLINE, alpha))
            .withOutlineSize(2f)
            .withHitTestVisible(false);

        // ── Left logo section (same as queue HUD) ────────────────────────────
        PanelBuilder logoSection = PanelBuilder.panel()
            .withId("nexori-return-logo-section")
            .withAnchor(new HyUIAnchor().setLeft(0).setTop(0).setWidth(235).setHeight(205))
            .withBackground(new HyUIPatchStyle().setColor(lerpAlpha(QUEUE_LEFT_BG, alpha)))
            .withOutlineColor(lerpAlpha(QUEUE_LEFT_OUTLINE, alpha))
            .withOutlineSize(1f)
            .withHitTestVisible(false);

        ImageBuilder logo = ImageBuilder.image()
            .withId("nexori-return-logo")
            .withImage("HUD/Nexori_logo_fondo_transparente.png")
            .withAnchor(new HyUIAnchor().setLeft(37).setTop(22).setWidth(160).setHeight(160))
            .withHitTestVisible(false);

        // ── "Returning to Origin Server" — centered in the right section ──────
        LabelBuilder mainLabel = LabelBuilder.label()
            .withId("nexori-return-main")
            .withText(state.mainText())
            .withAnchor(new HyUIAnchor().setLeft(240).setRight(0).setTop(28).setHeight(46))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(28)
                .setRenderBold(true)
                .setTextColor(MAIN_TEXT_COLOR)
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        // ── Outcome label (MATCH CANCELLED / VICTORY / ELIMINATED / MATCH COMPLETE) ──
        LabelBuilder outcomeLabel = LabelBuilder.label()
            .withId("nexori-return-outcome")
            .withText(state.titleText())
            .withAnchor(new HyUIAnchor().setLeft(240).setRight(0).setTop(84).setHeight(40))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(24)
                .setRenderBold(true)
                .setTextColor(state.accentColor())
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        // ── "Lobby in Xs" countdown ───────────────────────────────────────────
        LabelBuilder countdownLabel = LabelBuilder.label()
            .withId("nexori-return-countdown")
            .withText(state.statusText())
            .withAnchor(new HyUIAnchor().setLeft(240).setRight(0).setTop(138).setHeight(42))
            .withHitTestVisible(false)
            .withStyle(new HyUIStyle()
                .setFontSize(26)
                .setRenderBold(true)
                .setTextColor(state.accentColor())
                .setOutlineColor("#000000")
                .setAlignment(Alignment.Center));

        // ── Assemble ──────────────────────────────────────────────────────────
        card.addChild(logoSection);
        card.addChild(logo);
        card.addChild(mainLabel);
        card.addChild(outcomeLabel);
        card.addChild(countdownLabel);

        hud.addElement(card);
        return hud;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Interpolates the alpha channel of a {@code #RRGGBBAA} hex color.
     * At {@code t=0} the result is fully transparent; at {@code t=1} it
     * equals the original color.
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
    ) {}
}
