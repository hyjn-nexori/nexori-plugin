package io.github.hyjn.nexori.plugin.policy;

import javax.annotation.Nonnull;
import java.util.List;

record ServerPolicyCacheDocument(
    int schemaVersion,
    List<ServerPolicySummary> policies
) {
    static final int CURRENT_SCHEMA_VERSION = 1;

    @Nonnull
    static ServerPolicyCacheDocument empty() {
        return new ServerPolicyCacheDocument(CURRENT_SCHEMA_VERSION, List.of());
    }
}
