package io.github.hyjn.nexori.plugin.minigame.transfer;

/**
 * Ordered phases of the unified minigame player transfer and placement state machine.
 *
 * <p>Every player entering a minigame (initial match, backfill, or future join) goes through
 * this same sequence.  Terminal phases are {@link #CONFIRMED}, {@link #FAILED},
 * {@link #FALLBACK}, {@link #RETURNING_TO_LOBBY}, and {@link #CLOSED}.</p>
 */
public enum MinigameTransferPhase {

    // Pre-acceptance ----------------------------------------------------------------

    /** Session was created; waiting for the first safe PlayerReady in any world. */
    WAITING_FOR_SAFE_READY,

    /** Player is safely ready in the default/staging world; launch context will be parsed. */
    SAFE_DEFAULT_READY,

    /** Launch context was valid; match was created or resolved; player recorded as arrived. */
    ACCEPTED_BY_MINIGAME,

    // Instance-world phases (skipped for no-instance arenas) ------------------------

    /** Async instance world materialization is in progress. */
    INSTANCE_WORLD_CREATING,

    /** Instance world materialization succeeded; ready to issue the placement teleport. */
    INSTANCE_WORLD_READY,

    /** Teleport component has been added; waiting for the player to arrive in the instance world. */
    TELEPORT_ISSUED,

    /** Waiting for the instance world to become alive or for a fresh PlayerReady observation there. */
    WAITING_FOR_INSTANCE_READY,

    /** Post-ready stabilization grace period is elapsing. */
    POST_READY_GRACE,

    /** Verifying that the player's position is within tolerance of the expected spawn slot. */
    VALIDATING_POSITION,

    // Terminal phases ---------------------------------------------------------------

    /** Placement confirmed: player is active in the gameplay world. */
    CONFIRMED,

    /**
     * Transfer failed with a specific reason.
     *
     * <p>This is terminal even when the match gateway successfully dispatches return-to-lobby
     * travel; that dispatch is currently observed through NEXORI_TRANSFER_RETURNING_TO_LOBBY
     * logs rather than by mutating the session phase after gateway side effects.</p>
     */
    FAILED,

    /** A graceful fallback was applied (e.g. left in default world with no match entry). */
    FALLBACK,

    /** Return-to-lobby travel was dispatched after a failure by a phase-aware caller. */
    RETURNING_TO_LOBBY,

    /** Session cleanup complete; no further action needed. */
    CLOSED
}
