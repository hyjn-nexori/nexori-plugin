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
}
