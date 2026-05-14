package io.github.hyjn.nexori.plugin.backend.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.github.hyjn.nexori.plugin.backend.payload.BackendMatchAdmissionStateResponsePayload;

import javax.annotation.Nonnull;

/**
 * Decides how admission state reporting should apply backend HTTP responses.
 */
public final class AdmissionStateResponsePolicy {

    public static final String HEALTHY = "HEALTHY";
    public static final String AUTH_FAILED = "AUTH_FAILED";

    private final Gson gson = new GsonBuilder().create();

    @Nonnull
    public AdmissionStateResponseDecision decide(
        int statusCode,
        String responseBody,
        long nowEpochMs,
        long authBackoffMs,
        long retryIntervalMs,
        boolean snapshotAdmissionReportingClosed,
        long snapshotStateExpiresAtEpochMs,
        boolean stateDirty,
        boolean reportableMatchStillExists
    ) {
        if (statusCode >= 200 && statusCode < 300) {
            return decideSuccess(responseBody, snapshotAdmissionReportingClosed, stateDirty);
        }
        if (statusCode == 400 || statusCode == 422) {
            boolean removePublicationState = snapshotAdmissionReportingClosed || !reportableMatchStillExists;
            return new AdmissionStateResponseDecision(
                AdmissionStateResponseDecision.Outcome.PERMANENT_FAILURE,
                "",
                "",
                0L,
                0L,
                false,
                false,
                false,
                snapshotAdmissionReportingClosed,
                removePublicationState,
                false,
                false,
                false
            );
        }
        if (statusCode == 401 || statusCode == 403) {
            long nextGlobalAttemptAtEpochMs = nowEpochMs + authBackoffMs;
            return new AdmissionStateResponseDecision(
                AdmissionStateResponseDecision.Outcome.AUTH_FAILURE,
                "",
                AUTH_FAILED,
                nextGlobalAttemptAtEpochMs,
                0L,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false
            );
        }

        boolean retryable = statusCode == 408 || statusCode == 429 || statusCode >= 500 || statusCode == 0;
        if (!retryable) {
            return new AdmissionStateResponseDecision(
                AdmissionStateResponseDecision.Outcome.UNEXPECTED_FAILURE,
                "",
                "",
                0L,
                0L,
                false,
                false,
                stateDirty,
                false,
                false,
                false,
                false,
                false
            );
        }

        long nextGlobalAttemptAtEpochMs = nowEpochMs + retryIntervalMs;
        if (stateDirty) {
            return new AdmissionStateResponseDecision(
                AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE,
                "",
                "",
                nextGlobalAttemptAtEpochMs,
                nextGlobalAttemptAtEpochMs,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false
            );
        }
        if (nowEpochMs <= snapshotStateExpiresAtEpochMs) {
            return new AdmissionStateResponseDecision(
                AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE,
                "",
                "",
                nextGlobalAttemptAtEpochMs,
                nextGlobalAttemptAtEpochMs,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false
            );
        }
        return new AdmissionStateResponseDecision(
            AdmissionStateResponseDecision.Outcome.RETRYABLE_FAILURE,
            "",
            "",
            nextGlobalAttemptAtEpochMs,
            0L,
            false,
            false,
            false,
            false,
            !reportableMatchStillExists,
            false,
            false,
            reportableMatchStillExists
        );
    }

    @Nonnull
    private AdmissionStateResponseDecision decideSuccess(
        String responseBody,
        boolean snapshotAdmissionReportingClosed,
        boolean stateDirty
    ) {
        String backendStatus = parseBackendStatus(responseBody);
        if (!isSemanticallyAcceptedBackendStatus(backendStatus)) {
            return new AdmissionStateResponseDecision(
                AdmissionStateResponseDecision.Outcome.NOT_SEMANTICALLY_ACKNOWLEDGED,
                backendStatus,
                HEALTHY,
                0L,
                0L,
                false,
                true,
                true,
                false,
                false,
                false,
                false,
                false
            );
        }
        return new AdmissionStateResponseDecision(
            AdmissionStateResponseDecision.Outcome.ACKNOWLEDGED,
            backendStatus,
            HEALTHY,
            0L,
            0L,
            true,
            false,
            !snapshotAdmissionReportingClosed && stateDirty,
            snapshotAdmissionReportingClosed,
            snapshotAdmissionReportingClosed,
            false,
            false,
            false
        );
    }

    @Nonnull
    public String parseBackendStatus(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "OK";
        }
        try {
            BackendMatchAdmissionStateResponsePayload response = gson.fromJson(
                JsonParser.parseString(responseBody),
                BackendMatchAdmissionStateResponsePayload.class
            );
            if (response != null && response.status() != null && !response.status().isBlank()) {
                return response.status().trim();
            }
        } catch (RuntimeException ignored) {
        }
        return "OK";
    }

    public boolean isSemanticallyAcceptedBackendStatus(@Nonnull String rawStatus) {
        String status = normalizeOptional(rawStatus).toUpperCase();
        return status.isBlank()
            || "OK".equals(status)
            || "ACCEPTED".equals(status)
            || "DUPLICATE".equals(status)
            || "DUPLICATE_ACCEPTED".equals(status);
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }
}
