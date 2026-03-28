package io.github.hyjn.nexori.plugin.identity;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.UUID;

public record ServerIdentity(
    UUID serverId,
    String algorithm,
    String fingerprint,
    Instant createdAt,
    String publicKeyBase64,
    PrivateKey privateKey,
    PublicKey publicKey
) {
}
