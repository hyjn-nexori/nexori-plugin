package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchPlacementState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultPlayer;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultPlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultRequirements;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriBackendReportStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchCompletionStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerResolutionOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitMatchResultRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitMatchResultResult;
import io.github.hyjn.nexori.plugin.backend.BackendResultReportingService;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of Nexori's public minigame API.
 * This bridge translates the public contract into the internal arena-match runtime.
 */
public final class NexoriMinigameApiBridge implements NexoriMinigameApi {

    private final ArenaMatchService arenaMatchService;
    private final BackendResultReportingService backendResultReportingService;

    /**
     * Creates one bridge backed by the live arena-match service.
     */
    public NexoriMinigameApiBridge(
        @Nonnull ArenaMatchService arenaMatchService,
        @Nonnull BackendResultReportingService backendResultReportingService
    ) {
        this.arenaMatchService = arenaMatchService;
        this.backendResultReportingService = backendResultReportingService;
    }

    @Nonnull
    @Override
    public Optional<String> findActiveMatchId(@Nonnull UUID playerUuid) {
        return arenaMatchService.findActiveMatchId(playerUuid);
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

    @Nonnull
    @Override
    public Optional<NexoriMatchResultRequirements> findMatchResultRequirements(@Nonnull String matchId) {
        return arenaMatchService.findMatchResultRequirements(matchId)
            .map(requirements -> new NexoriMatchResultRequirements(
                requirements.matchId(),
                requirements.queueId(),
                requirements.arenaId(),
                requirements.requiredPlayerUuids(),
                requirements.expectedPlayerUuids(),
                requirements.arrivedPlayerUuids(),
                requirements.activePlayerUuids(),
                requirements.eliminatedPlayerUuids()
            ));
    }

    @Nonnull
    @Override
    public NexoriSubmitMatchResultResult submitMatchResult(@Nonnull NexoriSubmitMatchResultRequest request) {
        if (request == null) {
            return new NexoriSubmitMatchResultResult(
                NexoriMatchCompletionStatus.INVALID_RESULT,
                NexoriBackendReportStatus.NOT_ATTEMPTED,
                "",
                "Request cannot be null."
            );
        }

        List<ArenaMatchService.SubmitMatchPlayerResult> players = new ArrayList<>();
        if (request.players() != null) {
            for (NexoriMatchResultPlayer player : request.players()) {
                if (player == null || player.playerUuid() == null || player.outcome() == null) {
                    return new NexoriSubmitMatchResultResult(
                        NexoriMatchCompletionStatus.INVALID_RESULT,
                        NexoriBackendReportStatus.NOT_ATTEMPTED,
                        "",
                        "Request contains an invalid player result."
                    );
                }
                players.add(new ArenaMatchService.SubmitMatchPlayerResult(
                    player.playerUuid(),
                    runtimeOutcome(player.outcome()),
                    player.outcome().name(),
                    player.reason()
                ));
            }
        }

        ArenaMatchService.SubmitMatchResult localResult = arenaMatchService.submitMatchResult(
            request.matchId(),
            players,
            request.metadata() == null ? Map.of() : request.metadata(),
            request.returnDelaySeconds(),
            request.reason() == null ? "" : request.reason()
        );

        NexoriMatchCompletionStatus matchStatus = switch (localResult.outcome()) {
            case ACCEPTED -> NexoriMatchCompletionStatus.ACCEPTED;
            case ALREADY_SUBMITTED -> NexoriMatchCompletionStatus.ALREADY_SUBMITTED;
            case MATCH_MISSING -> NexoriMatchCompletionStatus.MATCH_MISSING;
            case INVALID_RESULT -> NexoriMatchCompletionStatus.INVALID_RESULT;
        };
        if (localResult.outcome() == ArenaMatchService.SubmitMatchOutcome.ALREADY_SUBMITTED) {
            return new NexoriSubmitMatchResultResult(
                matchStatus,
                localResult.duplicateConflict()
                    ? NexoriBackendReportStatus.DUPLICATE_CONFLICT
                    : NexoriBackendReportStatus.ALREADY_SUBMITTED,
                "",
                localResult.message()
            );
        }
        if (localResult.outcome() != ArenaMatchService.SubmitMatchOutcome.ACCEPTED) {
            return new NexoriSubmitMatchResultResult(
                matchStatus,
                NexoriBackendReportStatus.NOT_ATTEMPTED,
                "",
                localResult.message()
            );
        }

        BackendResultReportingService.EnqueueResult enqueueResult = backendResultReportingService.enqueueResult(
            localResult.activeMatch(),
            localResult.players(),
            localResult.metadata(),
            localResult.reason(),
            localResult.resultPayloadHash(),
            localResult.endedAtEpochMs()
        );
        return new NexoriSubmitMatchResultResult(
            matchStatus,
            switch (enqueueResult.outcome()) {
                case QUEUED -> NexoriBackendReportStatus.QUEUED;
                case DISABLED -> NexoriBackendReportStatus.DISABLED;
                case EXTERNAL_MATCH_MISSING -> NexoriBackendReportStatus.EXTERNAL_MATCH_MISSING;
                case STORE_FAILED -> NexoriBackendReportStatus.STORE_FAILED;
                case ALREADY_SUBMITTED -> NexoriBackendReportStatus.ALREADY_SUBMITTED;
                case DUPLICATE_CONFLICT -> NexoriBackendReportStatus.DUPLICATE_CONFLICT;
            },
            enqueueResult.resultId(),
            enqueueResult.message()
        );
    }

    @Nonnull
    private ArenaPlayerResolutionOutcome runtimeOutcome(@Nonnull NexoriMatchResultPlayerOutcome outcome) {
        return switch (outcome) {
            case WIN -> ArenaPlayerResolutionOutcome.WIN;
            case LOSS, DISCONNECTED -> ArenaPlayerResolutionOutcome.LOSS;
        };
    }

    @Nonnull
    @Override
    public Optional<NexoriMatchPlacementState> findMatchPlacementState(@Nonnull String matchId) {
        return arenaMatchService.findMatchPlacementState(matchId)
            .map(state -> new NexoriMatchPlacementState(
                state.expectedPlayers(),
                state.arrivedPlayers(),
                state.placedPlayers(),
                state.placementComplete()
            ));
    }

    @Nonnull
    @Override
    public Optional<String> findMatchResolutionTriggerId(@Nonnull String matchId) {
        return arenaMatchService.findMatchResolutionTriggerId(matchId);
    }
}
