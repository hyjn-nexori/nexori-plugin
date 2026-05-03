package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result states returned when a rules mod schedules a player return.
 */
public enum NexoriReturnPlayerStatus {
    SCHEDULED,
    MATCH_MISSING,
    PLAYER_MISSING,
    INVALID_DELAY,
    INVALID_REASON
}
