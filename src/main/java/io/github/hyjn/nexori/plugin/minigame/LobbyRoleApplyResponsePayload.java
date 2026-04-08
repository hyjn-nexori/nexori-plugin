package io.github.hyjn.nexori.plugin.minigame;

public record LobbyRoleApplyResponsePayload(
    String requestId,
    boolean success,
    String message
) {
}
