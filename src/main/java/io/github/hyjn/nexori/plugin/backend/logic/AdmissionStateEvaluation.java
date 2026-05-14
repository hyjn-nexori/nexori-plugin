package io.github.hyjn.nexori.plugin.backend.logic;

/**
 * Pure evaluation output for backend match admission state reporting.
 */
public record AdmissionStateEvaluation(
    String matchLifecycleStatus,
    boolean admissionOpen,
    long admissionOpenUntilEpochMs,
    int admissionCapacity,
    int admittedSlotCount,
    int availableAdmissionSlots,
    int initialRosterSize,
    int arrivedInitialPlayerCount,
    int unfilledInitialRosterCount,
    boolean admissionReportingClosed,
    String admissionReportingCloseReason,
    String primaryChangeReason,
    boolean initialRosterExceedsAdmissionCapacity
) {
}
