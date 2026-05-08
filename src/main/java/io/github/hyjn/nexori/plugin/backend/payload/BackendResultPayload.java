package io.github.hyjn.nexori.plugin.backend.payload;

import com.google.gson.JsonObject;

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
    Map<String, String> assignmentIdsByPlayerUuid,
    String queueId,
    String arenaId,
    String rulesEngineId,
    List<BackendResultPlayerPayload> players,
    String reason,
    Map<String, String> metadata,
    JsonObject customData,
    long endedAtEpochMs
) {
}
