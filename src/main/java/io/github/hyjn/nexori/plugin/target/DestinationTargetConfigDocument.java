package io.github.hyjn.nexori.plugin.target;

import javax.annotation.Nonnull;
import java.util.List;

record DestinationTargetConfigDocument(
    int schemaVersion,
    List<DestinationTargetDefinition> destinationTargets
) {
    static final int CURRENT_SCHEMA_VERSION = 1;

    @Nonnull
    static DestinationTargetConfigDocument empty() {
        return new DestinationTargetConfigDocument(CURRENT_SCHEMA_VERSION, List.of());
    }
}
