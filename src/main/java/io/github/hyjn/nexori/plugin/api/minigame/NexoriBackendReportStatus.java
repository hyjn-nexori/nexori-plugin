package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Public backend reporting states returned separately from local match completion.
 */
public enum NexoriBackendReportStatus {
    QUEUED,
    DISABLED,
    EXTERNAL_MATCH_MISSING,
    STORE_FAILED,
    ALREADY_SUBMITTED,
    DUPLICATE_CONFLICT,
    NOT_ATTEMPTED
}
