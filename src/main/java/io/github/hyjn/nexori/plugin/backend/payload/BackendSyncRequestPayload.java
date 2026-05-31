package io.github.hyjn.nexori.plugin.backend.payload;

import java.util.List;

public record BackendSyncRequestPayload(
    int schemaVersion,
    String syncId,
    long sequence,
    long sentAtEpochMs,
    String serverId,
    ServerSnapshot server,
    List<QueueSnapshot> queues,
    List<ArenaSnapshot> arenas,
    List<ActiveMatchSnapshot> activeMatches,
    List<BackendAssignmentAckPayload> assignmentAcks
) {

    public record ServerSnapshot(
        String fingerprint,
        String connectionAddress,
        String role,
        String region
    ) {
    }

    public record QueueSnapshot(
        String queueId,
        String displayName,
        int minPlayers,
        int maxPlayers,
        int countdownSeconds,
        String launchTravelProfileId,
        String matchmakingMode,
        boolean enabled,
        List<String> arenaIds,
        RuntimeSnapshot runtime
    ) {
    }

    public record RuntimeSnapshot(
        String phase,
        long countdownEndsAtEpochMs,
        long readyAtEpochMs,
        long lastStateChangeEpochMs,
        long lastLaunchAttemptAtEpochMs,
        String lastLaunchError,
        List<QueueMemberSnapshot> waitingMembers,
        List<QueueMemberSnapshot> readyMembers
    ) {
    }

    public record QueueMemberSnapshot(
        String playerUuid,
        String playerNameSnapshot,
        String sourceLobbyId,
        String sourcePortalId,
        long joinedAtEpochMs
    ) {
    }

    public record ArenaSnapshot(
        String arenaId,
        String displayName,
        String destinationConnectionAddress,
        String destinationTargetId,
        String instanceTemplateId,
        int maxSupportedPlayers,
        boolean enabled
    ) {
    }

    public record ActiveMatchSnapshot(
        String matchId,
        String queueId,
        String arenaId,
        int expectedPlayerCount,
        int arrivedPlayerCount,
        int activePlayerCount,
        long createdAtEpochMs,
        long updatedAtEpochMs,
        String lastError
    ) {
    }
}
