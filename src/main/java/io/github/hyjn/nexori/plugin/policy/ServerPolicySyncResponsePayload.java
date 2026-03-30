package io.github.hyjn.nexori.plugin.policy;

public record ServerPolicySyncResponsePayload(
    String requestId,
    boolean recoveryEnabled,
    int maxBackupsPerPlayer
) {
}
