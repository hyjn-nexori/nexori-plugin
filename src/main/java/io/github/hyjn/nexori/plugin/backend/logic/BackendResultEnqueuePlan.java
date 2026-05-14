package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.BackendResultStore;

import javax.annotation.Nonnull;

/**
 * Pure plan returned when a backend result is prepared for enqueueing.
 */
public record BackendResultEnqueuePlan(
    @Nonnull Outcome outcome,
    @Nonnull String message,
    BackendResultStore.BackendResultRecord record
) {

    public enum Outcome {
        DISABLED,
        EXTERNAL_MATCH_MISSING,
        STORE_PENDING
    }

    public BackendResultEnqueuePlan {
        message = message == null || message.isBlank() ? "" : message.trim();
    }

    @Nonnull
    public static BackendResultEnqueuePlan disabled(@Nonnull String message) {
        return new BackendResultEnqueuePlan(Outcome.DISABLED, message, null);
    }

    @Nonnull
    public static BackendResultEnqueuePlan externalMatchMissing(@Nonnull String message) {
        return new BackendResultEnqueuePlan(Outcome.EXTERNAL_MATCH_MISSING, message, null);
    }

    @Nonnull
    public static BackendResultEnqueuePlan storePending(@Nonnull BackendResultStore.BackendResultRecord record) {
        return new BackendResultEnqueuePlan(Outcome.STORE_PENDING, "", record);
    }
}
