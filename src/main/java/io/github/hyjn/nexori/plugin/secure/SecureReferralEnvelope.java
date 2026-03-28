package io.github.hyjn.nexori.plugin.secure;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.UUID;

public record SecureReferralEnvelope(
    String protocol,
    String payloadType,
    String issuerServerId,
    String playerUuid,
    String playerUsername,
    long issuedAtEpochMillis,
    long expiresAtEpochMillis,
    String nonce,
    String payloadJson,
    String signatureBase64
) {

    public static SecureReferralEnvelope unsigned(
        @Nonnull String payloadType,
        @Nonnull UUID issuerServerId,
        @Nonnull UUID playerUuid,
        @Nonnull String playerUsername,
        long issuedAtEpochMillis,
        long expiresAtEpochMillis,
        @Nonnull String payloadJson
    ) {
        return new SecureReferralEnvelope(
            "nexori.secure-referral.v1",
            payloadType,
            issuerServerId.toString(),
            playerUuid.toString(),
            playerUsername,
            issuedAtEpochMillis,
            expiresAtEpochMillis,
            UUID.randomUUID().toString(),
            payloadJson,
            ""
        );
    }

    @Nonnull
    public String canonicalPayload() {
        return protocol
            + "\npayloadType=" + payloadType
            + "\nissuerServerId=" + issuerServerId
            + "\nplayerUuid=" + playerUuid
            + "\nplayerUsername=" + playerUsername
            + "\nissuedAtEpochMillis=" + issuedAtEpochMillis
            + "\nexpiresAtEpochMillis=" + expiresAtEpochMillis
            + "\nnonce=" + nonce
            + "\npayloadJson=" + payloadJson;
    }

    @Nonnull
    public SecureReferralEnvelope signed(@Nonnull String signatureBase64) {
        return new SecureReferralEnvelope(
            protocol,
            payloadType,
            issuerServerId,
            playerUuid,
            playerUsername,
            issuedAtEpochMillis,
            expiresAtEpochMillis,
            nonce,
            payloadJson,
            signatureBase64
        );
    }

    public boolean isExpired(@Nonnull Instant now) {
        return expiresAtEpochMillis < now.toEpochMilli();
    }
}
