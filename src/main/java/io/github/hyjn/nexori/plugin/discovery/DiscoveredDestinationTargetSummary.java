package io.github.hyjn.nexori.plugin.discovery;

import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;

import javax.annotation.Nonnull;

public record DiscoveredDestinationTargetSummary(
    String id,
    String displayName,
    String kind,
    String worldName,
    String arrivalPointId,
    String portalId
) {

    @Nonnull
    public static DiscoveredDestinationTargetSummary from(@Nonnull DestinationTargetDefinition target) {
        return from(target, "");
    }

    @Nonnull
    public static DiscoveredDestinationTargetSummary from(
        @Nonnull DestinationTargetDefinition target,
        @Nonnull String portalId
    ) {
        return new DiscoveredDestinationTargetSummary(
            target.id(),
            target.displayName(),
            target.kind().name(),
            target.worldName(),
            target.arrivalPointId(),
            portalId == null ? "" : portalId.trim().toLowerCase()
        );
    }
}
