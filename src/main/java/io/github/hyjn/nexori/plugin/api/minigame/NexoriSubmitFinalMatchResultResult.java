package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result returned after Nexori attempts final local match completion and optional backend reporting.
 */
public record NexoriSubmitFinalMatchResultResult(
    NexoriMatchCompletionStatus matchStatus,
    NexoriBackendReportStatus backendReportStatus,
    String resultId,
    String message
) {
}
