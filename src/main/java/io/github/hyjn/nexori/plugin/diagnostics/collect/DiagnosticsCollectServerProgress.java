package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record DiagnosticsCollectServerProgress(
    @Nonnull String remoteServerId,
    @Nonnull String remoteConnectionAddress,
    @Nonnull DiagnosticsCollectStatus status,
    long estimatedBytes,
    long downloadedBytes,
    long estimatedEvents,
    long downloadedEvents,
    @Nullable String currentFileId,
    @Nullable String lastError,
    @Nonnull List<DiagnosticsCollectFileProgress> files
) {
}
