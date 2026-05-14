package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.backend.BackendResultStore;
import io.github.hyjn.nexori.plugin.backend.payload.BackendResultPayload;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BackendResultPayloadBuilderTest {

    private static final int SCHEMA_VERSION = 1;
    private static final long SENT_AT = 1_000L;
    private static final String REPORTING_SERVER_ID = "server-1";
    private static final long ENDED_AT = 2_000L;

    private final Gson gson = new Gson();
    private final BackendResultPayloadBuilder builder = new BackendResultPayloadBuilder();

    @Test
    void buildsPayloadWithExpectedRecordFields() {
        BackendResultPayload payload = buildPayload(baseRecord(customData()));

        assertEquals(SCHEMA_VERSION, payload.schemaVersion());
        assertEquals("result-1", payload.resultId());
        assertEquals(SENT_AT, payload.sentAtEpochMs());
        assertEquals(REPORTING_SERVER_ID, payload.serverId());
        assertEquals("local-match-1", payload.localMatchId());
        assertEquals("external-match-1", payload.externalMatchId());
        assertEquals("assignment-1", payload.assignmentId());
        assertEquals("queue-1", payload.queueId());
        assertEquals("arena-1", payload.arenaId());
        assertEquals("rules-1", payload.rulesEngineId());
        assertEquals("finished", payload.reason());
        assertEquals(ENDED_AT, payload.endedAtEpochMs());
    }

    @Test
    void preservesPlayerOrder() {
        BackendResultPayload payload = buildPayload(baseRecord(customData()));

        assertEquals("player-1", payload.players().get(0).playerUuid());
        assertEquals("player-2", payload.players().get(1).playerUuid());
    }

    @Test
    void mapsPlayerResultsToPayloadPlayers() {
        BackendResultPayload payload = buildPayload(baseRecord(customData()));

        assertEquals("WIN", payload.players().get(0).outcome());
        assertEquals("winner", payload.players().get(0).reason());
        assertEquals("LOSS", payload.players().get(1).outcome());
        assertEquals("eliminated", payload.players().get(1).reason());
    }

    @Test
    void preservesAssignmentIdsByPlayerUuid() {
        BackendResultPayload payload = buildPayload(baseRecord(customData()));

        assertEquals(Map.of(
            "player-1", "assignment-player-1",
            "player-2", "assignment-player-2"
        ), payload.assignmentIdsByPlayerUuid());
    }

    @Test
    void preservesMetadata() {
        BackendResultPayload payload = buildPayload(baseRecord(customData()));

        assertEquals(Map.of("map", "zone-alpha", "mode", "ctz"), payload.metadata());
    }

    @Test
    void usesEmptyCustomDataWhenRecordCustomDataIsNull() {
        BackendResultPayload payload = buildPayload(baseRecord(null));

        assertTrue(payload.customData().entrySet().isEmpty());
    }

    @Test
    void deepCopiesCustomDataWhenPresent() {
        JsonObject customData = customData();
        BackendResultPayload payload = buildPayload(baseRecord(customData));
        customData.addProperty("round", 99);

        assertNotSame(customData, payload.customData());
        assertEquals(3, payload.customData().get("round").getAsInt());
    }

    @Test
    void usesProvidedSchemaVersionSentAtAndReportingServerId() {
        BackendResultPayload payload = buildPayload(baseRecord(customData()));

        assertEquals(SCHEMA_VERSION, payload.schemaVersion());
        assertEquals(SENT_AT, payload.sentAtEpochMs());
        assertEquals(REPORTING_SERVER_ID, payload.serverId());
    }

    @Test
    void serializesBodyIfBuildResultIncludesBody() {
        BackendResultPayloadBuildResult result = build(baseRecord(customData()));
        BackendResultPayload parsedPayload = gson.fromJson(result.body(), BackendResultPayload.class);

        assertEquals(result.payload().resultId(), parsedPayload.resultId());
        assertEquals(result.payload().serverId(), parsedPayload.serverId());
        assertEquals(result.payload().players().size(), parsedPayload.players().size());
        assertEquals(result.payload().customData().get("round").getAsInt(), parsedPayload.customData().get("round").getAsInt());
    }

    private BackendResultPayload buildPayload(BackendResultStore.BackendResultRecord record) {
        return build(record).payload();
    }

    private BackendResultPayloadBuildResult build(BackendResultStore.BackendResultRecord record) {
        return builder.build(SCHEMA_VERSION, SENT_AT, REPORTING_SERVER_ID, record);
    }

    private static JsonObject customData() {
        JsonObject customData = new JsonObject();
        customData.addProperty("round", 3);
        customData.addProperty("overtime", false);
        return customData;
    }

    private static BackendResultStore.BackendResultRecord baseRecord(JsonObject customData) {
        return new BackendResultStore.BackendResultRecord(
            "result-1",
            "local-match-1",
            "external-match-1",
            "assignment-1",
            Map.of(
                "player-1", "assignment-player-1",
                "player-2", "assignment-player-2"
            ),
            "queue-1",
            "arena-1",
            "rules-1",
            List.of(
                new BackendResultStore.BackendResultPlayerRecord("player-1", "WIN", "winner"),
                new BackendResultStore.BackendResultPlayerRecord("player-2", "LOSS", "eliminated")
            ),
            "finished",
            Map.of("map", "zone-alpha", "mode", "ctz"),
            customData,
            "payload-hash-1",
            BackendResultStore.BackendResultStatus.PENDING.name(),
            0,
            500L,
            500L,
            ENDED_AT,
            0L,
            0L,
            "",
            "",
            0,
            ""
        );
    }
}
