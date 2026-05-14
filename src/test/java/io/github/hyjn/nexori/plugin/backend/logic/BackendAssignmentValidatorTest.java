package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentPlayerPayload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendAssignmentValidatorTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BackendAssignmentValidator validator = new BackendAssignmentValidator();

    @Test
    void rejectsNullAssignment() {
        BackendAssignmentValidationResult result = validate(null);

        assertInvalid(result, "Assignment is required.");
    }

    @Test
    void acceptsValidInitialMatchAssignment() {
        BackendAssignmentValidationResult result = validate(initialAssignment());

        assertTrue(result.valid());
        assertEquals(BackendAssignmentValidator.ASSIGNMENT_TYPE_INITIAL_MATCH, result.assignmentType());
        assertEquals("backend-match-1", result.matchId());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.playerUuids());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.expectedPlayerUuids());
    }

    @Test
    void acceptsValidBackfillAssignment() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, "reservation-1", 2_000L),
            ticket(PLAYER_TWO, "reservation-2", 2_000L)
        )));

        assertTrue(result.valid());
        assertEquals(BackendAssignmentValidator.ASSIGNMENT_TYPE_BACKFILL, result.assignmentType());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.playerUuids());
        assertEquals(2, result.backfillPlayerTickets().size());
    }

    @Test
    void rejectsBlankAssignmentId() {
        BackendAssignmentValidationResult result = validate(initialAssignment(" ", "INITIAL_MATCH", "CREATE_MATCH"));

        assertInvalid(result, "Assignment assignmentId is required.");
    }

    @Test
    void rejectsBlankAssignmentType() {
        BackendAssignmentValidationResult result = validate(initialAssignment("assignment-1", " ", ""));

        assertInvalid(result, "Assignment assignmentType is required unless legacy type is CREATE_MATCH.");
    }

    @Test
    void acceptsLegacyCreateMatchWithoutAssignmentType() {
        BackendAssignmentValidationResult result = validate(initialAssignment("assignment-1", " ", "CREATE_MATCH"));

        assertTrue(result.valid());
        assertEquals(BackendAssignmentValidator.ASSIGNMENT_TYPE_INITIAL_MATCH, result.assignmentType());
    }

    @Test
    void normalizesFlexibleAssignmentTypeValues() {
        BackendAssignmentValidationResult result = validate(initialAssignment("assignment-1", " initial_match ", "CREATE_MATCH"));

        assertTrue(result.valid());
        assertEquals(BackendAssignmentValidator.ASSIGNMENT_TYPE_INITIAL_MATCH, result.assignmentType());
    }

    @Test
    void rejectsUnknownAssignmentType() {
        BackendAssignmentValidationResult result = validate(initialAssignment("assignment-1", "SOMETHING_ELSE", "CREATE_MATCH"));

        assertInvalid(result, "Assignment assignmentType must be INITIAL_MATCH or BACKFILL.");
    }

    @Test
    void rejectsInitialMatchWithUnsupportedLegacyType() {
        BackendAssignmentValidationResult result = validate(initialAssignment("assignment-1", "INITIAL_MATCH", "JOIN_MATCH"));

        assertInvalid(result, "Unsupported assignment type.");
    }

    @Test
    void rejectsBackfillWithUnsupportedLegacyType() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(
            "assignment-1",
            "BACKFILL",
            "CREATE_MATCH",
            "external-match-1",
            "arena.example:19132",
            List.of(ticket(PLAYER_ONE, "reservation-1", 2_000L))
        ));

        assertInvalid(result, "Unsupported assignment type.");
    }

    @Test
    void rejectsInvalidMatchId() {
        BackendAssignmentValidationResult result = validate(new BackendAssignmentPayload(
            "INITIAL_MATCH",
            "assignment-1",
            "invalid_match_id",
            "external-match-1",
            "CREATE_MATCH",
            "queue-1",
            List.of(PLAYER_ONE.toString()),
            List.of(PLAYER_ONE.toString()),
            "arena-1",
            List.of(),
            "",
            "",
            "",
            "",
            false,
            null
        ));

        assertFalse(result.valid());
        assertTrue(result.message().startsWith("Assignment matchId is invalid:"));
    }

    @Test
    void rejectsInitialMatchWithoutPlayers() {
        BackendAssignmentValidationResult result = validate(new BackendAssignmentPayload(
            "INITIAL_MATCH",
            "assignment-1",
            "backend-match-1",
            "external-match-1",
            "CREATE_MATCH",
            "queue-1",
            List.of(),
            List.of(),
            "arena-1",
            List.of(),
            "",
            "",
            "",
            "",
            false,
            null
        ));

        assertInvalid(result, "Assignment must include at least one player.");
    }

    @Test
    void rejectsInitialMatchWithDuplicatePlayers() {
        BackendAssignmentPayload assignment = new BackendAssignmentPayload(
            "INITIAL_MATCH",
            "assignment-1",
            "backend-match-1",
            "external-match-1",
            "CREATE_MATCH",
            "queue-1",
            List.of(PLAYER_ONE.toString(), PLAYER_ONE.toString()),
            List.of(PLAYER_ONE.toString()),
            "arena-1",
            List.of(),
            "",
            "",
            "",
            "",
            false,
            null
        );

        BackendAssignmentValidationResult result = validate(assignment);

        assertInvalid(result, "Assignment includes duplicate players.");
    }

    @Test
    void rejectsInitialMatchWithInvalidExpectedPlayers() {
        BackendAssignmentPayload assignment = new BackendAssignmentPayload(
            "INITIAL_MATCH",
            "assignment-1",
            "backend-match-1",
            "external-match-1",
            "CREATE_MATCH",
            "queue-1",
            List.of(PLAYER_ONE.toString()),
            List.of("not-a-uuid"),
            "arena-1",
            List.of(),
            "",
            "",
            "",
            "",
            false,
            null
        );

        BackendAssignmentValidationResult result = validate(assignment);

        assertInvalid(result, "Assignment includes an invalid player UUID.");
    }

    @Test
    void rejectsBackfillWithoutAdmissionReservationId() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, " ", 2_000L)
        )));

        assertInvalid(result, "BACKFILL assignment requires one admissionReservationId per player.");
    }

    @Test
    void rejectsBackfillWithoutPlayerTickets() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of()));

        assertInvalid(result, "BACKFILL assignment must include players.");
    }

    @Test
    void rejectsBackfillWithoutTargetConnectionAddress() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(
            "assignment-1",
            "BACKFILL",
            "BACKFILL",
            "external-match-1",
            " ",
            List.of(ticket(PLAYER_ONE, "reservation-1", 2_000L))
        ));

        assertInvalid(result, "BACKFILL assignment requires targetConnectionAddress.");
    }

    @Test
    void acceptsBackfillWithBlankExternalMatchIdBecauseCurrentBehaviorAllowsIt() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(
            "assignment-1",
            "BACKFILL",
            "BACKFILL",
            " ",
            "arena.example:19132",
            List.of(ticket(PLAYER_ONE, "reservation-1", 2_000L))
        ));

        assertTrue(result.valid());
    }

    @Test
    void acceptsBackfillWithMultipleTicketsBecauseCurrentBehaviorAllowsIt() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, "reservation-1", 2_000L),
            ticket(PLAYER_TWO, "reservation-2", 2_000L)
        )));

        assertTrue(result.valid());
        assertEquals(2, result.backfillPlayerTickets().size());
    }

    @Test
    void rejectsBackfillInvalidPlayerUuid() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            new BackendAssignmentPlayerPayload("not-a-uuid", "reservation-1", 2_000L)
        )));

        assertInvalid(result, "BACKFILL assignment includes an invalid player UUID.");
    }

    @Test
    void preservesCaseSensitiveAdmissionReservationId() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, "Reservation-1", 2_000L)
        )));

        assertTrue(result.valid());
        assertEquals("Reservation-1", result.backfillPlayerTickets().get(0).admissionReservationId());
    }

    @Test
    void trimsAdmissionReservationIdWithoutLowercasing() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, " Reservation-1 ", 2_000L)
        )));

        assertTrue(result.valid());
        assertEquals("Reservation-1", result.backfillPlayerTickets().get(0).admissionReservationId());
    }

    @Test
    void rejectsExpectedPlayersNotSubsetOfPlayerUuidsIfCurrentBehaviorDoesThat() {
        BackendAssignmentPayload assignment = new BackendAssignmentPayload(
            "INITIAL_MATCH",
            "assignment-1",
            "backend-match-1",
            "external-match-1",
            "CREATE_MATCH",
            "queue-1",
            List.of(PLAYER_ONE.toString(), PLAYER_TWO.toString()),
            List.of(PLAYER_ONE.toString(), PLAYER_THREE.toString()),
            "arena-1",
            List.of(),
            "",
            "",
            "",
            "",
            false,
            null
        );

        BackendAssignmentValidationResult result = validate(assignment);

        assertInvalid(result, "Assignment playerUuids must be a subset of expectedPlayerUuids.");
    }

    @Test
    void rejectsBackfillDuplicatePlayers() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, "reservation-1", 2_000L),
            ticket(PLAYER_ONE, "reservation-2", 2_000L)
        )));

        assertInvalid(result, "BACKFILL assignment includes duplicate players.");
    }

    @Test
    void rejectsBackfillDuplicateAdmissionReservationIds() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, "reservation-1", 2_000L),
            ticket(PLAYER_TWO, "reservation-1", 2_000L)
        )));

        assertInvalid(result, "BACKFILL assignment includes duplicate admissionReservationIds.");
    }

    @Test
    void rejectsBackfillNonPositiveAdmissionExpiry() {
        BackendAssignmentValidationResult result = validate(backfillAssignment(List.of(
            ticket(PLAYER_ONE, "reservation-1", 0L)
        )));

        assertInvalid(result, "BACKFILL assignment requires a positive admissionExpiresAtEpochMs per player.");
    }

    private BackendAssignmentValidationResult validate(BackendAssignmentPayload assignment) {
        return validator.validate(assignment);
    }

    private static void assertInvalid(BackendAssignmentValidationResult result, String message) {
        assertFalse(result.valid());
        assertEquals(message, result.message());
    }

    private static BackendAssignmentPayload initialAssignment() {
        return initialAssignment("assignment-1", "INITIAL_MATCH", "CREATE_MATCH");
    }

    private static BackendAssignmentPayload initialAssignment(String assignmentId, String assignmentType, String legacyType) {
        return new BackendAssignmentPayload(
            assignmentType,
            assignmentId,
            "Backend-Match-1",
            "external-match-1",
            legacyType,
            "queue-1",
            List.of(PLAYER_ONE.toString(), PLAYER_TWO.toString()),
            List.of(PLAYER_ONE.toString(), PLAYER_TWO.toString()),
            "arena-1",
            List.of(),
            "",
            "",
            "",
            "",
            false,
            null
        );
    }

    private static BackendAssignmentPayload backfillAssignment(List<BackendAssignmentPlayerPayload> tickets) {
        return backfillAssignment("assignment-1", "BACKFILL", "BACKFILL", "external-match-1", "arena.example:19132", tickets);
    }

    private static BackendAssignmentPayload backfillAssignment(
        String assignmentId,
        String assignmentType,
        String legacyType,
        String externalMatchId,
        String targetConnectionAddress,
        List<BackendAssignmentPlayerPayload> tickets
    ) {
        return new BackendAssignmentPayload(
            assignmentType,
            assignmentId,
            "backend-match-1",
            externalMatchId,
            legacyType,
            "queue-1",
            List.of(),
            List.of(),
            "arena-1",
            tickets,
            "server-1",
            targetConnectionAddress,
            "",
            "",
            false,
            null
        );
    }

    private static BackendAssignmentPlayerPayload ticket(UUID playerUuid, String reservationId, long expiresAtEpochMs) {
        return new BackendAssignmentPlayerPayload(playerUuid.toString(), reservationId, expiresAtEpochMs);
    }
}
