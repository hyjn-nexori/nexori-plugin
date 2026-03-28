package io.github.hyjn.nexori.plugin.travel;

public record SecureTravelPayload(
    String sourceServerId,
    String sourceConnectionAddress,
    String routeKey,
    String entryPointId,
    String arrivalMessage,
    String contextJson
) {
}
