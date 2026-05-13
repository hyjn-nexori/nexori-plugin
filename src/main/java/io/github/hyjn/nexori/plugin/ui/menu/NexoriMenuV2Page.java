package io.github.hyjn.nexori.plugin.ui.menu;

import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.ContainerBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIPadding;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuSetupState;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriAboutView;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriAccessGateView;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriBackendView;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriHomeView;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriMinigameView;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriPortalView;
import io.github.hyjn.nexori.plugin.ui.menu.views.NexoriRulesView;

import javax.annotation.Nonnull;
import java.util.List;

import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.buildPlaceholderScroll;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.card;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.label;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.spacerX;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.spacerY;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.statusBar;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuComponents.viewSubtitle;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.BODY_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.BODY_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.CONTENT_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.CONTENT_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.PAGE_H;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.PAGE_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuLayout.SIDEBAR_W;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BODY;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BUTTON_ABOUT_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BUTTON_ABOUT_SELECTED_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BUTTON_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.BUTTON_SELECTED_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.PANEL_ALT_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.PANEL_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.ROOT_BG;
import static io.github.hyjn.nexori.plugin.ui.menu.components.NexoriMenuStyles.TITLE;

/**
 * Entrypoint and composition root for the Nexori V2 menu.
 * <p>
 * This class owns the top-level page shell, sidebar navigation, selected view routing,
 * and high-level page coordination. It should not accumulate feature-specific UI
 * implementation. Large sections belong in {@code io.github.hyjn.nexori.plugin.ui.menu.views},
 * reusable controls belong in {@code ui.menu.components}, and temporary UI state belongs
 * in {@code ui.menu.state}. When a new section or complex tab is added, route to a view
 * from here instead of adding hundreds of feature-specific lines to this class.
 */
public final class NexoriMenuV2Page {

    private NexoriMenuV2Page() {
    }

    public static void open(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PlayerRef playerRef, Player player, @Nonnull NexoriPlugin plugin) {
        open(ref, store, playerRef, player, plugin, NexoriMenuV2State.initial());
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull NexoriPlugin plugin,
        @Nonnull NexoriMenuV2State rawState
    ) {
        if (player == null) {
            playerRef.sendMessage(Message.raw("nexorimenuv2: could not resolve the live player entity."));
            return;
        }

        List<ConfiguredPeer> peers = plugin.getConfiguredPeerService().list();
        NexoriMenuSetupState setup = NexoriHomeView.buildSetupState(plugin, peers);
        NexoriMenuV2State state = normalizeStateForSetup(rawState, setup);

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI MENU V2")
            .withBackground(ROOT_BG)
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(16));
        NexoriMenuRenderContext context = new NexoriMenuRenderContext(ref, store, playerRef, player, plugin, state, peers);
        content.addChild(body(context, setup));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    @Nonnull
    private static NexoriMenuV2State normalizeStateForSetup(@Nonnull NexoriMenuV2State rawState, @Nonnull NexoriMenuSetupState setup) {
        NexoriMenuV2State state = rawState.normalized();
        if (setup.viewsLocked() && state.selectedView() != NexoriMenuV2View.HOME) {
            state = state.withSelectedView(NexoriMenuV2View.HOME);
        }
        if (state.selectedView() == NexoriMenuV2View.OPERATIONS) {
            state = state.withSelectedView(NexoriMenuV2View.HOME);
        }
        return state;
    }

