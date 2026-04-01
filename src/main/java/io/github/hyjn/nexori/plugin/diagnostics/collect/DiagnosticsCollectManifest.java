package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import java.util.List;

public record DiagnosticsCollectManifest(
    @Nonnull String remoteServerId,
    @Nonnull String remoteConnectionAddress,
    long windowStartEpochMs,
    long windowEndEpochMs,
    @Nonnull List<DiagnosticsCollectManifestEntry> entries,
    int nextEntryIndex,
    boolean complete,
    long plannedAtEpochMs
) {
}
