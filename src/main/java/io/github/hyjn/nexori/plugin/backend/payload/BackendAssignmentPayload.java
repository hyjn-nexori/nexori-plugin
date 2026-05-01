package io.github.hyjn.nexori.plugin.backend.payload;

import com.google.gson.JsonObject;

import java.util.List;

public record BackendAssignmentPayload(
    String assignmentId,
    String externalMatchId,
    String type,
    String queueId,
    List<String> playerUuids,
    String arenaId,
    String modeId,
    String kitId,
    boolean ranked,
    JsonObject metadata
) {
}
