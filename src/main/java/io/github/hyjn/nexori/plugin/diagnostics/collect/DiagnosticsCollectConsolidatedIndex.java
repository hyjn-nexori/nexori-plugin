package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import java.util.List;

public record DiagnosticsCollectConsolidatedIndex(
    int schemaVersion,
    @Nonnull String sessionId,
    long generatedAtEpochMs,
    long windowStartEpochMs,
    long windowEndEpochMs,
    long inputEvents,
    long writtenEvents,
    long duplicateEvents,
    long outputBytes,
    @Nonnull List<SourceSummary> sources
) {
    public static final int SCHEMA_VERSION = 1;

    public record SourceSummary(
        @Nonnull DiagnosticsCollectSourceKind sourceKind,
        @Nonnull String sourceServerId,
        @Nonnull String sourceConnectionAddress,
        long importedBytes,
        long importedEvents
    ) {
    }
}
