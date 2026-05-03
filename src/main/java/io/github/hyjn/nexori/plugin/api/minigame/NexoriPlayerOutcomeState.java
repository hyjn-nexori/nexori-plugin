package io.github.hyjn.nexori.plugin.api.minigame;

import java.util.UUID;

/**
 * Accumulated player outcome currently stored inside an active match.
 */
public record NexoriPlayerOutcomeState(
    UUID playerUuid,
    NexoriMatchResultPlayerOutcome outcome,
    String reason,
    long updatedAtEpochMs
) {
}
