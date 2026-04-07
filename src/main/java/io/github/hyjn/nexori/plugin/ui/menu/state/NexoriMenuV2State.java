package io.github.hyjn.nexori.plugin.ui.menu.state;

import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2View;

import javax.annotation.Nonnull;

public record NexoriMenuV2State(
    NexoriMenuV2View selectedView,
    PortalWorkspaceTab selectedPortalTab,
    MinigameWorkspaceTab selectedMinigameTab,
    String selectedTravelProfileId,
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
    String selectedTravelOutDisplayName,
    String pendingLobbyWorldName,
    String editingDestinationId,
    String pendingDestinationDisplayName,
    String pendingDestinationConnectionAddress,
    String pendingDestinationTargetId,
    String pendingDestinationInstanceTemplateId,
    String pendingDestinationTriggerId,
    String pendingDestinationMaxPlayers,
    String editingQueueId,
    String pendingQueueDisplayName,
    String pendingQueueDestinationId,
    String pendingQueueMinPlayers,
    String pendingQueueMaxPlayers,
    String pendingQueueCountdownSeconds
) {

    @Nonnull
    public NexoriMenuV2State normalized() {
        return new NexoriMenuV2State(
            selectedView == null ? NexoriMenuV2View.HOME : selectedView,
            selectedPortalTab == null ? PortalWorkspaceTab.BIND : selectedPortalTab,
            selectedMinigameTab == null ? MinigameWorkspaceTab.LOBBY : selectedMinigameTab,
            selectedTravelProfileId == null || selectedTravelProfileId.isBlank() ? "keep_inventory" : selectedTravelProfileId.trim().toLowerCase(),
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
            selectedTravelOutDisplayName == null ? "" : selectedTravelOutDisplayName.trim(),
            pendingLobbyWorldName == null ? "" : pendingLobbyWorldName.trim().toLowerCase(),
            editingDestinationId == null ? "" : editingDestinationId.trim().toLowerCase(),
            pendingDestinationDisplayName == null ? "" : pendingDestinationDisplayName.trim(),
            pendingDestinationConnectionAddress == null ? "" : pendingDestinationConnectionAddress.trim().toLowerCase(),
            pendingDestinationTargetId == null ? "" : pendingDestinationTargetId.trim().toLowerCase(),
            pendingDestinationInstanceTemplateId == null ? "" : pendingDestinationInstanceTemplateId.trim(),
            pendingDestinationTriggerId == null || pendingDestinationTriggerId.isBlank() ? "last_player_alive" : pendingDestinationTriggerId.trim().toLowerCase(),
            pendingDestinationMaxPlayers == null || pendingDestinationMaxPlayers.isBlank() ? "8" : pendingDestinationMaxPlayers.trim(),
            editingQueueId == null ? "" : editingQueueId.trim().toLowerCase(),
            pendingQueueDisplayName == null ? "" : pendingQueueDisplayName.trim(),
            pendingQueueDestinationId == null ? "" : pendingQueueDestinationId.trim().toLowerCase(),
            pendingQueueMinPlayers == null || pendingQueueMinPlayers.isBlank() ? "2" : pendingQueueMinPlayers.trim(),
            pendingQueueMaxPlayers == null || pendingQueueMaxPlayers.isBlank() ? "8" : pendingQueueMaxPlayers.trim(),
            pendingQueueCountdownSeconds == null || pendingQueueCountdownSeconds.isBlank() ? "15" : pendingQueueCountdownSeconds.trim()
        );
    }

    @Nonnull
    public static NexoriMenuV2State initial() {
        return new NexoriMenuV2State(
            NexoriMenuV2View.HOME,
            PortalWorkspaceTab.BIND,
            MinigameWorkspaceTab.LOBBY,
            "keep_inventory",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "last_player_alive",
            "8",
            "",
            "",
            "",
            "2",
            "8",
            "15"
        ).normalized();
    }

    @Nonnull
    public NexoriMenuV2State withSelectedView(@Nonnull NexoriMenuV2View view) {
        return copy(view, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withSelectedPortalTab(@Nonnull PortalWorkspaceTab tab) {
        return copy(selectedView, tab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withSelectedMinigameTab(@Nonnull MinigameWorkspaceTab tab) {
        return copy(selectedView, selectedPortalTab, tab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withSelectedTravelProfileId(@Nonnull String travelProfileId) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, travelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withStatusText(@Nonnull String text) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, text, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withPendingServerDisplayName(@Nonnull String text) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, text, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withPendingServerAddress(@Nonnull String text) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, text, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withEditingServerAddress(@Nonnull String address) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, address, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State clearedPendingServerDraft() {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, "", "", "", selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withSelectedTravelIn(@Nonnull String connectionAddress, @Nonnull String portalId, @Nonnull String targetId, @Nonnull String displayName) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, connectionAddress, portalId, targetId, displayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withSelectedTravelOut(@Nonnull String connectionAddress, @Nonnull String portalId, @Nonnull String targetId, @Nonnull String displayName) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, connectionAddress, portalId, targetId, displayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State clearedTravelSelection() {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, "", "", "", "", "", "", "", "", pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withPendingLobbyWorldName(@Nonnull String worldName) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, worldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withDestinationDraft(@Nonnull String displayName, @Nonnull String connectionAddress, @Nonnull String targetId, @Nonnull String instanceTemplateId, @Nonnull String triggerId, @Nonnull String maxPlayers) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, displayName, connectionAddress, targetId, instanceTemplateId, triggerId, maxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withEditingDestinationId(@Nonnull String destinationId) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, destinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State clearedDestinationDraft() {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, "", "", "", "", "", "last_player_alive", "8", editingQueueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withQueueDraft(@Nonnull String displayName, @Nonnull String destinationId, @Nonnull String minPlayers, @Nonnull String maxPlayers, @Nonnull String countdownSeconds) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, editingQueueId, displayName, destinationId, minPlayers, maxPlayers, countdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State withEditingQueueId(@Nonnull String queueId) {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, queueId, pendingQueueDisplayName, pendingQueueDestinationId, pendingQueueMinPlayers, pendingQueueMaxPlayers, pendingQueueCountdownSeconds);
    }

    @Nonnull
    public NexoriMenuV2State clearedQueueDraft() {
        return copy(selectedView, selectedPortalTab, selectedMinigameTab, selectedTravelProfileId, statusText, pendingServerDisplayName, pendingServerAddress, editingServerAddress, selectedTravelInConnectionAddress, selectedTravelInPortalId, selectedTravelInTargetId, selectedTravelInDisplayName, selectedTravelOutConnectionAddress, selectedTravelOutPortalId, selectedTravelOutTargetId, selectedTravelOutDisplayName, pendingLobbyWorldName, editingDestinationId, pendingDestinationDisplayName, pendingDestinationConnectionAddress, pendingDestinationTargetId, pendingDestinationInstanceTemplateId, pendingDestinationTriggerId, pendingDestinationMaxPlayers, "", "", "", "2", "8", "15");
    }

    @Nonnull
    private NexoriMenuV2State copy(
        NexoriMenuV2View selectedView,
        PortalWorkspaceTab selectedPortalTab,
        MinigameWorkspaceTab selectedMinigameTab,
        String selectedTravelProfileId,
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
        String selectedTravelOutDisplayName,
        String pendingLobbyWorldName,
        String editingDestinationId,
        String pendingDestinationDisplayName,
        String pendingDestinationConnectionAddress,
        String pendingDestinationTargetId,
        String pendingDestinationInstanceTemplateId,
        String pendingDestinationTriggerId,
        String pendingDestinationMaxPlayers,
        String editingQueueId,
        String pendingQueueDisplayName,
        String pendingQueueDestinationId,
        String pendingQueueMinPlayers,
        String pendingQueueMaxPlayers,
        String pendingQueueCountdownSeconds
    ) {
        return new NexoriMenuV2State(
            selectedView,
            selectedPortalTab,
            selectedMinigameTab,
            selectedTravelProfileId,
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
            selectedTravelOutDisplayName,
            pendingLobbyWorldName,
            editingDestinationId,
            pendingDestinationDisplayName,
            pendingDestinationConnectionAddress,
            pendingDestinationTargetId,
            pendingDestinationInstanceTemplateId,
            pendingDestinationTriggerId,
            pendingDestinationMaxPlayers,
            editingQueueId,
            pendingQueueDisplayName,
            pendingQueueDestinationId,
            pendingQueueMinPlayers,
            pendingQueueMaxPlayers,
            pendingQueueCountdownSeconds
        ).normalized();
    }
}
