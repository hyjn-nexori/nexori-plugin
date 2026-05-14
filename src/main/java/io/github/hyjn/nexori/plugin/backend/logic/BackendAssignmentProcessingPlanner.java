package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;

import javax.annotation.Nonnull;

/**
 * Pure planner for backend assignment processing.
 *
 * <p>The backend sync service owns persistence, hashing, validation, runtime active-match checks
 * and launch side effects. This class only chooses the next processing action from those inputs.</p>
 */
public final class BackendAssignmentProcessingPlanner {

    public static final String DUPLICATE_PAYLOAD_REUSE_REASON = "assignmentId reused with different payload";

    @Nonnull
    public BackendAssignmentProcessingPlan planBeforeLaunch(
        BackendAssignmentPayload assignment,
        @Nonnull String assignmentHash,
        String existingAssignmentHash,
        BackendAssignmentValidationResult validation,
        String activeMatchValidationError
    ) {
        if (assignment == null || normalize(assignment.assignmentId()).isBlank()) {
            return BackendAssignmentProcessingPlan.ignore();
        }
        String normalizedExistingHash = normalize(existingAssignmentHash);
        if (!normalizedExistingHash.isBlank()) {
            if (normalizedExistingHash.equals(assignmentHash)) {
                return BackendAssignmentProcessingPlan.ignore();
            }
            return BackendAssignmentProcessingPlan.rejectDuplicate(DUPLICATE_PAYLOAD_REUSE_REASON);
        }
        if (validation == null || !validation.valid()) {
            String reason = validation == null ? "Assignment validation failed." : validation.message();
            return BackendAssignmentProcessingPlan.rejectValidation(reason);
        }
        String activeMatchError = normalize(activeMatchValidationError);
        if (!activeMatchError.isBlank()) {
            return BackendAssignmentProcessingPlan.rejectActiveMatch(activeMatchError);
        }
        if (BackendAssignmentValidator.ASSIGNMENT_TYPE_BACKFILL.equals(validation.assignmentType())) {
            return BackendAssignmentProcessingPlan.launch(BackendAssignmentProcessingPlan.Action.LAUNCH_BACKFILL, validation);
        }
        return BackendAssignmentProcessingPlan.launch(BackendAssignmentProcessingPlan.Action.LAUNCH_INITIAL, validation);
    }

    @Nonnull
    public BackendAssignmentProcessingPlan planLaunchResult(
        @Nonnull String status,
        String localMatchId,
        String reason
    ) {
        return BackendAssignmentProcessingPlan.persistLaunchResult(status, localMatchId, reason);
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }
}
