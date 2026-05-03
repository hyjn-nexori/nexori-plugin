package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result states returned when a rules mod stores one player's runtime outcome.
 */
public enum NexoriSetPlayerOutcomeStatus {
    UPDATED,
    MATCH_MISSING,
    PLAYER_MISSING,
    MATCH_ALREADY_COMPLETED,
    INVALID_OUTCOME,
    INVALID_REASON
}
