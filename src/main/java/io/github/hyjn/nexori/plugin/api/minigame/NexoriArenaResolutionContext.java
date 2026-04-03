package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read/write context passed to public arena resolution triggers.
 * Implementations expose a narrow match view plus helper methods to mark players as winners or losers using Nexori's
 * existing return flow.
 */
public interface NexoriArenaResolutionContext {

    @Nonnull
    String matchId();

    @Nonnull
    String queueId();

    @Nonnull
    String arenaId();

    int expectedPlayerCount();

    int arrivedPlayerCount();

    int activePlayerCount();

    int alivePlayerCount();

    boolean allExpectedPlayersArrived();

    @Nonnull
    List<UUID> activePlayerUuids();

    @Nonnull
    List<UUID> alivePlayerUuids();

    boolean isPlayerEliminated(@Nonnull UUID playerUuid);

    boolean hasWinner();

    @Nonnull
    Optional<UUID> winnerPlayerUuid();

    /**
     * Marks one player as winner using Nexori's default delayed return behavior for automatic arena triggers.
     *
     * @return {@code true} when the player belonged to the active match and the match state was updated.
     */
    boolean markPlayerWin(@Nonnull UUID playerUuid, @Nonnull String reason);

    /**
     * Marks one player as loser using Nexori's default delayed return behavior for automatic arena triggers.
     *
     * @return {@code true} when the player belonged to the active match and the match state was updated.
     */
    boolean markPlayerLoss(@Nonnull UUID playerUuid, @Nonnull String reason);
}
