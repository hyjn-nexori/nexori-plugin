package io.github.hyjn.nexori.plugin.minigame;

public record LobbyRoleApplyRequestPayload(
    String requestId,
    boolean configured,
    String lobbyConnectionAddress,
    String lobbyWorldName,
    String entryTargetId,
    String returnTargetId
) {
}
