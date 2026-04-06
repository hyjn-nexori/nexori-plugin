package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2View;

import javax.annotation.Nonnull;

public record NexoriMenuV2State(
    NexoriMenuV2View selectedView,
    String statusText,
    String pendingServerDisplayName,
    String pendingServerAddress,
    String editingServerAddress,
    String selectedTravelInConnectionAddress,
    String selectedTravelInPortalId,
    String selectedTravelInTargetId,
    String selectedTravelInDisplayName,
    String selectedTravelOutConnectionAddress,
    String selectedTravelOutPortalId,
    String selectedTravelOutTargetId,
    String selectedTravelOutDisplayName
) {

    @Nonnull
    public NexoriMenuV2State normalized() {
        return new NexoriMenuV2State(
            selectedView == null ? NexoriMenuV2View.HOME : selectedView,
            statusText == null ? "" : statusText.trim(),
            pendingServerDisplayName == null ? "" : pendingServerDisplayName.trim(),
            pendingServerAddress == null ? "" : pendingServerAddress.trim(),
            editingServerAddress == null ? "" : editingServerAddress.trim(),
            selectedTravelInConnectionAddress == null ? "" : selectedTravelInConnectionAddress.trim().toLowerCase(),
            selectedTravelInPortalId == null ? "" : selectedTravelInPortalId.trim().toLowerCase(),
            selectedTravelInTargetId == null ? "" : selectedTravelInTargetId.trim().toLowerCase(),
            selectedTravelInDisplayName == null ? "" : selectedTravelInDisplayName.trim(),
            selectedTravelOutConnectionAddress == null ? "" : selectedTravelOutConnectionAddress.trim().toLowerCase(),
            selectedTravelOutPortalId == null ? "" : selectedTravelOutPortalId.trim().toLowerCase(),
            selectedTravelOutTargetId == null ? "" : selectedTravelOutTargetId.trim().toLowerCase(),
            selectedTravelOutDisplayName == null ? "" : selectedTravelOutDisplayName.trim()
        );
    }

    @Nonnull
    public static NexoriMenuV2State initial() {
        return new NexoriMenuV2State(NexoriMenuV2View.HOME, "", "", "", "", "", "", "", "", "", "", "", "").normalized();
    }

    @Nonnull
    public NexoriMenuV2State withSelectedView(@Nonnull NexoriMenuV2View view) {
        return new NexoriMenuV2State(
            view,
            statusText,
            pendingServerDisplayName,
            pendingServerAddress,
            editingServerAddress,
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withStatusText(@Nonnull String text) {
        return new NexoriMenuV2State(
            selectedView,
            text,
            pendingServerDisplayName,
            pendingServerAddress,
            editingServerAddress,
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withPendingServerDisplayName(@Nonnull String text) {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            text,
            pendingServerAddress,
            editingServerAddress,
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withPendingServerAddress(@Nonnull String text) {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            pendingServerDisplayName,
            text,
            editingServerAddress,
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withEditingServerAddress(@Nonnull String address) {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            pendingServerDisplayName,
            pendingServerAddress,
            address,
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State clearedPendingServerDraft() {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            "",
            "",
            "",
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withSelectedTravelIn(
        @Nonnull String connectionAddress,
        @Nonnull String portalId,
        @Nonnull String targetId,
        @Nonnull String displayName
    ) {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            pendingServerDisplayName,
            pendingServerAddress,
            editingServerAddress,
            connectionAddress,
            portalId,
            targetId,
            displayName,
            selectedTravelOutConnectionAddress,
            selectedTravelOutPortalId,
            selectedTravelOutTargetId,
            selectedTravelOutDisplayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withSelectedTravelOut(
        @Nonnull String connectionAddress,
        @Nonnull String portalId,
        @Nonnull String targetId,
        @Nonnull String displayName
    ) {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            pendingServerDisplayName,
            pendingServerAddress,
            editingServerAddress,
            selectedTravelInConnectionAddress,
            selectedTravelInPortalId,
            selectedTravelInTargetId,
            selectedTravelInDisplayName,
            connectionAddress,
            portalId,
            targetId,
            displayName
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State clearedTravelSelection() {
        return new NexoriMenuV2State(
            selectedView,
            statusText,
            pendingServerDisplayName,
            pendingServerAddress,
            editingServerAddress,
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            ""
        ).normalized();
    }
}
