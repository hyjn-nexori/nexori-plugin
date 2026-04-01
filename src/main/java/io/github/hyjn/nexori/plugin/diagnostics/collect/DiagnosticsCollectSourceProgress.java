package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record DiagnosticsCollectSourceProgress(
    @Nonnull DiagnosticsCollectSourceKind sourceKind,
    @Nonnull String sourceServerId,
    @Nonnull String sourceConnectionAddress,
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
