package io.github.hyjn.nexori.plugin.policy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerPolicySummaryTest {

    private static ServerPolicySummary summary(
        String connectionAddress, String remoteServerId, long confirmedAt,
        boolean recoveryEnabled, int maxBackupsPerPlayer
    ) {
        return new ServerPolicySummary(connectionAddress, remoteServerId, confirmedAt, recoveryEnabled, maxBackupsPerPlayer);
    }

    @Test
    void normalizedNormalizesConnectionAddressViaConfiguredPeer() {
        ServerPolicySummary s = summary("HOST.EXAMPLE.COM:5520", "srv-1", 1000L, true, 3).normalized();
        assertEquals("host.example.com:5520", s.connectionAddress());
    }

    @Test
    void normalizedTrimsRemoteServerId() {
        ServerPolicySummary s = summary("host.example.com:5520", "  srv-1  ", 1000L, true, 3).normalized();
        assertEquals("srv-1", s.remoteServerId());
    }

    @Test
    void normalizedDefaultsNullRemoteServerIdToEmpty() {
        ServerPolicySummary s = summary("host.example.com:5520", null, 1000L, true, 3).normalized();
        assertEquals("", s.remoteServerId());
    }

    @Test
    void normalizedClampsMaxBackupsPerPlayerToAtLeastOne() {
        ServerPolicySummary s = summary("host.example.com:5520", "srv-1", 1000L, true, 0).normalized();
        assertEquals(1, s.maxBackupsPerPlayer());
    }

    @Test
    void normalizedPreservesRecoveryEnabledTrue() {
        ServerPolicySummary s = summary("host.example.com:5520", "srv-1", 1000L, true, 3).normalized();
        assertTrue(s.recoveryEnabled());
    }

    @Test
    void normalizedPreservesConfirmedAtEpochMillis() {
        long ts = 9_999_999L;
        ServerPolicySummary s = summary("host.example.com:5520", "srv-1", ts, false, 3).normalized();
        assertEquals(ts, s.confirmedAtEpochMillis());
    }

    @Test
    void normalizedAddsDefaultPortWhenMissingAccordingToCurrentBehavior() {
        ServerPolicySummary s = summary("host.example.com", "srv-1", 1000L, false, 3).normalized();
        assertTrue(s.connectionAddress().endsWith(":5520"),
            "ConfiguredPeer.parse adds the default port when none is provided");
    }

    @Test
    void normalizedThrowsForInvalidConnectionAddressAccordingToCurrentBehavior() {
        ServerPolicySummary invalid = summary("   ", "srv-1", 1000L, false, 3);
        assertThrows(IllegalArgumentException.class, invalid::normalized,
            "normalized() delegates to ConfiguredPeer.parse which throws for blank/invalid address");
    }
}
