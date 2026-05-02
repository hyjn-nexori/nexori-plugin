package io.github.hyjn.nexori.plugin.backend;

import io.github.hyjn.nexori.plugin.backend.payload.BackendResultResponsePayload;

import javax.annotation.Nonnull;

public record BackendResultHttpResult(
    String requestId,
    String resultId,
    int statusCode,
    String errorClass,
    String message,
    BackendResultResponsePayload response
) {

    @Nonnull
    public static BackendResultHttpResult success(
        @Nonnull String requestId,
        @Nonnull String resultId,
        int statusCode,
        @Nonnull BackendResultResponsePayload response
    ) {
        return new BackendResultHttpResult(requestId, resultId, statusCode, "", "", response);
    }

    @Nonnull
    public static BackendResultHttpResult failure(
        @Nonnull String requestId,
        @Nonnull String resultId,
        int statusCode,
        @Nonnull String errorClass,
        @Nonnull String message
    ) {
        return new BackendResultHttpResult(requestId, resultId, statusCode, normalize(errorClass), normalize(message), null);
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
