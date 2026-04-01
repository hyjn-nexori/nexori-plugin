package io.github.hyjn.nexori.plugin.diagnostics.collect.payload;

import javax.annotation.Nonnull;

public record DiagnosticsCollectChunkResponsePayload(
    @Nonnull String sessionId,
    @Nonnull String requestId,
    @Nonnull String sourceServerId,
    @Nonnull String sourceConnectionAddress,
    @Nonnull String fileId,
    int chunkIndex,
    long startLineInclusive,
    long endLineExclusive,
    long chunkEventCount,
    long chunkRawBytes,
    @Nonnull String chunkSha256,
    @Nonnull String ndjsonBlock,
    boolean hasMoreInFile
) {
}
