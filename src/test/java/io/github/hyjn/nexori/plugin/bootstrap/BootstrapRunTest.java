package io.github.hyjn.nexori.plugin.bootstrap;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapRunTest {

    private static BootstrapRun makeRun(List<ConfiguredPeer> peers, int currentPeerIndex) {
        return new BootstrapRun(
            "session-1", "player-1", "origin-server",
            System.currentTimeMillis() + 60_000L,
            BootstrapPhase.COLLECT_PROOFS,
            currentPeerIndex, peers, List.of(),
            BootstrapMigrationPlan.empty(), null
        );
    }

    private static ConfiguredPeer peer(String host, int port) {
        return new ConfiguredPeer(host + ":" + port, host, port);
    }

    private static BundleMember member(String serverId) {
        return new BundleMember(serverId, "addr:5520", "fp-" + serverId, "pk-" + serverId, 1_000L);
    }

    // ── compact constructor defaults ──────────────────────────────────────────

    @Test
    void constructorDefaultsNullPeersToEmptyList() {
        BootstrapRun run = new BootstrapRun(
            "s", "p", "o", 9_999_999_999L, BootstrapPhase.COLLECT_PROOFS,
            0, null, List.of(), BootstrapMigrationPlan.empty(), null
        );
        assertNotNull(run.peers());
        assertTrue(run.peers().isEmpty());
    }

    @Test
    void constructorDefaultsNullVerifiedPeersToEmptyList() {
        BootstrapRun run = new BootstrapRun(
            "s", "p", "o", 9_999_999_999L, BootstrapPhase.COLLECT_PROOFS,
            0, List.of(), null, BootstrapMigrationPlan.empty(), null
        );
        assertNotNull(run.verifiedPeers());
        assertTrue(run.verifiedPeers().isEmpty());
    }

    @Test
    void constructorDefaultsNullMigrationPlanToEmpty() {
        BootstrapRun run = new BootstrapRun(
            "s", "p", "o", 9_999_999_999L, BootstrapPhase.COLLECT_PROOFS,
            0, List.of(), List.of(), null, null
        );
        assertNotNull(run.migrationPlan());
        assertTrue(run.migrationPlan().isEmpty());
    }

    // ── isExpired ─────────────────────────────────────────────────────────────

    @Test
    void isExpiredTrueForPastTimestamp() {
        BootstrapRun run = makeRun(List.of(), 0);
        BootstrapRun expired = new BootstrapRun(
            run.sessionId(), run.startedByPlayerUuid(), run.originServerId(),
            1L, // far past
            run.phase(), run.currentPeerIndex(), run.peers(), run.verifiedPeers(),
            run.migrationPlan(), null
        );
        assertTrue(expired.isExpired());
    }

    @Test
    void isExpiredFalseForFutureTimestamp() {
        BootstrapRun run = makeRun(List.of(), 0);
        assertFalse(run.isExpired());
    }

    // ── hasRemainingPeers / currentPeer ───────────────────────────────────────

    @Test
    void hasRemainingPeersFalseForNegativeIndex() {
        BootstrapRun run = makeRun(List.of(peer("a.host", 5520)), -1);
        assertFalse(run.hasRemainingPeers());
    }

    @Test
    void hasRemainingPeersFalseWhenIndexAtSize() {
        List<ConfiguredPeer> peers = List.of(peer("a.host", 5520));
        BootstrapRun run = makeRun(peers, 1);
        assertFalse(run.hasRemainingPeers());
    }

    @Test
    void hasRemainingPeersTrueWhenIndexInBounds() {
        List<ConfiguredPeer> peers = List.of(peer("a.host", 5520), peer("b.host", 5520));
        BootstrapRun run = makeRun(peers, 0);
        assertTrue(run.hasRemainingPeers());
    }

    @Test
    void currentPeerReturnsPeerAtCurrentIndex() {
        ConfiguredPeer expected = peer("a.host", 5520);
        BootstrapRun run = makeRun(List.of(expected, peer("b.host", 5520)), 0);
        assertEquals(expected.host(), run.currentPeer().host());
    }

    @Test
    void currentPeerReturnsNullWhenNoRemainingPeers() {
        BootstrapRun run = makeRun(List.of(), 0);
        assertNull(run.currentPeer());
    }

    // ── with* mutators ────────────────────────────────────────────────────────

    @Test
    void withCurrentChallengePreservesOtherFields() {
        BootstrapRun run = makeRun(List.of(peer("host", 5520)), 0);
        BootstrapChallenge challenge = BootstrapChallenge.create(
            UUID.randomUUID(), "t", "s", Instant.now().plusSeconds(60));

        BootstrapRun updated = run.withCurrentChallenge(1, challenge);

        assertEquals(run.sessionId(), updated.sessionId());
        assertEquals(run.startedByPlayerUuid(), updated.startedByPlayerUuid());
        assertEquals(1, updated.currentPeerIndex());
        assertEquals(challenge, updated.currentChallenge());
    }

    @Test
    void withVerifiedPeerDeduplicatesByServerIdAccordingToCurrentBehavior() {
        BootstrapRun run = makeRun(List.of(), 0);
        BundleMember m = member("server-1");

        BootstrapRun afterFirst = run.withVerifiedPeer(m);
        BootstrapRun afterSecond = afterFirst.withVerifiedPeer(m);

        assertEquals(1, afterSecond.verifiedPeers().size(),
            "Same serverId should not be duplicated in verifiedPeers");
    }

    @Test
    void withVerifiedPeerAddsNewMember() {
        BootstrapRun run = makeRun(List.of(), 0);
        BootstrapRun updated = run.withVerifiedPeer(member("server-1")).withVerifiedPeer(member("server-2"));
        assertEquals(2, updated.verifiedPeers().size());
    }

    @Test
    void withPhasePreservesOtherFields() {
        List<ConfiguredPeer> peers = List.of(peer("host", 5520));
        BootstrapRun run = makeRun(peers, 0);

        BootstrapRun updated = run.withPhase(BootstrapPhase.INSTALL_BUNDLE, 1, peers, null);

        assertEquals(BootstrapPhase.INSTALL_BUNDLE, updated.phase());
        assertEquals(run.sessionId(), updated.sessionId());
        assertEquals(run.verifiedPeers(), updated.verifiedPeers());
    }

    @Test
    void withMigrationPlanDefaultsNullToEmpty() {
        BootstrapRun run = makeRun(List.of(), 0);
        BootstrapRun updated = run.withMigrationPlan(null);
        assertNotNull(updated.migrationPlan());
        assertTrue(updated.migrationPlan().isEmpty());
    }
}
