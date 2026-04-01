package io.github.hyjn.nexori.plugin.diagnostics;

import javax.annotation.Nonnull;
import java.util.Map;

public record DiagnosticsSegmentMetadata(
    int schemaVersion,
    @Nonnull String fileId,
    @Nonnull String fileName,
    long startedAtEpochMs,
    long endedAtEpochMs,
    long lineCount,
    long byteSize,
    @Nonnull String sha256,
    long firstSequence,
    long lastSequence,
    Map<String, Long> categoryCounts,
    Map<String, Long> outcomeCounts
) {

    public static final int SCHEMA_VERSION = 1;
}
