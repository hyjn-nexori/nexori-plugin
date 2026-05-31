package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.BackendMatchmakingConfig;
import io.github.hyjn.nexori.plugin.backend.BackendResultStore;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerResolutionOutcome;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendResultEnqueuePlannerTest {

    private static final String RESULT_ID = "result-1";
    private static final String PAYLOAD_HASH = "sha256:payload";
    private static final String REASON = "match finished";
    private static final long ENDED_AT = 10_000L;
    private static final long CREATED_AT = 1_000L;
    private static final long UPDATED_AT = 2_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BackendResultEnqueuePlanner planner = new BackendResultEnqueuePlanner();

    @Test
    void disabledWhenResultReportingDisabled() {
        BackendResultEnqueuePlan plan = plan(config(false, "http://127.0.0.1:8000", "token"), match("external-match-1"));

        assertEquals(BackendResultEnqueuePlan.Outcome.DISABLED, plan.outcome());
        assertEquals("Result reporting is disabled.", plan.message());
        assertNull(plan.record());
    }

    @Test
    void disabledWhenBaseUrlMissing() {
        BackendResultEnqueuePlan plan = plan(config(true, "", "token"), match("external-match-1"));

        assertEquals(BackendResultEnqueuePlan.Outcome.DISABLED, plan.outcome());
        assertEquals("Result reporting requires baseUrl and serverToken.", plan.message());
        assertNull(plan.record());
    }

    @Test
    void disabledWhenServerTokenMissing() {
        BackendResultEnqueuePlan plan = plan(config(true, "http://127.0.0.1:8000", ""), match("external-match-1"));

        assertEquals(BackendResultEnqueuePlan.Outcome.DISABLED, plan.outcome());
        assertEquals("Result reporting requires baseUrl and serverToken.", plan.message());
        assertNull(plan.record());
    }

    @Test
    void rejectsMatchWithoutExternalMatchId() {
        BackendResultEnqueuePlan plan = plan(enabledConfig(), match("   "));

        assertEquals(BackendResultEnqueuePlan.Outcome.EXTERNAL_MATCH_MISSING, plan.outcome());
        assertEquals("Match has no externalMatchId.", plan.message());
        assertNull(plan.record());
    }

    @Test
    void buildsPendingRecordWithExpectedMatchFields() {
        BackendResultStore.BackendResultRecord record = record();

        assertEquals("local-match-1", record.localMatchId());
        assertEquals("external-match-1", record.externalMatchId());
        assertEquals("assignment-1", record.assignmentId());
        assertEquals("queue-1", record.queueId());
        assertEquals("arena-1", record.arenaId());
        assertEquals("rules-1", record.rulesEngineId());
    }

    @Test
    void usesProvidedResultId() {
        assertEquals(RESULT_ID, record().resultId());
    }

    @Test
    void mapsPlayerResultsInOrder() {
        List<BackendResultStore.BackendResultPlayerRecord> players = record().players();

        assertEquals(PLAYER_ONE.toString(), players.get(0).playerUuid());
        assertEquals("WIN", players.get(0).outcome());
        assertEquals("winner", players.get(0).reason());
        assertEquals(PLAYER_TWO.toString(), players.get(1).playerUuid());
        assertEquals("LOSS", players.get(1).outcome());
        assertEquals("eliminated", players.get(1).reason());
    }

    @Test
    void preservesMetadata() {
        assertEquals(metadata(), record().metadata());
    }

    @Test
    void deepCopiesCustomData() {
        JsonObject customData = customData();
        BackendResultStore.BackendResultRecord record = plan(
            enabledConfig(),
            match("external-match-1"),
            players(),
            metadata(),
            customData
        ).record();
        customData.addProperty("round", 99);

        assertNotSame(customData, record.customData());
        assertEquals(3, record.customData().get("round").getAsInt());
    }

    @Test
    void preservesPayloadHashAndReason() {
        BackendResultStore.BackendResultRecord record = record();

        assertEquals(PAYLOAD_HASH, record.payloadHash());
        assertEquals(REASON, record.reason());
    }

    @Test
    void mapsAssignmentIdsByPlayerUuidSortedByUuidString() {
        BackendResultStore.BackendResultRecord record = record(matchWithAssignmentIds(Map.of(
            PLAYER_THREE, "assignment-3",
            PLAYER_ONE, "assignment-1",
            PLAYER_TWO, "assignment-2"
        )));

        assertEquals(List.of(
            PLAYER_ONE.toString(),
            PLAYER_TWO.toString(),
            PLAYER_THREE.toString()
        ), new ArrayList<>(record.assignmentIdsByPlayerUuid().keySet()));
    }

    @Test
    void trimsAssignmentIdValues() {
        BackendResultStore.BackendResultRecord record = record(matchWithAssignmentIds(Map.of(
            PLAYER_ONE, " assignment-1 "
        )));

        assertEquals("assignment-1", record.assignmentIdsByPlayerUuid().get(PLAYER_ONE.toString()));
    }

    @Test
    void filtersBlankAssignmentIdValues() {
        LinkedHashMap<UUID, String> assignmentIds = new LinkedHashMap<>();
        assignmentIds.put(PLAYER_ONE, "assignment-1");
        assignmentIds.put(PLAYER_TWO, "   ");

        BackendResultStore.BackendResultRecord record = record(matchWithAssignmentIds(assignmentIds));

        assertEquals(Map.of(PLAYER_ONE.toString(), "assignment-1"), record.assignmentIdsByPlayerUuid());
        assertFalse(record.assignmentIdsByPlayerUuid().containsKey(PLAYER_TWO.toString()));
    }

    @Test
    void setsPendingStatusAttemptCountsAndTimestamps() {
        BackendResultStore.BackendResultRecord record = record();

        assertEquals(BackendResultStore.BackendResultStatus.PENDING.name(), record.status());
        assertEquals(0, record.attemptCount());
        assertEquals(ENDED_AT, record.createdAtEpochMs());
        assertEquals(ENDED_AT, record.updatedAtEpochMs());
        assertEquals(ENDED_AT, record.endedAtEpochMs());
        assertEquals(0L, record.nextAttemptAtEpochMs());
        assertEquals(0L, record.acknowledgedAtEpochMs());
        assertEquals("", record.lastErrorClass());
        assertEquals("", record.lastErrorMessage());
        assertEquals(0, record.lastStatusCode());
        assertEquals("", record.lastBackendStatus());
    }

    @Test
    void doesNotUseStoreHttpOrLoggerDependencies() {
        assertEquals(0, BackendResultEnqueuePlanner.class.getDeclaredFields().length);
    }

    private BackendResultStore.BackendResultRecord record() {
        return record(match("external-match-1"));
    }

    private BackendResultStore.BackendResultRecord record(ArenaActiveMatch match) {
        BackendResultEnqueuePlan plan = plan(enabledConfig(), match);

        assertEquals(BackendResultEnqueuePlan.Outcome.STORE_PENDING, plan.outcome());
        return plan.record();
    }

    private BackendResultEnqueuePlan plan(BackendMatchmakingConfig config, ArenaActiveMatch match) {
        return plan(config, match, players(), metadata(), customData());
    }

    private BackendResultEnqueuePlan plan(
        BackendMatchmakingConfig config,
        ArenaActiveMatch match,
        List<ArenaMatchService.SubmitMatchPlayerResult> players,
        Map<String, String> metadata,
        JsonObject customData
    ) {
        return planner.plan(
            config,
            match,
            players,
            metadata,
            customData,
            REASON,
            PAYLOAD_HASH,
            RESULT_ID,
            ENDED_AT
        );
    }

    private static BackendMatchmakingConfig enabledConfig() {
        return config(true, "http://127.0.0.1:8000", "token");
    }

    private static BackendMatchmakingConfig config(boolean resultReportingEnabled, String baseUrl, String serverToken) {
        return new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            baseUrl,
            serverToken,
            1_000L,
            "local",
            3_000L,
            resultReportingEnabled,
            5_000L
        );
    }

    private static List<ArenaMatchService.SubmitMatchPlayerResult> players() {
        return List.of(
            new ArenaMatchService.SubmitMatchPlayerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "WIN", "winner"),
            new ArenaMatchService.SubmitMatchPlayerResult(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, "LOSS", "eliminated")
        );
    }

    private static Map<String, String> metadata() {
        return Map.of("map", "zone-alpha", "mode", "ctz");
    }

    private static JsonObject customData() {
        JsonObject customData = new JsonObject();
        customData.addProperty("round", 3);
        customData.addProperty("overtime", false);
        return customData;
    }

    private static ArenaActiveMatch match(String externalMatchId) {
        return matchWithAssignmentIds(Map.of(PLAYER_ONE, "assignment-player-1", PLAYER_TWO, "assignment-player-2"), externalMatchId);
    }

    private static ArenaActiveMatch matchWithAssignmentIds(Map<UUID, String> assignmentIdsByPlayerUuid) {
        return matchWithAssignmentIds(assignmentIdsByPlayerUuid, "external-match-1");
    }

    private static ArenaActiveMatch matchWithAssignmentIds(Map<UUID, String> assignmentIdsByPlayerUuid, String externalMatchId) {
        return new ArenaActiveMatch(
            "local-match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby.example:19132",
            "lobby-1.natural_spawn",
            "keep_inventory",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "",
            "rules-1",
            "assignment-1",
            "INITIAL_MATCH",
            externalMatchId,
            ArenaMatchSource.BACKEND_DRIVEN.id(),
            1,
            2,
            false,
            QueueBackfillMode.NONE.id(),
            0,
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(),
            List.of(),
            assignmentIdsByPlayerUuid,
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
            UPDATED_AT,
            ""
        );
    }
}
