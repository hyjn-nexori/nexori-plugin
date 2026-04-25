package io.github.hyjn.nexori.plugin.hud;

import au.ellie.hyui.builders.Alignment;
import au.ellie.hyui.builders.HudBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIHud;
import au.ellie.hyui.builders.HyUIStyle;
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

    private static final String MAIN_TEXT_COLOR = "#FFFFFF";
    private static final String DETAIL_TEXT_COLOR = "#C8D7EA";
    private static final String QUEUE_WAITING_COLOR = "#8FDBFF";
    private static final String QUEUE_COUNTDOWN_COLOR = "#FFD36E";
    private static final String QUEUE_READY_COLOR = "#9AF4A8";
    private static final String RETURN_VICTORY_COLOR = "#F7D774";
    private static final String RETURN_ELIMINATED_COLOR = "#FF8B9A";
    private static final String RETURN_GENERIC_COLOR = "#8FC7FF";

    private final QueueCoordinatorService queueCoordinatorService;
    private final ArenaMatchService arenaMatchService;
    private final HytaleLogger logger;
    private final ConcurrentMap<UUID, HyUIHud> activeHuds = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, HudRenderState> renderedStates = new ConcurrentHashMap<>();

    public NexoriStatusHudService(
        @Nonnull QueueCoordinatorService queueCoordinatorService,
        @Nonnull ArenaMatchService arenaMatchService,
        @Nonnull HytaleLogger logger
    ) {
        this.queueCoordinatorService = queueCoordinatorService;
        this.arenaMatchService = arenaMatchService;
        this.logger = logger;
    }

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

        try {
            if (existing == null) {
                HyUIHud shownHud = builder.show(playerRef);
                activeHuds.put(playerUuid, shownHud);
            } else {
                existing.update(builder);
            }
            renderedStates.put(playerUuid, nextState);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to update Nexori status HUD for " + playerUuid + ".");
        }
    }

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
        String titleText;
        String accentColor;
        String statusText;

        switch (queueState.phase()) {
            case COUNTDOWN -> {
                titleText = "QUEUE READY";
                accentColor = QUEUE_COUNTDOWN_COLOR;
                statusText = "Starting in " + secondsRemaining(queueState.countdownEndsAtEpochMs(), nowEpochMs) + "s";
            }
            case READY -> {
                titleText = "LAUNCHING";
                accentColor = QUEUE_READY_COLOR;
                statusText = "Sending players now";
            }
            case WAITING -> {
                int neededPlayers = Math.max(0, queueState.minPlayers() - queueState.queuedPlayers());
                titleText = "IN QUEUE";
                accentColor = QUEUE_WAITING_COLOR;
                statusText = neededPlayers > 0
                    ? "Need " + neededPlayers + " more player" + (neededPlayers == 1 ? "" : "s")
                    : "Waiting for players";
            }
            default -> throw new IllegalStateException("Unexpected queue phase: " + queueState.phase());
        }

        return new HudRenderState(
            titleText,
            queueState.displayName(),
            queueState.queuedPlayers() + " / " + queueState.maxPlayers() + " players",
            statusText,
            accentColor
        );
    }

    @Nonnull
    private HudRenderState buildReturnState(@Nonnull ArenaMatchService.ReturnHudState returnHudState, long nowEpochMs) {
        String normalizedOutcome = returnHudState.outcomeLabel() == null ? "" : returnHudState.outcomeLabel().trim().toLowerCase();
        String titleText;
        String accentColor;
        if ("victory".equals(normalizedOutcome)) {
            titleText = "VICTORY";
            accentColor = RETURN_VICTORY_COLOR;
        } else if ("eliminated".equals(normalizedOutcome)) {
            titleText = "ELIMINATED";
            accentColor = RETURN_ELIMINATED_COLOR;
        } else {
            titleText = "MATCH COMPLETE";
            accentColor = RETURN_GENERIC_COLOR;
        }

        String detailText = returnHudState.arenaDisplayName() == null || returnHudState.arenaDisplayName().isBlank()
            ? "Get ready to queue again"
            : returnHudState.arenaDisplayName();

        return new HudRenderState(
            titleText,
            "Returning to Lobby",
            detailText,
            "Lobby in " + secondsRemaining(returnHudState.returnAtEpochMs(), nowEpochMs) + "s",
            accentColor
        );
    }

    @Nonnull
    private HudBuilder buildHud(@Nonnull PlayerRef playerRef, @Nonnull HudRenderState state) {
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

    private static int secondsRemaining(long targetEpochMs, long nowEpochMs) {
        long remainingMs = Math.max(0L, targetEpochMs - nowEpochMs);
        return (int) Math.max(0L, (remainingMs + 999L) / 1000L);
    }

    private record HudRenderState(
        String titleText,
        String mainText,
        String detailText,
        String statusText,
        String accentColor
    ) {
    }
}
