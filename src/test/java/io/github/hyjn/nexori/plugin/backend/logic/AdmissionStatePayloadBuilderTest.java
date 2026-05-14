package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.Gson;
import io.github.hyjn.nexori.plugin.backend.payload.BackendMatchAdmissionStatePayload;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.LastPlayerAliveArenaMatchResolutionTrigger;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AdmissionStatePayloadBuilderTest {

    private static final int SCHEMA_VERSION = 1;
    private static final String STATE_UPDATE_ID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final long SEQUENCE = 42L;
    private static final long SENT_AT = 1_000L;
    private static final long EXPIRES_AT = 11_000L;
    private static final String REPORTING_SERVER_ID = "server-1";
    private static final long CREATED_AT = 500L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Gson gson = new Gson();
    private final AdmissionStatePayloadBuilder builder = new AdmissionStatePayloadBuilder();

    @Test
    void buildsPayloadWithExpectedMatchAndEvaluationFields() {
        AdmissionStatePayloadBuildResult result = build(baseMatch(30), evaluation(), "PLAYER_ARRIVED", List.of("PLAYER_ARRIVED"), List.of());
        BackendMatchAdmissionStatePayload payload = result.payload();

        assertEquals(SCHEMA_VERSION, payload.schemaVersion());
        assertEquals(STATE_UPDATE_ID, payload.stateUpdateId());
        assertEquals(SEQUENCE, payload.admissionStateSequence());
        assertEquals(SENT_AT, payload.sentAtEpochMs());
        assertEquals(EXPIRES_AT, payload.stateExpiresAtEpochMs());
        assertEquals(REPORTING_SERVER_ID, payload.reportingServerId());
        assertEquals("match-1", payload.matchId());
        assertEquals("external-match-1", payload.externalMatchId());
        assertEquals("queue-1", payload.queueId());
        assertEquals("arena-1", payload.arenaId());
        assertTrue(payload.backfillEnabled());
        assertEquals(QueueBackfillMode.ACTIVE_WINDOW.id(), payload.backfillMode());
        assertEquals(30, payload.backfillWindowSeconds());
        assertEquals(AdmissionStateEvaluator.STATUS_ACTIVE, payload.matchLifecycleStatus());
        assertTrue(payload.admissionOpen());
        assertEquals(31_000L, payload.admissionOpenUntilEpochMs());
        assertEquals(4, payload.admissionCapacity());
        assertEquals(2, payload.admittedSlotCount());
        assertEquals(2, payload.availableAdmissionSlots());
        assertEquals(2, payload.initialRosterSize());
        assertEquals(1, payload.arrivedInitialPlayerCount());
        assertEquals(1, payload.unfilledInitialRosterCount());
        assertFalse(payload.admissionReportingClosed());
        assertEquals("", payload.admissionReportingCloseReason());
        assertEquals("PLAYER_ARRIVED", payload.primaryChangeReason());
    }

    @Test
    void fillsPayloadHash() {
        String payloadHash = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of(), List.of()).payload().payloadHash();

        assertEquals(64, payloadHash.length());
        assertTrue(payloadHash.matches("[0-9a-f]+"));
    }

    @Test
    void bodySerializesFinalPayloadWithComputedHash() {
        AdmissionStatePayloadBuildResult result = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of(), List.of());
        BackendMatchAdmissionStatePayload parsedPayload = gson.fromJson(result.body(), BackendMatchAdmissionStatePayload.class);

        assertEquals(result.payload().payloadHash(), parsedPayload.payloadHash());
        assertFalse(parsedPayload.payloadHash().isBlank());
        assertEquals(STATE_UPDATE_ID, parsedPayload.stateUpdateId());
        assertEquals(SEQUENCE, parsedPayload.admissionStateSequence());
    }

    @Test
    void hashCanonicalPayloadIgnoresPayloadHashField() {
        BackendMatchAdmissionStatePayload payload = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of(), List.of()).payload();
        BackendMatchAdmissionStatePayload firstPayload = withPayloadHash(payload, "hash-one");
        BackendMatchAdmissionStatePayload secondPayload = withPayloadHash(payload, "hash-two");

        assertEquals(builder.hashCanonicalPayload(firstPayload), builder.hashCanonicalPayload(secondPayload));
    }

    @Test
    void payloadHashChangesWhenAContractFieldChanges() {
        String firstHash = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of(), List.of()).payload().payloadHash();
        String secondHash = builder.build(
            SCHEMA_VERSION,
            STATE_UPDATE_ID,
            SEQUENCE + 1,
            SENT_AT,
            EXPIRES_AT,
            REPORTING_SERVER_ID,
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of(),
            List.of()
        ).payload().payloadHash();

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void payloadHashIsStableForSameInputs() {
        String firstHash = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of("A", "b"), List.of("r1")).payload().payloadHash();
        String secondHash = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of("A", "b"), List.of("r1")).payload().payloadHash();

        assertEquals(firstHash, secondHash);
    }

    @Test
    void filtersBlankCoalescedReasons() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of("", " ", "PLAYER_ARRIVED"),
            List.of()
        ).payload();

        assertEquals(List.of("PLAYER_ARRIVED"), payload.coalescedChangeReasons());
    }

    @Test
    void sortsCoalescedReasonsAccordingToCurrentBehavior() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of("z_REASON", "Alpha", "beta"),
            List.of()
        ).payload();

        assertEquals(List.of("Alpha", "beta", "z_REASON"), payload.coalescedChangeReasons());
    }

    @Test
    void usesExplicitPrimaryChangeReasonWhenPresent() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "PLAYER_ARRIVED",
            List.of("MATCH_STARTED"),
            List.of()
        ).payload();

        assertEquals("PLAYER_ARRIVED", payload.primaryChangeReason());
    }

    @Test
    void fallsBackToLastCoalescedReasonWhenPrimaryBlank() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "",
            List.of("beta", "Alpha"),
            List.of()
        ).payload();

        assertEquals("beta", payload.primaryChangeReason());
    }

    @Test
    void fallsBackToMatchStartedWhenPrimaryAndCoalescedReasonsBlank() {
        BackendMatchAdmissionStatePayload payload = build(baseMatch(30), evaluation(), "", List.of("", " "), List.of()).payload();

        assertEquals(AdmissionStatePayloadBuilder.CHANGE_REASON_MATCH_STARTED, payload.primaryChangeReason());
    }

    @Test
    void filtersBlankConsumedAdmissionReservationIds() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of(),
            List.of("", " ", "reservation-1")
        ).payload();

        assertEquals(List.of("reservation-1"), payload.consumedAdmissionReservationIds());
    }

    @Test
    void trimsConsumedAdmissionReservationIds() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of(),
            List.of(" reservation-1 ")
        ).payload();

        assertEquals(List.of("reservation-1"), payload.consumedAdmissionReservationIds());
    }

    @Test
    void preservesCaseSensitiveConsumedAdmissionReservationIds() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of(),
            List.of(" Reservation-A ", "reservation-a")
        ).payload();

        assertEquals(List.of("Reservation-A", "reservation-a"), payload.consumedAdmissionReservationIds());
    }

    @Test
    void sortsConsumedAdmissionReservationIdsAccordingToCurrentBehavior() {
        BackendMatchAdmissionStatePayload payload = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of(),
            List.of("z-token", "Alpha-token", "beta-token")
        ).payload();

        assertEquals(List.of("Alpha-token", "beta-token", "z-token"), payload.consumedAdmissionReservationIds());
    }

    @Test
    void includesConsumedAdmissionReservationIdsInBuildResult() {
        AdmissionStatePayloadBuildResult result = build(
            baseMatch(30),
            evaluation(),
            "MATCH_STARTED",
            List.of(),
            List.of(" reservation-2 ", "reservation-1")
        );

        assertEquals(result.payload().consumedAdmissionReservationIds(), result.consumedAdmissionReservationIdsIncluded());
    }

    @Test
    void clampsBackfillWindowSecondsToNonNegative() {
        BackendMatchAdmissionStatePayload payload = build(baseMatch(-5), evaluation(), "MATCH_STARTED", List.of(), List.of()).payload();

        assertEquals(0, payload.backfillWindowSeconds());
    }

    @Test
    void usesProvidedStateUpdateIdAndSequence() {
        BackendMatchAdmissionStatePayload payload = build(baseMatch(30), evaluation(), "MATCH_STARTED", List.of(), List.of()).payload();

        assertEquals(STATE_UPDATE_ID, payload.stateUpdateId());
        assertEquals(SEQUENCE, payload.admissionStateSequence());
    }

    private AdmissionStatePayloadBuildResult build(
        ArenaActiveMatch match,
        AdmissionStateEvaluation evaluation,
        String primaryChangeReason,
        List<String> coalescedChangeReasons,
        List<String> pendingConsumedAdmissionReservationIds
    ) {
        return builder.build(
            SCHEMA_VERSION,
            STATE_UPDATE_ID,
            SEQUENCE,
            SENT_AT,
            EXPIRES_AT,
            REPORTING_SERVER_ID,
            match,
            evaluation,
            primaryChangeReason,
            coalescedChangeReasons,
            pendingConsumedAdmissionReservationIds
        );
    }

    private static AdmissionStateEvaluation evaluation() {
        return new AdmissionStateEvaluation(
            AdmissionStateEvaluator.STATUS_ACTIVE,
            true,
            31_000L,
            4,
            2,
            2,
            2,
            1,
            1,
            false,
            "",
            "MATCH_STARTED",
            false
        );
    }

    private static BackendMatchAdmissionStatePayload withPayloadHash(BackendMatchAdmissionStatePayload payload, String payloadHash) {
        return new BackendMatchAdmissionStatePayload(
            payload.schemaVersion(),
            payload.stateUpdateId(),
            payload.admissionStateSequence(),
            payloadHash,
            payload.sentAtEpochMs(),
            payload.stateExpiresAtEpochMs(),
            payload.reportingServerId(),
            payload.matchId(),
            payload.externalMatchId(),
            payload.queueId(),
            payload.arenaId(),
            payload.backfillEnabled(),
            payload.backfillMode(),
            payload.backfillWindowSeconds(),
            payload.matchLifecycleStatus(),
            payload.admissionOpen(),
            payload.admissionOpenUntilEpochMs(),
            payload.admissionCapacity(),
            payload.admittedSlotCount(),
            payload.availableAdmissionSlots(),
            payload.initialRosterSize(),
            payload.arrivedInitialPlayerCount(),
            payload.unfilledInitialRosterCount(),
            payload.consumedAdmissionReservationIds(),
            payload.admissionReportingClosed(),
            payload.admissionReportingCloseReason(),
            payload.primaryChangeReason(),
            payload.coalescedChangeReasons()
        );
    }

    private static ArenaActiveMatch baseMatch(int backfillWindowSeconds) {
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
            LastPlayerAliveArenaMatchResolutionTrigger.ID,
            "rules-default",
            "assignment-1",
            "INITIAL_MATCH",
            "external-match-1",
            ArenaMatchSource.BACKEND_DRIVEN.id(),
            1,
            4,
            true,
            QueueBackfillMode.ACTIVE_WINDOW.id(),
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
        );
    }
}
