package io.github.hyjn.nexori.plugin.discovery;

import java.util.List;

public record DestinationTargetDiscoveryResponsePayload(
    String requestId,
    List<DiscoveredDestinationTargetSummary> targets
) {
}
