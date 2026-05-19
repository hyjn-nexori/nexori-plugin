package io.github.hyjn.nexori.plugin.api.minigame;

/**
 * Handle returned by Nexori listener registration methods.
 * Closing the handle unregisters the listener.
 */
public interface NexoriListenerRegistration extends AutoCloseable {

    /**
     * Unregisters the listener. Implementations should be idempotent.
     */
    @Override
    void close();
}
