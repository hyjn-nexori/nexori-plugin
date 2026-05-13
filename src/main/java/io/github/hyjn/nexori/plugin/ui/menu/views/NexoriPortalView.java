package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuSetupState;
import io.github.hyjn.nexori.plugin.ui.menu.views.portal.NexoriPortalSections;

import javax.annotation.Nonnull;

/**
 * Renders the portals, targets, and travel binding workspace.
 * <p>
 * Portal-specific UI belongs in this view instead of {@code NexoriMenuV2Page}.
 * Reusable controls should be promoted to {@code ui.menu.components}.
 */
public final class NexoriPortalView {

    private NexoriPortalView() {
    }

    @Nonnull
    public static GroupBuilder tabs(@Nonnull NexoriMenuRenderContext context) {
        return NexoriPortalSections.portalTabs(
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
        @Nonnull NexoriMenuSetupState setup,
        int viewportHeight,
        @Nonnull String scrollId
    ) {
        return NexoriPortalSections.buildPortalWorkspaceScroll(
            context.ref(),
            context.store(),
            context.playerRef(),
            context.player(),
            context.plugin(),
            context.state(),
            context.peers(),
            setup,
            viewportHeight,
            scrollId
        );
    }
}
