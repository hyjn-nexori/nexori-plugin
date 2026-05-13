package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuRenderContext;
import io.github.hyjn.nexori.plugin.ui.menu.context.NexoriMenuSetupState;
import io.github.hyjn.nexori.plugin.ui.menu.views.rules.NexoriRulesSections;

import javax.annotation.Nonnull;

/**
 * Renders the server rules workspace, including rule groups, assignments,
 * policy status, and sync operations.
 * <p>
 * Rules-specific UI belongs in this view instead of {@code NexoriMenuV2Page}.
 * Reusable controls should be promoted to {@code ui.menu.components}.
 */
public final class NexoriRulesView {

    private NexoriRulesView() {
    }

    @Nonnull
    public static GroupBuilder tabs(@Nonnull NexoriMenuRenderContext context) {
        return NexoriRulesSections.rulesTabs(
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
        return NexoriRulesSections.buildRulesWorkspaceScroll(
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
