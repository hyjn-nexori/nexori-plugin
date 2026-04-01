package io.github.hyjn.nexori.plugin.diagnostics.collect.payload;

import io.github.hyjn.nexori.plugin.diagnostics.collect.DiagnosticsCollectManifestEntry;

import javax.annotation.Nonnull;
import java.util.List;

public record DiagnosticsCollectManifestResponsePayload(
    @Nonnull String sessionId,
    @Nonnull String requestId,
    @Nonnull String sourceServerId,
    @Nonnull String sourceConnectionAddress,
    int startEntryIndex,
    int nextEntryIndex,
    boolean hasMoreEntries,
    @Nonnull List<DiagnosticsCollectManifestEntry> entries
) {
}
