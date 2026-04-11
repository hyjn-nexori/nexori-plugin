package io.github.hyjn.nexori.plugin.bootstrap;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record BootstrapRun(
    String sessionId,
    String startedByPlayerUuid,
    String originServerId,
    long expiresAtEpochMillis,
    BootstrapPhase phase,
    int currentPeerIndex,
    List<ConfiguredPeer> peers,
    List<BundleMember> verifiedPeers,
    BootstrapMigrationPlan migrationPlan,
    @Nullable BootstrapChallenge currentChallenge
) {

    public BootstrapRun {
        peers = peers == null ? List.of() : List.copyOf(peers);
        verifiedPeers = verifiedPeers == null ? List.of() : List.copyOf(verifiedPeers);
        migrationPlan = migrationPlan == null ? BootstrapMigrationPlan.empty() : migrationPlan;
    }

    public boolean isExpired() {
        return expiresAtEpochMillis <= System.currentTimeMillis();
    }

    public boolean hasRemainingPeers() {
        return currentPeerIndex >= 0 && currentPeerIndex < peers.size();
    }

    @Nullable
    public ConfiguredPeer currentPeer() {
        return hasRemainingPeers() ? peers.get(currentPeerIndex) : null;
    }

    public BootstrapRun withCurrentChallenge(int nextPeerIndex, @Nullable BootstrapChallenge nextChallenge) {
        return new BootstrapRun(
            sessionId,
            startedByPlayerUuid,
            originServerId,
            expiresAtEpochMillis,
            phase,
            nextPeerIndex,
            List.copyOf(peers),
            List.copyOf(verifiedPeers),
            migrationPlan == null ? BootstrapMigrationPlan.empty() : migrationPlan,
            nextChallenge
        );
    }

    public BootstrapRun withVerifiedPeer(BundleMember nextMember) {
        Map<String, BundleMember> byServerId = new LinkedHashMap<>();
        for (BundleMember verifiedPeer : verifiedPeers) {
            byServerId.put(verifiedPeer.serverId(), verifiedPeer);
        }
        byServerId.put(nextMember.serverId(), nextMember);

        return new BootstrapRun(
            sessionId,
            startedByPlayerUuid,
            originServerId,
            expiresAtEpochMillis,
            phase,
            currentPeerIndex,
            List.copyOf(peers),
            new ArrayList<>(byServerId.values()),
            migrationPlan == null ? BootstrapMigrationPlan.empty() : migrationPlan,
            currentChallenge
        );
    }

    public BootstrapRun withPhase(
        BootstrapPhase nextPhase,
        int nextPeerIndex,
        List<ConfiguredPeer> nextPeers,
        @Nullable BootstrapChallenge nextChallenge
    ) {
        return new BootstrapRun(
            sessionId,
            startedByPlayerUuid,
            originServerId,
            expiresAtEpochMillis,
            nextPhase,
            nextPeerIndex,
            List.copyOf(nextPeers),
            List.copyOf(verifiedPeers),
            migrationPlan == null ? BootstrapMigrationPlan.empty() : migrationPlan,
            nextChallenge
        );
    }

    public BootstrapRun withMigrationPlan(@Nullable BootstrapMigrationPlan nextMigrationPlan) {
        return new BootstrapRun(
            sessionId,
            startedByPlayerUuid,
            originServerId,
            expiresAtEpochMillis,
            phase,
            currentPeerIndex,
            List.copyOf(peers),
            List.copyOf(verifiedPeers),
            nextMigrationPlan == null ? BootstrapMigrationPlan.empty() : nextMigrationPlan,
            currentChallenge
        );
    }
}
