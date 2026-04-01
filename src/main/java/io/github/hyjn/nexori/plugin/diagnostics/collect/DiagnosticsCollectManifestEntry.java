package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record DiagnosticsCollectManifestEntry(
    @Nonnull String fileId,
    @Nonnull String fileName,
    boolean closed,
    @Nullable String fileSha256,
    long lineCount,
    long byteSize,
    long startedAtEpochMs,
    long endedAtEpochMs,
    long windowStartLineInclusive,
    long windowEndLineExclusive,
    long windowEventCount,
    long windowByteCount,
    @Nullable Long snapshotLineCount,
    @Nullable Long snapshotByteSize,
    @Nullable Long snapshotEndedAtEpochMs,
    @Nullable String snapshotPrefixSha256
) {
}
