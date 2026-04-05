package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2View;

import javax.annotation.Nonnull;

public record NexoriMenuV2State(
    NexoriMenuV2View selectedView,
    String statusText,
    String pendingServerDisplayName,
    String pendingServerAddress,
    String editingServerAddress
) {

    @Nonnull
    public NexoriMenuV2State normalized() {
        return new NexoriMenuV2State(
            selectedView == null ? NexoriMenuV2View.HOME : selectedView,
            statusText == null ? "" : statusText.trim(),
            pendingServerDisplayName == null ? "" : pendingServerDisplayName.trim(),
            pendingServerAddress == null ? "" : pendingServerAddress.trim(),
            editingServerAddress == null ? "" : editingServerAddress.trim()
        );
    }

    @Nonnull
    public static NexoriMenuV2State initial() {
        return new NexoriMenuV2State(NexoriMenuV2View.HOME, "", "", "", "").normalized();
    }

    @Nonnull
    public NexoriMenuV2State withSelectedView(@Nonnull NexoriMenuV2View view) {
        return new NexoriMenuV2State(view, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withStatusText(@Nonnull String text) {
        return new NexoriMenuV2State(selectedView, text, pendingServerDisplayName, pendingServerAddress, editingServerAddress).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withPendingServerDisplayName(@Nonnull String text) {
        return new NexoriMenuV2State(selectedView, statusText, text, pendingServerAddress, editingServerAddress).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withPendingServerAddress(@Nonnull String text) {
        return new NexoriMenuV2State(selectedView, statusText, pendingServerDisplayName, text, editingServerAddress).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withEditingServerAddress(@Nonnull String address) {
        return new NexoriMenuV2State(selectedView, statusText, pendingServerDisplayName, pendingServerAddress, address).normalized();
    }

    @Nonnull
    public NexoriMenuV2State clearedPendingServerDraft() {
        return new NexoriMenuV2State(selectedView, statusText, "", "", "").normalized();
    }
}
