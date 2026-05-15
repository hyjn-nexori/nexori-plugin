package io.github.hyjn.nexori.plugin.discovery.logic;

import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;

import javax.annotation.Nonnull;
import java.util.Map;

public final class DestinationTargetSummaryBuilder {

    private DestinationTargetSummaryBuilder() {
    }

    @Nonnull
    public static DiscoveredDestinationTargetSummary summarize(
        @Nonnull DestinationTargetDefinition target,
        @Nonnull Map<String, String> portalIdsByTargetId
    ) {
        if (target.kind() != DestinationTargetKind.PORTAL) {
            return DiscoveredDestinationTargetSummary.from(target);
        }
        return DiscoveredDestinationTargetSummary.from(
            target,
            portalIdsByTargetId.getOrDefault(target.id(), "")
        );
    }
}
