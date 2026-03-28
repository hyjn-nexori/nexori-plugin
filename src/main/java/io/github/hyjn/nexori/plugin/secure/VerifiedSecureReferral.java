package io.github.hyjn.nexori.plugin.secure;

import com.google.gson.Gson;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;

import javax.annotation.Nonnull;

public record VerifiedSecureReferral(
    SecureReferralEnvelope envelope,
    BundleMember issuer
) {

    @Nonnull
    public <T> T decodePayload(@Nonnull Gson gson, @Nonnull Class<T> payloadType) {
        return gson.fromJson(envelope.payloadJson(), payloadType);
    }
}
