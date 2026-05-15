package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapPayloadCodecTest {

    private static final byte[] MAGIC = new byte[]{'N', 'X', 'B', '1'};

    private static byte[] buildRawBytes(String jsonContent) throws IOException {
        byte[] json = jsonContent.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
            gzip.write(json);
        }
        byte[] compressed = buf.toByteArray();
        byte[] result = new byte[MAGIC.length + compressed.length];
        System.arraycopy(MAGIC, 0, result, 0, MAGIC.length);
        System.arraycopy(compressed, 0, result, MAGIC.length, compressed.length);
        return result;
    }

    private static BootstrapReferralPayload makeRequest() {
        BootstrapChallenge challenge = BootstrapChallenge.create(
            UUID.randomUUID(), "target", "session-1", Instant.now().plusSeconds(60));
        return BootstrapReferralPayload.request("player-1", challenge, 0, 3);
    }

    @Test
    void encodeThenDecodeRoundTripsPayload() throws IOException {
        BootstrapPayloadCodec codec = new BootstrapPayloadCodec();
        BootstrapReferralPayload original = makeRequest();

        byte[] encoded = codec.encode(original);
        Optional<BootstrapReferralPayload> decoded = codec.tryDecode(encoded);

        assertTrue(decoded.isPresent());
        assertEquals(original.type(), decoded.get().type());
        assertEquals(original.startedByPlayerUuid(), decoded.get().startedByPlayerUuid());
        assertEquals(original.currentPeerIndex(), decoded.get().currentPeerIndex());
        assertEquals(original.totalPeers(), decoded.get().totalPeers());
    }

    @Test
    void encodedPayloadStartsWithMagicBytesNXB1() throws IOException {
        BootstrapPayloadCodec codec = new BootstrapPayloadCodec();
        byte[] encoded = codec.encode(makeRequest());

        assertArrayEquals(MAGIC, new byte[]{encoded[0], encoded[1], encoded[2], encoded[3]});
    }

    @Test
    void tryDecodeReturnsEmptyForNullPayload() throws IOException {
        Optional<BootstrapReferralPayload> result = new BootstrapPayloadCodec().tryDecode(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void tryDecodeReturnsEmptyForPayloadShorterThanMagic() throws IOException {
        Optional<BootstrapReferralPayload> result = new BootstrapPayloadCodec().tryDecode(new byte[]{78, 88});
        assertTrue(result.isEmpty());
    }

    @Test
    void tryDecodeReturnsEmptyForPayloadExactlyMagicLength() throws IOException {
        Optional<BootstrapReferralPayload> result = new BootstrapPayloadCodec().tryDecode(new byte[]{78, 88, 66, 49});
        assertTrue(result.isEmpty());
    }

    @Test
    void tryDecodeReturnsEmptyForWrongMagic() throws IOException {
        byte[] wrongMagic = new byte[]{'N', 'X', 'B', '2', 0, 1, 2, 3};
        Optional<BootstrapReferralPayload> result = new BootstrapPayloadCodec().tryDecode(wrongMagic);
        assertTrue(result.isEmpty());
    }

    @Test
    void tryDecodeThrowsForCorrectMagicWithInvalidGzipAccordingToCurrentBehavior() {
        byte[] invalidGzip = new byte[]{'N', 'X', 'B', '1', 1, 2, 3, 4, 5, 6, 7, 8};
        assertThrows(IOException.class, () -> new BootstrapPayloadCodec().tryDecode(invalidGzip),
            "Invalid gzip after correct magic should throw IOException");
    }

    @Test
    void encodeRejectsPayloadOver4096Bytes() throws IOException {
        BootstrapPayloadCodec codec = new BootstrapPayloadCodec();
        byte[] randomBytes = new byte[6000];
        new Random(42).nextBytes(randomBytes);
        String bigString = java.util.Base64.getEncoder().encodeToString(randomBytes);
        BootstrapReferralPayload bigPayload = new BootstrapReferralPayload(
            "REQUEST_PROOF", bigString, null, null,
            bigString, bigString, bigString, bigString,
            "", "", BootstrapMigrationPlan.empty(), 0, 1
        );

        assertThrows(IllegalArgumentException.class, () -> codec.encode(bigPayload),
            "Payload > 4096 bytes should throw IllegalArgumentException");
    }

    @Test
    void tryDecodeReturnsEmptyForJsonNullAccordingToCurrentBehavior() throws IOException {
        byte[] bytes = buildRawBytes("null");
        Optional<BootstrapReferralPayload> result = new BootstrapPayloadCodec().tryDecode(bytes);
        assertTrue(result.isEmpty(), "JSON 'null' decompressed content should produce empty Optional");
    }

    @Test
    void tryDecodeMalformedJsonBehaviorMatchesCurrentBehavior() throws IOException {
        byte[] bytes = buildRawBytes("{\"not-closed");
        assertThrows(RuntimeException.class, () -> new BootstrapPayloadCodec().tryDecode(bytes),
            "Malformed JSON after decompression should throw RuntimeException (JsonSyntaxException)");
    }

    @Test
    void roundTripPreservesRequestPayload() throws IOException {
        BootstrapPayloadCodec codec = new BootstrapPayloadCodec();
        BootstrapChallenge challenge = new BootstrapChallenge("sess", "origin", "target", "nonce-123", 9_999_999L);
        BootstrapReferralPayload payload = BootstrapReferralPayload.request("player-uuid", challenge, 2, 5);

        BootstrapReferralPayload decoded = codec.tryDecode(codec.encode(payload)).orElseThrow();

        assertEquals(BootstrapMessageType.REQUEST_PROOF.name(), decoded.type());
        assertEquals(2, decoded.currentPeerIndex());
        assertEquals(5, decoded.totalPeers());
        assertEquals("nonce-123", decoded.challenge().nonce());
    }

    @Test
    void roundTripPreservesErrorPayload() throws IOException {
        BootstrapPayloadCodec codec = new BootstrapPayloadCodec();
        BootstrapChallenge challenge = new BootstrapChallenge("sess", "origin", "target", "nonce", 1000L);
        BootstrapReferralPayload request = BootstrapReferralPayload.request("player", challenge, 0, 1);
        BootstrapReferralPayload error = BootstrapReferralPayload.error(request, "Something failed");

        BootstrapReferralPayload decoded = codec.tryDecode(codec.encode(error)).orElseThrow();

        assertEquals(BootstrapMessageType.ERROR.name(), decoded.type());
        assertEquals("Something failed", decoded.errorMessage());
    }
}
