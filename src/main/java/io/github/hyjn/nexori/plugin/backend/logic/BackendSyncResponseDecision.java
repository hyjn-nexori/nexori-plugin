package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.BackendSyncHealthState;

import javax.annotation.Nonnull;

/**
 * Pure decision returned by {@link BackendSyncResponsePolicy} for backend sync response handling.
 */
public record BackendSyncResponseDecision(
    @Nonnull Action action,
    @Nonnull BackendSyncHealthState healthState,
    long nextAttemptAtEpochMs,
    boolean shouldProcessResponse
) {

    public enum Action {
        PROCESS_RESPONSE,
        AUTH_FAILED,
        FORBIDDEN,
        FAILED_NO_RESPONSE
    }
}
