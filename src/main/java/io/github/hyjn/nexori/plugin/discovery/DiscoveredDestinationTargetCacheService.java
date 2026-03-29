package io.github.hyjn.nexori.plugin.discovery;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DiscoveredDestinationTargetCacheService {

    private final DiscoveredDestinationTargetCacheStore store;
    private final Map<String, DiscoveredDestinationTargetSet> discoveriesByAddress = new LinkedHashMap<>();

    public DiscoveredDestinationTargetCacheService(@Nonnull DiscoveredDestinationTargetCacheStore store) throws IOException {
        this.store = store;
        for (DiscoveredDestinationTargetSet discovery : store.loadOrCreate()) {
            DiscoveredDestinationTargetSet normalized = discovery.normalized();
            discoveriesByAddress.put(normalized.connectionAddress(), normalized);
        }
    }

    @Nonnull
    public synchronized List<DiscoveredDestinationTargetSet> list() {
        return discoveriesByAddress.values().stream()
            .sorted(Comparator.comparing(DiscoveredDestinationTargetSet::connectionAddress))
            .toList();
    }

    @Nonnull
    public synchronized Optional<DiscoveredDestinationTargetSet> find(@Nonnull String rawConnectionAddress) {
        try {
            ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
            return Optional.ofNullable(discoveriesByAddress.get(peer.connectionAddress()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Nonnull
    public synchronized DiscoveredDestinationTargetSet saveDiscovery(
        @Nonnull String rawConnectionAddress,
        @Nonnull String remoteServerId,
        @Nonnull List<DiscoveredDestinationTargetSummary> targets
    ) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
        DiscoveredDestinationTargetSet discovery = new DiscoveredDestinationTargetSet(
            peer.connectionAddress(),
            remoteServerId,
            Instant.now().toEpochMilli(),
            targets
        ).normalized();
        discoveriesByAddress.put(discovery.connectionAddress(), discovery);
        persist();
        return discovery;
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(discoveriesByAddress.values()));
    }
}
