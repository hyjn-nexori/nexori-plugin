package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Public resolution result returned by {@link NexoriMinigameApi#resolvePlayerOutcome(String, UUID, NexoriPlayerResolutionOutcome, int, String)}.
 */
public record NexoriResolvePlayerResult(
    NexoriResolvePlayerOutcome outcome,
    String matchId,
    @Nullable UUID playerUuid,
    @Nullable NexoriPlayerResolutionOutcome playerOutcome
) {
}
