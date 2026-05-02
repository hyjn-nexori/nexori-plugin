package io.github.hyjn.nexori.plugin.api.minigame;

import java.util.UUID;

/**
 * One player outcome submitted by a gameplay/rules integration.
 */
public record NexoriMatchResultPlayer(
    UUID playerUuid,
    NexoriMatchResultPlayerOutcome outcome,
    String reason
) {
}
