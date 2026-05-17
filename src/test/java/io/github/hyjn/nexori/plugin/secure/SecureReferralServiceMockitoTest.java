package io.github.hyjn.nexori.plugin.secure;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.testsupport.BackendTestFixtures;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-based tests for {@link SecureReferralService#createPayload}.
 *
 * <p>Uses a mock {@link ServerIdentityManager} (not final — can be mocked with subclass maker)
 * and a real {@link SecureReferralPayloadCodec} to verify encode/decode round-trips.
 * {@link DiagnosticsService} is constructed as a real instance using @TempDir because it is
 * a final class and cannot be mocked with mock-maker-subclass.</p>
 *
 * <p>SKIP: registerHandlerThenValidReferralInvokesHandlerIfEventMockIsClean() —
 * BLOCKED because it requires {@code PlayerSetupConnectEvent}.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
final class SecureReferralServiceMockitoTest {

    private static final UUID PLAYER_UUID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final String PLAYER_NAME = "TestPlayer";
    private static final String PAYLOAD_TYPE = "nexori.test.referral";
    private static final String DETERMINISTIC_SIGNATURE = "dGVzdC1zaWduYXR1cmU="; // base64 "test-signature"

    // HytaleLogger cannot be mocked (private constructor / Flogger internals)
    private HytaleLogger logger;

    // ServerIdentityManager is NOT final — can be mocked
    @Mock ServerIdentityManager identityManager;

    @TempDir Path tempDir;

    private ServerIdentity serverIdentity;
    private TrustBundleStore trustBundleStore;
    private DiagnosticsService diagnosticsService;
    private SecureReferralService service;
    private SecureReferralPayloadCodec codec;

    @BeforeEach
    void setUp() throws Exception {
        logger = HytaleLogger.getLogger();
        serverIdentity = BackendTestFixtures.testServerIdentity();
        trustBundleStore = new TrustBundleStore(tempDir.resolve("trust-bundle.json"));

        // DiagnosticsService is final — build a real instance using @TempDir
        diagnosticsService = new DiagnosticsService(
            logger,
            tempDir,
            serverIdentity,
            () -> "",
            "test-1.0"
        );

        // signCanonicalPayload(ServerIdentity identity, String canonicalPayload) → String
        when(identityManager.signCanonicalPayload(any(ServerIdentity.class), anyString()))
            .thenReturn(DETERMINISTIC_SIGNATURE);

        service = new SecureReferralService(
            logger,
            identityManager,
            serverIdentity,
            trustBundleStore,
            diagnosticsService
        );

        codec = new SecureReferralPayloadCodec();
    }

    // ── Test 1: createPayload signs envelope and encodes referral ─────────────

    @Test
    void createPayloadSignsEnvelopeAndEncodesReferral() throws IOException, GeneralSecurityException {
        record TestPayload(String data) {}
        TestPayload payload = new TestPayload("hello");

        byte[] encoded = service.createPayload(PLAYER_UUID, PLAYER_NAME, PAYLOAD_TYPE, payload, Duration.ofSeconds(30));

        assertNotNull(encoded);
        assertTrue(encoded.length > 0);

        // Decode and verify envelope fields
        SecureReferralEnvelope envelope = codec.tryDecode(encoded)
            .orElseThrow(() -> new AssertionError("Expected envelope but got empty"));

        assertNotNull(envelope);
        assertEquals(PAYLOAD_TYPE, envelope.payloadType());
        assertEquals(serverIdentity.serverId().toString(), envelope.issuerServerId());
        assertEquals(PLAYER_UUID.toString(), envelope.playerUuid());
        assertEquals(PLAYER_NAME, envelope.playerUsername());
        assertEquals(DETERMINISTIC_SIGNATURE, envelope.signatureBase64());

        // signCanonicalPayload should have been called exactly once
        verify(identityManager, times(1)).signCanonicalPayload(any(ServerIdentity.class), anyString());
    }

    // ── Test 2: createPayload uses TTL for issuedAt and expiresAt ─────────────

    @Test
    void createPayloadUsesTtlForIssuedAndExpiresAt() throws IOException, GeneralSecurityException {
        record EmptyPayload() {}
        Duration ttl = Duration.ofSeconds(30);

        long beforeCallMs = System.currentTimeMillis();
        byte[] encoded = service.createPayload(PLAYER_UUID, PLAYER_NAME, PAYLOAD_TYPE, new EmptyPayload(), ttl);
        long afterCallMs = System.currentTimeMillis();

        SecureReferralEnvelope envelope = codec.tryDecode(encoded)
            .orElseThrow(() -> new AssertionError("Expected envelope but got empty"));

        long issuedAt = envelope.issuedAtEpochMillis();
        long expiresAt = envelope.expiresAtEpochMillis();

        // issuedAt should be within the call window
        assertTrue(issuedAt >= beforeCallMs, "issuedAt should be >= before call");
        assertTrue(issuedAt <= afterCallMs, "issuedAt should be <= after call");

        // expiresAt should be issuedAt + ttl
        assertTrue(expiresAt > issuedAt, "expiresAt must be after issuedAt");

        long diff = expiresAt - issuedAt;
        long expectedDiffMs = ttl.toMillis();
        long tolerance = 5_000L;
        assertTrue(Math.abs(diff - expectedDiffMs) <= tolerance,
            "Difference between expiresAt and issuedAt should be ≈" + expectedDiffMs + "ms, was " + diff + "ms");
    }
}
