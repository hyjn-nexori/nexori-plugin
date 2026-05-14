package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentAckPayload;
import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncRequestPayload;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.LastPlayerAliveArenaMatchResolutionTrigger;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMatchmakingMode;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueuePhase;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class BackendSyncRequestPayloadBuilderTest {

    private static final int SCHEMA_VERSION = 1;
    private static final String SYNC_ID = "sync-1";
    private static final long SEQUENCE = 42L;
    private static final long SENT_AT = 10_000L;
    private static final String SERVER_ID = "server-1";
    private static final String FINGERPRINT = "fingerprint-1";
    private static final String CONNECTION_ADDRESS = "server.example:19132";
    private static final String REGION = "us-east";
    private static final long CREATED_AT = 500L;
    private static final long UPDATED_AT = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final BackendSyncRequestPayloadBuilder builder = new BackendSyncRequestPayloadBuilder();

    @Test
    void buildsServerSnapshotWithProvidedIdentityAndRegion() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        assertEquals(SERVER_ID, payload.serverId());
        assertEquals(FINGERPRINT, payload.server().fingerprint());
        assertEquals(CONNECTION_ADDRESS, payload.server().connectionAddress());
        assertEquals("SERVER", payload.server().role());
        assertEquals(REGION, payload.server().region());
    }

    @Test
    void buildsQueueSnapshotsWithRuntimeWhenPresent() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        BackendSyncRequestPayload.QueueSnapshot queue = payload.queues().get(0);

        assertEquals("queue-one", queue.queueId());
        assertEquals("Queue One", queue.displayName());
        assertEquals(2, queue.minPlayers());
        assertEquals(4, queue.maxPlayers());
        assertEquals(15, queue.countdownSeconds());
        assertEquals("keep_inventory", queue.launchTravelProfileId());
        assertEquals(true, queue.enabled());
        assertEquals("COUNTDOWN", queue.runtime().phase());
    }

    @Test
    void buildsQueueSnapshotWithNullRuntimeWhenMissing() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        BackendSyncRequestPayload.QueueSnapshot queue = payload.queues().get(1);

        assertEquals("queue-two", queue.queueId());
        assertNull(queue.runtime());
    }

    @Test
    void usesEffectiveMatchmakingModeInQueueSnapshot() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        assertEquals(QueueMatchmakingMode.BACKEND_DRIVEN.id(), payload.queues().get(0).matchmakingMode());
        assertEquals(QueueMatchmakingMode.LOCAL_FIFO.id(), payload.queues().get(1).matchmakingMode());
    }

    @Test
    void preservesQueueArenaIdsOrder() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        assertEquals(List.of("arena-two", "arena-one"), payload.queues().get(0).arenaIds());
    }

    @Test
    void buildsRuntimeSnapshotWithWaitingAndReadyMembers() {
        BackendSyncRequestPayload.RuntimeSnapshot runtime = builder.build(baseInput()).queues().get(0).runtime();

        assertEquals("COUNTDOWN", runtime.phase());
        assertEquals(SENT_AT + 5_000L, runtime.countdownEndsAtEpochMs());
        assertEquals(SENT_AT + 1_000L, runtime.readyAtEpochMs());
        assertEquals(SENT_AT - 1_000L, runtime.lastStateChangeEpochMs());
        assertEquals(SENT_AT - 500L, runtime.lastLaunchAttemptAtEpochMs());
        assertEquals("last launch failed", runtime.lastLaunchError());
        assertEquals(2, runtime.waitingMembers().size());
        assertEquals(1, runtime.readyMembers().size());
    }

    @Test
    void preservesQueueMemberSnapshotFields() {
        BackendSyncRequestPayload.QueueMemberSnapshot member = builder.build(baseInput())
            .queues()
            .get(0)
            .runtime()
            .waitingMembers()
            .get(0);

        assertEquals(PLAYER_ONE.toString(), member.playerUuid());
        assertEquals("Player One", member.playerNameSnapshot());
        assertEquals("lobby-one", member.sourceLobbyId());
        assertEquals("portal-one", member.sourcePortalId());
        assertEquals(CREATED_AT, member.joinedAtEpochMs());
    }

    @Test
    void buildsArenaSnapshots() {
        BackendSyncRequestPayload.ArenaSnapshot arena = builder.build(baseInput()).arenas().get(0);

        assertEquals("arena-one", arena.arenaId());
        assertEquals("Arena One", arena.displayName());
        assertEquals("arena.example:19132", arena.destinationConnectionAddress());
        assertEquals("arena-one.spawn", arena.destinationTargetId());
        assertEquals("template-one", arena.instanceTemplateId());
        assertEquals(LastPlayerAliveArenaMatchResolutionTrigger.ID, arena.matchResolutionTriggerId());
        assertEquals(8, arena.maxSupportedPlayers());
        assertEquals(true, arena.enabled());
    }

    @Test
    void buildsActiveMatchSnapshotsWithArrivedAndActiveCounts() {
        BackendSyncRequestPayload.ActiveMatchSnapshot match = builder.build(baseInput()).activeMatches().get(0);

        assertEquals("match-one", match.matchId());
        assertEquals("queue-one", match.queueId());
        assertEquals("arena-one", match.arenaId());
        assertEquals(2, match.expectedPlayerCount());
        assertEquals(3, match.arrivedPlayerCount());
        assertEquals(2, match.activePlayerCount());
        assertEquals(CREATED_AT, match.createdAtEpochMs());
        assertEquals(UPDATED_AT, match.updatedAtEpochMs());
        assertEquals("last error", match.lastError());
    }

    @Test
    void includesPendingAssignmentAcks() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        assertEquals(List.of(ack("ack-one"), ack("ack-two")), payload.assignmentAcks());
    }

    @Test
    void preservesQueueArenaActiveMatchAndAckOrdering() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        assertEquals(List.of("queue-one", "queue-two"), payload.queues().stream().map(BackendSyncRequestPayload.QueueSnapshot::queueId).toList());
        assertEquals(List.of("arena-one", "arena-two"), payload.arenas().stream().map(BackendSyncRequestPayload.ArenaSnapshot::arenaId).toList());
        assertEquals(List.of("match-one", "match-two"), payload.activeMatches().stream().map(BackendSyncRequestPayload.ActiveMatchSnapshot::matchId).toList());
        assertEquals(List.of("ack-one", "ack-two"), payload.assignmentAcks().stream().map(BackendAssignmentAckPayload::ackId).toList());
    }

    @Test
    void usesProvidedSchemaSyncIdSequenceAndSentAt() {
        BackendSyncRequestPayload payload = builder.build(baseInput());

        assertEquals(SCHEMA_VERSION, payload.schemaVersion());
        assertEquals(SYNC_ID, payload.syncId());
        assertEquals(SEQUENCE, payload.sequence());
        assertEquals(SENT_AT, payload.sentAtEpochMs());
    }

    private static BackendSyncRequestPayloadBuildInput baseInput() {
        return new BackendSyncRequestPayloadBuildInput(
            SCHEMA_VERSION,
            SYNC_ID,
            SEQUENCE,
            SENT_AT,
            SERVER_ID,
            FINGERPRINT,
            CONNECTION_ADDRESS,
            REGION,
            List.of(queueOne(), queueTwo()),
            List.of(runtimeOne()),
            List.of(arenaOne(), arenaTwo()),
            List.of(activeMatch("match-one", "last error"), activeMatch("match-two", "")),
            List.of(ack("ack-one"), ack("ack-two"))
        );
    }

    private static QueueDefinition queueOne() {
        return new QueueDefinition(
            "queue-one",
            "Queue One",
            List.of("arena-two", "arena-one"),
            2,
            4,
            15,
            "keep_inventory",
            "backend driven",
            true
        );
    }

    private static QueueDefinition queueTwo() {
        return new QueueDefinition(
            "queue-two",
            "Queue Two",
            List.of("arena-two"),
            1,
            2,
            10,
            "keep_inventory",
            "",
            false
        );
    }

    private static QueueRuntimeState runtimeOne() {
        return new QueueRuntimeState(
            "queue-one",
            QueuePhase.COUNTDOWN,
            List.of(member(PLAYER_ONE, "Player One", "portal-one"), member(PLAYER_TWO, "Player Two", "portal-two")),
            List.of(member(PLAYER_THREE, "Player Three", "portal-three")),
            SENT_AT + 5_000L,
            SENT_AT + 1_000L,
            SENT_AT - 1_000L,
            SENT_AT - 500L,
            "last launch failed"
        ).normalized();
    }

    private static QueueMemberState member(UUID playerUuid, String playerName, String portalId) {
        return new QueueMemberState(
            playerUuid,
            playerName,
            "lobby-one",
            portalId,
            CREATED_AT
        ).normalized();
    }

    private static ArenaDefinition arenaOne() {
        return new ArenaDefinition(
            "arena-one",
            "Arena One",
            "arena.example:19132",
            "arena-one.spawn",
            "template-one",
            LastPlayerAliveArenaMatchResolutionTrigger.ID,
            8,
            true
        );
    }

    private static ArenaDefinition arenaTwo() {
        return new ArenaDefinition(
            "arena-two",
            "Arena Two",
            "arena-two.example:19132",
            "arena-two.spawn",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID,
            4,
            false
        );
    }

    private static ArenaActiveMatch activeMatch(String matchId, String lastError) {
        return new ArenaActiveMatch(
            matchId,
            "queue-one",
            "arena-one",
            "lobby-one",
            "lobby.example:19132",
            "lobby-one.natural_spawn",
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
            30,
            List.of(PLAYER_ONE, PLAYER_TWO),
            2,
            List.of(PLAYER_ONE, PLAYER_TWO, PLAYER_THREE),
            List.of(PLAYER_ONE, PLAYER_TWO),
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
            UPDATED_AT,
            lastError
        ).normalized();
    }

    private static BackendAssignmentAckPayload ack(String ackId) {
        return new BackendAssignmentAckPayload(
            ackId,
            "assignment-" + ackId,
            "external-" + ackId,
            "LAUNCHED",
            "match-" + ackId,
            "reason-" + ackId,
            CREATED_AT
        );
    }
}
