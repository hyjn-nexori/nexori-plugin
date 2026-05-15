package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapChallengeTest {

    @Test
    void createUsesProvidedSessionOriginTargetAndExpiry() {
        UUID originId = UUID.randomUUID();
        Instant expiresAt = Instant.ofEpochMilli(9_000_000L);

        BootstrapChallenge challenge = BootstrapChallenge.create(originId, "target-server", "session-abc", expiresAt);

        assertEquals("session-abc", challenge.sessionId());
        assertEquals(originId.toString(), challenge.originServerId());
        assertEquals("target-server", challenge.targetDescriptor());
        assertEquals(9_000_000L, challenge.expiresAtEpochMillis());
    }

    @Test
    void createGeneratesNonBlankNonce() {
        BootstrapChallenge challenge = BootstrapChallenge.create(
            UUID.randomUUID(), "target", "session", Instant.now().plusSeconds(60));

        assertNotNull(challenge.nonce());
        assertFalse(challenge.nonce().isBlank());
    }

    @Test
    void createGeneratesDifferentNonceEachTime() {
        UUID origin = UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(60);

        BootstrapChallenge c1 = BootstrapChallenge.create(origin, "target", "session", exp);
        BootstrapChallenge c2 = BootstrapChallenge.create(origin, "target", "session", exp);

        assertFalse(c1.nonce().equals(c2.nonce()), "Each create() call should generate a different nonce");
    }

    @Test
    void canonicalPayloadIncludesAllContractFieldsInCurrentOrder() {
        BootstrapChallenge challenge = new BootstrapChallenge(
            "sess-1", "origin-1", "target-1", "nonce-abc", 5_000L);

        String payload = challenge.canonicalPayload();

        assertTrue(payload.contains("nexori/bootstrap-challenge"));
        assertTrue(payload.contains("sessionId=sess-1"));
        assertTrue(payload.contains("originServerId=origin-1"));
        assertTrue(payload.contains("targetDescriptor=target-1"));
        assertTrue(payload.contains("nonce=nonce-abc"));
        assertTrue(payload.contains("expiresAtEpochMillis=5000"));
    }

    @Test
    void canonicalPayloadChangesWhenNonceChanges() {
        BootstrapChallenge c1 = new BootstrapChallenge("s", "o", "t", "nonce-1", 1000L);
        BootstrapChallenge c2 = new BootstrapChallenge("s", "o", "t", "nonce-2", 1000L);

        assertFalse(c1.canonicalPayload().equals(c2.canonicalPayload()));
    }

    @Test
    void isExpiredFalseWhenNowEqualsExpiresAt() {
        BootstrapChallenge challenge = new BootstrapChallenge("s", "o", "t", "n", 5_000L);

        assertFalse(challenge.isExpired(Instant.ofEpochMilli(5_000L)),
            "isExpired should be false when now == expiresAt (strict >)");
    }

    @Test
    void isExpiredTrueOnlyWhenNowAfterExpiresAt() {
        BootstrapChallenge challenge = new BootstrapChallenge("s", "o", "t", "n", 5_000L);

        assertTrue(challenge.isExpired(Instant.ofEpochMilli(5_001L)));
        assertFalse(challenge.isExpired(Instant.ofEpochMilli(4_999L)));
    }

    @Test
    void preservesNullOrBlankFieldsAccordingToCurrentBehavior() {
        BootstrapChallenge challenge = new BootstrapChallenge(null, "", null, "", 0L);

        String payload = challenge.canonicalPayload();
        assertTrue(payload.contains("sessionId=null") || payload.contains("sessionId="),
            "Null sessionId preserved as-is: " + payload);
    }
}
