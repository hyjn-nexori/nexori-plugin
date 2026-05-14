package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.UUID;

/**
 * Pure admission-state evaluator for backend-driven match reporting.
 *
 * <p>Scheduling, placement lookup, HTTP, retry, ACK handling, logging, and
 * payload publication remain owned by BackendMatchAdmissionStateReportingService.</p>
 */
public final class AdmissionStateEvaluator {

    public static final String CHANGE_REASON_MATCH_STARTED = "MATCH_STARTED";
    public static final String CHANGE_REASON_ADMISSION_WINDOW_EXPIRED = "ADMISSION_WINDOW_EXPIRED";
    public static final String CHANGE_REASON_ADMISSION_CLOSED = "ADMISSION_CLOSED";
    public static final String CLOSE_REASON_WINDOW_EXPIRED = "BACKFILL_WINDOW_EXPIRED";
    public static final String CLOSE_REASON_NO_LONGER_ACCEPTING = "MATCH_NO_LONGER_ACCEPTING_PLAYERS";
    public static final String CLOSE_REASON_NOT_REPORTABLE = "MATCH_NOT_REPORTABLE";
    public static final String STATUS_PLACEMENT = "PLACEMENT";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Nonnull
    public AdmissionStateEvaluation evaluate(
        @Nonnull ArenaActiveMatch match,
        boolean placementComplete,
        long nowEpochMs,
        String primaryChangeReason
    ) {
        int admissionCapacity = Math.max(match.admissionCapacity(), 0);
        int initialRosterSize = Math.max(match.expectedPlayerCount(), match.expectedPlayerUuids().size());
        int arrivedInitialPlayerCount = countArrivedInitialPlayers(match);
        int admittedSlotCount = Math.min(admissionCapacity, initialRosterSize + Math.max(match.consumedBackfillAdmissionCount(), 0));
        int unfilledInitialRosterCount = Math.max(0, initialRosterSize - arrivedInitialPlayerCount);
        boolean initialRosterExceedsAdmissionCapacity = initialRosterSize > admissionCapacity;
        String lifecycleStatus = lifecycleStatus(placementComplete);
        QueueBackfillMode mode = match.effectiveBackfillMode();
        long admissionOpenUntilEpochMs = 0L;
        boolean admissionOpen = false;
        boolean admissionReportingClosed = false;
        String closeReason = "";
        int availableAdmissionSlots = 0;

        if (match.explicitAdmissionClosed()) {
            return new AdmissionStateEvaluation(
                lifecycleStatus,
                false,
                0L,
                admissionCapacity,
                admittedSlotCount,
                0,
                initialRosterSize,
                arrivedInitialPlayerCount,
                unfilledInitialRosterCount,
                true,
                normalizeOptional(match.explicitAdmissionCloseReason()),
                normalizeOptional(match.explicitAdmissionCloseReason()),
                initialRosterExceedsAdmissionCapacity
            );
        }

        switch (mode) {
            case NONE -> {
                availableAdmissionSlots = 0;
                if (STATUS_ACTIVE.equals(lifecycleStatus)) {
                    admissionReportingClosed = true;
                    closeReason = CLOSE_REASON_NO_LONGER_ACCEPTING;
                }
            }
            case PLACEMENT_ONLY -> {
                admissionOpen = !placementComplete;
                availableAdmissionSlots = admissionOpen ? Math.max(0, admissionCapacity - admittedSlotCount) : 0;
                if (placementComplete) {
                    admissionReportingClosed = true;
                    closeReason = CLOSE_REASON_NO_LONGER_ACCEPTING;
                }
            }
            case ACTIVE_WINDOW -> {
                if (STATUS_PLACEMENT.equals(lifecycleStatus)) {
                    admissionOpen = true;
                    admissionOpenUntilEpochMs = 0L;
                    availableAdmissionSlots = Math.max(0, admissionCapacity - admittedSlotCount);
                } else {
                    long startedAtEpochMs = match.matchStartedAtEpochMs();
                    admissionOpenUntilEpochMs = startedAtEpochMs > 0L
                        ? startedAtEpochMs + Math.max(match.backfillWindowSeconds(), 0) * 1000L
                        : 0L;
                    admissionOpen = admissionOpenUntilEpochMs > 0L && nowEpochMs <= admissionOpenUntilEpochMs;
                    availableAdmissionSlots = admissionOpen ? Math.max(0, admissionCapacity - admittedSlotCount) : 0;
                    if (!admissionOpen && admissionOpenUntilEpochMs > 0L) {
                        admissionReportingClosed = true;
                        closeReason = CLOSE_REASON_WINDOW_EXPIRED;
                    } else if (admissionOpen && availableAdmissionSlots == 0) {
                        admissionOpen = false;
                        admissionReportingClosed = true;
                        closeReason = CLOSE_REASON_NO_LONGER_ACCEPTING;
                        availableAdmissionSlots = 0;
                    }
                }
            }
        }

        if (admissionReportingClosed && closeReason.isBlank()) {
            closeReason = CLOSE_REASON_NOT_REPORTABLE;
        }

        String primaryReason = normalizeOptional(primaryChangeReason);
        if (admissionReportingClosed) {
            if (CLOSE_REASON_WINDOW_EXPIRED.equals(closeReason)) {
                primaryReason = CHANGE_REASON_ADMISSION_WINDOW_EXPIRED;
            } else {
                primaryReason = CHANGE_REASON_ADMISSION_CLOSED;
            }
        }

        return new AdmissionStateEvaluation(
            lifecycleStatus,
            admissionOpen,
            admissionOpenUntilEpochMs,
            admissionCapacity,
            admittedSlotCount,
            availableAdmissionSlots,
            initialRosterSize,
            arrivedInitialPlayerCount,
            unfilledInitialRosterCount,
            admissionReportingClosed,
            closeReason,
            primaryReason,
            initialRosterExceedsAdmissionCapacity
        );
    }

    @Nonnull
    private static String lifecycleStatus(boolean placementComplete) {
        return placementComplete ? STATUS_ACTIVE : STATUS_PLACEMENT;
    }

    private static int countArrivedInitialPlayers(@Nonnull ArenaActiveMatch match) {
        if (match.expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        int arrivedInitialPlayers = 0;
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            if (expected.contains(playerUuid)) {
                arrivedInitialPlayers++;
            }
        }
        return arrivedInitialPlayers;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
