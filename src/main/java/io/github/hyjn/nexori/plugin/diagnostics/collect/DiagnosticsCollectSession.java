package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public record DiagnosticsCollectSession(
    int schemaVersion,
    @Nonnull String sessionId,
    @Nonnull DiagnosticsCollectStatus status,
    long createdAtEpochMs,
    long updatedAtEpochMs,
    @Nonnull String windowPresetId,
    long windowStartEpochMs,
    long windowEndEpochMs,
    @Nonnull String originPlayerUuid,
    @Nonnull DiagnosticsCollectOriginSnapshot originSnapshot,
    @Nonnull List<DiagnosticsCollectSourceProgress> sources,
    @Nullable String currentSourceServerId,
    @Nullable String currentRequestId,
    long estimatedTotalBytes,
    long downloadedBytes,
    long estimatedTotalEvents,
    long downloadedEvents,
    @Nullable String lastError
) {
    public static final int SCHEMA_VERSION = 1;
}
