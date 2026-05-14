package io.github.hyjn.nexori.plugin.travel.logic;

import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.travel.SecureTravelPayload;

import javax.annotation.Nonnull;

/**
 * Pure dispatch plan for a secure travel referral before runtime signing and transfer side effects.
 */
public record SecureTravelDispatchPlan(
    @Nonnull TravelProfileType travelProfileType,
    @Nonnull SecureTravelPayload payload,
    boolean shouldPrepareOriginInventoryTransfer
) {
}
