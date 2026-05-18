package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriActiveMatchInfo;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriBackendReportStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionReason;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchCompletionStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchPlacementState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultPlayer;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultPlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultRequirements;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerOutcomeState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerResolutionOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriResolvePlayerResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriReturnPlayerResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriReturnPlayerStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerOutcomeResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerOutcomeStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerSpectatorResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerSpectatorStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitFinalMatchResultRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitFinalMatchResultResult;
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
    public Optional<NexoriActiveMatchInfo> findActiveMatchInfo(@Nonnull String matchId) {
        return arenaMatchService.findActiveMatchInfo(matchId)
            .map(info -> new NexoriActiveMatchInfo(
                info.matchId(),
                info.queueId(),
                info.arenaId(),
                info.assignmentId(),
                info.externalMatchId(),
                info.rulesEngineId(),
                info.matchResolutionTriggerId(),
                info.expectedPlayerUuids(),
                info.arrivedPlayerUuids(),
                info.activePlayerUuids(),
                info.eliminatedPlayerUuids(),
                info.spectatorPlayerUuids(),
                info.requiredResultPlayerUuids(),
                info.playerOutcomes().stream()
                    .map(outcome -> new NexoriPlayerOutcomeState(
                        outcome.playerUuid(),
                        publicOutcome(outcome.outcome(), outcome.backendOutcome()),
                        outcome.reason(),
                        outcome.updatedAtEpochMs()
                    ))
                    .toList(),
                info.expectedPlayerCount(),
                info.completedAtEpochMs(),
                info.resultSubmittedAtEpochMs()
            ));
    }

    @Nonnull
    @Override
    public Optional<String> findRulesEngineId(@Nonnull String matchId) {
        return arenaMatchService.findRulesEngineId(matchId);
    }

    @Deprecated
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
                case LOSS, DISCONNECTED -> NexoriPlayerResolutionOutcome.LOSS;
            }
        );
    }

    @Nonnull
    @Override
    public NexoriSetPlayerOutcomeResult setPlayerOutcome(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        @Nonnull NexoriMatchResultPlayerOutcome outcome,
        @Nonnull String reason
    ) {
        ArenaMatchService.SetPlayerOutcomeResult result = arenaMatchService.setPlayerOutcome(
            matchId,
            playerUuid,
            runtimeOutcome(outcome),
            outcome.name(),
            reason
        );
        return new NexoriSetPlayerOutcomeResult(
            switch (result.outcome()) {
                case UPDATED -> NexoriSetPlayerOutcomeStatus.UPDATED;
                case MATCH_MISSING -> NexoriSetPlayerOutcomeStatus.MATCH_MISSING;
                case PLAYER_MISSING -> NexoriSetPlayerOutcomeStatus.PLAYER_MISSING;
                case MATCH_ALREADY_COMPLETED -> NexoriSetPlayerOutcomeStatus.MATCH_ALREADY_COMPLETED;
                case INVALID_OUTCOME -> NexoriSetPlayerOutcomeStatus.INVALID_OUTCOME;
                case INVALID_REASON -> NexoriSetPlayerOutcomeStatus.INVALID_REASON;
            },
            result.matchId(),
            result.playerUuid(),
            result.playerOutcome() == null ? null : publicOutcome(result.playerOutcome(), result.playerOutcome().name()),
            result.message()
        );
    }

    @Nonnull
    @Override
    public NexoriSetPlayerSpectatorResult setPlayerSpectator(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        boolean spectator,
        @Nonnull String reason
    ) {
        return setPlayerSpectator(matchId, playerUuid, spectator, reason, null);
    }

    @Nonnull
    @Override
    public NexoriSetPlayerSpectatorResult setPlayerSpectator(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        boolean spectator,
        @Nonnull String reason,
        String spectatorModelId
    ) {
        ArenaMatchService.SetPlayerSpectatorResult result = arenaMatchService.setPlayerSpectator(
            matchId,
            playerUuid,
            spectator,
            reason,
            spectatorModelId
        );
        return new NexoriSetPlayerSpectatorResult(
            switch (result.outcome()) {
                case UPDATED -> NexoriSetPlayerSpectatorStatus.UPDATED;
                case MATCH_MISSING -> NexoriSetPlayerSpectatorStatus.MATCH_MISSING;
                case PLAYER_MISSING -> NexoriSetPlayerSpectatorStatus.PLAYER_MISSING;
                case MATCH_ALREADY_COMPLETED -> NexoriSetPlayerSpectatorStatus.MATCH_ALREADY_COMPLETED;
                case INVALID_REASON -> NexoriSetPlayerSpectatorStatus.INVALID_REASON;
            },
            result.matchId(),
            result.playerUuid(),
            result.spectator(),
            result.message()
        );
    }

    @Nonnull
    @Override
    public NexoriReturnPlayerResult returnPlayerToLobby(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        int delaySeconds,
        @Nonnull String reason
    ) {
        ArenaMatchService.ReturnPlayerResult result = arenaMatchService.returnPlayerToLobby(matchId, playerUuid, delaySeconds, reason);
        return new NexoriReturnPlayerResult(
            switch (result.outcome()) {
                case SCHEDULED -> NexoriReturnPlayerStatus.SCHEDULED;
                case MATCH_MISSING -> NexoriReturnPlayerStatus.MATCH_MISSING;
                case PLAYER_MISSING -> NexoriReturnPlayerStatus.PLAYER_MISSING;
                case INVALID_DELAY -> NexoriReturnPlayerStatus.INVALID_DELAY;
                case INVALID_REASON -> NexoriReturnPlayerStatus.INVALID_REASON;
            },
            result.matchId(),
            result.playerUuid(),
            result.returnAtEpochMs(),
            result.message()
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

    @Deprecated
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
        return enqueueBackendResult(localResult);
    }

    @Nonnull
    @Override
    public NexoriSubmitFinalMatchResultResult submitFinalMatchResult(@Nonnull NexoriSubmitFinalMatchResultRequest request) {
        if (request == null) {
            return new NexoriSubmitFinalMatchResultResult(
                NexoriMatchCompletionStatus.INVALID_RESULT,
                NexoriBackendReportStatus.NOT_ATTEMPTED,
                "",
                "Request cannot be null."
            );
        }
        ArenaMatchService.SubmitMatchResult localResult = arenaMatchService.submitFinalMatchResult(
            request.matchId(),
            request.reason() == null ? "" : request.reason(),
            request.customData()
        );
        NexoriSubmitMatchResultResult result = enqueueBackendResult(localResult);
        return new NexoriSubmitFinalMatchResultResult(
            result.matchStatus(),
            result.backendReportStatus(),
            result.resultId(),
            result.message()
        );
    }

    @Nonnull
    @Override
    public NexoriCloseMatchAdmissionResult closeMatchAdmission(@Nonnull NexoriCloseMatchAdmissionRequest request) {
        if (request == null) {
            return new NexoriCloseMatchAdmissionResult(
                NexoriCloseMatchAdmissionStatus.INVALID_REASON,
                "",
                false,
                "Request cannot be null."
            );
        }
        ArenaMatchService.CloseMatchAdmissionResult localResult = arenaMatchService.closeMatchAdmission(
            request.matchId(),
            request.reason() == null
                ? null
                : switch (request.reason()) {
                    case MOD_REQUEST -> ArenaMatchService.CloseMatchAdmissionReason.MOD_REQUEST;
                    case GAME_PHASE_LOCKED -> ArenaMatchService.CloseMatchAdmissionReason.GAME_PHASE_LOCKED;
                    case ROSTER_LOCKED -> ArenaMatchService.CloseMatchAdmissionReason.ROSTER_LOCKED;
                    case ADMIN_FORCED -> ArenaMatchService.CloseMatchAdmissionReason.ADMIN_FORCED;
                },
            request.message() == null ? "" : request.message()
        );
        return new NexoriCloseMatchAdmissionResult(
            switch (localResult.outcome()) {
                case CLOSED -> NexoriCloseMatchAdmissionStatus.CLOSED;
                case ALREADY_CLOSED -> NexoriCloseMatchAdmissionStatus.ALREADY_CLOSED;
                case MATCH_MISSING -> NexoriCloseMatchAdmissionStatus.MATCH_MISSING;
                case MATCH_NOT_BACKEND_DRIVEN -> NexoriCloseMatchAdmissionStatus.MATCH_NOT_BACKEND_DRIVEN;
                case INVALID_REASON -> NexoriCloseMatchAdmissionStatus.INVALID_REASON;
                case REPORTING_DISABLED -> NexoriCloseMatchAdmissionStatus.REPORTING_DISABLED;
            },
            localResult.matchId(),
            localResult.closedLocally(),
            localResult.message()
        );
    }

    @Nonnull
    private NexoriSubmitMatchResultResult enqueueBackendResult(@Nonnull ArenaMatchService.SubmitMatchResult localResult) {
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
            localResult.customData(),
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
            case LOSS -> ArenaPlayerResolutionOutcome.LOSS;
            case DISCONNECTED -> ArenaPlayerResolutionOutcome.DISCONNECTED;
        };
    }

    @Nonnull
    private NexoriMatchResultPlayerOutcome publicOutcome(
        @Nonnull ArenaPlayerResolutionOutcome outcome,
        @Nonnull String backendOutcome
    ) {
        if (NexoriMatchResultPlayerOutcome.DISCONNECTED.name().equalsIgnoreCase(backendOutcome)) {
            return NexoriMatchResultPlayerOutcome.DISCONNECTED;
        }
        return switch (outcome) {
            case WIN -> NexoriMatchResultPlayerOutcome.WIN;
            case LOSS, DISCONNECTED -> NexoriMatchResultPlayerOutcome.LOSS;
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
