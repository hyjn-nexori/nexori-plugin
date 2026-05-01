package io.github.hyjn.nexori.plugin.backend.payload;

public record BackendAssignmentAckPayload(
    String ackId,
    String assignmentId,
    String externalMatchId,
    String status,
    String localMatchId,
    String reason,
    long createdAtEpochMs
) {
}
