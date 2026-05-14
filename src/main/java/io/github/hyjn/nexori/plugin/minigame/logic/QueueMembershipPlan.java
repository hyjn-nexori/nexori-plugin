package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;

/**
 * Pure result for queue membership state transitions.
 */
public record QueueMembershipPlan(
    boolean removed,
    @Nonnull QueueRuntimeState state
) {
}
