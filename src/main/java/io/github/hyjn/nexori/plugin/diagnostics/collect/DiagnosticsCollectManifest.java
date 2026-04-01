package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import java.util.List;

public record DiagnosticsCollectManifest(
    @Nonnull DiagnosticsCollectSourceKind sourceKind,
    @Nonnull String sourceServerId,
    @Nonnull String sourceConnectionAddress,
    long windowStartEpochMs,
    long windowEndEpochMs,
    @Nonnull List<DiagnosticsCollectManifestEntry> entries,
    int nextEntryIndex,
    boolean complete,
    long plannedAtEpochMs
) {
}
