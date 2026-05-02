package io.github.hyjn.nexori.plugin.backend.payload;

import java.util.List;
import java.util.Map;

public record BackendResultPayload(
    int schemaVersion,
    String resultId,
    long sentAtEpochMs,
    String serverId,
    String localMatchId,
    String externalMatchId,
    String assignmentId,
    String queueId,
    String arenaId,
    List<BackendResultPlayerPayload> players,
    String reason,
    Map<String, String> metadata,
    long endedAtEpochMs
) {
}
