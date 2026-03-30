package io.github.hyjn.nexori.plugin.policy;

public record ServerPolicyApplyRequestPayload(
    String requestId,
    boolean recoveryEnabled,
    int maxBackupsPerPlayer
) {
}
