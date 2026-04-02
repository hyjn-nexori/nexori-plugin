package io.github.hyjn.nexori.plugin.travel;

public record PendingArrival(
    String travelOperationId,
    String sourceServerId,
    String sourceConnectionAddress,
    String destinationTargetId,
    String destinationTargetDisplayName,
    String destinationTargetKind,
    String worldName,
    String arrivalPointId,
    String travelProfileId,
    String arrivalMessage,
    String contextJson,
    String metadataJson
) {
}
