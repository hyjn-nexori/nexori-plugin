package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure evaluator for initial roster placement and match lifecycle completion.
 */
public final class MatchPlacementEvaluator {

    @Nonnull
    public MatchPlacementEvaluation evaluate(
        @Nonnull ArenaActiveMatch match,
        @Nonnull Set<UUID> pendingUnconfirmedPlacementPlayerUuids
    ) {
        int expectedPlayers = match.expectedPlayerCount();
        int arrivedInitialPlayers = countArrivedInitialPlayers(match);
        int placedInitialPlayers = countPlacedInitialPlayers(match, pendingUnconfirmedPlacementPlayerUuids);
        boolean placementComplete = expectedPlayers > 0
            && arrivedInitialPlayers >= expectedPlayers
            && placedInitialPlayers >= expectedPlayers;
        return new MatchPlacementEvaluation(
            expectedPlayers,
            arrivedInitialPlayers,
            placedInitialPlayers,
            placementComplete,
            match.placementCompletedAtEpochMs() <= 0L && placementComplete,
            match.hasWinner() || match.hasSubmittedResult()
        );
    }

    public int countArrivedInitialPlayers(@Nonnull ArenaActiveMatch match) {
        if (match.expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        int arrivedInitialPlayers = 0;
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        for (UUID playerUuid : match.arrivedPlayerUuids()) {
            if (expected.contains(playerUuid)) {
                arrivedInitialPlayers++;
            }
        }
        return arrivedInitialPlayers;
    }

    public int countPlacedInitialPlayers(
        @Nonnull ArenaActiveMatch match,
        @Nonnull Set<UUID> pendingUnconfirmedPlacementPlayerUuids
    ) {
        if (match.expectedPlayerUuids().isEmpty()) {
            return 0;
        }
        int placedInitialPlayers = 0;
        LinkedHashSet<UUID> expected = new LinkedHashSet<>(match.expectedPlayerUuids());
        for (UUID playerUuid : match.activePlayerUuids()) {
            if (!expected.contains(playerUuid)) {
                continue;
            }
            placedInitialPlayers++;
        }
        return placedInitialPlayers;
    }
}
