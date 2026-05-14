package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendAssignmentProcessingPlannerTest {

    private static final String ASSIGNMENT_HASH = "sha256:assignment";
    private static final String EXISTING_HASH = "sha256:existing";
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final BackendAssignmentProcessingPlanner planner = new BackendAssignmentProcessingPlanner();

    @Test
    void ignoresNullAssignment() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(null, ASSIGNMENT_HASH, "", null, "");

        assertEquals(BackendAssignmentProcessingPlan.Action.IGNORE, plan.action());
    }

    @Test
    void ignoresBlankAssignmentId() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("   "),
            ASSIGNMENT_HASH,
            "",
            initialValidation(),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.IGNORE, plan.action());
    }

    @Test
    void ignoresDuplicateAssignmentWithSameHash() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            ASSIGNMENT_HASH,
            initialValidation(),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.IGNORE, plan.action());
    }

    @Test
    void sameHashDuplicateIgnoresEvenWhenValidationFails() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            ASSIGNMENT_HASH,
            BackendAssignmentValidationResult.invalid("Should not win."),
            "Should not win either."
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.IGNORE, plan.action());
    }

    @Test
    void rejectsDuplicateAssignmentWithDifferentHash() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            EXISTING_HASH,
            initialValidation(),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.REJECT_DUPLICATE, plan.action());
        assertEquals("REJECTED", plan.status());
        assertEquals(BackendAssignmentProcessingPlanner.DUPLICATE_PAYLOAD_REUSE_REASON, plan.reason());
    }

    @Test
    void rejectsInvalidValidationResult() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            BackendAssignmentValidationResult.invalid("Assignment must include at least one player."),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.REJECT_VALIDATION, plan.action());
        assertEquals("REJECTED", plan.status());
        assertEquals("Assignment must include at least one player.", plan.reason());
    }

    @Test
    void invalidValidationWinsWhenNoDuplicateExists() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            BackendAssignmentValidationResult.invalid("Assignment validation failed first."),
            "Should not win."
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.REJECT_VALIDATION, plan.action());
        assertEquals("Assignment validation failed first.", plan.reason());
    }

    @Test
    void rejectsActiveMatchValidationError() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            initialValidation(),
            "Player already active."
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.REJECT_ACTIVE_MATCH, plan.action());
        assertEquals("REJECTED", plan.status());
        assertEquals("Player already active.", plan.reason());
    }

    @Test
    void plansInitialMatchLaunchForValidInitialAssignment() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            initialValidation(),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.LAUNCH_INITIAL, plan.action());
        assertEquals(BackendAssignmentValidator.ASSIGNMENT_TYPE_INITIAL_MATCH, plan.assignmentType());
        assertEquals("match-1", plan.matchId());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), plan.playerUuids());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), plan.expectedPlayerUuids());
    }

    @Test
    void plansBackfillLaunchForValidBackfillAssignment() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            backfillValidation(),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.LAUNCH_BACKFILL, plan.action());
        assertEquals(BackendAssignmentValidator.ASSIGNMENT_TYPE_BACKFILL, plan.assignmentType());
        assertEquals("match-1", plan.matchId());
        assertEquals(List.of(PLAYER_ONE), plan.playerUuids());
        assertEquals(1, plan.backfillPlayerTickets().size());
        assertEquals("Reservation-1", plan.backfillPlayerTickets().get(0).admissionReservationId());
    }

    @Test
    void mapsSuccessfulLaunchResultToProcessedAck() {
        BackendAssignmentProcessingPlan plan = planner.planLaunchResult("LAUNCHED", "local-match-1", "");

        assertEquals(BackendAssignmentProcessingPlan.Action.PERSIST_LAUNCH_RESULT, plan.action());
        assertEquals("LAUNCHED", plan.status());
        assertEquals("local-match-1", plan.localMatchId());
        assertEquals("", plan.reason());
    }

    @Test
    void mapsRejectedLaunchResultToProcessedAck() {
        BackendAssignmentProcessingPlan plan = planner.planLaunchResult("REJECTED", "local-match-1", "No arena available.");

        assertEquals(BackendAssignmentProcessingPlan.Action.PERSIST_LAUNCH_RESULT, plan.action());
        assertEquals("REJECTED", plan.status());
        assertEquals("local-match-1", plan.localMatchId());
        assertEquals("No arena available.", plan.reason());
    }

    @Test
    void planLaunchResultTrimsStatusLocalMatchIdAndReason() {
        BackendAssignmentProcessingPlan plan = planner.planLaunchResult(" LAUNCHED ", " local-match-1 ", " done ");

        assertEquals("LAUNCHED", plan.status());
        assertEquals("local-match-1", plan.localMatchId());
        assertEquals("done", plan.reason());
    }

    @Test
    void preservesValidationReasonInRejectedAck() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            BackendAssignmentValidationResult.invalid("BACKFILL assignment requires targetConnectionAddress."),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.REJECT_VALIDATION, plan.action());
        assertEquals("BACKFILL assignment requires targetConnectionAddress.", plan.reason());
    }

    @Test
    void preservesDuplicatePayloadReuseReason() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            EXISTING_HASH,
            BackendAssignmentValidationResult.invalid("This should not win."),
            ""
        );

        assertEquals(BackendAssignmentProcessingPlan.Action.REJECT_DUPLICATE, plan.action());
        assertEquals(BackendAssignmentProcessingPlanner.DUPLICATE_PAYLOAD_REUSE_REASON, plan.reason());
    }

    @Test
    void planCollectionsAreImmutableCopies() {
        BackendAssignmentProcessingPlan plan = planner.planBeforeLaunch(
            assignment("assignment-1"),
            ASSIGNMENT_HASH,
            "",
            initialValidation(),
            ""
        );

        assertTrue(plan.playerUuids().contains(PLAYER_ONE));
    }

    private static BackendAssignmentPayload assignment(String assignmentId) {
        return new BackendAssignmentPayload(
            "INITIAL_MATCH",
            assignmentId,
            "match-1",
            "external-match-1",
            "CREATE_MATCH",
            "queue-1",
            List.of(PLAYER_ONE.toString(), PLAYER_TWO.toString()),
            List.of(PLAYER_ONE.toString(), PLAYER_TWO.toString()),
            "arena-1",
            List.of(),
            "server-1",
            "",
            "",
            "",
            false,
            null
        );
    }

    private static BackendAssignmentValidationResult initialValidation() {
        return BackendAssignmentValidationResult.valid(
            BackendAssignmentValidator.ASSIGNMENT_TYPE_INITIAL_MATCH,
            "match-1",
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of()
        );
    }

    private static BackendAssignmentValidationResult backfillValidation() {
        return BackendAssignmentValidationResult.valid(
            BackendAssignmentValidator.ASSIGNMENT_TYPE_BACKFILL,
            "match-1",
            List.of(PLAYER_ONE),
            List.of(),
            List.of(new BackendAssignmentValidationResult.BackfillPlayerTicket(PLAYER_ONE, "Reservation-1", 20_000L))
        );
    }
}
