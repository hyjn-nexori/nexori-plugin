package io.github.hyjn.nexori.plugin.api.minigame;

import javax.annotation.Nonnull;

/**
 * Public lifecycle callbacks for third-party minigame/rules-engine integrations.
 */
public interface NexoriMatchLifecycleListener {

    default void onMatchCreated(@Nonnull NexoriMatchLifecycleEvent event) {
    }

    default void onPlayerArrived(@Nonnull NexoriPlayerMatchLifecycleEvent event) {
    }

    default void onPlayerPlacementConfirmed(@Nonnull NexoriPlayerPlacementLifecycleEvent event) {
    }

    default void onMatchPlacementCompleted(@Nonnull NexoriMatchLifecycleEvent event) {
    }

    default void onMatchCompleted(@Nonnull NexoriMatchLifecycleEvent event) {
    }

    default void onMatchRuntimeClosed(@Nonnull NexoriMatchLifecycleEvent event) {
    }
}
