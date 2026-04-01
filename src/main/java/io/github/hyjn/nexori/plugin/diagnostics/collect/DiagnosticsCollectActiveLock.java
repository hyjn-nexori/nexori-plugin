package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;

public record DiagnosticsCollectActiveLock(
    @Nonnull String sessionId,
    @Nonnull DiagnosticsCollectStatus status,
    long updatedAtEpochMs
) {
}
