package io.github.hyjn.nexori.plugin.ui.menu.views;

import au.ellie.hyui.builders.ReorderableListBuilder;

import javax.annotation.Nonnull;

/**
 * Renders the informational "About Nexori" workspace.
 * <p>
 * This view keeps informational content outside the top-level menu router so
 * {@code NexoriMenuV2Page} remains focused on shell composition and navigation.
 */
public final class NexoriAboutView {

    private NexoriAboutView() {
    }

    @Nonnull
    public static ReorderableListBuilder render(int viewportHeight, @Nonnull String scrollId) {
        return NexoriMenuSections.buildAboutScroll(viewportHeight, scrollId);
    }
}
