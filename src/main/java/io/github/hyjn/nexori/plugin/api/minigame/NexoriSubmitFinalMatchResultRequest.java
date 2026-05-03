package io.github.hyjn.nexori.plugin.api.minigame;

import com.google.gson.JsonObject;

/**
 * Final match result request. Player outcomes are read from the active match runtime.
 */
public record NexoriSubmitFinalMatchResultRequest(
    String matchId,
    String reason,
    JsonObject customData
) {
}
