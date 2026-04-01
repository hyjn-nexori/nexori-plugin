package io.github.hyjn.nexori.plugin.diagnostics.collect.payload;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record DiagnosticsCollectErrorPayload(
    @Nonnull String sessionId,
    @Nonnull String requestId,
    @Nullable String fileId,
    @Nonnull String code,
    @Nonnull String message
) {
}
