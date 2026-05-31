package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncRequestPayload;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueMemberState;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure builder for backend sync request snapshots.
 */
public final class BackendSyncRequestPayloadBuilder {

    @Nonnull
    public BackendSyncRequestPayload build(@Nonnull BackendSyncRequestPayloadBuildInput input) {
        Map<String, QueueRuntimeState> runtimeByQueueId = new LinkedHashMap<>();
        for (QueueRuntimeState state : input.queueRuntimeStates()) {
            runtimeByQueueId.put(state.queueId(), state);
        }

        List<BackendSyncRequestPayload.QueueSnapshot> queues = new ArrayList<>();
        for (QueueDefinition queue : input.queues()) {
            QueueRuntimeState runtime = runtimeByQueueId.get(queue.queueId());
            queues.add(new BackendSyncRequestPayload.QueueSnapshot(
                queue.queueId(),
                queue.displayName(),
                queue.minPlayers(),
                queue.maxPlayers(),
                queue.countdownSeconds(),
                queue.launchTravelProfileId(),
                queue.effectiveMatchmakingMode().id(),
                queue.enabled(),
                queue.arenaIds(),
                runtime == null ? null : buildRuntimeSnapshot(runtime)
            ));
        }

        List<BackendSyncRequestPayload.ArenaSnapshot> arenas = new ArrayList<>();
        for (ArenaDefinition arena : input.arenas()) {
            arenas.add(new BackendSyncRequestPayload.ArenaSnapshot(
                arena.arenaId(),
                arena.displayName(),
                arena.destinationConnectionAddress(),
                arena.destinationTargetId(),
                arena.instanceTemplateId(),
                arena.maxSupportedPlayers(),
                arena.enabled()
            ));
        }

        List<BackendSyncRequestPayload.ActiveMatchSnapshot> activeMatches = new ArrayList<>();
        for (ArenaActiveMatch match : input.activeMatches()) {
            activeMatches.add(new BackendSyncRequestPayload.ActiveMatchSnapshot(
                match.matchId(),
                match.queueId(),
                match.arenaId(),
                match.expectedPlayerCount(),
                match.arrivedPlayerUuids().size(),
                match.activePlayerUuids().size(),
                match.createdAtEpochMs(),
                match.lastUpdatedAtEpochMs(),
                match.lastError()
            ));
        }

        return new BackendSyncRequestPayload(
            input.schemaVersion(),
            input.syncId(),
            input.sequence(),
            input.sentAtEpochMs(),
            input.serverId(),
            new BackendSyncRequestPayload.ServerSnapshot(
                input.fingerprint(),
                input.connectionAddress(),
                "SERVER",
                input.region()
            ),
            List.copyOf(queues),
            List.copyOf(arenas),
            List.copyOf(activeMatches),
            List.copyOf(input.assignmentAcks())
        );
    }

    @Nonnull
    private BackendSyncRequestPayload.RuntimeSnapshot buildRuntimeSnapshot(@Nonnull QueueRuntimeState runtime) {
        return new BackendSyncRequestPayload.RuntimeSnapshot(
            runtime.phase().name(),
            runtime.countdownEndsAtEpochMs(),
            runtime.readyAtEpochMs(),
            runtime.lastStateChangeEpochMs(),
            runtime.lastLaunchAttemptAtEpochMs(),
            runtime.lastLaunchError(),
            buildMemberSnapshots(runtime.waitingMembers()),
            buildMemberSnapshots(runtime.readyMembers())
        );
    }

    @Nonnull
    private List<BackendSyncRequestPayload.QueueMemberSnapshot> buildMemberSnapshots(@Nonnull List<QueueMemberState> members) {
        List<BackendSyncRequestPayload.QueueMemberSnapshot> snapshots = new ArrayList<>();
        for (QueueMemberState member : members) {
            snapshots.add(new BackendSyncRequestPayload.QueueMemberSnapshot(
                member.playerUuid().toString(),
                member.playerNameSnapshot(),
                member.sourceLobbyId(),
                member.sourcePortalId(),
                member.joinedAtEpochMs()
            ));
        }
        return List.copyOf(snapshots);
    }
}
