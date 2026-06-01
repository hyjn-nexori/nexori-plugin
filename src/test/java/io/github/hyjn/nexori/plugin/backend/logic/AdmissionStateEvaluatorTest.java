package io.github.hyjn.nexori.plugin.backend.logic;

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

final class AdmissionStateEvaluatorTest {

    private static final long CREATED_AT = 500L;
    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID BACKFILL_PLAYER = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final AdmissionStateEvaluator evaluator = new AdmissionStateEvaluator();

    @Test
    void evaluatesNoneModeDuringPlacementAccordingToCurrentBehavior() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.NONE, 0, 4, 0), false, NOW, "MATCH_CREATED");

        assertEquals(AdmissionStateEvaluator.STATUS_PLACEMENT, result.matchLifecycleStatus());
        assertFalse(result.admissionOpen());
        assertEquals(0, result.availableAdmissionSlots());
        assertFalse(result.admissionReportingClosed());
        assertEquals("MATCH_CREATED", result.primaryChangeReason());
    }

    @Test
    void evaluatesNoneModeAfterPlacementAccordingToCurrentBehavior() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.NONE, 0, 4, 0), true, NOW, "MATCH_STARTED");

        assertEquals(AdmissionStateEvaluator.STATUS_ACTIVE, result.matchLifecycleStatus());
        assertFalse(result.admissionOpen());
        assertTrue(result.admissionReportingClosed());
        assertEquals(AdmissionStateEvaluator.CLOSE_REASON_NO_LONGER_ACCEPTING, result.admissionReportingCloseReason());
        assertEquals(AdmissionStateEvaluator.CHANGE_REASON_ADMISSION_CLOSED, result.primaryChangeReason());
    }

    @Test
    void evaluatesPlacementOnlyBeforePlacementCompleteAsOpen() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.PLACEMENT_ONLY, 0, 4, 0), false, NOW, "MATCH_CREATED");

        assertTrue(result.admissionOpen());
        assertEquals(2, result.availableAdmissionSlots());
        assertFalse(result.admissionReportingClosed());
    }

    @Test
    void evaluatesPlacementOnlyAfterPlacementCompleteAsClosed() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.PLACEMENT_ONLY, 0, 4, 0), true, NOW, "PLACEMENT_COMPLETED");

        assertFalse(result.admissionOpen());
        assertTrue(result.admissionReportingClosed());
        assertEquals(AdmissionStateEvaluator.CLOSE_REASON_NO_LONGER_ACCEPTING, result.admissionReportingCloseReason());
        assertEquals(AdmissionStateEvaluator.CHANGE_REASON_ADMISSION_CLOSED, result.primaryChangeReason());
    }

    @Test
    void evaluatesActiveWindowDuringPlacementAsOpenWithoutDeadlineIfCurrentBehaviorDoesThat() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0), false, NOW, "MATCH_CREATED");

        assertTrue(result.admissionOpen());
        assertEquals(0L, result.admissionOpenUntilEpochMs());
        assertEquals(2, result.availableAdmissionSlots());
    }

    @Test
    void evaluatesActiveWindowInsideActiveWindowAsOpenWithDeadline() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0)
            .withPlacementCompleted(NOW, NOW)
            .withStartGateOpened("ALL_INITIAL_PLAYERS_PLACED", NOW, NOW);

        AdmissionStateEvaluation result = evaluate(match, true, NOW + 10_000L, "MATCH_STARTED");

        assertTrue(result.admissionOpen());
        assertEquals(NOW + 30_000L, result.admissionOpenUntilEpochMs());
        assertEquals(2, result.availableAdmissionSlots());
        assertEquals("MATCH_STARTED", result.primaryChangeReason());
    }

    @Test
    void evaluatesActiveWindowAfterDeadlineAsClosedWindowExpired() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0)
            .withPlacementCompleted(NOW, NOW)
            .withStartGateOpened("ALL_INITIAL_PLAYERS_PLACED", NOW, NOW);

        AdmissionStateEvaluation result = evaluate(match, true, NOW + 30_001L, "MATCH_STARTED");

        assertFalse(result.admissionOpen());
        assertTrue(result.admissionReportingClosed());
        assertEquals(AdmissionStateEvaluator.CLOSE_REASON_WINDOW_EXPIRED, result.admissionReportingCloseReason());
        assertEquals(AdmissionStateEvaluator.CHANGE_REASON_ADMISSION_WINDOW_EXPIRED, result.primaryChangeReason());
    }

    @Test
    void evaluatesActiveWindowAtFullCapacityAsClosedNoLongerAccepting() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 2, 0)
            .withPlacementCompleted(NOW, NOW)
            .withStartGateOpened("ALL_INITIAL_PLAYERS_PLACED", NOW, NOW);

        AdmissionStateEvaluation result = evaluate(match, true, NOW + 10_000L, "PLAYER_ARRIVED");

        assertFalse(result.admissionOpen());
        assertTrue(result.admissionReportingClosed());
        assertEquals(0, result.availableAdmissionSlots());
        assertEquals(AdmissionStateEvaluator.CLOSE_REASON_NO_LONGER_ACCEPTING, result.admissionReportingCloseReason());
        assertEquals(AdmissionStateEvaluator.CHANGE_REASON_ADMISSION_CLOSED, result.primaryChangeReason());
    }

    @Test
    void explicitAdmissionClosedAlwaysClosesReporting() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0)
            .withExplicitAdmissionClosed("manual", "closed", NOW, NOW);

        AdmissionStateEvaluation result = evaluate(match, false, NOW, "PLAYER_ARRIVED");

        assertFalse(result.admissionOpen());
        assertTrue(result.admissionReportingClosed());
        assertEquals("manual", result.admissionReportingCloseReason());
        assertEquals("manual", result.primaryChangeReason());
        assertEquals(0, result.availableAdmissionSlots());
    }

    @Test
    void calculatesAdmittedSlotsFromInitialRosterAndConsumedBackfill() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 5, 0)
            .withConsumedBackfillAdmissionIncrement(NOW)
            .withConsumedBackfillAdmissionIncrement(NOW);

        AdmissionStateEvaluation result = evaluate(match, false, NOW, "");

        assertEquals(4, result.admittedSlotCount());
        assertEquals(1, result.availableAdmissionSlots());
    }

    @Test
    void clampsAdmittedSlotsToAdmissionCapacity() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 3, 0)
            .withConsumedBackfillAdmissionIncrement(NOW)
            .withConsumedBackfillAdmissionIncrement(NOW);

        AdmissionStateEvaluation result = evaluate(match, false, NOW, "");

        assertEquals(3, result.admittedSlotCount());
        assertTrue(result.initialRosterExceedsAdmissionCapacity() == false);
    }

    @Test
    void initialRosterExceedsAdmissionCapacitySetsFlagAndClampsAdmittedSlots() {
        UUID playerThree = UUID.fromString("44444444-4444-4444-4444-444444444444");
        ArenaActiveMatch match = baseMatch(
            QueueBackfillMode.ACTIVE_WINDOW,
            30,
            2,
            0,
            List.of(PLAYER_ONE, PLAYER_TWO, playerThree),
            3
        );

        AdmissionStateEvaluation result = evaluate(match, false, NOW, "");

        assertEquals(3, result.initialRosterSize());
        assertEquals(2, result.admittedSlotCount());
        assertTrue(result.initialRosterExceedsAdmissionCapacity());
    }

    @Test
    void calculatesArrivedAndUnfilledInitialRosterCounts() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0)
            .withPlayerArrival(PLAYER_ONE, NOW)
            .withPlayerArrival(BACKFILL_PLAYER, NOW);

        AdmissionStateEvaluation result = evaluate(match, false, NOW, "");

        assertEquals(2, result.initialRosterSize());
        assertEquals(1, result.arrivedInitialPlayerCount());
        assertEquals(1, result.unfilledInitialRosterCount());
    }

    @Test
    void preservesPrimaryChangeReasonWhenNotClosing() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0), false, NOW, "PLAYER_ARRIVED");

        assertFalse(result.admissionReportingClosed());
        assertEquals("PLAYER_ARRIVED", result.primaryChangeReason());
    }

    @Test
    void overridesPrimaryChangeReasonWhenWindowExpired() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0)
            .withPlacementCompleted(NOW, NOW)
            .withStartGateOpened("ALL_INITIAL_PLAYERS_PLACED", NOW, NOW);

        AdmissionStateEvaluation result = evaluate(match, true, NOW + 30_001L, "PLAYER_ARRIVED");

        assertEquals(AdmissionStateEvaluator.CHANGE_REASON_ADMISSION_WINDOW_EXPIRED, result.primaryChangeReason());
    }

    @Test
    void activeWindowAtExactDeadlineRemainsOpen() {
        ArenaActiveMatch match = baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0)
            .withPlacementCompleted(NOW, NOW)
            .withStartGateOpened("ALL_INITIAL_PLAYERS_PLACED", NOW, NOW);

        AdmissionStateEvaluation result = evaluate(match, true, NOW + 30_000L, "MATCH_STARTED");

        assertTrue(result.admissionOpen());
        assertFalse(result.admissionReportingClosed());
    }

    @Test
    void activeWindowAfterPlacementWithoutMatchStartedTimestampPreservesCurrentBehavior() {
        AdmissionStateEvaluation result = evaluate(
            baseMatch(QueueBackfillMode.ACTIVE_WINDOW, 30, 4, 0),
            true,
            NOW,
            "MATCH_STARTED"
        );

        assertEquals(AdmissionStateEvaluator.STATUS_ACTIVE, result.matchLifecycleStatus());
        assertFalse(result.admissionOpen());
        assertEquals(0L, result.admissionOpenUntilEpochMs());
        assertFalse(result.admissionReportingClosed());
    }

    @Test
    void overridesPrimaryChangeReasonWhenAdmissionClosed() {
        AdmissionStateEvaluation result = evaluate(baseMatch(QueueBackfillMode.PLACEMENT_ONLY, 0, 4, 0), true, NOW, "PLAYER_ARRIVED");

        assertEquals(AdmissionStateEvaluator.CHANGE_REASON_ADMISSION_CLOSED, result.primaryChangeReason());
    }

    private AdmissionStateEvaluation evaluate(
        ArenaActiveMatch match,
        boolean placementComplete,
        long nowEpochMs,
        String primaryChangeReason
    ) {
        return evaluator.evaluate(match, placementComplete, nowEpochMs, primaryChangeReason);
    }

    private static ArenaActiveMatch baseMatch(QueueBackfillMode mode, int backfillWindowSeconds, int admissionCapacity, int consumedBackfillCount) {
        return baseMatch(
            mode,
            backfillWindowSeconds,
            admissionCapacity,
            consumedBackfillCount,
            List.of(PLAYER_ONE, PLAYER_TWO),
            2
        );
    }

    private static ArenaActiveMatch baseMatch(
        QueueBackfillMode mode,
        int backfillWindowSeconds,
        int admissionCapacity,
        int consumedBackfillCount,
        List<UUID> expectedPlayerUuids,
        int expectedPlayerCount
    ) {
        ArenaActiveMatch match = new ArenaActiveMatch(
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
            ArenaMatchSource.BACKEND_DRIVEN.id(),
            1,
            admissionCapacity,
            true,
            mode.id(),
            backfillWindowSeconds,
            expectedPlayerUuids,
            expectedPlayerCount,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            consumedBackfillCount,
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
        return match;
    }
}
