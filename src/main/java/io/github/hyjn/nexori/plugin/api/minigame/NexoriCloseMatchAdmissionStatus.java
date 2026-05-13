package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result states returned after one explicit admission close request.
 */
public enum NexoriCloseMatchAdmissionStatus {
    CLOSED,
    ALREADY_CLOSED,
    MATCH_MISSING,
    MATCH_NOT_BACKEND_DRIVEN,
    INVALID_REASON,
    REPORTING_DISABLED
}
