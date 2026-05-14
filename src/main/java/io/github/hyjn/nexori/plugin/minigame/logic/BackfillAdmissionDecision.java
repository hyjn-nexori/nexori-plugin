package io.github.hyjn.nexori.plugin.minigame.logic;

import javax.annotation.Nonnull;

/**
 * Pure result for deciding whether a backend backfill launch ticket may join an
 * existing arena match.
 */
public record BackfillAdmissionDecision(
    boolean allowed,
    String reasonCode,
    String reason,
    String admissionReservationId
) {

    @Nonnull
    public static BackfillAdmissionDecision allow(@Nonnull String admissionReservationId) {
        return new BackfillAdmissionDecision(true, "", "", admissionReservationId);
    }

    @Nonnull
    public static BackfillAdmissionDecision reject(@Nonnull String reasonCode, @Nonnull String reason, @Nonnull String admissionReservationId) {
        return new BackfillAdmissionDecision(false, reasonCode, reason, admissionReservationId);
    }

    public boolean rejected() {
        return !allowed;
    }

    public BackfillAdmissionDecision {
        reasonCode = normalizeOptional(reasonCode);
        reason = normalizeOptional(reason);
        admissionReservationId = normalizeOptional(admissionReservationId);
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
