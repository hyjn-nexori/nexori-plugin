package io.github.hyjn.nexori.plugin.accessgate.logic;

import javax.annotation.Nonnull;

/**
 * Pure admission decision for an access-gate setup connection.
 */
public record AccessGateAdmissionDecision(
    @Nonnull Outcome outcome,
    @Nonnull String message,
    int occupancy,
    int maxPlayers,
    int publicCap,
    @Nonnull AccessGateBypassType bypassType,
    boolean shouldTrackPending
) {

    public enum Outcome {
        ALLOW,
        DENY_HARD_CAP,
        DENY_PUBLIC_CAP
    }

    public boolean allowed() {
        return outcome == Outcome.ALLOW;
    }
}
