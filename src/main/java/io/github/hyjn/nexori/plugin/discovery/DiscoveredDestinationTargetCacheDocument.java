package io.github.hyjn.nexori.plugin.discovery;

import javax.annotation.Nonnull;
import java.util.List;

record DiscoveredDestinationTargetCacheDocument(
    int schemaVersion,
    List<DiscoveredDestinationTargetSet> discoveries
) {
    static final int CURRENT_SCHEMA_VERSION = 1;

    @Nonnull
    static DiscoveredDestinationTargetCacheDocument empty() {
        return new DiscoveredDestinationTargetCacheDocument(CURRENT_SCHEMA_VERSION, List.of());
    }
}
