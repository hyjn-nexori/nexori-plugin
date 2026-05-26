package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriActiveMatchInfo;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkActivityListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriBackendReportStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionReason;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriCloseMatchAdmissionStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriListenerRegistration;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchCompletionStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchPlacementState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultPlayerOutcome;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchResultRequirements;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerOutcomeState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriReturnPlayerResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriReturnPlayerStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetAfkDetectionPolicyResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetAfkDetectionPolicyStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetMatchAfkDetectionPolicyRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerAfkDetectionPolicyRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerOutcomeResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerOutcomeStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerSpectatorResult;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSetPlayerSpectatorStatus;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitFinalMatchResultRequest;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriSubmitFinalMatchResultResult;
import io.github.hyjn.nexori.plugin.backend.BackendResultReportingService;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of Nexori's public minigame API.
 * This bridge translates the public contract into the internal arena-match runtime.
 */
public final class NexoriMinigameApiBridge implements NexoriMinigameApi {

    private final ArenaMatchService arenaMatchService;
    private final AfkActivityService afkActivityService;
    private final BackendResultReportingService backendResultReportingService;
    private final NexoriMatchLifecycleDispatcher matchLifecycleDispatcher;
    private final NexoriAfkActivityDispatcher afkActivityDispatcher;

    /**
     * Creates one bridge backed by the live arena-match service.
     */
    public NexoriMinigameApiBridge(
        @Nonnull ArenaMatchService arenaMatchService,
        @Nonnull AfkActivityService afkActivityService,
        @Nonnull BackendResultReportingService backendResultReportingService,
        @Nonnull NexoriMatchLifecycleDispatcher matchLifecycleDispatcher,
        @Nonnull NexoriAfkActivityDispatcher afkActivityDispatcher
    ) {
        this.arenaMatchService = arenaMatchService;
        this.afkActivityService = afkActivityService;
        this.backendResultReportingService = backendResultReportingService;
        this.matchLifecycleDispatcher = matchLifecycleDispatcher;
        this.afkActivityDispatcher = afkActivityDispatcher;
    }

    @Nonnull
    @Override
    public NexoriListenerRegistration registerMatchLifecycleListener(
        @Nonnull String rulesEngineId,
        @Nonnull NexoriMatchLifecycleListener listener
    ) {
        return matchLifecycleDispatcher.register(rulesEngineId, listener);
    }

    @Nonnull
    @Override
    public NexoriListenerRegistration registerAfkActivityListener(
        @Nonnull String rulesEngineId,
        @Nonnull NexoriAfkActivityListener listener
    ) {
        return afkActivityDispatcher.register(rulesEngineId, listener);
    }

    @Nonnull
    @Override
    public NexoriSetAfkDetectionPolicyResult setMatchAfkDetectionPolicy(
        @Nonnull NexoriSetMatchAfkDetectionPolicyRequest request
    ) {
        if (request == null || request.policy() == null || isBlank(request.matchId())) {
            return invalidAfkPolicyResult(request == null ? "" : request.matchId(), null);
        }
        ArenaMatchService.SetAfkDetectionPolicyResult result = arenaMatchService.setMatchAfkDetectionPolicy(
            request.matchId(),
            runtimeAfkPolicy(request.policy())
        );
        applyAfkPolicyStateActions(result);
        return publicAfkPolicyResult(result);
    }

    @Nonnull
    @Override
    public NexoriSetAfkDetectionPolicyResult clearMatchAfkDetectionPolicy(@Nonnull String matchId) {
        if (isBlank(matchId)) {
            return invalidAfkPolicyResult(matchId, null);
        }
        ArenaMatchService.SetAfkDetectionPolicyResult result = arenaMatchService.clearMatchAfkDetectionPolicy(matchId);
        applyAfkPolicyStateActions(result);
        return publicAfkPolicyResult(result);
    }

    @Nonnull
    @Override
    public NexoriSetAfkDetectionPolicyResult setPlayerAfkDetectionPolicy(
        @Nonnull NexoriSetPlayerAfkDetectionPolicyRequest request
    ) {
        if (request == null || request.playerUuid() == null || request.policy() == null || isBlank(request.matchId())) {
            return invalidAfkPolicyResult(request == null ? "" : request.matchId(), request == null ? null : request.playerUuid());
        }
        ArenaMatchService.SetAfkDetectionPolicyResult result = arenaMatchService.setPlayerAfkDetectionPolicy(
            request.matchId(),
            request.playerUuid(),
            runtimeAfkPolicy(request.policy())
        );
        applyAfkPolicyStateActions(result);
        return publicAfkPolicyResult(result);
    }

