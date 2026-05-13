package io.github.hyjn.nexori.plugin.backend.payload;

public record BackendAssignmentPlayerPayload(
    String playerUuid,
    String admissionReservationId,
    long admissionExpiresAtEpochMs
) {
}
