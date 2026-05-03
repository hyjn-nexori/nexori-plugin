package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result states returned when a rules mod updates logical spectator state.
 */
public enum NexoriSetPlayerSpectatorStatus {
    UPDATED,
    MATCH_MISSING,
    PLAYER_MISSING,
    MATCH_ALREADY_COMPLETED,
    INVALID_REASON
}
