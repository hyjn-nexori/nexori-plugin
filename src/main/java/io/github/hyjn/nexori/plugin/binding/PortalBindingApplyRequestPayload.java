package io.github.hyjn.nexori.plugin.binding;

public record PortalBindingApplyRequestPayload(
    String requestId,
    String sourcePortalId,
    String destinationConnectionAddress,
    String destinationTargetId,
    String travelProfileId,
    String contextJson
) {
}
