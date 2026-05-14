package io.github.hyjn.nexori.plugin.minigame.logic;

import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;

/**
 * Pure result for queue launch outcome state transitions.
 */
public record QueueLaunchOutcomePlan(@Nonnull QueueRuntimeState state) {
}
