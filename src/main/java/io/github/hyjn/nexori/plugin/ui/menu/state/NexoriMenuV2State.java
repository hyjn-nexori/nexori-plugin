package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2View;

import javax.annotation.Nonnull;

public record NexoriMenuV2State(
    NexoriMenuV2View selectedView,
    String statusText
) {

    @Nonnull
    public NexoriMenuV2State normalized() {
        return new NexoriMenuV2State(
            selectedView == null ? NexoriMenuV2View.PORTALS : selectedView,
            statusText == null ? "" : statusText.trim()
        );
    }

    @Nonnull
    public static NexoriMenuV2State initial() {
        return new NexoriMenuV2State(NexoriMenuV2View.PORTALS, "").normalized();
    }

    @Nonnull
    public NexoriMenuV2State withSelectedView(@Nonnull NexoriMenuV2View view) {
        return new NexoriMenuV2State(view, statusText).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withStatusText(@Nonnull String text) {
        return new NexoriMenuV2State(selectedView, text).normalized();
    }
}
