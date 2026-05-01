package io.github.hyjn.nexori.plugin.backend.payload;

import java.util.List;

public record BackendSyncResponsePayload(
    int schemaVersion,
    long receivedSequence,
    List<String> acknowledgedAssignmentAckIds,
    List<BackendAssignmentPayload> assignments
) {
}
