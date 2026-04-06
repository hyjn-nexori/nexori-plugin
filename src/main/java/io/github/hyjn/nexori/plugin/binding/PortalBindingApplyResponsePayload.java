package io.github.hyjn.nexori.plugin.binding;

public record PortalBindingApplyResponsePayload(
    String requestId,
    boolean success,
    String message
) {
}
