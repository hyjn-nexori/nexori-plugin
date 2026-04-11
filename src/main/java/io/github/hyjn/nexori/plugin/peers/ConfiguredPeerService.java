package io.github.hyjn.nexori.plugin.peers;

import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ConfiguredPeerService {

    private final ConfiguredPeerStore store;
    private final ConfiguredPeerMigrationService migrationService;
    private final DiagnosticsService diagnosticsService;
    private final Map<String, ConfiguredPeer> peersByAddress = new LinkedHashMap<>();

    public ConfiguredPeerService(
        @Nonnull ConfiguredPeerStore store,
        @Nonnull ConfiguredPeerMigrationService migrationService,
        @Nonnull DiagnosticsService diagnosticsService
    ) throws IOException {
        this.store = store;
        this.migrationService = migrationService;
        this.diagnosticsService = diagnosticsService;
        for (ConfiguredPeer peer : store.load()) {
            ConfiguredPeer normalized = peer.normalized();
            peersByAddress.put(normalized.connectionAddress(), normalized);
        }
    }

    @Nonnull
    public synchronized List<ConfiguredPeer> list() {
        return peersByAddress.values().stream()
            .sorted(Comparator.comparing(ConfiguredPeer::displayName).thenComparing(ConfiguredPeer::connectionAddress))
            .toList();
    }

    @Nonnull
    public synchronized ConfiguredPeer add(@Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
        return add(peer.displayName(), peer.connectionAddress());
    }

    @Nonnull
    public synchronized ConfiguredPeer add(@Nonnull String rawDisplayName, @Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.create(rawDisplayName, rawConnectionAddress);
        peersByAddress.put(peer.connectionAddress(), peer);
        persist();
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            DiagnosticsAction.CONFIG_BOOTSTRAP_PEER_ADD,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BOOTSTRAP_PEER_ADDED,
            "Added a bootstrap peer to this server's local setup input.",
            operationId,
            event -> event
                .entityType("BOOTSTRAP_PEER")
                .entityId(peer.connectionAddress())
                .changeType("ADDED")
                .remoteConnectionAddress(peer.connectionAddress())
                .addPreview("displayName", peer.displayName())
                .addPreview("connectionAddress", peer.connectionAddress())
        );
        return peer;
    }

    @Nonnull
    public synchronized ConfiguredPeer update(@Nonnull String rawExistingConnectionAddress, @Nonnull String rawDisplayName, @Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer existingKey = ConfiguredPeer.parse(rawExistingConnectionAddress);
        ConfiguredPeer existing = peersByAddress.get(existingKey.connectionAddress());
        if (existing == null) {
            throw new IllegalArgumentException("No configured server exists for " + existingKey.connectionAddress() + ".");
        }

        ConfiguredPeer updated = ConfiguredPeer.create(rawDisplayName, rawConnectionAddress);
        if (!existing.connectionAddress().equalsIgnoreCase(updated.connectionAddress())
            && peersByAddress.containsKey(updated.connectionAddress())) {
            throw new IllegalArgumentException("Another configured server already uses " + updated.connectionAddress() + ".");
        }

        peersByAddress.remove(existing.connectionAddress());
        peersByAddress.put(updated.connectionAddress(), updated);
        migrationService.recordUpdate(existing, updated);
        persist();
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            DiagnosticsAction.CONFIG_BOOTSTRAP_PEER_UPDATE,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BOOTSTRAP_PEER_UPDATED,
            "Updated a bootstrap peer in this server's local setup input.",
            operationId,
            event -> event
                .entityType("BOOTSTRAP_PEER")
                .entityId(updated.connectionAddress())
                .changeType("UPDATED")
                .remoteConnectionAddress(updated.connectionAddress())
                .addPreview("displayName", updated.displayName())
                .addPreview("connectionAddress", updated.connectionAddress())
        );
        return updated;
    }

    public synchronized boolean remove(@Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer peer = ConfiguredPeer.parse(rawConnectionAddress);
        ConfiguredPeer removed = peersByAddress.remove(peer.connectionAddress());
        persist();
        if (removed != null) {
            String operationId = diagnosticsService.newOperationId("config");
            diagnosticsService.record(
                DiagnosticsCategory.CONFIG,
                DiagnosticsAction.CONFIG_BOOTSTRAP_PEER_REMOVE,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.BOOTSTRAP_PEER_REMOVED,
                "Removed a bootstrap peer from this server's local setup input.",
                operationId,
                event -> event
                    .entityType("BOOTSTRAP_PEER")
                    .entityId(removed.connectionAddress())
                    .changeType("REMOVED")
                    .remoteConnectionAddress(removed.connectionAddress())
                    .addPreview("connectionAddress", removed.connectionAddress())
            );
        }
        return removed != null;
    }

    public synchronized void clear() throws IOException {
        peersByAddress.clear();
        persist();
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            DiagnosticsAction.CONFIG_BOOTSTRAP_PEER_CLEAR,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BOOTSTRAP_PEERS_CLEARED,
            "Cleared this server's local bootstrap peer list.",
            operationId,
            event -> event
                .entityType("BOOTSTRAP_PEER")
                .entityId("all")
                .changeType("CLEARED")
        );
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(peersByAddress.values()));
    }
}
