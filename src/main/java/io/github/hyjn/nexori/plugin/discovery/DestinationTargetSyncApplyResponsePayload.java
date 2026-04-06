package io.github.hyjn.nexori.plugin.discovery;

public record DestinationTargetSyncApplyResponsePayload(
    String requestId,
    boolean success,
    String message
) {
}
