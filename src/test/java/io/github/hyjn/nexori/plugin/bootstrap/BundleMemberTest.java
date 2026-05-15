package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BundleMemberTest {

    @Test
    void constructorPreservesAllFields() {
        BundleMember member = new BundleMember(
            "server-uuid-123", "host.example.com:5520", "fp-abc", "pubkey-base64-xyz", 9_000_000L);

        assertEquals("server-uuid-123", member.serverId());
        assertEquals("host.example.com:5520", member.connectionAddress());
        assertEquals("fp-abc", member.fingerprint());
        assertEquals("pubkey-base64-xyz", member.publicKeyBase64());
        assertEquals(9_000_000L, member.verifiedAtEpochMillis());
    }

    @Test
    void blankFieldsArePreservedAccordingToCurrentBehavior() {
        BundleMember member = new BundleMember("", "", "", "", 0L);

        assertTrue(member.serverId().isBlank(), "Blank serverId should be preserved as-is");
        assertTrue(member.connectionAddress().isBlank());
        assertTrue(member.fingerprint().isBlank());
        assertTrue(member.publicKeyBase64().isBlank());
        assertEquals(0L, member.verifiedAtEpochMillis());
    }
}
