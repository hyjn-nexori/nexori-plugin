package io.github.hyjn.nexori.plugin.diagnostics.reporting;

import javax.annotation.Nonnull;

public record DiagnosticsTestChartState(
    int schemaVersion,
    int version,
    @Nonnull String currentFileName,
    long generatedAtEpochMs,
    int visualValue
) {
    public static final int SCHEMA_VERSION = 1;
}
