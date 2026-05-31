package io.github.hyjn.nexori.plugin.minigame.transfer;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Result returned by {@link MinigameTransferService#onMinigameLaunchSafeReady}.
 *
 * <h3>Outcomes and arrival handling</h3>
 * <ul>
 *   <li>{@link Kind#ACCEPTED} — transfer service took ownership.  Caller must call
 *       {@code SecureTravelService.acknowledgeRecentArrival} to remove the entry.</li>
 *   <li>{@link Kind#TERMINAL_FAILURE} — permanent rejection (invalid context, expired/rejected
 *       reservation).  Caller must call {@code acknowledgeRecentArrival} so the arrival is
 *       removed and does not accumulate.  Return-to-lobby is attempted by the service itself
 *       if return info is available.</li>
 *   <li>{@link Kind#RETRY_LATER} — timing issue (e.g. BACKFILL arrived before the initial
 *       match record exists on this server).  Caller must acknowledge the arrival because
 *       the service has stored an internal {@code PendingBackfillRetry} copy and will retry
 *       once the expected match appears.  The retry entry has a TTL after which it is
 *       promoted to TERMINAL_FAILURE.</li>
 * </ul>
 */
public record MinigameTransferOnReadyResult(
    @Nonnull Kind kind,
    @Nonnull List<Runnable> lifecycleDispatches
) {

    public enum Kind {
        ACCEPTED,
        TERMINAL_FAILURE,
        RETRY_LATER
    }

    /**
     * Caller should acknowledge (remove) the arrival from SecureTravelService.recentArrivals.
     * True for ACCEPTED, TERMINAL_FAILURE, and RETRY_LATER — the service takes ownership
     * of the arrival in all three cases to prevent stale entries from accumulating.
     * For RETRY_LATER, the arrival is stored internally and used on the retry attempt.
     */
    public boolean arrivalAcknowledged() {
        return kind == Kind.ACCEPTED || kind == Kind.TERMINAL_FAILURE || kind == Kind.RETRY_LATER;
    }

    @Nonnull
    public static MinigameTransferOnReadyResult accepted(@Nonnull List<Runnable> dispatches) {
        return new MinigameTransferOnReadyResult(Kind.ACCEPTED, dispatches);
    }

    @Nonnull
    public static MinigameTransferOnReadyResult terminalFailure() {
        return new MinigameTransferOnReadyResult(Kind.TERMINAL_FAILURE, List.of());
    }

    @Nonnull
    public static MinigameTransferOnReadyResult terminalFailure(@Nonnull List<Runnable> dispatches) {
        return new MinigameTransferOnReadyResult(Kind.TERMINAL_FAILURE, dispatches);
    }

    @Nonnull
    public static MinigameTransferOnReadyResult retryLater() {
        return new MinigameTransferOnReadyResult(Kind.RETRY_LATER, List.of());
    }
}
