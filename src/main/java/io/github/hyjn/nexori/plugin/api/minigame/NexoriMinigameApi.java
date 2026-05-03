package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Nexori minigame integration surface intended for other mods.
 * This keeps external integrations away from internal runtime services while still exposing the supported hooks.
 */
public interface NexoriMinigameApi {

    /**
     * Finds the currently active Nexori match id for one player UUID.
     */
    @Nonnull
    Optional<String> findActiveMatchId(@Nonnull UUID playerUuid);

    /**
     * Finds an active player in one match by UUID string or by current username.
     */
    @Nonnull
    Optional<UUID> findActivePlayerUuid(@Nonnull String matchId, @Nonnull String playerToken);

    /**
     * Returns the public runtime snapshot for one active match.
     */
    @Nonnull
    Optional<NexoriActiveMatchInfo> findActiveMatchInfo(@Nonnull String matchId);

    /**
     * Returns the rules engine id that should control one active manual/custom match.
     */
    @Nonnull
    Optional<String> findRulesEngineId(@Nonnull String matchId);

    /**
     * Resolves one player result manually and lets Nexori handle the delayed return flow.
     */
    @Deprecated
    @Nonnull
    NexoriResolvePlayerResult resolvePlayerOutcome(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        @Nonnull NexoriPlayerResolutionOutcome outcome,
        int returnDelaySeconds,
        @Nonnull String reason
    );

    /**
     * Stores or replaces one player's accumulated outcome inside the active match runtime.
     */
    @Nonnull
    NexoriSetPlayerOutcomeResult setPlayerOutcome(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        @Nonnull NexoriMatchResultPlayerOutcome outcome,
        @Nonnull String reason
    );

    /**
     * Stores logical spectator state for one player inside the active match runtime.
     */
    @Nonnull
    NexoriSetPlayerSpectatorResult setPlayerSpectator(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        boolean spectator,
        @Nonnull String reason
    );

    /**
     * Schedules one player for Nexori's return-to-lobby flow without changing their outcome.
     */
    @Nonnull
    NexoriReturnPlayerResult returnPlayerToLobby(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        int delaySeconds,
        @Nonnull String reason
    );

    /**
     * Returns the complete player set a rules mod must include when submitting a match result.
     */
    @Nonnull
    Optional<NexoriMatchResultRequirements> findMatchResultRequirements(@Nonnull String matchId);

    /**
     * Completes one match locally and optionally queues a backend result report when configured.
     */
    @Deprecated
    @Nonnull
    NexoriSubmitMatchResultResult submitMatchResult(@Nonnull NexoriSubmitMatchResultRequest request);

    /**
     * Completes one match using accumulated player outcomes and optionally queues a backend result report when configured.
     */
    @Nonnull
    NexoriSubmitFinalMatchResultResult submitFinalMatchResult(@Nonnull NexoriSubmitFinalMatchResultRequest request);

    /**
     * Returns the state of Nexori's initial player placement phase for one active match.
     */
    @Nonnull
    Optional<NexoriMatchPlacementState> findMatchPlacementState(@Nonnull String matchId);

    /**
     * Returns the active match resolution trigger id for one active match.
     * When this returns "none", the match expects manual resolution from the third-party mod.
     */
    @Nonnull
    Optional<String> findMatchResolutionTriggerId(@Nonnull String matchId);
}
