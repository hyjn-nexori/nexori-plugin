package io.github.hyjn.nexori.plugin.peers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfiguredPeerService {

    private final ConfiguredPeerStore store;
    private final Map<String, ConfiguredPeer> peersByAddress = new LinkedHashMap<>();

    public ConfiguredPeerService(@Nonnull ConfiguredPeerStore store) throws IOException {
        this.store = store;
        for (ConfiguredPeer peer : store.load()) {
            ConfiguredPeer normalized = peer.normalized();
            peersByAddress.put(normalized.connectionAddress(), normalized);
        }
    }

    @Nonnull
    public synchronized List<ConfiguredPeer> list() {
        return peersByAddress.values().stream()
            .sorted(Comparator.comparing(ConfiguredPeer::connectionAddress))
            .toList();
    }

    @Nonnull
    public synchronized ConfiguredPeer add(@Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
        peersByAddress.put(peer.connectionAddress(), peer);
        persist();
        return peer;
    }

    public synchronized boolean remove(@Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
        ConfiguredPeer removed = peersByAddress.remove(peer.connectionAddress());
        persist();
        return removed != null;
    }

    public synchronized void clear() throws IOException {
        peersByAddress.clear();
        persist();
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(peersByAddress.values()));
    }
}
