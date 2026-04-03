package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Public result states returned when an integration tries to resolve one player inside an active Nexori match.
 */
public enum NexoriResolvePlayerOutcome {
    UPDATED,
    MATCH_MISSING,
    PLAYER_MISSING
}
