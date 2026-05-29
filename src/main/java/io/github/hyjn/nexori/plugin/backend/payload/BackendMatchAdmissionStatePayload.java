package io.github.hyjn.nexori.plugin.backend.payload;

import java.util.List;

public record BackendMatchAdmissionStatePayload(
    int schemaVersion,
    String stateUpdateId,
    long admissionStateSequence,
    String payloadHash,
    long sentAtEpochMs,
    long stateExpiresAtEpochMs,
    String reportingServerId,
    /**
     * Connection address of the server that owns the match (the minigame/game server).
     * Included so the backend can build valid backfill assignment {@code targetConnectionAddress}
     * values even when that server has sync disabled.
     * Optional — blank for servers that have not yet sent this field.
     */
    String reportingServerConnectionAddress,
    String matchId,
    String externalMatchId,
    String queueId,
    String arenaId,
    boolean backfillEnabled,
    String backfillMode,
    int backfillWindowSeconds,
    String matchLifecycleStatus,
    boolean admissionOpen,
    long admissionOpenUntilEpochMs,
    int admissionCapacity,
    int admittedSlotCount,
    int availableAdmissionSlots,
    int initialRosterSize,
    int arrivedInitialPlayerCount,
    int unfilledInitialRosterCount,
    List<String> consumedAdmissionReservationIds,
    boolean admissionReportingClosed,
    String admissionReportingCloseReason,
    String primaryChangeReason,
    List<String> coalescedChangeReasons
) {
}
