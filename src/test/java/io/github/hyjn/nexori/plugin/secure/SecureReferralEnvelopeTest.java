package io.github.hyjn.nexori.plugin.secure;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SecureReferralEnvelopeTest {

    private static final UUID ISSUER_SERVER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void canonicalPayloadExcludesSignature() {
        SecureReferralEnvelope first = envelope("sig-one");
        SecureReferralEnvelope second = envelope("sig-two");

        assertEquals(first.canonicalPayload(), second.canonicalPayload());
        assertFalse(first.canonicalPayload().contains("signatureBase64"));
    }

    @Test
    void signedReturnsCopyWithSignatureOnlyChanged() {
        SecureReferralEnvelope unsigned = envelope("");

        SecureReferralEnvelope signed = unsigned.signed("abc");

        assertEquals(unsigned.protocol(), signed.protocol());
        assertEquals(unsigned.payloadType(), signed.payloadType());
        assertEquals(unsigned.issuerServerId(), signed.issuerServerId());
        assertEquals(unsigned.playerUuid(), signed.playerUuid());
        assertEquals(unsigned.playerUsername(), signed.playerUsername());
        assertEquals(unsigned.issuedAtEpochMillis(), signed.issuedAtEpochMillis());
        assertEquals(unsigned.expiresAtEpochMillis(), signed.expiresAtEpochMillis());
        assertEquals(unsigned.nonce(), signed.nonce());
        assertEquals(unsigned.payloadJson(), signed.payloadJson());
        assertEquals("abc", signed.signatureBase64());
    }

    @Test
    void isExpiredReturnsTrueOnlyWhenExpiresBeforeNow() {
        SecureReferralEnvelope envelope = envelope("sig-one");

        assertTrue(envelope.isExpired(Instant.ofEpochMilli(2_001L)));
        assertFalse(envelope.isExpired(Instant.ofEpochMilli(2_000L)));
        assertFalse(envelope.isExpired(Instant.ofEpochMilli(1_999L)));
    }

    @Test
    void unsignedUsesExpectedProtocolAndEmptySignature() {
        SecureReferralEnvelope unsigned = SecureReferralEnvelope.unsigned(
            "MINIGAME_LAUNCH",
            ISSUER_SERVER_ID,
            PLAYER_UUID,
            "Janiel778",
            1_000L,
            2_000L,
            "{}"
        );

        assertEquals("nexori.secure-referral.v1", unsigned.protocol());
        assertEquals("", unsigned.signatureBase64());
        assertFalse(unsigned.nonce().isBlank());
    }

    private static SecureReferralEnvelope envelope(String signatureBase64) {
        return new SecureReferralEnvelope(
            "nexori.secure-referral.v1",
            "MINIGAME_LAUNCH",
            ISSUER_SERVER_ID.toString(),
            PLAYER_UUID.toString(),
            "Janiel778",
            1_000L,
            2_000L,
            "nonce-1",
            "{\"matchId\":\"match-1\"}",
            signatureBase64
        );
    }
}
