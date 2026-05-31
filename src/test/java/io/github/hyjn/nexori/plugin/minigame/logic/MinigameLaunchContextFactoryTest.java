package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinigameLaunchContextFactoryTest {

    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String RETURN_CONNECTION_ADDRESS = "lobby.example:19132";

    private final Gson gson = new Gson();
    private final MinigameLaunchContextFactory factory = new MinigameLaunchContextFactory();

    @Test
    void buildsInitialMatchLaunchContext() {
        MinigameLaunchContextBuildResult result = buildInitial(List.of(PLAYER_ONE, PLAYER_TWO), "external-match-1", arena("arena-1", "target-1"));
        JsonObject context = context(result);

        assertEquals("minigame.launch", context.get("flowType").getAsString());
        assertEquals(MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH, context.get("assignmentType").getAsString());
        assertEquals("assignment-1", context.get("assignmentId").getAsString());
        assertEquals("match-1", context.get("matchId").getAsString());
        assertEquals("external-match-1", context.get("externalMatchId").getAsString());
        assertEquals("queue-1", context.get("queueId").getAsString());
        assertEquals("arena-1", context.get("arenaId").getAsString());
        assertEquals(2, context.get("expectedPlayerCount").getAsInt());
        assertEquals(1, context.get("admissionPolicySchemaVersion").getAsInt());
        assertEquals("BACKEND_DRIVEN", context.get("matchSource").getAsString());
        JsonObject afkPolicy = context.getAsJsonObject("afkDetectionPolicy");
        assertFalse(afkPolicy.get("enabled").getAsBoolean());
        assertEquals(30, afkPolicy.get("inactivityTimeoutSeconds").getAsInt());
        assertEquals(2, result.matchSessionState().expectedPlayerUuids().size());
    }

    @Test
    void initialContextCopiesArenaAfkDetectionPolicy() {
        ArenaDefinition arena = new ArenaDefinition(
            "arena-1",
            "Arena One",
            "arena.example:19132",
            "target-1",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "rules-1",
            4,
            true,
            new AfkDetectionPolicy(true, 12)
        ).normalized();

        JsonObject afkPolicy = context(buildInitial(List.of(PLAYER_ONE), "external-match-1", arena))
            .getAsJsonObject("afkDetectionPolicy");

        assertTrue(afkPolicy.get("enabled").getAsBoolean());
        assertEquals(12, afkPolicy.get("inactivityTimeoutSeconds").getAsInt());
    }

    @Test
    void buildsBackfillLaunchContext() {
        MinigameLaunchContextBuildResult result = buildBackfill("external-match-1", " Reservation-A ");
        JsonObject context = context(result);

        assertEquals(MinigameLaunchContextFactory.ASSIGNMENT_TYPE_BACKFILL, context.get("assignmentType").getAsString());
        assertEquals("assignment-1", context.get("assignmentId").getAsString());
        assertEquals("match-1", context.get("matchId").getAsString());
        assertEquals("external-match-1", context.get("externalMatchId").getAsString());
        assertFalse(context.has("expectedPlayerUuids"));
        assertEquals(List.of(), result.matchSessionState().expectedPlayerUuids());
    }

    @Test
    void rejectsInitialMatchWithoutPlayers() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> factory.buildInitialMatchLaunchContext(
            queue(),
            arena("arena-1", "target-1"),
            List.of(),
            NOW,
            "assignment-1",
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            "match-1",
            "generated-match-1",
            "external-match-1",
            List.of(),
            List.of(),
            "server-1",
            RETURN_CONNECTION_ADDRESS,
            1
        ));

        assertEquals("Cannot build a launch context for an empty ready batch.", exception.getMessage());
    }

    @Test
    void preservesExpectedPlayerCanonicalOrderAccordingToCurrentBehavior() {
        MinigameLaunchContextBuildResult result = buildInitial(List.of(PLAYER_TWO, PLAYER_ONE), "external-match-1", arena("arena-1", "target-1"));
        JsonArray expectedPlayerUuids = context(result).getAsJsonArray("expectedPlayerUuids");

        assertEquals(PLAYER_ONE.toString(), expectedPlayerUuids.get(0).getAsString());
        assertEquals(PLAYER_TWO.toString(), expectedPlayerUuids.get(1).getAsString());
    }

    @Test
    void includesAssignmentId() {
        JsonObject context = context(buildInitial(List.of(PLAYER_ONE), "external-match-1", arena("arena-1", "target-1")));

        assertEquals("assignment-1", context.get("assignmentId").getAsString());
    }

    @Test
    void includesAdmissionReservationIdForBackfill() {
        MinigameLaunchContextBuildResult result = buildBackfill("external-match-1", "reservation-1");
        String playerContextJson = factory.backfillContextJsonForPlayer(
            result.contextJson(),
            member(PLAYER_ONE, "lobby-1"),
            result.playerReturnTargetsByUuid(),
            result.assignmentPlayerTicketsByUuid(),
            result.reportingServerId()
        );
        JsonObject playerContext = gson.fromJson(playerContextJson, JsonObject.class);

        assertEquals("reservation-1", playerContext.get("admissionReservationId").getAsString());
        assertEquals(5_000L, playerContext.get("admissionExpiresAtEpochMs").getAsLong());
    }

    @Test
    void preservesCaseSensitiveAdmissionReservationId() {
        MinigameLaunchContextBuildResult result = buildBackfill("external-match-1", "Reservation-A");
        JsonObject playerContext = backfillPlayerContext(result);

        assertEquals("Reservation-A", playerContext.get("admissionReservationId").getAsString());
    }

    @Test
    void trimsAdmissionReservationIdWithoutLowercasing() {
        MinigameLaunchContextBuildResult result = buildBackfill("external-match-1", " Reservation-A ");
        JsonObject playerContext = backfillPlayerContext(result);

        assertEquals("Reservation-A", playerContext.get("admissionReservationId").getAsString());
    }

    @Test
    void includesExternalMatchIdWhenPresent() {
        JsonObject context = context(buildInitial(List.of(PLAYER_ONE), "external-match-1", arena("arena-1", "target-1")));

        assertEquals("external-match-1", context.get("externalMatchId").getAsString());
    }

    @Test
    void allowsBlankExternalMatchIdIfCurrentBehaviorAllowsIt() {
        JsonObject context = context(buildBackfill("", "reservation-1"));

        assertFalse(context.has("externalMatchId"));
    }

    @Test
    void allowsMissingDestinationTargetAccordingToCurrentBehavior() {
        JsonObject context = context(buildInitial(List.of(PLAYER_ONE), "external-match-1", arena("arena-1", "")));

        assertEquals("arena-1", context.get("arenaId").getAsString());
    }

    @Test
    void contextJsonWithLaunchIndexAddsPerPlayerReturnTargetFields() {
        MinigameLaunchContextBuildResult result = buildInitial(List.of(PLAYER_ONE), "external-match-1", arena("arena-1", "target-1"));
        JsonObject context = gson.fromJson(factory.contextJsonWithLaunchIndex(
            result.contextJson(),
            3,
            member(PLAYER_ONE, "lobby-1"),
            result.playerReturnTargetsByUuid(),
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            result.assignmentPlayerTicketsByUuid(),
            result.reportingServerId()
        ), JsonObject.class);

        assertEquals(3, context.get("launchIndex").getAsInt());
        assertEquals(MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH, context.get("assignmentType").getAsString());
        assertEquals("lobby-1", context.get("originLobbyId").getAsString());
        assertEquals(RETURN_CONNECTION_ADDRESS, context.get("returnConnectionAddress").getAsString());
        assertEquals("lobby-1.natural_spawn", context.get("returnFallbackTargetId").getAsString());
        assertEquals(queue().launchTravelProfileId(), context.get("launchTravelProfileId").getAsString());
    }

    @Test
    void contextJsonWithLaunchIndexClampsNegativeLaunchIndex() {
        MinigameLaunchContextBuildResult result = buildInitial(List.of(PLAYER_ONE), "external-match-1", arena("arena-1", "target-1"));
        JsonObject context = gson.fromJson(factory.contextJsonWithLaunchIndex(
            result.contextJson(),
            -5,
            member(PLAYER_ONE, "lobby-1"),
            result.playerReturnTargetsByUuid(),
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            result.assignmentPlayerTicketsByUuid(),
            result.reportingServerId()
        ), JsonObject.class);

        assertEquals(0, context.get("launchIndex").getAsInt());
    }

    @Test
    void contextJsonWithLaunchIndexThrowsWhenReturnTargetMissing() {
        MinigameLaunchContextBuildResult result = buildInitial(List.of(PLAYER_ONE), "external-match-1", arena("arena-1", "target-1"));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> factory.contextJsonWithLaunchIndex(
            result.contextJson(),
            0,
            member(PLAYER_ONE, "lobby-1"),
            Map.of(),
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            result.assignmentPlayerTicketsByUuid(),
            result.reportingServerId()
        ));

        assertEquals("Missing per-player return target for launched player " + PLAYER_ONE + ".", exception.getMessage());
    }

    @Test
    void backfillContextJsonForPlayerThrowsWhenTicketMissing() {
        MinigameLaunchContextBuildResult result = buildBackfill("external-match-1", "reservation-1");

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> factory.backfillContextJsonForPlayer(
            result.contextJson(),
            member(PLAYER_ONE, "lobby-1"),
            result.playerReturnTargetsByUuid(),
            Map.of(),
            result.reportingServerId()
        ));

        assertEquals("Missing backfill ticket for launched player " + PLAYER_ONE + ".", exception.getMessage());
    }

    @Test
    void initialLaunchRejectsBlankReturnConnectionAddress() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> factory.buildInitialMatchLaunchContext(
            queue(),
            arena("arena-1", "target-1"),
            List.of(member(PLAYER_ONE, "lobby-1")),
            NOW,
            "assignment-1",
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            "match-1",
            "generated-match-1",
            "external-match-1",
            List.of(PLAYER_ONE),
            List.of(),
            "server-1",
            " ",
            1
        ));

        assertEquals("This server does not have a local connection address configured for minigame return.", exception.getMessage());
    }

    @Test
    void backfillLaunchRejectsBlankReturnConnectionAddress() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> factory.buildBackfillLaunchContext(
            queue(),
            "arena-1",
            List.of(member(PLAYER_ONE, "lobby-1")),
            NOW,
            "assignment-1",
            "match-1",
            "external-match-1",
            List.of(new MinigameLaunchContextFactory.AssignmentPlayerTicket(PLAYER_ONE, "reservation-1", 5_000L)),
            "server-1",
            " "
        ));

        assertEquals("This server does not have a local connection address configured for minigame return.", exception.getMessage());
    }

    @Test
    void initialContextAddsServerEntryModeWhenArenaUsesInstanceTemplate() {
        ArenaDefinition arena = new ArenaDefinition(
            "arena-1",
            "Arena One",
            "arena.example:19132",
            "",
            "template-1",
            "rules-1",
            4,
            true
        ).normalized();

        JsonObject context = context(buildInitial(List.of(PLAYER_ONE), "external-match-1", arena));

        assertEquals("default_world_natural_spawn", context.get("serverEntryMode").getAsString());
    }

    @Test
    void initialContextClampsAdmissionCapacityToArenaCapacity() {
        JsonObject context = context(buildInitial(List.of(PLAYER_ONE), "external-match-1", arenaWithCapacity(2)));

        assertEquals(2, context.get("admissionCapacity").getAsInt());
    }

    @Test
    void initialContextClampsNegativeBackfillWindowSeconds() {
        JsonObject context = context(factory.buildInitialMatchLaunchContext(
            queueWithBackfillWindow(-10),
            arena("arena-1", "target-1"),
            List.of(member(PLAYER_ONE, "lobby-1")),
            NOW,
            "assignment-1",
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            "match-1",
            "generated-match-1",
            "external-match-1",
            List.of(PLAYER_ONE),
            List.of(),
            "server-1",
            RETURN_CONNECTION_ADDRESS,
            1
        ));

        assertEquals(0, context.get("backfillWindowSeconds").getAsInt());
    }

    private JsonObject context(MinigameLaunchContextBuildResult result) {
        return gson.fromJson(result.contextJson(), JsonObject.class);
    }

    private JsonObject backfillPlayerContext(MinigameLaunchContextBuildResult result) {
        return gson.fromJson(factory.backfillContextJsonForPlayer(
            result.contextJson(),
            member(PLAYER_ONE, "lobby-1"),
            result.playerReturnTargetsByUuid(),
            result.assignmentPlayerTicketsByUuid(),
            result.reportingServerId()
        ), JsonObject.class);
    }

    private MinigameLaunchContextBuildResult buildInitial(List<UUID> expectedPlayerUuids, String externalMatchId, ArenaDefinition arena) {
        return factory.buildInitialMatchLaunchContext(
            queue(),
            arena,
            List.of(member(PLAYER_ONE, "lobby-1"), member(PLAYER_TWO, "lobby-1")),
            NOW,
            "assignment-1",
            MinigameLaunchContextFactory.ASSIGNMENT_TYPE_INITIAL_MATCH,
            "match-1",
            "generated-match-1",
            externalMatchId,
            expectedPlayerUuids,
            List.of(),
            "server-1",
            RETURN_CONNECTION_ADDRESS,
            1
        );
    }

    private MinigameLaunchContextBuildResult buildBackfill(String externalMatchId, String admissionReservationId) {
        return factory.buildBackfillLaunchContext(
            queue(),
            "arena-1",
            List.of(member(PLAYER_ONE, "lobby-1")),
            NOW,
            "assignment-1",
            "match-1",
            externalMatchId,
            List.of(new MinigameLaunchContextFactory.AssignmentPlayerTicket(PLAYER_ONE, admissionReservationId, 5_000L)),
            "server-1",
            RETURN_CONNECTION_ADDRESS
        );
    }

    private static QueueMemberState member(UUID playerUuid, String sourceLobbyId) {
        return new QueueMemberState(playerUuid, "Player", sourceLobbyId, "portal-1", NOW).normalized();
    }

    private static QueueDefinition queue() {
        return queueWithBackfillWindow(30);
    }

    private static QueueDefinition queueWithBackfillWindow(int backfillWindowSeconds) {
        return new QueueDefinition(
            "queue-1",
            "Queue One",
            List.of("arena-1"),
            1,
            4,
            5,
            TravelProfileType.KEEP_INVENTORY.id(),
            QueueMatchmakingMode.BACKEND_DRIVEN.id(),
            true,
            true,
            QueueBackfillMode.ACTIVE_WINDOW.id(),
            backfillWindowSeconds
        ).normalized();
    }

    private static ArenaDefinition arena(String arenaId, String destinationTargetId) {
        return arenaWithCapacity(arenaId, destinationTargetId, 4);
    }

    private static ArenaDefinition arenaWithCapacity(int maxSupportedPlayers) {
        return arenaWithCapacity("arena-1", "target-1", maxSupportedPlayers);
    }

    private static ArenaDefinition arenaWithCapacity(String arenaId, String destinationTargetId, int maxSupportedPlayers) {
        return new ArenaDefinition(
            arenaId,
            "Arena One",
            "arena.example:19132",
            destinationTargetId,
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "rules-1",
            maxSupportedPlayers,
            true
        ).normalized();
    }
}
