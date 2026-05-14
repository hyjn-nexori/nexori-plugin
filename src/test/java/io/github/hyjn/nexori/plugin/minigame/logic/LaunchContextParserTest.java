package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LaunchContextParserTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final LaunchContextParser parser = new LaunchContextParser();

    @Test
    void parsesValidInitialMatchContext() {
        LaunchContextData context = parser.parse(validInitialContext());

        assertEquals("match-one", context.matchId());
        assertEquals("queue-one", context.queueId());
        assertEquals("arena-one", context.arenaId());
        assertEquals("lobby-one", context.originLobbyId());
        assertEquals("return.example:1234", context.returnConnectionAddress());
        assertEquals("lobby-one.natural_spawn", context.returnFallbackTargetId());
        assertEquals("keep_inventory", context.launchTravelProfileId());
        assertEquals("template-one", context.instanceTemplateId());
        assertEquals("last-player-alive", context.matchResolutionTriggerId());
        assertEquals("rules.Engine-1", context.rulesEngineId());
        assertEquals("assignment-one", context.assignmentId());
        assertEquals("INITIAL_MATCH", context.assignmentType());
        assertEquals("external-one", context.externalMatchId());
        assertEquals(ArenaMatchSource.BACKEND_DRIVEN.id(), context.matchSource());
        assertEquals(1, context.admissionPolicySchemaVersion());
        assertEquals(4, context.admissionCapacity());
        assertTrue(context.backfillEnabled());
        assertEquals(QueueBackfillMode.ACTIVE_WINDOW.id(), context.backfillMode());
        assertEquals(30, context.backfillWindowSeconds());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), context.expectedPlayerUuids());
        assertEquals(2, context.expectedPlayerCount());
    }

    @Test
    void parsesValidBackfillContext() {
        JsonObject root = validInitialContext();
        root.addProperty("assignmentType", "BACKFILL");
        root.addProperty("playerUuid", PLAYER_ONE.toString());
        root.addProperty("admissionReservationId", " Reservation-Token ");
        root.addProperty("admissionExpiresAtEpochMs", 123_456L);
        root.addProperty("reportingServerId", "server-one");

        LaunchContextData context = parser.parse(root);

        assertEquals("BACKFILL", context.assignmentType());
        assertEquals(PLAYER_ONE, context.playerUuid());
        assertEquals("Reservation-Token", context.admissionReservationId());
        assertEquals(123_456L, context.admissionExpiresAtEpochMs());
        assertEquals("server-one", context.reportingServerId());
    }

    @Test
    void rejectsMissingRequiredMatchId() {
        JsonObject root = validInitialContext();
        root.remove("matchId");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(root));

        assertEquals("Missing required minigame context field 'matchId'.", exception.getMessage());
    }

    @Test
    void rejectsBlankRequiredMatchId() {
        JsonObject root = validInitialContext();
        root.addProperty("matchId", " ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(root));

        assertEquals("Minigame context field 'matchId' cannot be blank.", exception.getMessage());
    }

    @Test
    void rejectsExpectedPlayerUuidsThatIsNotArray() {
        JsonObject root = validInitialContext();
        root.addProperty("expectedPlayerUuids", PLAYER_ONE.toString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(root));

        assertEquals("Minigame context field 'expectedPlayerUuids' must be an array.", exception.getMessage());
    }

    @Test
    void rejectsInvalidExpectedPlayerUuid() {
        JsonObject root = validInitialContext();
        JsonArray expected = new JsonArray();
        expected.add("not-a-uuid");
        root.add("expectedPlayerUuids", expected);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(root));

        assertEquals("Minigame context field 'expectedPlayerUuids' contains invalid UUID 'not-a-uuid'.", exception.getMessage());
    }

    @Test
    void canonicalizesExpectedPlayerUuids() {
        JsonObject root = validInitialContext();
        JsonArray expected = new JsonArray();
        expected.add(PLAYER_TWO.toString());
        expected.add(PLAYER_ONE.toString());
        expected.add(PLAYER_TWO.toString());
        root.add("expectedPlayerUuids", expected);

        LaunchContextData context = parser.parse(root);

        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), context.expectedPlayerUuids());
    }

    @Test
    void rejectsInvalidPlayerUuid() {
        JsonObject root = validInitialContext();
        root.addProperty("playerUuid", "not-a-uuid");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(root));

        assertEquals("Minigame context field 'playerUuid' contains invalid UUID.", exception.getMessage());
    }

    @Test
    void defaultsOptionalFields() {
        LaunchContextData context = parser.parse(minimalContext());

        assertEquals(ArenaDefinition.NO_INSTANCE_TEMPLATE_ID, context.instanceTemplateId());
        assertEquals(ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID, context.matchResolutionTriggerId());
        assertEquals("", context.rulesEngineId());
        assertEquals("", context.assignmentId());
        assertEquals("INITIAL_MATCH", context.assignmentType());
        assertEquals("", context.externalMatchId());
        assertEquals(ArenaMatchSource.defaultSource().id(), context.matchSource());
        assertEquals(0, context.admissionPolicySchemaVersion());
        assertEquals(0, context.admissionCapacity());
        assertFalse(context.backfillEnabled());
        assertEquals(QueueBackfillMode.defaultMode().id(), context.backfillMode());
        assertEquals(0, context.backfillWindowSeconds());
        assertEquals(List.of(), context.expectedPlayerUuids());
        assertEquals(0, context.expectedPlayerCount());
        assertNull(context.playerUuid());
        assertEquals("", context.admissionReservationId());
        assertEquals(0L, context.admissionExpiresAtEpochMs());
        assertEquals("", context.reportingServerId());
    }

    @Test
    void optionalBlankFieldsFallBackToDefaults() {
        JsonObject root = validInitialContext();
        root.addProperty("instanceTemplateId", " ");
        root.addProperty("matchResolutionTriggerId", " ");
        root.addProperty("assignmentId", " ");
        root.addProperty("assignmentType", " ");
        root.addProperty("externalMatchId", " ");
        root.addProperty("matchSource", " ");
        root.addProperty("backfillMode", " ");
        root.addProperty("admissionReservationId", " ");
        root.addProperty("reportingServerId", " ");

        LaunchContextData context = parser.parse(root);

        assertEquals(ArenaDefinition.NO_INSTANCE_TEMPLATE_ID, context.instanceTemplateId());
        assertEquals(ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID, context.matchResolutionTriggerId());
        assertEquals("", context.assignmentId());
        assertEquals("INITIAL_MATCH", context.assignmentType());
        assertEquals("", context.externalMatchId());
        assertEquals(ArenaMatchSource.defaultSource().id(), context.matchSource());
        assertEquals(QueueBackfillMode.defaultMode().id(), context.backfillMode());
        assertEquals("", context.admissionReservationId());
        assertEquals("", context.reportingServerId());
    }

    @Test
    void expectedPlayerUuidsIgnoresNullAndBlankEntries() {
        JsonObject root = validInitialContext();
        JsonArray expected = new JsonArray();
        expected.add(JsonNull.INSTANCE);
        expected.add("");
        expected.add("   ");
        expected.add(PLAYER_ONE.toString());
        root.add("expectedPlayerUuids", expected);

        LaunchContextData context = parser.parse(root);

        assertEquals(List.of(PLAYER_ONE), context.expectedPlayerUuids());
    }

    @Test
    void optionalPlayerUuidWithWhitespacePreservesCurrentInvalidBehavior() {
        JsonObject root = validInitialContext();
        root.addProperty("playerUuid", " " + PLAYER_ONE + " ");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parser.parse(root));

        assertEquals("Minigame context field 'playerUuid' contains invalid UUID.", exception.getMessage());
    }

    @Test
    void clampsNegativeNumericFields() {
        JsonObject root = validInitialContext();
        root.addProperty("admissionPolicySchemaVersion", -1);
        root.addProperty("admissionCapacity", -2);
        root.addProperty("backfillWindowSeconds", -3);
        root.addProperty("expectedPlayerCount", -4);
        root.addProperty("admissionExpiresAtEpochMs", -5L);

        LaunchContextData context = parser.parse(root);

        assertEquals(0, context.admissionPolicySchemaVersion());
        assertEquals(0, context.admissionCapacity());
        assertEquals(0, context.backfillWindowSeconds());
        assertEquals(0, context.expectedPlayerCount());
        assertEquals(0L, context.admissionExpiresAtEpochMs());
    }

    @Test
    void lowercasesLaunchTravelProfileId() {
        JsonObject root = validInitialContext();
        root.addProperty("launchTravelProfileId", " Keep_Inventory ");

        LaunchContextData context = parser.parse(root);

        assertEquals("keep_inventory", context.launchTravelProfileId());
        assertEquals("keep_inventory", context.playerReturnTarget().launchTravelProfileId());
    }

    @Test
    void preservesCaseSensitiveAdmissionReservationId() {
        JsonObject root = validInitialContext();
        root.addProperty("admissionReservationId", "Reservation-Aa");

        LaunchContextData context = parser.parse(root);

        assertEquals("Reservation-Aa", context.admissionReservationId());
    }

    @Test
    void trimsAdmissionReservationIdWithoutLowercasing() {
        JsonObject root = validInitialContext();
        root.addProperty("admissionReservationId", " Reservation-Aa ");

        LaunchContextData context = parser.parse(root);

        assertEquals("Reservation-Aa", context.admissionReservationId());
    }

    @Test
    void buildsPlayerReturnTarget() {
        LaunchContextData context = parser.parse(validInitialContext());

        assertEquals("lobby-one", context.playerReturnTarget().originLobbyId());
        assertEquals("return.example:1234", context.playerReturnTarget().returnConnectionAddress());
        assertEquals("lobby-one.natural_spawn", context.playerReturnTarget().returnFallbackTargetId());
        assertEquals("keep_inventory", context.playerReturnTarget().launchTravelProfileId());
    }

    @Test
    void usesInstanceTemplateReturnsTrueOnlyForRealTemplate() {
        LaunchContextData context = parser.parse(validInitialContext());

        assertTrue(context.usesInstanceTemplate());
    }

    @Test
    void usesInstanceTemplateReturnsFalseForNoInstanceTemplate() {
        LaunchContextData context = parser.parse(minimalContext());

        assertFalse(context.usesInstanceTemplate());
    }

    private static JsonObject validInitialContext() {
        JsonObject root = minimalContext();
        root.addProperty("instanceTemplateId", "template-one");
        root.addProperty("matchResolutionTriggerId", "Last-Player-Alive");
        root.addProperty("rulesEngineId", "rules.Engine-1");
        root.addProperty("assignmentId", "assignment-one");
        root.addProperty("assignmentType", "INITIAL_MATCH");
        root.addProperty("externalMatchId", "external-one");
        root.addProperty("matchSource", ArenaMatchSource.BACKEND_DRIVEN.id());
        root.addProperty("admissionPolicySchemaVersion", 1);
        root.addProperty("admissionCapacity", 4);
        root.addProperty("backfillEnabled", true);
        root.addProperty("backfillMode", QueueBackfillMode.ACTIVE_WINDOW.id());
        root.addProperty("backfillWindowSeconds", 30);
        root.addProperty("expectedPlayerCount", 2);
        JsonArray expected = new JsonArray();
        expected.add(PLAYER_ONE.toString());
        expected.add(PLAYER_TWO.toString());
        root.add("expectedPlayerUuids", expected);
        return root;
    }

    private static JsonObject minimalContext() {
        JsonObject root = new JsonObject();
        root.addProperty("matchId", " Match-One ");
        root.addProperty("queueId", " Queue-One ");
        root.addProperty("arenaId", " Arena-One ");
        root.addProperty("originLobbyId", " Lobby-One ");
        root.addProperty("returnConnectionAddress", "return.example:1234");
        root.addProperty("returnFallbackTargetId", "lobby-one.natural_spawn");
        root.addProperty("launchTravelProfileId", "KEEP_INVENTORY");
        return root;
    }
}
