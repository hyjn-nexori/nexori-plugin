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
     * Resolves one player result manually and lets Nexori handle the delayed return flow.
     */
    @Nonnull
    NexoriResolvePlayerResult resolvePlayerOutcome(
        @Nonnull String matchId,
        @Nonnull UUID playerUuid,
        @Nonnull NexoriPlayerResolutionOutcome outcome,
        int returnDelaySeconds,
        @Nonnull String reason
    );

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
