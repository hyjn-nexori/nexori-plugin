package io.github.hyjn.nexori.plugin.minigame.transfer;

import com.hypixel.hytale.math.vector.Transform;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Mutable runtime state for a single player's unified minigame transfer and placement session.
 *
 * <p>One session is created per player per launch.  All fields are package-private so that
 * {@link MinigameTransferService} — the only class that creates and advances sessions — can
 * read and write them directly without boilerplate accessors.  External callers use the
 * read-only view returned by {@link MinigameTransferService#findSession(UUID)}.</p>
 *
 * <p>Instances are always accessed from within the synchronized context of
 * {@link io.github.hyjn.nexori.plugin.minigame.ArenaMatchService}, so no additional
 * synchronization is required here.</p>
 */
public final class MinigameTransferSession {

    // Identity -----------------------------------------------------------------------

    final UUID playerUuid;
    String username;

    // Launch context metadata --------------------------------------------------------

    String matchId = "";
    String queueId = "";
    String arenaId = "";
    String assignmentType = "";
    String assignmentId = "";
    String admissionReservationId = "";

    // Return info --------------------------------------------------------------------

    String returnConnectionAddress = "";
    String returnFallbackTargetId = "";
    String launchTravelProfileId = "";
    String originLobbyId = "";

    // Instance placement fields ------------------------------------------------------

    String instanceTemplateId = "";
    String expectedWorldName = "";
    @Nullable Transform expectedTransform = null;

    // Timing -------------------------------------------------------------------------

    final long createdAtEpochMs;
    long phaseStartedAtEpochMs;
    long lastUpdatedAtEpochMs;
    long lastReadyObservedAtEpochMs = 0L;
    long lastTeleportIssuedAtEpochMs = 0L;

    // Observed state (reset each tick) -----------------------------------------------

    String lastObservedWorldName = "";
    int stableTicks = 0;

    // Phase machine ------------------------------------------------------------------

    @Nonnull
    MinigameTransferPhase phase;

    @Nullable
    String failureReason = null;

    // Async handle for instance world creation (initial match with instance template) -

    @Nullable
    CompletableFuture<com.hypixel.hytale.server.core.universe.world.World> instanceWorldFuture = null;

    // Lifecycle guards ---------------------------------------------------------------

    boolean arrivalAcknowledged = false;
    boolean admissionReservationConsumed = false;
    boolean playerArrivedEventDispatched = false;
    boolean placementConfirmedEventDispatched = false;
    boolean returnToLobbyRequested = false;
    boolean matchWasCreatedByThisSession = false;
    boolean instanceTeleportDeferred = false;
    String deferredTeleportFailureDetail = "";

    MinigameTransferSession(@Nonnull UUID playerUuid, @Nonnull String username, long createdAtEpochMs) {
        this.playerUuid = playerUuid;
        this.username = username;
        this.createdAtEpochMs = createdAtEpochMs;
        this.phaseStartedAtEpochMs = createdAtEpochMs;
        this.lastUpdatedAtEpochMs = createdAtEpochMs;
        this.phase = MinigameTransferPhase.WAITING_FOR_SAFE_READY;
    }

    // Read-only accessors for external callers ---------------------------------------

    @Nonnull
    public UUID playerUuid() {
        return playerUuid;
    }

    @Nonnull
    public String username() {
        return username;
    }

    @Nonnull
    public String matchId() {
        return matchId;
    }

    @Nonnull
    public String arenaId() {
        return arenaId;
    }

    @Nonnull
    public String assignmentType() {
        return assignmentType;
    }

    @Nonnull
    public MinigameTransferPhase phase() {
        return phase;
    }

    @Nullable
    public String failureReason() {
        return failureReason;
    }

    @Nonnull
    public String expectedWorldName() {
        return expectedWorldName;
    }

    @Nonnull
    public String instanceTemplateId() {
        return instanceTemplateId;
    }

    public long createdAtEpochMs() {
        return createdAtEpochMs;
    }

    public boolean isTerminal() {
        MinigameTransferPhase p = this.phase;
        return p == MinigameTransferPhase.CONFIRMED
            || p == MinigameTransferPhase.FAILED
            || p == MinigameTransferPhase.FALLBACK
            || p == MinigameTransferPhase.RETURNING_TO_LOBBY
            || p == MinigameTransferPhase.CLOSED;
    }

    /** Returns true only when placement is fully confirmed in the gameplay world. */
    public boolean isPlacementConfirmed() {
        return phase == MinigameTransferPhase.CONFIRMED;
    }

    /** Returns true when the session has accepted the launch context and has a valid matchId. */
    public boolean hasAcceptedLaunch() {
        MinigameTransferPhase p = this.phase;
        return p != MinigameTransferPhase.WAITING_FOR_SAFE_READY
            && p != MinigameTransferPhase.SAFE_DEFAULT_READY
            && p != MinigameTransferPhase.FAILED
            && p != MinigameTransferPhase.FALLBACK
            && p != MinigameTransferPhase.CLOSED;
    }
}
