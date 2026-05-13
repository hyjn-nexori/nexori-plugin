package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Result returned by closeMatchAdmission.
 */
public record NexoriCloseMatchAdmissionResult(
    NexoriCloseMatchAdmissionStatus status,
    String matchId,
    boolean closedLocally,
    String message
) {
}
