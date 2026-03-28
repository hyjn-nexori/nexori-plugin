package io.github.hyjn.nexori.plugin.travel;

public record PendingArrival(
    String sourceServerId,
    String sourceConnectionAddress,
    String routeKey,
    String entryPointId,
    String arrivalMessage,
    String contextJson
) {
}
