package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackfillAdmissionDeciderTest {

    private static final long CREATED_AT = 500L;
    private static final long NOW = 1_000L;
    private static final long EXPIRES_AT = 2_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BACKFILL_PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BackfillAdmissionDecider decider = new BackfillAdmissionDecider();

    @Test
    void rejectsMissingMatch() {
        BackfillAdmissionDecision decision = decide(null);

        assertRejected(decision, BackfillAdmissionDecider.REASON_MISSING_MATCH, BackfillAdmissionDecider.MESSAGE_MISSING_MATCH);
    }

    @Test
    void rejectsWrongAssignmentType() {
        BackfillAdmissionDecision decision = decider.decide(
            baseMatch(),
            "INITIAL_MATCH",
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "reservation-1",
            EXPIRES_AT,
            NOW
        );

        assertRejected(decision, BackfillAdmissionDecider.REASON_WRONG_ASSIGNMENT_TYPE, BackfillAdmissionDecider.MESSAGE_WRONG_ASSIGNMENT_TYPE);
    }

    @Test
    void rejectsPlayerUuidMismatch() {
        BackfillAdmissionDecision decision = decider.decide(
            baseMatch(),
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            PLAYER_ONE,
            BACKFILL_PLAYER,
            "reservation-1",
            EXPIRES_AT,
            NOW
        );

        assertRejected(decision, BackfillAdmissionDecider.REASON_PLAYER_UUID_MISMATCH, BackfillAdmissionDecider.MESSAGE_PLAYER_UUID_MISMATCH);
    }

    @Test
    void rejectsMissingAdmissionReservationId() {
        BackfillAdmissionDecision decision = decider.decide(
            baseMatch(),
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            " ",
            EXPIRES_AT,
            NOW
        );

        assertRejected(
            decision,
            BackfillAdmissionDecider.REASON_MISSING_ADMISSION_RESERVATION_ID,
            BackfillAdmissionDecider.MESSAGE_MISSING_ADMISSION_RESERVATION_ID
        );
    }

    @Test
    void rejectsExpiredReservation() {
        BackfillAdmissionDecision decision = decider.decide(
            baseMatch(),
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "reservation-1",
            NOW - 1,
            NOW
        );

        assertRejected(decision, BackfillAdmissionDecider.REASON_EXPIRED_RESERVATION, BackfillAdmissionDecider.MESSAGE_EXPIRED_RESERVATION);
    }

    @Test
    void rejectsNonBackendDrivenMatchIfCurrentBehaviorDoesThat() {
        BackfillAdmissionDecision decision = decide(baseMatch(ArenaMatchSource.LOCAL_FIFO.id(), true, QueueBackfillMode.ACTIVE_WINDOW.id(), 30));

        assertRejected(decision, BackfillAdmissionDecider.REASON_NOT_BACKEND_DRIVEN, BackfillAdmissionDecider.MESSAGE_NOT_BACKEND_DRIVEN);
    }

    @Test
    void rejectsExplicitAdmissionClosed() {
        BackfillAdmissionDecision decision = decide(baseMatch().withExplicitAdmissionClosed("manual", "closed", NOW, NOW));

        assertRejected(
            decision,
            BackfillAdmissionDecider.REASON_EXPLICIT_ADMISSION_CLOSED,
            BackfillAdmissionDecider.MESSAGE_EXPLICIT_ADMISSION_CLOSED
        );
    }

    @Test
    void rejectsBackfillModeNone() {
        BackfillAdmissionDecision decision = decide(baseMatch(ArenaMatchSource.BACKEND_DRIVEN.id(), false, QueueBackfillMode.ACTIVE_WINDOW.id(), 30));

        assertRejected(decision, BackfillAdmissionDecider.REASON_ADMISSION_CLOSED, BackfillAdmissionDecider.MESSAGE_ADMISSION_CLOSED);
    }

    @Test
    void allowsPlacementOnlyBeforePlacementCompleted() {
        BackfillAdmissionDecision decision = decide(baseMatch(ArenaMatchSource.BACKEND_DRIVEN.id(), true, QueueBackfillMode.PLACEMENT_ONLY.id(), 0));

        assertAllowed(decision, "reservation-1");
    }

    @Test
    void rejectsPlacementOnlyAfterPlacementCompleted() {
        ArenaActiveMatch match = baseMatch(ArenaMatchSource.BACKEND_DRIVEN.id(), true, QueueBackfillMode.PLACEMENT_ONLY.id(), 0)
            .withPlacementCompleted(NOW, NOW);

        BackfillAdmissionDecision decision = decide(match);

        assertRejected(decision, BackfillAdmissionDecider.REASON_ADMISSION_CLOSED, BackfillAdmissionDecider.MESSAGE_ADMISSION_CLOSED);
    }

    @Test
    void allowsActiveWindowInsideWindow() {
        ArenaActiveMatch match = baseMatch()
            .withPlacementCompleted(NOW, NOW);

        BackfillAdmissionDecision decision = decider.decide(
            match,
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "reservation-1",
            EXPIRES_AT + 100_000L,
            NOW + 30_000L
        );

        assertAllowed(decision, "reservation-1");
    }

    @Test
    void rejectsActiveWindowAfterWindow() {
        ArenaActiveMatch match = baseMatch()
            .withPlacementCompleted(NOW, NOW);

        BackfillAdmissionDecision decision = decider.decide(
            match,
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "reservation-1",
            EXPIRES_AT + 100_000L,
            NOW + 30_001L
        );

        assertRejected(decision, BackfillAdmissionDecider.REASON_ADMISSION_CLOSED, BackfillAdmissionDecider.MESSAGE_ADMISSION_CLOSED);
    }

    @Test
    void rejectsWhenCapacityIsFull() {
        ArenaActiveMatch match = baseMatch()
            .withConsumedBackfillAdmissionIncrement(NOW)
            .withConsumedBackfillAdmissionIncrement(NOW);

        BackfillAdmissionDecision decision = decide(match);

        assertRejected(decision, BackfillAdmissionDecider.REASON_CAPACITY_FULL, BackfillAdmissionDecider.MESSAGE_CAPACITY_FULL);
    }

    @Test
    void rejectsDuplicateReservation() {
        ArenaActiveMatch match = baseMatch()
            .withAcceptedBackfillReservation("reservation-1", NOW);

        BackfillAdmissionDecision decision = decide(match);

        assertRejected(decision, BackfillAdmissionDecider.REASON_DUPLICATE_RESERVATION, BackfillAdmissionDecider.MESSAGE_DUPLICATE_RESERVATION);
    }

    @Test
    void preservesCaseSensitiveReservationIds() {
        BackfillAdmissionDecision decision = decider.decide(
            baseMatch(),
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "Reservation-1",
            EXPIRES_AT,
            NOW
        );

        assertAllowed(decision, "Reservation-1");
    }

    @Test
    void trimsReservationIdsWithoutLowercasing() {
        BackfillAdmissionDecision decision = decider.decide(
            baseMatch(),
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            " Reservation-1 ",
            EXPIRES_AT,
            NOW
        );

        assertAllowed(decision, "Reservation-1");
    }

    @Test
    void caseDifferentReservationIdsAreNotTreatedAsDuplicates() {
        ArenaActiveMatch match = baseMatch()
            .withAcceptedBackfillReservation("Reservation-1", NOW);

        BackfillAdmissionDecision decision = decider.decide(
            match,
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "reservation-1",
            EXPIRES_AT,
            NOW
        );

        assertAllowed(decision, "reservation-1");
    }

    private BackfillAdmissionDecision decide(ArenaActiveMatch match) {
        return decider.decide(
            match,
            BackfillAdmissionDecider.ASSIGNMENT_TYPE_BACKFILL,
            BACKFILL_PLAYER,
            BACKFILL_PLAYER,
            "reservation-1",
            EXPIRES_AT,
            NOW
        );
    }

    private static void assertAllowed(BackfillAdmissionDecision decision, String reservationId) {
        assertTrue(decision.allowed());
        assertFalse(decision.rejected());
        assertEquals("", decision.reasonCode());
        assertEquals("", decision.reason());
        assertEquals(reservationId, decision.admissionReservationId());
    }

    private static void assertRejected(BackfillAdmissionDecision decision, String reasonCode, String reason) {
        assertFalse(decision.allowed());
        assertTrue(decision.rejected());
        assertEquals(reasonCode, decision.reasonCode());
        assertEquals(reason, decision.reason());
    }

    private static ArenaActiveMatch baseMatch() {
        return baseMatch(ArenaMatchSource.BACKEND_DRIVEN.id(), true, QueueBackfillMode.ACTIVE_WINDOW.id(), 30);
    }

    private static ArenaActiveMatch baseMatch(String matchSource, boolean backfillEnabled, String backfillMode, int backfillWindowSeconds) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby.example:19132",
            "lobby-1.natural_spawn",
            "keep_inventory",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "",
            "rules-default",
            "assignment-1",
            "INITIAL_MATCH",
            "external-match-1",
            matchSource,
            1,
            4,
            backfillEnabled,
            backfillMode,
            backfillWindowSeconds,
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            0L,
            0L,
            0L,
            0L,
            "",
            CREATED_AT,
            CREATED_AT,
            ""
        ).normalized();
    }
}
