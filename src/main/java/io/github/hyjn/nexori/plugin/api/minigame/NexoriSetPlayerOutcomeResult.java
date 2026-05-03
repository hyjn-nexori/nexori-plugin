package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Public result returned by setPlayerOutcome.
 */
public record NexoriSetPlayerOutcomeResult(
    NexoriSetPlayerOutcomeStatus status,
    String matchId,
    @Nullable UUID playerUuid,
    @Nullable NexoriMatchResultPlayerOutcome outcome,
    String message
) {
}
