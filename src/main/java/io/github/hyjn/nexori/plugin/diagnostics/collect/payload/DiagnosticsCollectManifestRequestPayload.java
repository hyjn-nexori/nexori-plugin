package io.github.hyjn.nexori.plugin.diagnostics.collect.payload;

import javax.annotation.Nonnull;

public record DiagnosticsCollectManifestRequestPayload(
    @Nonnull String sessionId,
    @Nonnull String requestId,
    long windowStartEpochMs,
    long windowEndEpochMs,
    int startEntryIndex
) {
}
