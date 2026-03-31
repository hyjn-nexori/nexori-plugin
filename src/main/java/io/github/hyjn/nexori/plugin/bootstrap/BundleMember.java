package io.github.hyjn.nexori.plugin.bootstrap;

public record BundleMember(
    String serverId,
    String connectionAddress,
    String fingerprint,
    String publicKeyBase64,
    long verifiedAtEpochMillis
) {
}
