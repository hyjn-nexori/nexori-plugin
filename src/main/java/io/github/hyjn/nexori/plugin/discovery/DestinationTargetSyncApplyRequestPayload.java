package io.github.hyjn.nexori.plugin.discovery;

import java.util.List;

public record DestinationTargetSyncApplyRequestPayload(
    String requestId,
    List<DiscoveredDestinationTargetSet> discoveries
) {
}
