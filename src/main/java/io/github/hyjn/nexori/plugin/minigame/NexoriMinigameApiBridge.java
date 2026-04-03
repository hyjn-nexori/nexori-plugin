package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriArenaResolutionContext;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriArenaResolutionTrigger;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerResolutionOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerResult;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class NexoriMinigameApiBridge implements NexoriMinigameApi {

    private final ArenaMatchService arenaMatchService;
    private final ArenaMatchResolutionTriggerRegistry triggerRegistry;

    public NexoriMinigameApiBridge(
        @Nonnull ArenaMatchService arenaMatchService,
        @Nonnull ArenaMatchResolutionTriggerRegistry triggerRegistry
    ) {
        this.arenaMatchService = arenaMatchService;
        this.triggerRegistry = triggerRegistry;
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

    @Override
    public void registerResolutionTrigger(@Nonnull NexoriArenaResolutionTrigger trigger) {
        String triggerId = normalizeRequired(trigger.id(), "Arena resolution trigger id cannot be blank.");
        if (ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equalsIgnoreCase(triggerId)) {
            throw new IllegalArgumentException("'" + ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID + "' is reserved and cannot be registered as a custom trigger id.");
        }
        triggerRegistry.register(new PublicResolutionTriggerAdapter(triggerId, trigger));
    }

    @Nonnull
    @Override
    public Set<String> listResolutionTriggerIds() {
        return triggerRegistry.listIds();
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static final class PublicResolutionTriggerAdapter implements ArenaMatchResolutionTrigger {

        private final String id;
        private final NexoriArenaResolutionTrigger delegate;

        private PublicResolutionTriggerAdapter(@Nonnull String id, @Nonnull NexoriArenaResolutionTrigger delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Nonnull
        @Override
        public String id() {
            return id;
        }

        @Nonnull
        @Override
        public ArenaActiveMatch evaluate(@Nonnull ArenaMatchService arenaMatchService, @Nonnull ArenaActiveMatch match, long nowEpochMs) {
            ResolutionContextAdapter context = new ResolutionContextAdapter(arenaMatchService, match, nowEpochMs);
            delegate.evaluate(context);
            return context.currentMatch();
        }
    }

    private static final class ResolutionContextAdapter implements NexoriArenaResolutionContext {

        private final ArenaMatchService arenaMatchService;
        private final long nowEpochMs;
        private ArenaActiveMatch currentMatch;

        private ResolutionContextAdapter(
            @Nonnull ArenaMatchService arenaMatchService,
            @Nonnull ArenaActiveMatch match,
            long nowEpochMs
        ) {
            this.arenaMatchService = arenaMatchService;
            this.currentMatch = match;
            this.nowEpochMs = nowEpochMs;
        }

        @Nonnull
        private ArenaActiveMatch currentMatch() {
            return currentMatch;
        }

        @Nonnull
        @Override
        public String matchId() {
            return currentMatch.matchId();
        }

        @Nonnull
        @Override
        public String queueId() {
            return currentMatch.queueId();
        }

        @Nonnull
        @Override
        public String arenaId() {
            return currentMatch.arenaId();
        }

        @Override
        public int expectedPlayerCount() {
            return currentMatch.expectedPlayerCount();
        }

        @Override
        public int arrivedPlayerCount() {
            return currentMatch.arrivedPlayerUuids().size();
        }

        @Override
        public int activePlayerCount() {
            return currentMatch.activePlayerUuids().size();
        }

        @Override
        public int alivePlayerCount() {
            return currentMatch.alivePlayerUuids().size();
        }

        @Override
        public boolean allExpectedPlayersArrived() {
            return currentMatch.allExpectedPlayersArrived();
        }

        @Nonnull
        @Override
        public List<UUID> activePlayerUuids() {
            return currentMatch.activePlayerUuids();
        }

        @Nonnull
        @Override
        public List<UUID> alivePlayerUuids() {
            return currentMatch.alivePlayerUuids();
        }

        @Override
        public boolean isPlayerEliminated(@Nonnull UUID playerUuid) {
            return currentMatch.isPlayerEliminated(playerUuid);
        }

        @Override
        public boolean hasWinner() {
            return currentMatch.hasWinner();
        }

        @Nonnull
        @Override
        public Optional<UUID> winnerPlayerUuid() {
            if (!currentMatch.hasWinner()) {
                return Optional.empty();
            }
            try {
                return Optional.of(UUID.fromString(currentMatch.winnerPlayerUuid()));
            } catch (IllegalArgumentException ignored) {
                return Optional.empty();
            }
        }

        @Override
        public boolean markPlayerWin(@Nonnull UUID playerUuid, @Nonnull String reason) {
            if (!currentMatch.hasPlayer(playerUuid)) {
                return false;
            }
            currentMatch = arenaMatchService.markPlayerWinInternal(currentMatch, playerUuid, reason, nowEpochMs);
            return true;
        }

        @Override
        public boolean markPlayerLoss(@Nonnull UUID playerUuid, @Nonnull String reason) {
            if (!currentMatch.hasPlayer(playerUuid)) {
                return false;
            }
            currentMatch = arenaMatchService.markPlayerLossInternal(currentMatch, playerUuid, reason, nowEpochMs);
            return true;
        }
    }
}
