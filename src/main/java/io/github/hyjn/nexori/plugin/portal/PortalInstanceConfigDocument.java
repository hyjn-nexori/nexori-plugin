package io.github.hyjn.nexori.plugin.portal;

import javax.annotation.Nonnull;
import java.util.List;

public record PortalInstanceConfigDocument(
    int schemaVersion,
    List<PortalInstanceDefinition> portalInstances
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Nonnull
    public PortalInstanceConfigDocument {
        portalInstances = portalInstances == null ? List.of() : List.copyOf(portalInstances);
    }
}