    @Nonnull
    private static GroupBuilder body(@Nonnull NexoriMenuRenderContext context, @Nonnull NexoriMenuSetupState setup) {
        GroupBuilder body = GroupBuilder.group().withLayoutMode("Top").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(BODY_H));
        GroupBuilder mainRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(CONTENT_H));
        mainRow.addChild(sidebar(context, setup));
        mainRow.addChild(spacerX(20));
        mainRow.addChild(contentPanel(context, setup));
        body.addChild(mainRow);
        body.addChild(spacerY(12));
        body.addChild(statusBar(context.state()));
        return body;
    }

    @Nonnull
    private static GroupBuilder sidebar(@Nonnull NexoriMenuRenderContext context, @Nonnull NexoriMenuSetupState setup) {
        GroupBuilder sidebar = card(SIDEBAR_W, CONTENT_H, PANEL_BG);
        sidebar.addChild(label("Views", TITLE, SIDEBAR_W - 32));
        sidebar.addChild(spacerY(12));

        List<NexoriMenuV2View> views = List.of(
            NexoriMenuV2View.HOME,
            NexoriMenuV2View.PORTALS,
            NexoriMenuV2View.RULES,
            NexoriMenuV2View.QUEUES,
            NexoriMenuV2View.BACKEND,
            NexoriMenuV2View.ACCESS_GATE
        );
        for (NexoriMenuV2View view : views) {
            boolean selected = context.state().selectedView() == view;
            boolean locked = setup.viewsLocked() && view != NexoriMenuV2View.HOME;
            sidebar.addChild(
                (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                    .withText(view.label())
                    .withBackground(selected ? BUTTON_SELECTED_BG : BUTTON_BG)
                    .withAnchor(new HyUIAnchor().setWidth(SIDEBAR_W - 32).setHeight(46))
                    .withDisabled(selected || locked)
                    .onClick((ignored, ctx) -> open(
                        context.ref(),
                        context.store(),
                        context.playerRef(),
                        context.player(),
                        context.plugin(),
                        context.state().withSelectedView(view).withStatusText("Switched to " + view.label() + ".")
                    ))
            );
            sidebar.addChild(spacerY(8));
        }

        sidebar.addChild(spacerY(Math.max(24, CONTENT_H - 436)));
        boolean aboutSelected = context.state().selectedView() == NexoriMenuV2View.ABOUT;
        sidebar.addChild(
            (aboutSelected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                .withText(NexoriMenuV2View.ABOUT.label())
                .withBackground(aboutSelected ? BUTTON_ABOUT_SELECTED_BG : BUTTON_ABOUT_BG)
                .withAnchor(new HyUIAnchor().setWidth(SIDEBAR_W - 32).setHeight(44))
                .withDisabled(aboutSelected || setup.viewsLocked())
                .onClick((ignored, ctx) -> open(
                    context.ref(),
                    context.store(),
                    context.playerRef(),
                    context.player(),
                    context.plugin(),
                    context.state().withSelectedView(NexoriMenuV2View.ABOUT).withStatusText("Opened How To Use.")
                ))
        );
        return sidebar;
    }

    @Nonnull
    private static GroupBuilder contentPanel(@Nonnull NexoriMenuRenderContext context, @Nonnull NexoriMenuSetupState setup) {
        GroupBuilder panel = card(CONTENT_W, CONTENT_H, PANEL_ALT_BG);
        panel.addChild(label(context.state().selectedView().label(), TITLE, CONTENT_W - 32));
        if (!viewSubtitle(context.state().selectedView()).isBlank()) {
            panel.addChild(spacerY(8));
            panel.addChild(label(viewSubtitle(context.state().selectedView()), BODY, CONTENT_W - 32));
        }
        if (context.state().selectedView() == NexoriMenuV2View.PORTALS) {
            panel.addChild(spacerY(12));
            panel.addChild(NexoriPortalView.tabs(context));
        }
        if (context.state().selectedView() == NexoriMenuV2View.QUEUES) {
            panel.addChild(spacerY(12));
            panel.addChild(NexoriMinigameView.tabs(context));
        }
        if (context.state().selectedView() == NexoriMenuV2View.ACCESS_GATE) {
            panel.addChild(spacerY(12));
            panel.addChild(NexoriAccessGateView.tabs(context));
        }
        if (context.state().selectedView() == NexoriMenuV2View.BACKEND) {
            panel.addChild(spacerY(12));
            panel.addChild(NexoriBackendView.tabs(context));
        }
        panel.addChild(spacerY(12));
        int viewportHeight = CONTENT_H - (hasTopTabs(context.state().selectedView()) ? 158 : 104);
        panel.addChild(buildContentScroll(context, setup, viewportHeight));
        return panel;
    }

    private static boolean hasTopTabs(@Nonnull NexoriMenuV2View view) {
        return view == NexoriMenuV2View.PORTALS
            || view == NexoriMenuV2View.QUEUES
            || view == NexoriMenuV2View.ACCESS_GATE
            || view == NexoriMenuV2View.BACKEND;
    }

    @Nonnull
    private static ReorderableListBuilder buildContentScroll(
        @Nonnull NexoriMenuRenderContext context,
        @Nonnull NexoriMenuSetupState setup,
        int viewportHeight
    ) {
        String scrollId = "nexori-v2-scroll-" + context.state().selectedView().name().toLowerCase()
            + (context.state().selectedView() == NexoriMenuV2View.PORTALS ? "-" + context.state().selectedPortalTab().name().toLowerCase() : "")
            + (context.state().selectedView() == NexoriMenuV2View.QUEUES ? "-" + context.state().selectedMinigameTab().name().toLowerCase() : "")
            + (context.state().selectedView() == NexoriMenuV2View.RULES ? "-" + context.state().selectedRulesTab().name().toLowerCase() : "")
            + (context.state().selectedView() == NexoriMenuV2View.ACCESS_GATE ? "-" + context.state().selectedAccessGateTab().name().toLowerCase() : "")
            + (context.state().selectedView() == NexoriMenuV2View.BACKEND ? NexoriBackendView.scrollSuffix(context.playerRef()) : "");
        return switch (context.state().selectedView()) {
            case HOME -> NexoriHomeView.render(context, setup, viewportHeight, scrollId);
            case ABOUT -> NexoriAboutView.render(viewportHeight, scrollId);
            case PORTALS, TARGETS -> NexoriPortalView.render(context, setup, viewportHeight, scrollId);
            case QUEUES -> NexoriMinigameView.render(context, setup, viewportHeight, scrollId);
            case RULES -> NexoriRulesView.render(context, setup, viewportHeight, scrollId);
            case BACKEND -> NexoriBackendView.render(context, viewportHeight, scrollId);
            case ACCESS_GATE -> NexoriAccessGateView.render(context, viewportHeight, scrollId);
            case OPERATIONS -> buildPlaceholderScroll(context.state().selectedView(), viewportHeight, scrollId);
        };
    }
}
