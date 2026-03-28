package io.github.hyjn.nexori.plugin.secure;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class SecureReferralService {

    private final HytaleLogger logger;
    private final ServerIdentityManager identityManager;
    private final ServerIdentity localIdentity;
    private final TrustBundleStore trustBundleStore;
    private final SecureReferralPayloadCodec codec;
    private final Gson gson;
    private final Map<String, SecureReferralHandler> handlersByType = new ConcurrentHashMap<>();

    public SecureReferralService(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentityManager identityManager,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull TrustBundleStore trustBundleStore
    ) {
        this.logger = logger;
        this.identityManager = identityManager;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.codec = new SecureReferralPayloadCodec();
        this.gson = new GsonBuilder().create();
    }

    public void registerHandler(@Nonnull SecureReferralHandler handler) {
        handlersByType.put(handler.payloadType(), handler);
    }

    @Nonnull
    public Gson gson() {
        return gson;
    }

    public void referPlayer(
        @Nonnull PlayerRef playerRef,
        @Nonnull String host,
        int port,
        @Nonnull String payloadType,
        @Nonnull Object payload,
        @Nonnull Duration ttl
    ) throws IOException, GeneralSecurityException {
        playerRef.referToServer(host, port, createPayload(playerRef, payloadType, payload, ttl));
    }

    @Nonnull
    public byte[] createPayload(
        @Nonnull PlayerRef playerRef,
        @Nonnull String payloadType,
        @Nonnull Object payload,
        @Nonnull Duration ttl
    ) throws IOException, GeneralSecurityException {
        long issuedAt = System.currentTimeMillis();
        String payloadJson = gson.toJson(payload);
        SecureReferralEnvelope unsignedEnvelope = SecureReferralEnvelope.unsigned(
            payloadType,
            localIdentity.serverId(),
            playerRef.getUuid(),
            playerRef.getUsername(),
            issuedAt,
            issuedAt + ttl.toMillis(),
            payloadJson
        );
        String signature = identityManager.signCanonicalPayload(localIdentity, unsignedEnvelope.canonicalPayload());
        return codec.encode(unsignedEnvelope.signed(signature));
    }

    public boolean handlePlayerSetupConnect(@Nonnull PlayerSetupConnectEvent event) {
        if (!event.isReferralConnection()) {
            return false;
        }

        SecureReferralEnvelope envelope;
        try {
            Optional<SecureReferralEnvelope> decoded = codec.tryDecode(event.getReferralData());
            if (decoded.isEmpty()) {
                return false;
            }
            envelope = decoded.get();
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to decode Nexori secure referral payload.");
            deny(event, "This Nexori referral payload could not be decoded.");
            return true;
        }

        if (envelope.isExpired(Instant.now())) {
            deny(event, "This Nexori referral expired before it could be used.");
            return true;
        }

        BundleMember issuer = findTrustedIssuer(envelope.issuerServerId());
        if (issuer == null) {
            deny(event, "This Nexori referral came from a server that is not in the current trust bundle.");
            return true;
        }

        try {
            boolean valid = identityManager.verifyCanonicalPayload(
                envelope.canonicalPayload(),
                issuer.publicKeyBase64(),
                envelope.signatureBase64()
            );
            if (!valid) {
                deny(event, "This Nexori referral signature was invalid.");
                return true;
            }
        } catch (GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to verify Nexori secure referral signature.");
            deny(event, "This Nexori referral could not be verified.");
            return true;
        }

        SecureReferralHandler handler = handlersByType.get(envelope.payloadType());
        if (handler == null) {
            deny(event, "This Nexori referral type is not supported on the destination server.");
            return true;
        }

        handler.handle(event, new VerifiedSecureReferral(envelope, issuer));
        return true;
    }

    private BundleMember findTrustedIssuer(@Nonnull String issuerServerId) {
        TrustBundle bundle = trustBundleStore.getCurrentBundle();
        for (BundleMember member : bundle.members()) {
            if (issuerServerId.equals(member.serverId())) {
                return member;
            }
        }
        return null;
    }

    private void deny(@Nonnull PlayerSetupConnectEvent event, @Nonnull String reason) {
        event.setCancelled(true);
        event.setReason(Message.raw(reason));
    }
}
