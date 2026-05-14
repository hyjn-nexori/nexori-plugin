package io.github.hyjn.nexori.plugin.minigame.logic;

/**
 * Pure placement and lifecycle evaluation for an active arena match.
 */
public record MatchPlacementEvaluation(
    int expectedPlayers,
    int arrivedInitialPlayers,
    int placedInitialPlayers,
    boolean placementComplete,
    boolean shouldMarkPlacementCompleted,
    boolean shouldMarkMatchCompleted
) {
}
