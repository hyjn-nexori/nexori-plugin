package io.github.hyjn.nexori.plugin.backend.payload;

public record BackendResultPlayerPayload(
    String playerUuid,
    String outcome,
    String reason
) {
}
