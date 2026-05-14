package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Pure admission policy for backend-driven backfill arrivals.
 *
 * <p>This class intentionally has no Hytale runtime dependencies. Runtime
 * services gather the launch/match data, call this decider, then perform side
 * effects such as logging, storing match state, travel, and reporting.</p>
 */
public final class BackfillAdmissionDecider {

    public static final String ASSIGNMENT_TYPE_BACKFILL = "BACKFILL";

    public static final String REASON_MISSING_MATCH = "MISSING_MATCH";
    public static final String REASON_WRONG_ASSIGNMENT_TYPE = "WRONG_ASSIGNMENT_TYPE";
    public static final String REASON_PLAYER_UUID_MISMATCH = "PLAYER_UUID_MISMATCH";
    public static final String REASON_MISSING_ADMISSION_RESERVATION_ID = "MISSING_ADMISSION_RESERVATION_ID";
    public static final String REASON_EXPIRED_RESERVATION = "EXPIRED_RESERVATION";
    public static final String REASON_NOT_BACKEND_DRIVEN = "NOT_BACKEND_DRIVEN";
    public static final String REASON_EXPLICIT_ADMISSION_CLOSED = "EXPLICIT_ADMISSION_CLOSED";
    public static final String REASON_ADMISSION_CLOSED = "ADMISSION_CLOSED";
    public static final String REASON_CAPACITY_FULL = "CAPACITY_FULL";
    public static final String REASON_DUPLICATE_RESERVATION = "DUPLICATE_RESERVATION";

    public static final String MESSAGE_MISSING_MATCH = "backfill launch requires an existing match";
    public static final String MESSAGE_WRONG_ASSIGNMENT_TYPE = "assignmentType is not BACKFILL";
    public static final String MESSAGE_PLAYER_UUID_MISMATCH = "playerUuid does not match the backfill ticket owner";
    public static final String MESSAGE_MISSING_ADMISSION_RESERVATION_ID = "admissionReservationId is required";
    public static final String MESSAGE_EXPIRED_RESERVATION = "admission reservation expired";
    public static final String MESSAGE_NOT_BACKEND_DRIVEN = "match is not BACKEND_DRIVEN";
    public static final String MESSAGE_EXPLICIT_ADMISSION_CLOSED = "admission was explicitly closed";
    public static final String MESSAGE_ADMISSION_CLOSED = "match is no longer accepting backfill arrivals";
    public static final String MESSAGE_CAPACITY_FULL = "admission capacity has already been reached";
    public static final String MESSAGE_DUPLICATE_RESERVATION = "admission reservation was already accepted";

    @Nonnull
    public BackfillAdmissionDecision decide(
        ArenaActiveMatch existing,
        String assignmentType,
        UUID ticketPlayerUuid,
        @Nonnull UUID arrivingPlayerUuid,
        String admissionReservationId,
        long admissionExpiresAtEpochMs,
        long nowEpochMs
    ) {
        String normalizedReservationId = normalizeReservationId(admissionReservationId);
        if (existing == null) {
            return reject(REASON_MISSING_MATCH, MESSAGE_MISSING_MATCH, normalizedReservationId);
        }
        if (!ASSIGNMENT_TYPE_BACKFILL.equalsIgnoreCase(normalizeOptional(assignmentType))) {
            return reject(REASON_WRONG_ASSIGNMENT_TYPE, MESSAGE_WRONG_ASSIGNMENT_TYPE, normalizedReservationId);
        }
        if (ticketPlayerUuid == null || !ticketPlayerUuid.equals(arrivingPlayerUuid)) {
            return reject(REASON_PLAYER_UUID_MISMATCH, MESSAGE_PLAYER_UUID_MISMATCH, normalizedReservationId);
        }
        if (normalizedReservationId.isBlank()) {
            return reject(REASON_MISSING_ADMISSION_RESERVATION_ID, MESSAGE_MISSING_ADMISSION_RESERVATION_ID, normalizedReservationId);
        }
        if (admissionExpiresAtEpochMs <= 0L || nowEpochMs > admissionExpiresAtEpochMs) {
            return reject(REASON_EXPIRED_RESERVATION, MESSAGE_EXPIRED_RESERVATION, normalizedReservationId);
        }
        if (existing.effectiveMatchSource() != ArenaMatchSource.BACKEND_DRIVEN) {
            return reject(REASON_NOT_BACKEND_DRIVEN, MESSAGE_NOT_BACKEND_DRIVEN, normalizedReservationId);
        }
        if (existing.explicitAdmissionClosed()) {
            return reject(REASON_EXPLICIT_ADMISSION_CLOSED, MESSAGE_EXPLICIT_ADMISSION_CLOSED, normalizedReservationId);
        }
        if (!isBackfillAdmissionOpen(existing, nowEpochMs)) {
            return reject(REASON_ADMISSION_CLOSED, MESSAGE_ADMISSION_CLOSED, normalizedReservationId);
        }
        int initialRosterSize = Math.max(existing.expectedPlayerCount(), existing.expectedPlayerUuids().size());
        int admittedSlotCount = Math.min(existing.admissionCapacity(), initialRosterSize + existing.consumedBackfillAdmissionCount());
        if (admittedSlotCount >= existing.admissionCapacity()) {
            return reject(REASON_CAPACITY_FULL, MESSAGE_CAPACITY_FULL, normalizedReservationId);
        }
        if (existing.hasAcceptedBackfillReservation(normalizedReservationId)) {
            return reject(REASON_DUPLICATE_RESERVATION, MESSAGE_DUPLICATE_RESERVATION, normalizedReservationId);
        }
        return BackfillAdmissionDecision.allow(normalizedReservationId);
    }

    public boolean isBackfillAdmissionOpen(@Nonnull ArenaActiveMatch match, long nowEpochMs) {
        return switch (match.effectiveBackfillMode()) {
            case NONE -> false;
            case PLACEMENT_ONLY -> match.placementCompletedAtEpochMs() <= 0L;
            case ACTIVE_WINDOW -> {
                if (match.placementCompletedAtEpochMs() <= 0L) {
                    yield true;
                }
                long startedAtEpochMs = match.matchStartedAtEpochMs();
                if (startedAtEpochMs <= 0L) {
                    yield false;
                }
                long closesAtEpochMs = startedAtEpochMs + Math.max(match.backfillWindowSeconds(), 0) * 1000L;
                yield closesAtEpochMs > 0L && nowEpochMs <= closesAtEpochMs;
            }
        };
    }

    @Nonnull
    private static BackfillAdmissionDecision reject(@Nonnull String reasonCode, @Nonnull String reason, @Nonnull String reservationId) {
        return BackfillAdmissionDecision.reject(reasonCode, reason, reservationId);
    }

    @Nonnull
    private static String normalizeReservationId(String rawValue) {
        return normalizeOptional(rawValue);
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
