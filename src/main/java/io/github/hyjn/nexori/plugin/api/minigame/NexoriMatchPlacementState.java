package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Public snapshot of Nexori's initial player placement phase for one active match.
 * This only describes whether Nexori has finished positioning players at their initial entry locations.
 */
public record NexoriMatchPlacementState(
    int expectedPlayers,
    int arrivedPlayers,
    int placedPlayers,
    boolean placementComplete
) {
}
