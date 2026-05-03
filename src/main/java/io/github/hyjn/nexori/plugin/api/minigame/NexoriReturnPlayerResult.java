package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Public result returned by returnPlayerToLobby.
 */
public record NexoriReturnPlayerResult(
    NexoriReturnPlayerStatus status,
    String matchId,
    @Nullable UUID playerUuid,
    long returnAtEpochMs,
    String message
) {
}
