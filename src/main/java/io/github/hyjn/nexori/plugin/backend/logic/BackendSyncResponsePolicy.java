package io.github.hyjn.nexori.plugin.backend.logic;

import io.github.hyjn.nexori.plugin.backend.BackendSyncHealthState;
import io.github.hyjn.nexori.plugin.backend.BackendSyncHttpResult;

import javax.annotation.Nonnull;

/**
 * Pure backend sync response policy for health state, backoff and response-processing decisions.
 */
public final class BackendSyncResponsePolicy {

    @Nonnull
    public BackendSyncResponseDecision decide(
        @Nonnull BackendSyncHttpResult result,
        long nowEpochMs,
        long authBackoffMs,
        long errorBackoffMs
    ) {
        if (result.isAuthFailure()) {
            long nextAttemptAtEpochMs = nowEpochMs + authBackoffMs;
            return new BackendSyncResponseDecision(
                BackendSyncResponseDecision.Action.AUTH_FAILED,
                BackendSyncHealthState.failed(
                    "AUTH_FAILED",
                    401,
                    "HTTP",
                    "Backend sync auth failed.",
                    nowEpochMs,
                    nextAttemptAtEpochMs
                ),
                nextAttemptAtEpochMs,
                false
            );
        }
        if (result.isForbidden()) {
            long nextAttemptAtEpochMs = nowEpochMs + authBackoffMs;
            return new BackendSyncResponseDecision(
                BackendSyncResponseDecision.Action.FORBIDDEN,
                BackendSyncHealthState.failed(
                    "AUTH_FORBIDDEN",
                    403,
                    "HTTP",
                    "Backend sync forbidden.",
                    nowEpochMs,
                    nextAttemptAtEpochMs
                ),
                nextAttemptAtEpochMs,
                false
            );
        }
        if (!result.hasResponse()) {
            long nextAttemptAtEpochMs = nowEpochMs + errorBackoffMs;
            return new BackendSyncResponseDecision(
                BackendSyncResponseDecision.Action.FAILED_NO_RESPONSE,
                BackendSyncHealthState.failed(
                    "SYNC_FAILED",
                    result.statusCode(),
                    result.errorClass(),
                    result.message(),
                    nowEpochMs,
                    nextAttemptAtEpochMs
                ),
                nextAttemptAtEpochMs,
                false
            );
        }
        return new BackendSyncResponseDecision(
            BackendSyncResponseDecision.Action.PROCESS_RESPONSE,
            BackendSyncHealthState.healthy(nowEpochMs),
            0L,
            true
        );
    }
}
