package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;

public record DiagnosticsCollectWindow(
    long startEpochMs,
    long endEpochMs,
    @Nonnull String label
) {
}
