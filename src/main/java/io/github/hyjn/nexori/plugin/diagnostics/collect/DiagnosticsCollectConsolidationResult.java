package io.github.hyjn.nexori.plugin.diagnostics.collect;

import javax.annotation.Nonnull;

public record DiagnosticsCollectConsolidationResult(
    long inputEvents,
    long writtenEvents,
    long duplicateEvents,
    long outputBytes,
    @Nonnull String consolidatedNdjson
) {
}
