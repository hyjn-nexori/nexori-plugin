package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerResolutionOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerResult;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

public final class NexoriMinigameApiBridge implements NexoriMinigameApi {

    private final ArenaMatchService arenaMatchService;

    public NexoriMinigameApiBridge(@Nonnull ArenaMatchService arenaMatchService) {
        this.arenaMatchService = arenaMatchService;
    }

    @Nonnull
    @Override
    public Optional<UUID> findActivePlayerUuid(@Nonnull String matchId, @Nonnull String playerToken) {
        return arenaMatchService.findActivePlayerUuid(matchId, playerToken);
    }

    @Nonnull
    @Override
    public NexoriResolvePlayerResult resolvePlayerOutcome(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        @Nonnull NexoriPlayerResolutionOutcome outcome,
        int returnDelaySeconds,
        @Nonnull String reason
    ) {
        ArenaMatchService.ResolvePlayerResult result = arenaMatchService.resolvePlayerOutcome(
            matchId,
            playerUuid,
            switch (outcome) {
                case WIN -> ArenaPlayerResolutionOutcome.WIN;
                case LOSS -> ArenaPlayerResolutionOutcome.LOSS;
            },
            returnDelaySeconds,
            reason
        );
        return new NexoriResolvePlayerResult(
            switch (result.outcome()) {
                case UPDATED -> NexoriResolvePlayerOutcome.UPDATED;
                case MATCH_MISSING -> NexoriResolvePlayerOutcome.MATCH_MISSING;
                case PLAYER_MISSING -> NexoriResolvePlayerOutcome.PLAYER_MISSING;
            },
            result.matchId(),
            result.playerUuid(),
            result.playerOutcome() == null ? null : switch (result.playerOutcome()) {
                case WIN -> NexoriPlayerResolutionOutcome.WIN;
                case LOSS -> NexoriPlayerResolutionOutcome.LOSS;
            }
        );
    }
}
