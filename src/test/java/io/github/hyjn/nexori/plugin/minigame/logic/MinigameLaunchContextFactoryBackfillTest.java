package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MinigameLaunchContextFactoryBackfillTest {

    private static final Gson GSON = new Gson();
    private final MinigameLaunchContextFactory factory = new MinigameLaunchContextFactory();

    private static QueueDefinition makeQueue() {
        return new QueueDefinition(
            "skywars_queue",
            "Skywars Queue",
            List.of("skywars_arena"),
            2,
            2,
            30,
            "nexori_launch",
            "BACKEND_DRIVEN",
            true,
            true,
            "NONE",
            0
        );
    }

    private static ArenaDefinition makeArena() {
        return new ArenaDefinition(
            "skywars_arena",
            "Skywars",
            "localhost:25565",
            "skywars_arena.spawn",
            "skywars_nexori_template",
            "last_player_alive",
            "skywars_rules",
            2,
            true,
            new AfkDetectionPolicy(true, 30)
        );
    }

    private static QueueMemberState makeMember(UUID playerUuid) {
        return new QueueMemberState(
            playerUuid,
            "player_" + playerUuid.toString().substring(0, 4),
            "lobby_main",
            "portal_1",
            System.currentTimeMillis()
        );
    }

    @Test
    void backfillContextWithArenaIncludesInstanceTemplateId() {
        UUID player = UUID.fromString("11111111-1111-1111-1111-111111111111");
        QueueDefinition queue = makeQueue();
        ArenaDefinition arena = makeArena();
        QueueMemberState member = makeMember(player);
        List<MinigameLaunchContextFactory.AssignmentPlayerTicket> tickets = List.of(
            new MinigameLaunchContextFactory.AssignmentPlayerTicket(player, "res-abc", System.currentTimeMillis() + 60_000)
        );

        MinigameLaunchContextBuildResult result = factory.buildBackfillLaunchContext(
            queue, arena, "skywars_arena",
            List.of(member), System.currentTimeMillis(),
            "assign-1", "match-xyz", "ext-xyz",
            tickets, "reporting-server", "lobby:25565"
        );

        JsonObject context = GSON.fromJson(result.contextJson(), JsonObject.class);
        assertEquals("minigame.launch", context.get("flowType").getAsString());
        assertEquals("BACKFILL", context.get("assignmentType").getAsString());
        assertEquals("skywars_nexori_template", context.get("instanceTemplateId").getAsString());
        assertEquals("default_world_natural_spawn", context.get("serverEntryMode").getAsString());
        assertEquals("last_player_alive", context.get("matchResolutionTriggerId").getAsString());
        assertEquals("skywars_rules", context.get("rulesEngineId").getAsString());
        assertTrue(context.has("backfillEnabled"));
        assertTrue(context.has("backfillMode"));
        assertTrue(context.has("afkDetectionPolicy"));
    }

    @Test
    void backfillContextWithoutArenaOmitsInstanceFields() {
        UUID player = UUID.fromString("22222222-2222-2222-2222-222222222222");
        QueueDefinition queue = makeQueue();
        QueueMemberState member = makeMember(player);
        List<MinigameLaunchContextFactory.AssignmentPlayerTicket> tickets = List.of(
            new MinigameLaunchContextFactory.AssignmentPlayerTicket(player, "res-def", System.currentTimeMillis() + 60_000)
        );

        MinigameLaunchContextBuildResult result = factory.buildBackfillLaunchContext(
            queue, null, "skywars_arena",
            List.of(member), System.currentTimeMillis(),
            "assign-2", "match-abc", "ext-abc",
            tickets, "reporting-server", "lobby:25565"
        );

        JsonObject context = GSON.fromJson(result.contextJson(), JsonObject.class);
        assertEquals("BACKFILL", context.get("assignmentType").getAsString());
        assertFalse(context.has("instanceTemplateId"));
        assertFalse(context.has("serverEntryMode"));
    }

    @Test
    void backfillContextIsParsableByLaunchContextParser() {
        UUID player = UUID.fromString("33333333-3333-3333-3333-333333333333");
        QueueDefinition queue = makeQueue();
        ArenaDefinition arena = makeArena();
        QueueMemberState member = makeMember(player);
        List<MinigameLaunchContextFactory.AssignmentPlayerTicket> tickets = List.of(
            new MinigameLaunchContextFactory.AssignmentPlayerTicket(player, "res-ghi", System.currentTimeMillis() + 60_000)
        );

        MinigameLaunchContextBuildResult result = factory.buildBackfillLaunchContext(
            queue, arena, "skywars_arena",
            List.of(member), System.currentTimeMillis(),
            "assign-3", "match-parseable", "ext-parseable",
            tickets, "reporting-server", "lobby:25565"
        );

        String perPlayerContext = factory.backfillContextJsonForPlayer(
            result.contextJson(),
            member,
            result.playerReturnTargetsByUuid(),
            result.assignmentPlayerTicketsByUuid(),
            "reporting-server"
        );

        JsonObject perPlayerRoot = GSON.fromJson(perPlayerContext, JsonObject.class);
        LaunchContextParser parser = new LaunchContextParser();
        LaunchContextData parsed = parser.parse(perPlayerRoot);

        assertEquals("match-parseable", parsed.matchId());
        assertEquals("BACKFILL", parsed.assignmentType());
        assertEquals("skywars_nexori_template", parsed.instanceTemplateId());
        assertTrue(parsed.usesInstanceTemplate());
        assertEquals("res-ghi", parsed.admissionReservationId());
        assertEquals(player, parsed.playerUuid());
    }

    @Test
    void legacyOverloadWithoutArenaDefinitionStillBuilds() {
        UUID player = UUID.fromString("44444444-4444-4444-4444-444444444444");
        QueueDefinition queue = makeQueue();
        QueueMemberState member = makeMember(player);
        List<MinigameLaunchContextFactory.AssignmentPlayerTicket> tickets = List.of(
            new MinigameLaunchContextFactory.AssignmentPlayerTicket(player, "res-legacy", System.currentTimeMillis() + 60_000)
        );

        MinigameLaunchContextBuildResult result = factory.buildBackfillLaunchContext(
            queue, "skywars_arena",
            List.of(member), System.currentTimeMillis(),
            "assign-legacy", "match-legacy", "ext-legacy",
            tickets, "reporting-server", "lobby:25565"
        );

        JsonObject context = GSON.fromJson(result.contextJson(), JsonObject.class);
        assertEquals("BACKFILL", context.get("assignmentType").getAsString());
        assertEquals("match-legacy", context.get("matchId").getAsString());
    }
}
