package io.github.hyjn.nexori.plugin.secure;

import com.google.gson.Gson;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

final class VerifiedSecureReferralTest {

    private static SecureReferralEnvelope envelope(String payloadJson) {
        return new SecureReferralEnvelope(
            "nexori.secure-referral.v1",
            "test.payload",
            "server-uuid",
            "player-uuid",
            "PlayerName",
            1_000_000L,
            2_000_000L,
            "nonce-abc",
            payloadJson,
            "sig-base64"
        );
    }

    private static BundleMember issuer() {
        return new BundleMember("srv-1", "srv.host:25565", "fingerprint-abc", "pubkey-base64", 1_000_000L);
    }

    private record SimplePayload(String id, String value) {}

    @Test
    void preservesEnvelopeAndIssuer() {
        SecureReferralEnvelope env = envelope("{\"id\":\"abc\",\"value\":\"xyz\"}");
        BundleMember member = issuer();
        VerifiedSecureReferral referral = new VerifiedSecureReferral(env, member);

        assertEquals(env, referral.envelope());
        assertEquals(member, referral.issuer());
    }

    @Test
    void decodePayloadUsesEnvelopePayloadJson() {
        SecureReferralEnvelope env = envelope("{\"id\":\"test-id\",\"value\":\"hello\"}");
        VerifiedSecureReferral referral = new VerifiedSecureReferral(env, issuer());

        SimplePayload decoded = referral.decodePayload(new Gson(), SimplePayload.class);

        assertNotNull(decoded);
        assertEquals("test-id", decoded.id());
        assertEquals("hello", decoded.value());
    }

    @Test
    void decodePayloadReturnsNullForJsonNullAccordingToCurrentBehavior() {
        SecureReferralEnvelope env = envelope("null");
        VerifiedSecureReferral referral = new VerifiedSecureReferral(env, issuer());

        SimplePayload decoded = referral.decodePayload(new Gson(), SimplePayload.class);

        assertNull(decoded,
            "Gson.fromJson(\"null\", Class) returns null; decodePayload does not guard against this");
    }

    @Test
    void decodePayloadThrowsForMalformedPayloadAccordingToCurrentBehavior() {
        SecureReferralEnvelope env = envelope("{ this is not valid json }");
        VerifiedSecureReferral referral = new VerifiedSecureReferral(env, issuer());

        org.junit.jupiter.api.Assertions.assertThrows(
            com.google.gson.JsonSyntaxException.class,
            () -> referral.decodePayload(new Gson(), SimplePayload.class),
            "decodePayload delegates directly to Gson.fromJson; malformed JSON propagates as JsonSyntaxException"
        );
    }

    @Test
    void decodePayloadDoesNotVerifySignatureAccordingToCurrentBehavior() {
        SecureReferralEnvelope env = new SecureReferralEnvelope(
            "nexori.secure-referral.v1", "test.payload",
            "server-uuid", "player-uuid", "PlayerName",
            1_000_000L, 2_000_000L, "nonce-abc",
            "{\"id\":\"abc\",\"value\":\"xyz\"}",
            ""
        );
        VerifiedSecureReferral referral = new VerifiedSecureReferral(env, issuer());

        SimplePayload decoded = referral.decodePayload(new Gson(), SimplePayload.class);

        assertNotNull(decoded,
            "decodePayload does not re-verify the signature; it only reads from envelope.payloadJson()");
    }
}
