package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentAckPayload;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import java.util.List;

/**
 * Pure inputs required to build a backend sync request payload.
 */
public record BackendSyncRequestPayloadBuildInput(
    int schemaVersion,
    String syncId,
    long sequence,
    long sentAtEpochMs,
    String serverId,
    String fingerprint,
    String connectionAddress,
    String region,
    List<QueueDefinition> queues,
    List<QueueRuntimeState> queueRuntimeStates,
    List<ArenaDefinition> arenas,
    List<ArenaActiveMatch> activeMatches,
    List<BackendAssignmentAckPayload> assignmentAcks
) {
}
