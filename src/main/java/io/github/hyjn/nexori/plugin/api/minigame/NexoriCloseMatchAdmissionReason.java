package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Public reasons a rules mod can use to explicitly close backend admission reporting.
 */
public enum NexoriCloseMatchAdmissionReason {
    MOD_REQUEST,
    GAME_PHASE_LOCKED,
    ROSTER_LOCKED,
    ADMIN_FORCED
}
