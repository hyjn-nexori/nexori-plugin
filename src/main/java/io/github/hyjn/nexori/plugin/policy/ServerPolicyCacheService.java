package io.github.hyjn.nexori.plugin.policy;

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

public final class ServerPolicyCacheService {

    private final ServerPolicyCacheStore store;
    private final Map<String, ServerPolicySummary> policiesByAddress = new LinkedHashMap<>();

    public ServerPolicyCacheService(@Nonnull ServerPolicyCacheStore store) throws IOException {
        this.store = store;
        for (ServerPolicySummary policy : store.loadOrCreate()) {
            ServerPolicySummary normalized = policy.normalized();
            policiesByAddress.put(normalized.connectionAddress(), normalized);
        }
    }

    @Nonnull
    public synchronized List<ServerPolicySummary> list() {
        return policiesByAddress.values().stream()
            .sorted(Comparator.comparing(ServerPolicySummary::connectionAddress))
            .toList();
    }

    @Nonnull
    public synchronized Optional<ServerPolicySummary> find(@Nonnull String rawConnectionAddress) {
        try {
            ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
            return Optional.ofNullable(policiesByAddress.get(peer.connectionAddress()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Nonnull
    public synchronized ServerPolicySummary saveConfirmedPolicy(
        @Nonnull String rawConnectionAddress,
        @Nonnull String remoteServerId,
        boolean recoveryEnabled,
        int maxBackupsPerPlayer
    ) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
        ServerPolicySummary summary = new ServerPolicySummary(
            peer.connectionAddress(),
            remoteServerId,
            Instant.now().toEpochMilli(),
            recoveryEnabled,
            maxBackupsPerPlayer
        ).normalized();
        policiesByAddress.put(summary.connectionAddress(), summary);
        persist();
        return summary;
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(policiesByAddress.values()));
    }
}
