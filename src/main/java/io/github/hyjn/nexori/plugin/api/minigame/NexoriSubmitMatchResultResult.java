package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result returned after Nexori attempts local match completion and optional backend reporting.
 */
public record NexoriSubmitMatchResultResult(
    NexoriMatchCompletionStatus matchStatus,
    NexoriBackendReportStatus backendReportStatus,
    String resultId,
    String message
) {
}
