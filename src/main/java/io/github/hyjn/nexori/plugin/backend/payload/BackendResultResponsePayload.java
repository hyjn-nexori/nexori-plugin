package io.github.hyjn.nexori.plugin.backend.payload;

public record BackendResultResponsePayload(
    int schemaVersion,
    String receivedResultId,
    String status
) {
}
