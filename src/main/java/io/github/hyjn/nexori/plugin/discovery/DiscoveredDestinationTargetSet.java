package io.github.hyjn.nexori.plugin.discovery;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;

public record DiscoveredDestinationTargetSet(
    String connectionAddress,
    String remoteServerId,
    long discoveredAtEpochMillis,
    List<DiscoveredDestinationTargetSummary> targets
) {

    @Nonnull
    public DiscoveredDestinationTargetSet normalized() {
        ConfiguredPeer peer = ConfiguredPeer.parse(connectionAddress);
        List<DiscoveredDestinationTargetSummary> normalizedTargets = targets == null
            ? List.of()
            : targets.stream()
                .filter(target -> target != null && target.id() != null && !target.id().isBlank())
                .sorted(Comparator.comparing(DiscoveredDestinationTargetSummary::id))
                .toList();
        return new DiscoveredDestinationTargetSet(
            peer.connectionAddress(),
            remoteServerId == null ? "" : remoteServerId.trim(),
            discoveredAtEpochMillis,
            normalizedTargets
        );
    }
}
