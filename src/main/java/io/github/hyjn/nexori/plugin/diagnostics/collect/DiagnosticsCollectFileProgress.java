package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record DiagnosticsCollectFileProgress(
    @Nonnull String fileId,
    @Nonnull String fileName,
    boolean closed,
    @Nullable String expectedFileSha256,
    @Nullable Long snapshotLineCount,
    @Nullable Long snapshotByteSize,
    @Nullable Long snapshotEndedAtEpochMs,
    @Nullable String snapshotPrefixSha256,
    long startedAtEpochMs,
    long endedAtEpochMs,
    long windowStartLineInclusive,
    long windowEndLineExclusive,
    long windowEventCount,
    long windowByteCount,
    long nextLineInclusive,
    int lastChunkIndex,
    boolean completed,
    int retryCount,
    @Nullable String lastError
) {

    @Nonnull
    public DiagnosticsCollectFileProgress withNextLineInclusive(long nextLineInclusive, int lastChunkIndex) {
        return new DiagnosticsCollectFileProgress(
            fileId,
            fileName,
            closed,
            expectedFileSha256,
            snapshotLineCount,
            snapshotByteSize,
            snapshotEndedAtEpochMs,
            snapshotPrefixSha256,
            startedAtEpochMs,
            endedAtEpochMs,
            windowStartLineInclusive,
            windowEndLineExclusive,
            windowEventCount,
            windowByteCount,
            nextLineInclusive,
            lastChunkIndex,
            nextLineInclusive >= windowEndLineExclusive,
            retryCount,
            lastError
        );
    }

    @Nonnull
    public DiagnosticsCollectFileProgress withError(@Nullable String lastError, int retryCount) {
        return new DiagnosticsCollectFileProgress(
            fileId,
            fileName,
            closed,
            expectedFileSha256,
            snapshotLineCount,
            snapshotByteSize,
            snapshotEndedAtEpochMs,
            snapshotPrefixSha256,
            startedAtEpochMs,
            endedAtEpochMs,
            windowStartLineInclusive,
            windowEndLineExclusive,
            windowEventCount,
            windowByteCount,
            nextLineInclusive,
            lastChunkIndex,
            completed,
            retryCount,
            lastError
        );
    }
}
