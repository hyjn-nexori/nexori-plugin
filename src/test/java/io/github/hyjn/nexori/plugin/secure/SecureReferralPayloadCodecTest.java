package io.github.hyjn.nexori.plugin.secure;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SecureReferralPayloadCodecTest {

    private static final UUID ISSUER_SERVER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_UUID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final SecureReferralPayloadCodec codec = new SecureReferralPayloadCodec();

    @Test
    void encodeThenDecodeRoundTripsEnvelope() throws IOException {
        SecureReferralEnvelope envelope = envelope("MINIGAME_LAUNCH", "{\"matchId\":\"match-1\"}", "sig-1");

        Optional<SecureReferralEnvelope> decoded = codec.tryDecode(codec.encode(envelope));

        assertTrue(decoded.isPresent());
        assertEquals(envelope.protocol(), decoded.get().protocol());
        assertEquals(envelope.payloadType(), decoded.get().payloadType());
        assertEquals(envelope.issuerServerId(), decoded.get().issuerServerId());
        assertEquals(envelope.playerUuid(), decoded.get().playerUuid());
        assertEquals(envelope.playerUsername(), decoded.get().playerUsername());
        assertEquals(envelope.issuedAtEpochMillis(), decoded.get().issuedAtEpochMillis());
        assertEquals(envelope.expiresAtEpochMillis(), decoded.get().expiresAtEpochMillis());
        assertEquals(envelope.nonce(), decoded.get().nonce());
        assertEquals(envelope.payloadJson(), decoded.get().payloadJson());
        assertEquals(envelope.signatureBase64(), decoded.get().signatureBase64());
    }

    @Test
    void encodedPayloadStartsWithMagicBytes() throws IOException {
        byte[] encoded = codec.encode(envelope("MINIGAME_LAUNCH", "{}", "sig-1"));

        assertArrayEquals(new byte[] {'N', 'X', 'S', '1'}, new byte[] {
            encoded[0],
            encoded[1],
            encoded[2],
            encoded[3]
        });
    }

    @Test
    void tryDecodeReturnsEmptyForNullPayload() throws IOException {
        assertEquals(Optional.empty(), codec.tryDecode(null));
    }

    @Test
    void tryDecodeReturnsEmptyForPayloadShorterThanMagic() throws IOException {
        assertEquals(Optional.empty(), codec.tryDecode(new byte[] {'N', 'X', 'S'}));
    }

    @Test
    void tryDecodeReturnsEmptyForWrongMagic() throws IOException {
        assertEquals(Optional.empty(), codec.tryDecode(new byte[] {'B', 'A', 'D', '!', 1, 2, 3}));
    }

    @Test
    void tryDecodeThrowsIOExceptionForMagicWithInvalidGzip() {
        byte[] invalidGzip = new byte[] {'N', 'X', 'S', '1', 1, 2, 3, 4};

        assertThrows(IOException.class, () -> codec.tryDecode(invalidGzip));
    }

    @Test
    void tryPeekPayloadTypeReturnsPayloadType() throws IOException {
        byte[] encoded = codec.encode(envelope("MINIGAME_LAUNCH", "{}", "sig-1"));

        assertEquals(Optional.of("MINIGAME_LAUNCH"), codec.tryPeekPayloadType(encoded));
    }

    @Test
    void tryPeekPayloadTypeReturnsEmptyForWrongMagic() {
        assertEquals(Optional.empty(), codec.tryPeekPayloadType(new byte[] {'B', 'A', 'D', '!', 1, 2, 3}));
    }

    @Test
    void tryPeekPayloadTypeReturnsEmptyForInvalidGzip() {
        byte[] invalidGzip = new byte[] {'N', 'X', 'S', '1', 1, 2, 3, 4};

        assertEquals(Optional.empty(), codec.tryPeekPayloadType(invalidGzip));
    }

    @Test
    void encodeRejectsPayloadOverReferralLimit() {
        SecureReferralEnvelope envelope = envelope("MINIGAME_LAUNCH", largeDifficultToCompressJson(), "sig-1");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> codec.encode(envelope));

        assertTrue(exception.getMessage().contains("exceeds the 4096 byte Hytale referral limit."));
    }

    private static SecureReferralEnvelope envelope(String payloadType, String payloadJson, String signatureBase64) {
        return new SecureReferralEnvelope(
            "nexori.secure-referral.v1",
            payloadType,
            ISSUER_SERVER_ID.toString(),
            PLAYER_UUID.toString(),
            "Janiel778",
            1_000L,
            2_000L,
            "nonce-1",
            payloadJson,
            signatureBase64
        );
    }

    private static String largeDifficultToCompressJson() {
        StringBuilder builder = new StringBuilder("{\"values\":[");
        for (int i = 0; i < 1_200; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"')
                .append(UUID.nameUUIDFromBytes(("payload-" + i).getBytes(StandardCharsets.UTF_8)))
                .append('"');
        }
        builder.append("]}");
        return builder.toString();
    }
}
