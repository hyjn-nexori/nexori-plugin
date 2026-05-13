package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.views.backend.NexoriBackendSections;

import javax.annotation.Nonnull;

/**
 * Renders the backend workspace, including backend tabs, configuration forms,
 * health details, and request terminal UI.
 * <p>
 * Backend-specific UI belongs in this view instead of {@code NexoriMenuV2Page}.
 * Reusable controls should be promoted to {@code ui.menu.components}.
 */
public final class NexoriBackendView {

    private NexoriBackendView() {
    }

    @Nonnull
    public static GroupBuilder tabs(@Nonnull NexoriMenuRenderContext context) {
        return NexoriBackendSections.backendTabs(
            context.ref(),
            context.store(),
            context.playerRef(),
            context.player(),
            context.plugin(),
            context.state()
        );
    }

    @Nonnull
    public static ReorderableListBuilder render(
        @Nonnull NexoriMenuRenderContext context,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        return NexoriBackendSections.buildBackendScroll(
            context.ref(),
            context.store(),
            context.playerRef(),
            context.player(),
            context.plugin(),
            context.state(),
            viewportHeight,
            scrollId
        );
    }

    @Nonnull
    public static String scrollSuffix(@Nonnull PlayerRef playerRef) {
        return "-" + NexoriBackendSections.currentBackendTab(playerRef).name().toLowerCase();
    }
}
