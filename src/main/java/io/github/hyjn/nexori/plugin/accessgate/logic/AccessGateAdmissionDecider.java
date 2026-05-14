package io.github.hyjn.nexori.plugin.accessgate.logic;

import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateConfigDocument;

import javax.annotation.Nonnull;

/**
 * Pure decider for access-gate capacity and bypass admission rules.
 */
public final class AccessGateAdmissionDecider {

    @Nonnull
    public AccessGateAdmissionDecision decide(
        @Nonnull NexoriAccessGateConfigDocument config,
        int occupancy,
        @Nonnull AccessGateBypassType bypassType
    ) {
        int normalizedOccupancy = Math.max(0, occupancy);
        int maxPlayers = Math.max(1, config.maxPlayers());
        int publicCap = Math.max(0, maxPlayers - config.reservedPrioritySlots());
        AccessGateBypassType normalizedBypassType = bypassType == null ? AccessGateBypassType.NONE : bypassType;
        boolean bypass = normalizedBypassType != AccessGateBypassType.NONE;

        if (!config.enabled()) {
            return allow(config, normalizedOccupancy, maxPlayers, publicCap, normalizedBypassType);
        }
        if (normalizedOccupancy >= maxPlayers) {
            return deny(
                AccessGateAdmissionDecision.Outcome.DENY_HARD_CAP,
                config,
                normalizedOccupancy,
                maxPlayers,
                publicCap,
                normalizedBypassType
            );
        }
        if (!bypass && normalizedOccupancy >= publicCap) {
            return deny(
                AccessGateAdmissionDecision.Outcome.DENY_PUBLIC_CAP,
                config,
                normalizedOccupancy,
                maxPlayers,
                publicCap,
                normalizedBypassType
            );
        }
        return allow(config, normalizedOccupancy, maxPlayers, publicCap, normalizedBypassType);
    }

    @Nonnull
    private static AccessGateAdmissionDecision allow(
        @Nonnull NexoriAccessGateConfigDocument config,
        int occupancy,
        int maxPlayers,
        int publicCap,
        @Nonnull AccessGateBypassType bypassType
    ) {
        return new AccessGateAdmissionDecision(
            AccessGateAdmissionDecision.Outcome.ALLOW,
            config.fullMessage(),
            occupancy,
            maxPlayers,
            publicCap,
            bypassType,
            true
        );
    }

    @Nonnull
    private static AccessGateAdmissionDecision deny(
        @Nonnull AccessGateAdmissionDecision.Outcome outcome,
        @Nonnull NexoriAccessGateConfigDocument config,
        int occupancy,
        int maxPlayers,
        int publicCap,
        @Nonnull AccessGateBypassType bypassType
    ) {
        return new AccessGateAdmissionDecision(
            outcome,
            config.fullMessage(),
            occupancy,
            maxPlayers,
            publicCap,
            bypassType,
            false
        );
    }
}
