package io.github.hyjn.nexori.plugin.peers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ConfiguredPeerTest {

    // ── parse ─────────────────────────────────────────────────────────────────

    @Test
    void parseHostOnlyDefaultsToPort5520() {
        ConfiguredPeer peer = ConfiguredPeer.parse("host.example.com");
        assertEquals("host.example.com", peer.host());
        assertEquals(ConfiguredPeer.DEFAULT_PORT, peer.port());
    }

    @Test
    void parseHostAndPort() {
        ConfiguredPeer peer = ConfiguredPeer.parse("host.example.com:7001");
        assertEquals("host.example.com", peer.host());
        assertEquals(7001, peer.port());
    }

    @Test
    void parseTrimsAndLowercasesHost() {
        ConfiguredPeer peer = ConfiguredPeer.parse("  HOST.EXAMPLE.COM:5520  ");
        assertEquals("host.example.com", peer.host());
    }

    @Test
    void parseRejectsBlankAddress() {
        assertThrows(IllegalArgumentException.class, () -> ConfiguredPeer.parse("   "));
    }

    @Test
    void parseRejectsTrailingColon() {
        assertThrows(IllegalArgumentException.class, () -> ConfiguredPeer.parse("host.example.com:"));
    }

    @Test
    void parseLeadingColonTreatedAsHostOnlyAccordingToCurrentBehavior() {
        // separator <= 0 means ":5520" goes to the host-only branch; host becomes ":5520", not blank.
        ConfiguredPeer peer = ConfiguredPeer.parse(":5520");
        assertEquals(":5520", peer.host());
        assertEquals(ConfiguredPeer.DEFAULT_PORT, peer.port());
    }

    @Test
    void parseRejectsNonNumericPort() {
        assertThrows(IllegalArgumentException.class, () -> ConfiguredPeer.parse("host.example.com:abc"));
    }

    @Test
    void parseRejectsPortBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> ConfiguredPeer.parse("host.example.com:0"));
    }

    @Test
    void parseRejectsPortAbove65535() {
        assertThrows(IllegalArgumentException.class, () -> ConfiguredPeer.parse("host.example.com:65536"));
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void createRequiresDisplayName() {
        assertThrows(IllegalArgumentException.class,
            () -> ConfiguredPeer.create("   ", "host.example.com:5520"));
    }

    @Test
    void createNormalizesDisplayNameHostAndPort() {
        ConfiguredPeer peer = ConfiguredPeer.create("  My Server  ", "  HOST.EXAMPLE.COM:7001  ");
        assertEquals("My Server", peer.displayName());
        assertEquals("host.example.com", peer.host());
        assertEquals(7001, peer.port());
    }

    // ── normalized ────────────────────────────────────────────────────────────

    @Test
    void normalizedDefaultsBlankDisplayNameToHostPort() {
        ConfiguredPeer peer = new ConfiguredPeer("", "host.example.com", 5520).normalized();
        assertEquals("host.example.com:5520", peer.displayName());
    }

    @Test
    void normalizedDefaultsInvalidPortToDefaultPort() {
        ConfiguredPeer peer = new ConfiguredPeer("My Server", "host.example.com", 0).normalized();
        assertEquals(ConfiguredPeer.DEFAULT_PORT, peer.port());
    }

    @Test
    void normalizedLowercasesHost() {
        ConfiguredPeer peer = new ConfiguredPeer("Name", "HOST.EXAMPLE.COM", 5520).normalized();
        assertEquals("host.example.com", peer.host());
    }

    // ── connectionAddress ─────────────────────────────────────────────────────

    @Test
    void connectionAddressUsesNormalizedHostAndPort() {
        ConfiguredPeer peer = ConfiguredPeer.parse("HOST.EXAMPLE.COM:7001");
        assertEquals("host.example.com:7001", peer.connectionAddress());
    }

    @Test
    void connectionAddressDefaultsToPort5520WhenNoPort() {
        ConfiguredPeer peer = ConfiguredPeer.parse("host.example.com");
        assertTrue(peer.connectionAddress().endsWith(":" + ConfiguredPeer.DEFAULT_PORT));
    }

    // ── edge cases ────────────────────────────────────────────────────────────

    @Test
    void parseIpv6OrMultipleColonBehaviorMatchesCurrentBehavior() {
        // "::1" — lastIndexOf(':') = 1, host = ":".substring(0,1) = ":" (not blank), port = 1.
        // The parser does NOT reject this; host becomes ":" and port becomes 1.
        ConfiguredPeer peer = ConfiguredPeer.parse("::1");
        assertEquals(":", peer.host());
        assertEquals(1, peer.port());
    }
}
