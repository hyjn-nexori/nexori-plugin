package io.github.hyjn.nexori.plugin.backend.logic;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

/**
 * Pure plan for backend assignment processing decisions.
 */
public record BackendAssignmentProcessingPlan(
    @Nonnull Action action,
    @Nonnull String status,
    @Nonnull String localMatchId,
    @Nonnull String reason,
    @Nonnull String assignmentType,
    @Nonnull String matchId,
    @Nonnull List<UUID> playerUuids,
    @Nonnull List<UUID> expectedPlayerUuids,
    @Nonnull List<BackendAssignmentValidationResult.BackfillPlayerTicket> backfillPlayerTickets
) {

    public enum Action {
        IGNORE,
        REJECT_DUPLICATE,
        REJECT_VALIDATION,
        REJECT_ACTIVE_MATCH,
        LAUNCH_INITIAL,
        LAUNCH_BACKFILL,
        PERSIST_LAUNCH_RESULT
    }

    public BackendAssignmentProcessingPlan {
        status = normalize(status);
        localMatchId = normalize(localMatchId);
        reason = normalize(reason);
        assignmentType = normalize(assignmentType);
        matchId = normalize(matchId);
        playerUuids = playerUuids == null ? List.of() : List.copyOf(playerUuids);
        expectedPlayerUuids = expectedPlayerUuids == null ? List.of() : List.copyOf(expectedPlayerUuids);
        backfillPlayerTickets = backfillPlayerTickets == null ? List.of() : List.copyOf(backfillPlayerTickets);
    }

    @Nonnull
    static BackendAssignmentProcessingPlan ignore() {
        return new BackendAssignmentProcessingPlan(Action.IGNORE, "", "", "", "", "", List.of(), List.of(), List.of());
    }

    @Nonnull
    static BackendAssignmentProcessingPlan rejectDuplicate(@Nonnull String reason) {
        return rejected(Action.REJECT_DUPLICATE, reason);
    }

    @Nonnull
    static BackendAssignmentProcessingPlan rejectValidation(@Nonnull String reason) {
        return rejected(Action.REJECT_VALIDATION, reason);
    }

    @Nonnull
    static BackendAssignmentProcessingPlan rejectActiveMatch(@Nonnull String reason) {
        return rejected(Action.REJECT_ACTIVE_MATCH, reason);
    }

    @Nonnull
    static BackendAssignmentProcessingPlan launch(@Nonnull Action action, @Nonnull BackendAssignmentValidationResult validation) {
        return new BackendAssignmentProcessingPlan(
            action,
            "",
            "",
            "",
            validation.assignmentType(),
            validation.matchId(),
            validation.playerUuids(),
            validation.expectedPlayerUuids(),
            validation.backfillPlayerTickets()
        );
    }

    @Nonnull
    static BackendAssignmentProcessingPlan persistLaunchResult(
        @Nonnull String status,
        @Nonnull String localMatchId,
        @Nonnull String reason
    ) {
        return new BackendAssignmentProcessingPlan(
            Action.PERSIST_LAUNCH_RESULT,
            status,
            localMatchId,
            reason,
            "",
            "",
            List.of(),
            List.of(),
            List.of()
        );
    }

    @Nonnull
    private static BackendAssignmentProcessingPlan rejected(@Nonnull Action action, @Nonnull String reason) {
        return new BackendAssignmentProcessingPlan(action, "REJECTED", "", reason, "", "", List.of(), List.of(), List.of());
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }
}
