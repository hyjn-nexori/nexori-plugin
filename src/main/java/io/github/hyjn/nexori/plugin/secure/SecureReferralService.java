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
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.diagnostics.protocol.DiagnosticsProtocol;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SecureReferralService {

    private final HytaleLogger logger;
    private final ServerIdentityManager identityManager;
    private final ServerIdentity localIdentity;
    private final TrustBundleStore trustBundleStore;
    private final SecureReferralPayloadCodec codec;
    private final DiagnosticsService diagnosticsService;
    private final Gson gson;
    private final Map<String, SecureReferralHandler> handlersByType = new ConcurrentHashMap<>();

    public SecureReferralService(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentityManager identityManager,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull DiagnosticsService diagnosticsService
    ) {
        this.logger = logger;
        this.identityManager = identityManager;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.diagnosticsService = diagnosticsService;
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
        return createPayload(playerRef.getUuid(), playerRef.getUsername(), payloadType, payload, ttl);
    }

    @Nonnull
    public byte[] createPayload(
        @Nonnull UUID playerUuid,
        @Nonnull String playerUsername,
        @Nonnull String payloadType,
        @Nonnull Object payload,
        @Nonnull Duration ttl
    ) throws IOException, GeneralSecurityException {
        long issuedAt = System.currentTimeMillis();
        String payloadJson = gson.toJson(payload);
        SecureReferralEnvelope unsignedEnvelope = SecureReferralEnvelope.unsigned(
            payloadType,
            localIdentity.serverId(),
            playerUuid,
            playerUsername,
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
            String payloadType = codec.tryPeekPayloadType(event.getReferralData()).orElse("unknown");
            if (shouldRecordReferralDiagnostics(payloadType)) {
                diagnosticsService.record(
                    DiagnosticsCategory.SECURITY,
                    DiagnosticsAction.SECURITY_REFERRAL_DECODE,
                    DiagnosticsOutcome.DENIED,
                    DiagnosticsReasonClass.SECURITY,
                    DiagnosticsReasonCode.REFERRAL_DECODE_FAILED,
                    "This Nexori referral payload could not be decoded.",
                    diagnosticsService.newOperationId("security"),
                    diagnostics -> diagnostics
                        .payloadType(payloadType)
                        .remoteConnectionAddress(event.getReferralSource() == null || event.getReferralSource().host == null ? "" : event.getReferralSource().host + ":" + event.getReferralSource().port)
                        .addPreview("rawLengthBytes", Integer.toString(event.getReferralData() == null ? 0 : event.getReferralData().length))
                );
            }
            deny(event, "This Nexori referral payload could not be decoded.");
            return true;
        }

        if (envelope.isExpired(Instant.now())) {
            recordDenied(
                event,
                envelope,
                DiagnosticsAction.SECURITY_REFERRAL_EXPIRY,
                DiagnosticsReasonCode.REFERRAL_EXPIRED,
                "This Nexori referral expired before it could be used."
            );
            deny(event, "This Nexori referral expired before it could be used.");
            return true;
        }

        BundleMember issuer = findTrustedIssuer(envelope.issuerServerId());
        if (issuer == null) {
            recordDenied(
                event,
                envelope,
                DiagnosticsAction.SECURITY_REFERRAL_ISSUER_LOOKUP,
                DiagnosticsReasonCode.ISSUER_NOT_TRUSTED,
                "This Nexori referral came from a server that is not in the current trust bundle."
            );
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
                recordDenied(
                    event,
                    envelope,
                    DiagnosticsAction.SECURITY_REFERRAL_SIGNATURE_VERIFY,
                    DiagnosticsReasonCode.SIGNATURE_INVALID,
                    "This Nexori referral signature was invalid."
                );
                deny(event, "This Nexori referral signature was invalid.");
                return true;
            }
        } catch (GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to verify Nexori secure referral signature.");
            recordDenied(
                event,
                envelope,
                DiagnosticsAction.SECURITY_REFERRAL_SIGNATURE_VERIFY,
                DiagnosticsReasonCode.SIGNATURE_VERIFY_EXCEPTION,
                "This Nexori referral could not be verified."
            );
            deny(event, "This Nexori referral could not be verified.");
            return true;
        }

        SecureReferralHandler handler = handlersByType.get(envelope.payloadType());
        if (handler == null) {
            recordDenied(
                event,
                envelope,
                DiagnosticsAction.SECURITY_REFERRAL_PAYLOAD_TYPE_LOOKUP,
                DiagnosticsReasonCode.PAYLOAD_TYPE_UNSUPPORTED,
                "This Nexori referral type is not supported on the destination server."
            );
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

    private void recordDenied(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull SecureReferralEnvelope envelope,
        @Nonnull String action,
        @Nonnull String reasonCode,
        @Nonnull String message
    ) {
        if (!shouldRecordReferralDiagnostics(envelope.payloadType())) {
            return;
        }
        String operationId = "security:" + envelope.nonce();
        diagnosticsService.record(
            DiagnosticsCategory.SECURITY,
            action,
            DiagnosticsOutcome.DENIED,
            DiagnosticsReasonClass.SECURITY,
            reasonCode,
            message,
            operationId,
            diagnostics -> {
                diagnostics.payloadType(envelope.payloadType());
                diagnostics.payloadHash(hash(envelope.payloadJson()));
                diagnostics.playerUuid(envelope.playerUuid());
                diagnostics.playerNameClaimed(envelope.playerUsername());
                diagnostics.remoteServerId(envelope.issuerServerId());
                diagnostics.remoteConnectionAddress(event.getReferralSource() == null || event.getReferralSource().host == null ? "" : event.getReferralSource().host + ":" + event.getReferralSource().port);
                diagnostics.payloadPreview(buildPayloadPreview(envelope));
            }
        );
    }

    private boolean shouldRecordReferralDiagnostics(@Nonnull String payloadType) {
        return !DiagnosticsProtocol.isOperationalOnlyPayloadType(payloadType);
    }

    @Nonnull
    private Map<String, String> buildPayloadPreview(@Nonnull SecureReferralEnvelope envelope) {
        LinkedHashMap<String, String> preview = new LinkedHashMap<>();
        preview.put("payloadType", envelope.payloadType());
        preview.put("issuerServerId", envelope.issuerServerId());
        preview.put("playerNameClaimed", envelope.playerUsername());
        preview.put("expiresAtEpochMillis", Long.toString(envelope.expiresAtEpochMillis()));
        return preview;
    }

    @Nonnull
    private String hash(@Nonnull String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("sha256:");
            for (byte value : hash) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "";
        }
    }
}