    @Nonnull
    @Override
    public NexoriSetAfkDetectionPolicyResult clearPlayerAfkDetectionPolicy(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid
    ) {
        if (playerUuid == null || isBlank(matchId)) {
            return invalidAfkPolicyResult(matchId, null);
        }
        ArenaMatchService.SetAfkDetectionPolicyResult result = arenaMatchService.clearPlayerAfkDetectionPolicy(matchId, playerUuid);
        applyAfkPolicyStateActions(result);
        return publicAfkPolicyResult(result);
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
                afkActivityService.afkPlayerUuids(info.matchId()),
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
        BackendResultEnqueueOutcome result = enqueueBackendResult(localResult);
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
    private BackendResultEnqueueOutcome enqueueBackendResult(@Nonnull ArenaMatchService.SubmitMatchResult localResult) {
        NexoriMatchCompletionStatus matchStatus = switch (localResult.outcome()) {
            case ACCEPTED -> NexoriMatchCompletionStatus.ACCEPTED;
            case ALREADY_SUBMITTED -> NexoriMatchCompletionStatus.ALREADY_SUBMITTED;
            case MATCH_MISSING -> NexoriMatchCompletionStatus.MATCH_MISSING;
            case INVALID_RESULT -> NexoriMatchCompletionStatus.INVALID_RESULT;
        };
        if (localResult.outcome() == ArenaMatchService.SubmitMatchOutcome.ALREADY_SUBMITTED) {
            return new BackendResultEnqueueOutcome(
                matchStatus,
                localResult.duplicateConflict()
                    ? NexoriBackendReportStatus.DUPLICATE_CONFLICT
                    : NexoriBackendReportStatus.ALREADY_SUBMITTED,
                "",
                localResult.message()
            );
        }
        if (localResult.outcome() != ArenaMatchService.SubmitMatchOutcome.ACCEPTED) {
            return new BackendResultEnqueueOutcome(
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
        return new BackendResultEnqueueOutcome(
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

    private record BackendResultEnqueueOutcome(
        NexoriMatchCompletionStatus matchStatus,
        NexoriBackendReportStatus backendReportStatus,
        String resultId,
        String message
    ) {
    }

    private void applyAfkPolicyStateActions(@Nonnull ArenaMatchService.SetAfkDetectionPolicyResult result) {
        for (ArenaMatchService.AfkPolicyStateAction action : result.stateActions()) {
            if (action.type() == ArenaMatchService.AfkPolicyStateActionType.CLEAR_FOR_POLICY_CHANGE) {
                afkActivityService.clearPlayerForPolicyChange(action.playerUuid(), action.effectivePolicy(), System.currentTimeMillis());
            } else {
                afkActivityService.resetPlayerActivity(action.playerUuid());
            }
        }
    }

    @Nonnull
    private NexoriSetAfkDetectionPolicyResult publicAfkPolicyResult(
        @Nonnull ArenaMatchService.SetAfkDetectionPolicyResult result
    ) {
        return new NexoriSetAfkDetectionPolicyResult(
            switch (result.outcome()) {
                case UPDATED -> NexoriSetAfkDetectionPolicyStatus.UPDATED;
                case CLEARED -> NexoriSetAfkDetectionPolicyStatus.CLEARED;
                case MATCH_MISSING -> NexoriSetAfkDetectionPolicyStatus.MATCH_MISSING;
                case PLAYER_MISSING -> NexoriSetAfkDetectionPolicyStatus.PLAYER_MISSING;
                case MATCH_ALREADY_COMPLETED -> NexoriSetAfkDetectionPolicyStatus.MATCH_ALREADY_COMPLETED;
                case INVALID_POLICY -> NexoriSetAfkDetectionPolicyStatus.INVALID_POLICY;
            },
            result.matchId(),
            result.playerUuid(),
            result.policy() == null ? null : publicAfkPolicy(result.policy()),
            result.message()
        );
    }

    @Nonnull
    private NexoriSetAfkDetectionPolicyResult invalidAfkPolicyResult(String matchId, UUID playerUuid) {
        return new NexoriSetAfkDetectionPolicyResult(
            NexoriSetAfkDetectionPolicyStatus.INVALID_POLICY,
            matchId == null ? "" : matchId.trim(),
            playerUuid,
            null,
            "AFK detection policy request and policy must be non-null."
        );
    }

    private boolean isBlank(String rawValue) {
        return rawValue == null || rawValue.trim().isBlank();
    }

    @Nonnull
    private AfkDetectionPolicy runtimeAfkPolicy(@Nonnull NexoriAfkDetectionPolicy policy) {
        NexoriAfkDetectionPolicy normalized = policy.normalized();
        return new AfkDetectionPolicy(normalized.enabled(), normalized.inactivityTimeoutSeconds());
    }

    @Nonnull
    private NexoriAfkDetectionPolicy publicAfkPolicy(@Nonnull AfkDetectionPolicy policy) {
        AfkDetectionPolicy normalized = AfkDetectionPolicy.normalize(policy);
        return new NexoriAfkDetectionPolicy(normalized.enabled(), normalized.inactivityTimeoutSeconds());
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
