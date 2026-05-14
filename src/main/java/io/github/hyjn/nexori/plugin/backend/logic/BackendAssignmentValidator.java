package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPlayerPayload;
import io.github.hyjn.nexori.plugin.minigame.NexoriMatchIds;
import io.github.hyjn.nexori.plugin.minigame.PlayerUuidLists;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pure validator for backend assignment payload contracts.
 *
 * <p>BackendSyncService owns HTTP, persistence, ACK wiring, logging, and launch
 * side effects. This class only validates and normalizes assignment data.</p>
 */
public final class BackendAssignmentValidator {

    public static final String ASSIGNMENT_TYPE_INITIAL_MATCH = "INITIAL_MATCH";
    public static final String ASSIGNMENT_TYPE_BACKFILL = "BACKFILL";

    @Nonnull
    public BackendAssignmentValidationResult validate(BackendAssignmentPayload assignment) {
        if (assignment == null) {
            return BackendAssignmentValidationResult.invalid("Assignment is required.");
        }
        if (normalize(assignment.assignmentId()).isBlank()) {
            return BackendAssignmentValidationResult.invalid("Assignment assignmentId is required.");
        }

        String assignmentType;
        try {
            assignmentType = normalizeAssignmentType(assignment);
        } catch (IllegalArgumentException exception) {
            return BackendAssignmentValidationResult.invalid(exception.getMessage());
        }

        String legacyTypeError = validateLegacyType(assignment, assignmentType);
        if (!legacyTypeError.isBlank()) {
            return BackendAssignmentValidationResult.invalid(legacyTypeError);
        }
        if (ASSIGNMENT_TYPE_BACKFILL.equals(assignmentType)
            && normalize(assignment.targetConnectionAddress()).isBlank()) {
            return BackendAssignmentValidationResult.invalid("BACKFILL assignment requires targetConnectionAddress.");
        }

        List<UUID> assignmentPlayerUuids;
        List<BackendAssignmentValidationResult.BackfillPlayerTicket> backfillTickets = List.of();
        try {
            if (ASSIGNMENT_TYPE_BACKFILL.equals(assignmentType)) {
                backfillTickets = parseBackfillPlayerTickets(assignment.players());
                assignmentPlayerUuids = backfillTickets.stream()
                    .map(BackendAssignmentValidationResult.BackfillPlayerTicket::playerUuid)
                    .toList();
            } else {
                assignmentPlayerUuids = parsePlayerUuids(assignment.playerUuids());
            }
        } catch (IllegalArgumentException exception) {
            return BackendAssignmentValidationResult.invalid(exception.getMessage());
        }

        String normalizedMatchId;
        try {
            normalizedMatchId = normalizeBackendMatchId(assignment.matchId());
        } catch (IllegalArgumentException exception) {
            return BackendAssignmentValidationResult.invalid(exception.getMessage());
        }

        List<UUID> expectedPlayerUuids = List.of();
        if (ASSIGNMENT_TYPE_INITIAL_MATCH.equals(assignmentType)) {
            try {
                expectedPlayerUuids = parseExpectedPlayerUuids(assignment.expectedPlayerUuids());
            } catch (IllegalArgumentException exception) {
                return BackendAssignmentValidationResult.invalid(exception.getMessage());
            }
            if (!expectedPlayerUuids.isEmpty()
                && !PlayerUuidLists.isSubset(assignmentPlayerUuids, expectedPlayerUuids)) {
                return BackendAssignmentValidationResult.invalid("Assignment playerUuids must be a subset of expectedPlayerUuids.");
            }
        }

        return BackendAssignmentValidationResult.valid(
            assignmentType,
            normalizedMatchId,
            assignmentPlayerUuids,
            expectedPlayerUuids,
            backfillTickets
        );
    }

    @Nonnull
    public String normalizeAssignmentType(@Nonnull BackendAssignmentPayload assignment) {
        String rawAssignmentType = normalize(assignment.assignmentType()).toUpperCase();
        if (rawAssignmentType.isBlank()) {
            if ("CREATE_MATCH".equalsIgnoreCase(normalize(assignment.type()))) {
                return ASSIGNMENT_TYPE_INITIAL_MATCH;
            }
            throw new IllegalArgumentException("Assignment assignmentType is required unless legacy type is CREATE_MATCH.");
        }
        if (ASSIGNMENT_TYPE_INITIAL_MATCH.equals(rawAssignmentType) || ASSIGNMENT_TYPE_BACKFILL.equals(rawAssignmentType)) {
            return rawAssignmentType;
        }
        throw new IllegalArgumentException("Assignment assignmentType must be INITIAL_MATCH or BACKFILL.");
    }

