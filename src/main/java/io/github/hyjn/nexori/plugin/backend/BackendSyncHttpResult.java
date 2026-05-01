package io.github.hyjn.nexori.plugin.backend;

import io.github.hyjn.nexori.plugin.backend.payload.BackendSyncResponsePayload;

import javax.annotation.Nonnull;

public record BackendSyncHttpResult(
    String syncId,
    long sequence,
    int statusCode,
    String errorClass,
    String message,
    BackendSyncResponsePayload response
) {

    @Nonnull
    public static BackendSyncHttpResult success(
        @Nonnull String syncId,
        long sequence,
        int statusCode,
        @Nonnull BackendSyncResponsePayload response
    ) {
        return new BackendSyncHttpResult(syncId, sequence, statusCode, "", "", response);
    }

    @Nonnull
    public static BackendSyncHttpResult failure(
        @Nonnull String syncId,
        long sequence,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message
    ) {
        return new BackendSyncHttpResult(syncId, sequence, statusCode, normalize(errorClass), normalize(message), null);
    }

    public boolean hasResponse() {
        return response != null;
    }

    public boolean isAuthFailure() {
        return statusCode == 401;
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }

    @Nonnull
    private static String normalize(String rawValue) {
        return rawValue == null || rawValue.isBlank() ? "" : rawValue.trim();
    }
}
