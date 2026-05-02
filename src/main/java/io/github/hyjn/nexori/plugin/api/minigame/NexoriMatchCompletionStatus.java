package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Public match completion states returned by submitMatchResult.
 */
public enum NexoriMatchCompletionStatus {
    ACCEPTED,
    ALREADY_SUBMITTED,
    MATCH_MISSING,
    INVALID_RESULT
}
