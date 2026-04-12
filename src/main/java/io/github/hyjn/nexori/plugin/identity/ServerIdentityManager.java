package io.github.hyjn.nexori.plugin.identity;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapChallenge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

/**
 * Loads, creates, and uses the local server identity used by Nexori trust bootstrap.
 */
public class ServerIdentityManager {

    private static final String ALGORITHM = "Ed25519";

    private final Path identityDir;
    private final Path metadataPath;
    private final Path privateKeyPath;
    private final Path publicKeyPath;

    /**
     * Creates an identity manager rooted at the given identity directory.
     */
    public ServerIdentityManager(Path identityDir) {
        this.identityDir = identityDir;
        this.metadataPath = identityDir.resolve("identity.properties");
        this.privateKeyPath = identityDir.resolve("server-private-key.pkcs8");
        this.publicKeyPath = identityDir.resolve("server-public-key.spki");
    }

    /**
     * Loads the persisted server identity or creates a new one when the identity directory is empty.
     */
    public ServerIdentity loadOrCreate() throws IOException, GeneralSecurityException {
        Files.createDirectories(identityDir);
        boolean metadataExists = Files.exists(metadataPath);
        boolean privateExists = Files.exists(privateKeyPath);
        boolean publicExists = Files.exists(publicKeyPath);

        if (!metadataExists && !privateExists && !publicExists) {
            return createNewIdentity();
        }

        if (!(metadataExists && privateExists && publicExists)) {
            throw new IOException("Nexori identity files are incomplete in " + identityDir);
        }

        return loadExistingIdentity();
    }

    /**
     * Signs a bootstrap challenge with the local private key.
     */
    public String signChallenge(ServerIdentity identity, BootstrapChallenge challenge) throws GeneralSecurityException {
        return signCanonicalPayload(identity, challenge.canonicalPayload());
    }

    /**
     * Verifies a signed bootstrap challenge using the provided public key.
     */
    public boolean verifyChallenge(BootstrapChallenge challenge, String publicKeyBase64, String signatureBase64) throws GeneralSecurityException {
        return verifyCanonicalPayload(challenge.canonicalPayload(), publicKeyBase64, signatureBase64);
    }

    /**
     * Signs a canonical payload string with the local private key.
     */
    public String signCanonicalPayload(ServerIdentity identity, String canonicalPayload) throws GeneralSecurityException {
        Signature signature = Signature.getInstance(ALGORITHM);
        signature.initSign(identity.privateKey());
        signature.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * Verifies a canonical payload string with a public key and signature.
     */
    public boolean verifyCanonicalPayload(String canonicalPayload, String publicKeyBase64, String signatureBase64) throws GeneralSecurityException {
        PublicKey publicKey = decodePublicKey(publicKeyBase64);
        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(publicKey);
        verifier.update(canonicalPayload.getBytes(StandardCharsets.UTF_8));
        return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    }

    private ServerIdentity createNewIdentity() throws IOException, GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
        KeyPair keyPair = generator.generateKeyPair();

        UUID serverId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        String fingerprint = fingerprintFor(keyPair.getPublic().getEncoded());

        Files.writeString(privateKeyPath, Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()), StandardCharsets.US_ASCII);
        Files.writeString(publicKeyPath, publicKeyBase64, StandardCharsets.US_ASCII);

        Properties properties = new Properties();
        properties.setProperty("serverId", serverId.toString());
        properties.setProperty("algorithm", ALGORITHM);
        properties.setProperty("createdAt", createdAt.toString());
        properties.setProperty("fingerprint", fingerprint);
        writeProperties(metadataPath, properties);

        return new ServerIdentity(serverId, ALGORITHM, fingerprint, createdAt, publicKeyBase64, keyPair.getPrivate(), keyPair.getPublic());
    }

    private ServerIdentity loadExistingIdentity() throws IOException, GeneralSecurityException {
        Properties properties = readProperties(metadataPath);
        UUID serverId = UUID.fromString(properties.getProperty("serverId"));
        Instant createdAt = Instant.parse(properties.getProperty("createdAt"));
        String fingerprint = properties.getProperty("fingerprint");
        String publicKeyBase64 = Files.readString(publicKeyPath, StandardCharsets.US_ASCII).trim();
        byte[] privateBytes = Base64.getDecoder().decode(Files.readString(privateKeyPath, StandardCharsets.US_ASCII).trim());
        byte[] publicBytes = Base64.getDecoder().decode(publicKeyBase64);

        KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
        return new ServerIdentity(
            serverId,
            properties.getProperty("algorithm", ALGORITHM),
            fingerprint,
            createdAt,
            publicKeyBase64,
            keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes)),
            keyFactory.generatePublic(new X509EncodedKeySpec(publicBytes))
        );
    }

    private PublicKey decodePublicKey(String publicKeyBase64) throws GeneralSecurityException {
        byte[] publicBytes = Base64.getDecoder().decode(publicKeyBase64);
        return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(publicBytes));
    }

    private String fingerprintFor(byte[] publicKeyBytes) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(publicKeyBytes));
    }

    private Properties readProperties(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        }
        return properties;
    }

    private void writeProperties(Path path, Properties properties) throws IOException {
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            properties.store(outputStream, "Nexori identity");
        }
    }
}
