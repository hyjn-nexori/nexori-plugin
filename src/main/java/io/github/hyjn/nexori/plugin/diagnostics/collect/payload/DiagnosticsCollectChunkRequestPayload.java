package io.github.hyjn.nexori.plugin.diagnostics.collect.payload;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record DiagnosticsCollectChunkRequestPayload(
    @Nonnull String sessionId,
    @Nonnull String requestId,
    @Nonnull String fileId,
    boolean closed,
    @Nullable String expectedFileSha256,
    @Nullable Long snapshotLineCount,
    @Nullable Long snapshotByteSize,
    @Nullable Long snapshotEndedAtEpochMs,
    @Nullable String snapshotPrefixSha256,
    long windowStartLineInclusive,
    long windowEndLineExclusive,
    long nextLineInclusive
) {
}
