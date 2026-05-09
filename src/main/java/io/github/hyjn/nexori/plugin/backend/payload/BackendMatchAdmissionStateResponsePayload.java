package io.github.hyjn.nexori.plugin.backend.payload;

public record BackendMatchAdmissionStateResponsePayload(
    int schemaVersion,
    String receivedStateUpdateId,
    long receivedAdmissionStateSequence,
    String status
) {
}
