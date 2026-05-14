package io.github.hyjn.nexori.plugin.backend.logic;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Pure result for backend assignment validation.
 */
public record BackendAssignmentValidationResult(
    boolean valid,
    String message,
    String assignmentType,
    String matchId,
    List<UUID> playerUuids,
    List<UUID> expectedPlayerUuids,
    List<BackfillPlayerTicket> backfillPlayerTickets
) {

    @Nonnull
    public static BackendAssignmentValidationResult valid(
        @Nonnull String assignmentType,
        @Nonnull String matchId,
        @Nonnull List<UUID> playerUuids,
        @Nonnull List<UUID> expectedPlayerUuids,
        @Nonnull List<BackfillPlayerTicket> backfillPlayerTickets
    ) {
        return new BackendAssignmentValidationResult(
            true,
            "",
            assignmentType,
            matchId,
            List.copyOf(playerUuids),
            List.copyOf(expectedPlayerUuids),
            List.copyOf(backfillPlayerTickets)
        );
    }

    @Nonnull
    public static BackendAssignmentValidationResult invalid(@Nonnull String message) {
        return new BackendAssignmentValidationResult(false, normalize(message), "", "", List.of(), List.of(), List.of());
    }

    public BackendAssignmentValidationResult {
        message = normalize(message);
        assignmentType = normalize(assignmentType);
        matchId = normalize(matchId);
        playerUuids = playerUuids == null ? List.of() : List.copyOf(playerUuids);
        expectedPlayerUuids = expectedPlayerUuids == null ? List.of() : List.copyOf(expectedPlayerUuids);
        backfillPlayerTickets = backfillPlayerTickets == null ? List.of() : List.copyOf(backfillPlayerTickets);
    }

    public record BackfillPlayerTicket(
        UUID playerUuid,
        String admissionReservationId,
        long admissionExpiresAtEpochMs
    ) {
        public BackfillPlayerTicket {
            admissionReservationId = normalize(admissionReservationId);
        }
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }
}
