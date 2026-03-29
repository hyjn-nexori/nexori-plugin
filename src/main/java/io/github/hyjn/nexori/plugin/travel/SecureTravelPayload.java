package io.github.hyjn.nexori.plugin.travel;

public record SecureTravelPayload(
    String sourceServerId,
    String sourceConnectionAddress,
    String destinationTargetId,
    String arrivalPointId,
    String travelProfileId,
    String arrivalMessage,
    String contextJson
) {
}
