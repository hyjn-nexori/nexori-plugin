package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nullable;

/**
 * Explicitly closes backend admission reporting for one active backend-driven match.
 */
public record NexoriCloseMatchAdmissionRequest(
    String matchId,
    NexoriCloseMatchAdmissionReason reason,
    @Nullable String message
) {
}
