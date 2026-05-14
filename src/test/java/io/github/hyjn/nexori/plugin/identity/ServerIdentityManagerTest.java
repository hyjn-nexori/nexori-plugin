package io.github.hyjn.nexori.plugin.identity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerIdentityManagerTest {

    @Test
    void loadOrCreateInEmptyDirCreatesNewIdentity(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);

        ServerIdentity identity = manager.loadOrCreate();

        assertNotNull(identity);
        assertNotNull(identity.serverId());
    }

    @Test
    void loadOrCreateCreatesIdentityFiles(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);

        manager.loadOrCreate();

        assertTrue(Files.exists(dir.resolve("identity.properties")));
        assertTrue(Files.exists(dir.resolve("server-private-key.pkcs8")));
        assertTrue(Files.exists(dir.resolve("server-public-key.spki")));
    }

    @Test
    void loadOrCreateIsIdempotentSameServerId(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);
        ServerIdentity first = manager.loadOrCreate();

        ServerIdentityManager reloaded = new ServerIdentityManager(dir);
        ServerIdentity second = reloaded.loadOrCreate();

        assertEquals(first.serverId(), second.serverId());
    }

    @Test
    void loadOrCreateIdempotentHasSameFingerprint(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);
        ServerIdentity first = manager.loadOrCreate();

        ServerIdentityManager reloaded = new ServerIdentityManager(dir);
        ServerIdentity second = reloaded.loadOrCreate();

        assertEquals(first.fingerprint(), second.fingerprint());
    }

    @Test
    void identityHasNonBlankFingerprint(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);

        ServerIdentity identity = manager.loadOrCreate();

        assertFalse(identity.fingerprint().isBlank(), "fingerprint should not be blank");
    }

    @Test
    void signAndVerifyRoundTrip(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);
        ServerIdentity identity = manager.loadOrCreate();

        String payload = "canonical-payload-123";
        String signature = manager.signCanonicalPayload(identity, payload);
        boolean valid = manager.verifyCanonicalPayload(payload, identity.publicKeyBase64(), signature);

        assertTrue(valid);
    }

    @Test
    void verifyWithWrongPayloadReturnsFalse(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);
        ServerIdentity identity = manager.loadOrCreate();

        String signature = manager.signCanonicalPayload(identity, "original-payload");
        boolean valid = manager.verifyCanonicalPayload("tampered-payload", identity.publicKeyBase64(), signature);

        assertFalse(valid);
    }

    @Test
    void incompleteIdentityFilesThrowsIOException(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);
        manager.loadOrCreate();
        Files.delete(dir.resolve("server-private-key.pkcs8"));

        ServerIdentityManager reloaded = new ServerIdentityManager(dir);
        assertThrows(IOException.class, reloaded::loadOrCreate,
            "Incomplete identity files should throw IOException");
    }

    @Test
    void malformedPublicKeyThrowsAccordingToCurrentBehavior(@TempDir Path dir) throws IOException, GeneralSecurityException {
        ServerIdentityManager manager = new ServerIdentityManager(dir);
        manager.loadOrCreate();
        // overwrite public key with base64 of random bytes that are not a valid Ed25519 key spec
        byte[] invalidKeyBytes = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        Files.writeString(dir.resolve("server-public-key.spki"),
            Base64.getEncoder().encodeToString(invalidKeyBytes),
            StandardCharsets.US_ASCII);

        ServerIdentityManager reloaded = new ServerIdentityManager(dir);
        assertThrows(Exception.class, reloaded::loadOrCreate,
            "Malformed public key bytes should throw GeneralSecurityException or IOException");
    }
}
