package io.github.hyjn.nexori.plugin.bootstrap;

import java.time.Instant;
import java.util.UUID;

public record BootstrapChallenge(
    String sessionId,
    String originServerId,
    String targetDescriptor,
    String nonce,
    long expiresAtEpochMillis
) {

    public static BootstrapChallenge create(UUID originServerId, String targetDescriptor, String sessionId, Instant expiresAt) {
        return new BootstrapChallenge(
            sessionId,
            originServerId.toString(),
            targetDescriptor,
            UUID.randomUUID().toString(),
            expiresAt.toEpochMilli()
        );
    }

    public String canonicalPayload() {
        return "nexori/bootstrap-challenge"
            + "\nsessionId=" + sessionId
            + "\noriginServerId=" + originServerId
            + "\ntargetDescriptor=" + targetDescriptor
            + "\nnonce=" + nonce
            + "\nexpiresAtEpochMillis=" + expiresAtEpochMillis;
    }

    public boolean isExpired(Instant now) {
        return now.toEpochMilli() > expiresAtEpochMillis;
    }
}
