package io.github.hyjn.nexori.plugin.policy;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nonnull;

public record ServerPolicySummary(
    String connectionAddress,
    String remoteServerId,
    long confirmedAtEpochMillis,
    boolean recoveryEnabled,
    int maxBackupsPerPlayer
) {

    @Nonnull
    public ServerPolicySummary normalized() {
        ConfiguredPeer peer = ConfiguredPeer.parse(connectionAddress);
        return new ServerPolicySummary(
            peer.connectionAddress(),
            remoteServerId == null ? "" : remoteServerId.trim(),
            confirmedAtEpochMillis,
            recoveryEnabled,
            Math.max(1, maxBackupsPerPlayer)
        );
    }
}