    @Nonnull
    private String validateLegacyType(@Nonnull BackendAssignmentPayload assignment, @Nonnull String assignmentType) {
        if (ASSIGNMENT_TYPE_INITIAL_MATCH.equals(assignmentType)
            && !"CREATE_MATCH".equalsIgnoreCase(normalize(assignment.type()))) {
            return "Unsupported assignment type.";
        }
        if (ASSIGNMENT_TYPE_BACKFILL.equals(assignmentType)
            && !normalize(assignment.type()).isBlank()
            && !"JOIN_MATCH".equalsIgnoreCase(normalize(assignment.type()))
            && !"BACKFILL".equalsIgnoreCase(normalize(assignment.type()))) {
            return "Unsupported assignment type.";
        }
        return "";
    }

    @Nonnull
    private List<BackendAssignmentValidationResult.BackfillPlayerTicket> parseBackfillPlayerTickets(
        List<BackendAssignmentPlayerPayload> rawPlayers
    ) {
        if (rawPlayers == null || rawPlayers.isEmpty()) {
            throw new IllegalArgumentException("BACKFILL assignment must include players.");
        }
        Set<UUID> parsedPlayerUuids = new HashSet<>();
        Set<String> parsedReservationIds = new HashSet<>();
        List<BackendAssignmentValidationResult.BackfillPlayerTicket> tickets = new ArrayList<>();
        for (BackendAssignmentPlayerPayload rawPlayer : rawPlayers) {
            if (rawPlayer == null) {
                throw new IllegalArgumentException("BACKFILL assignment includes a null player ticket.");
            }
            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(normalize(rawPlayer.playerUuid()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("BACKFILL assignment includes an invalid player UUID.");
            }
            if (!parsedPlayerUuids.add(playerUuid)) {
                throw new IllegalArgumentException("BACKFILL assignment includes duplicate players.");
            }
            String reservationId = normalize(rawPlayer.admissionReservationId());
            if (reservationId.isBlank()) {
                throw new IllegalArgumentException("BACKFILL assignment requires one admissionReservationId per player.");
            }
            if (!parsedReservationIds.add(reservationId)) {
                throw new IllegalArgumentException("BACKFILL assignment includes duplicate admissionReservationIds.");
            }
            if (rawPlayer.admissionExpiresAtEpochMs() <= 0L) {
                throw new IllegalArgumentException("BACKFILL assignment requires a positive admissionExpiresAtEpochMs per player.");
            }
            tickets.add(new BackendAssignmentValidationResult.BackfillPlayerTicket(
                playerUuid,
                reservationId,
                rawPlayer.admissionExpiresAtEpochMs()
            ));
        }
        return List.copyOf(tickets);
    }

    @Nonnull
    private List<UUID> parsePlayerUuids(List<String> rawPlayerUuids) {
        if (rawPlayerUuids == null || rawPlayerUuids.isEmpty()) {
            throw new IllegalArgumentException("Assignment must include at least one player.");
        }
        Set<UUID> parsed = new HashSet<>();
        List<UUID> ordered = new ArrayList<>();
        for (String rawPlayerUuid : rawPlayerUuids) {
            UUID playerUuid;
            try {
                playerUuid = UUID.fromString(normalize(rawPlayerUuid));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Assignment includes an invalid player UUID.");
            }
            if (!parsed.add(playerUuid)) {
                throw new IllegalArgumentException("Assignment includes duplicate players.");
            }
            ordered.add(playerUuid);
        }
        return List.copyOf(ordered);
    }

    @Nonnull
    private List<UUID> parseExpectedPlayerUuids(List<String> rawPlayerUuids) {
        if (rawPlayerUuids == null || rawPlayerUuids.isEmpty()) {
            return List.of();
        }
        return PlayerUuidLists.canonicalize(parsePlayerUuids(rawPlayerUuids));
    }

    @Nonnull
    private String normalizeBackendMatchId(String rawMatchId) {
        try {
            return NexoriMatchIds.normalizeBackendOwnedMatchId(rawMatchId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Assignment matchId is invalid: " + exception.getMessage());
        }
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }
}
