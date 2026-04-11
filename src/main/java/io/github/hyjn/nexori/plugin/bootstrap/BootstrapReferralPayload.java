package io.github.hyjn.nexori.plugin.bootstrap;

import javax.annotation.Nonnull;

public record BootstrapReferralPayload(
    String type,
    String startedByPlayerUuid,
    BootstrapChallenge challenge,
    TrustBundle trustBundle,
    String responderServerId,
    String responderFingerprint,
    String responderPublicKeyBase64,
    String signatureBase64,
    String acknowledgedBundleHash,
    String errorMessage,
    BootstrapMigrationPlan migrationPlan,
    int currentPeerIndex,
    int totalPeers
) {

    @Nonnull
    public static BootstrapReferralPayload request(
        @Nonnull String startedByPlayerUuid,
        @Nonnull BootstrapChallenge challenge,
        int currentPeerIndex,
        int totalPeers
    ) {
        return new BootstrapReferralPayload(
            BootstrapMessageType.REQUEST_PROOF.name(),
            startedByPlayerUuid,
            challenge,
            null,
            "",
            "",
            "",
            "",
            "",
            "",
            BootstrapMigrationPlan.empty(),
            currentPeerIndex,
            totalPeers
        );
    }

    @Nonnull
    public static BootstrapReferralPayload response(
        @Nonnull BootstrapReferralPayload request,
        @Nonnull String responderServerId,
        @Nonnull String responderFingerprint,
        @Nonnull String responderPublicKeyBase64,
        @Nonnull String signatureBase64
    ) {
        return new BootstrapReferralPayload(
            BootstrapMessageType.RESPONSE_PROOF.name(),
            request.startedByPlayerUuid,
            request.challenge,
            null,
            responderServerId,
            responderFingerprint,
            responderPublicKeyBase64,
            signatureBase64,
            "",
            "",
            BootstrapMigrationPlan.empty(),
            request.currentPeerIndex,
            request.totalPeers
        );
    }

    @Nonnull
    public static BootstrapReferralPayload installBundle(
        @Nonnull String startedByPlayerUuid,
        @Nonnull BootstrapChallenge challenge,
        @Nonnull TrustBundle trustBundle,
        @Nonnull BootstrapMigrationPlan migrationPlan,
        int currentPeerIndex,
        int totalPeers
    ) {
        return new BootstrapReferralPayload(
            BootstrapMessageType.INSTALL_BUNDLE.name(),
            startedByPlayerUuid,
            challenge,
            trustBundle,
            "",
            "",
            "",
            "",
            "",
            "",
            migrationPlan,
            currentPeerIndex,
            totalPeers
        );
    }

    @Nonnull
    public static BootstrapReferralPayload installAck(
        @Nonnull BootstrapReferralPayload request,
        @Nonnull String responderServerId,
        @Nonnull String acknowledgedBundleHash
    ) {
        return new BootstrapReferralPayload(
            BootstrapMessageType.INSTALL_ACK.name(),
            request.startedByPlayerUuid,
            request.challenge,
            null,
            responderServerId,
            "",
            "",
            "",
            acknowledgedBundleHash,
            "",
            BootstrapMigrationPlan.empty(),
            request.currentPeerIndex,
            request.totalPeers
        );
    }

    @Nonnull
    public static BootstrapReferralPayload error(
        @Nonnull BootstrapReferralPayload request,
        @Nonnull String errorMessage
    ) {
        return new BootstrapReferralPayload(
            BootstrapMessageType.ERROR.name(),
            request.startedByPlayerUuid,
            request.challenge,
            null,
            "",
            "",
            "",
            "",
            "",
            errorMessage,
            BootstrapMigrationPlan.empty(),
            request.currentPeerIndex,
            request.totalPeers
        );
    }

    @Nonnull
    public BootstrapMessageType messageType() {
        return BootstrapMessageType.valueOf(type);
    }
}
