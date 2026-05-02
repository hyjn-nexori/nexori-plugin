package io.github.hyjn.nexori.plugin.api.minigame;

import java.util.List;
import java.util.Map;

/**
 * Complete match result submitted by a gameplay/rules integration.
 */
public record NexoriSubmitMatchResultRequest(
    String matchId,
    List<NexoriMatchResultPlayer> players,
    String reason,
    Map<String, String> metadata,
    int returnDelaySeconds
) {
}
