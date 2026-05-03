package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Public result returned by setPlayerSpectator.
 */
public record NexoriSetPlayerSpectatorResult(
    NexoriSetPlayerSpectatorStatus status,
    String matchId,
    @Nullable UUID playerUuid,
    boolean spectator,
    String message
) {
}
