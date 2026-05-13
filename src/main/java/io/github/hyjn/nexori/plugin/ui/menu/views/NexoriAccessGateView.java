package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.views.accessgate.NexoriAccessGateSections;

import javax.annotation.Nonnull;

/**
 * Renders the Access Gate workspace for join limits, reserved slots, bypass
 * players, and manual connection controls.
 * <p>
 * Access Gate-specific UI belongs in this view instead of {@code NexoriMenuV2Page}.
 * Reusable controls should be promoted to {@code ui.menu.components}.
 */
public final class NexoriAccessGateView {

    private NexoriAccessGateView() {
    }

    @Nonnull
    public static GroupBuilder tabs(@Nonnull NexoriMenuRenderContext context) {
        return NexoriAccessGateSections.accessGateTabs(
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
        return NexoriAccessGateSections.buildAccessGateScroll(
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
}
