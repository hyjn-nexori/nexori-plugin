package io.github.hyjn.nexori.plugin.bootstrap;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BootstrapCoordinator {

    private final HytaleLogger logger;
    private final ServerIdentityManager identityManager;
    private final ServerIdentity localIdentity;
    private final BootstrapStateStore bootstrapStateStore;
    private final ConfiguredPeerService configuredPeerService;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final BootstrapRunStore bootstrapRunStore;
    private final TrustBundleStore trustBundleStore;
    private final BootstrapPayloadCodec payloadCodec;
    private final Map<UUID, String> pendingMessages = new ConcurrentHashMap<>();

    public BootstrapCoordinator(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentityManager identityManager,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull BootstrapStateStore bootstrapStateStore,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull BootstrapRunStore bootstrapRunStore,
        @Nonnull TrustBundleStore trustBundleStore
    ) {
        this.logger = logger;
        this.identityManager = identityManager;
        this.localIdentity = localIdentity;
        this.bootstrapStateStore = bootstrapStateStore;
        this.configuredPeerService = configuredPeerService;
        this.localConnectionAddressService = localConnectionAddressService;
        this.bootstrapRunStore = bootstrapRunStore;
        this.trustBundleStore = trustBundleStore;
        this.payloadCodec = new BootstrapPayloadCodec();
    }

    @Nonnull
    public StartResult start(@Nonnull PlayerRef playerRef) {
        List<ConfiguredPeer> peers = configuredPeerService.list();
        if (peers.isEmpty()) {
            return StartResult.failed("Add at least one peer IP before starting Nexori bootstrap.");
        }

        BootstrapRun existingRun = bootstrapRunStore.getCurrentRun();
        if (existingRun != null && !existingRun.isExpired()) {
            return StartResult.failed("A Nexori bootstrap run is already active for session " + existingRun.sessionId() + ".");
        }

        try {
            BootstrapState state = bootstrapStateStore.openSession(Duration.ofMinutes(10));
            BootstrapChallenge challenge = createChallenge(state.sessionId(), peers.getFirst().connectionAddress(), state.sessionExpiresAtEpochMillis());
            BootstrapRun run = new BootstrapRun(
                state.sessionId(),
                playerRef.getUuid().toString(),
                localIdentity.serverId().toString(),
                state.sessionExpiresAtEpochMillis(),
                BootstrapPhase.COLLECT_PROOFS,
                0,
                List.copyOf(peers),
                List.of(),
                challenge
            );
            bootstrapRunStore.save(run);

            playerRef.referToServer(
                peers.getFirst().host(),
                peers.getFirst().port(),
                payloadCodec.encode(BootstrapReferralPayload.request(run.startedByPlayerUuid(), challenge, 0, peers.size()))
            );

            logger.atInfo().log("Started Nexori bootstrap session " + state.sessionId() + " with " + peers.size() + " configured peers.");
            return StartResult.started("Started Nexori bootstrap with " + peers.size() + " peer(s).");
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to start Nexori bootstrap.");
            return StartResult.failed("Failed to start Nexori bootstrap: " + exception.getMessage());
        }
    }

    public void handlePlayerSetupConnect(@Nonnull PlayerSetupConnectEvent event) {
        BootstrapReferralPayload payload = decode(event);
        if (payload == null) {
            return;
        }

        switch (payload.messageType()) {
            case REQUEST_PROOF -> handleProofRequest(event, payload);
            case RESPONSE_PROOF -> handleProofResponse(event, payload);
            case INSTALL_BUNDLE -> handleBundleInstallRequest(event, payload);
            case INSTALL_ACK -> handleBundleInstallAck(event, payload);
            case ERROR -> handleError(payload);
        }
    }

    public void handlePlayerConnect(@Nonnull PlayerConnectEvent event) {
        if (event.getPlayerRef() == null) {
            return;
        }

        UUID playerUuid = event.getPlayerRef().getUuid();
        String pendingMessage = pendingMessages.remove(playerUuid);
        if (pendingMessage != null && !pendingMessage.isBlank()) {
            event.getPlayerRef().sendMessage(Message.raw(pendingMessage));
        }
    }

    @Nonnull
    public TrustBundle getTrustBundle() {
        return trustBundleStore.getCurrentBundle();
    }

    private void handleProofRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload) {
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }

        try {
            if (payload.challenge() == null || payload.challenge().isExpired(Instant.now())) {
                bounceError(event, payload, "The Nexori bootstrap challenge expired.");
                return;
            }

            String signature = identityManager.signChallenge(localIdentity, payload.challenge());
            event.referToServer(
                referralSource.host,
                referralSource.port,
                payloadCodec.encode(BootstrapReferralPayload.response(
                    payload,
                    localIdentity.serverId().toString(),
                    localIdentity.fingerprint(),
                    localIdentity.publicKeyBase64(),
                    signature
                ))
            );
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori proof request.");
            bounceError(event, payload, "Failed to sign the Nexori proof challenge.");
        }
    }

    private void handleProofResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        if (currentRun == null || currentRun.phase() != BootstrapPhase.COLLECT_PROOFS) {
            return;
        }

        try {
            if (!matchesCurrentRun(currentRun, payload)) {
                queueStatus(currentRun.startedByPlayerUuid(), "Ignored a Nexori proof response that did not match the active session.");
                return;
            }

            boolean verified = identityManager.verifyChallenge(
                currentRun.currentChallenge(),
                payload.responderPublicKeyBase64(),
                payload.signatureBase64()
            );
            if (!verified) {
                bootstrapRunStore.clear();
                queueStatus(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed because one server returned an invalid signature.");
                return;
            }

            HostAddress referralSource = event.getReferralSource();
            String connectionAddress = referralSource == null || referralSource.host == null
                ? currentRun.currentPeer().connectionAddress()
                : referralSource.host + ":" + referralSource.port;

            BootstrapRun updatedRun = currentRun;
            if (!localIdentity.serverId().toString().equals(payload.responderServerId())) {
                BundleMember verifiedPeer = new BundleMember(
                    payload.responderServerId(),
                    connectionAddress,
                    payload.responderFingerprint(),
                    payload.responderPublicKeyBase64(),
                    Instant.now().toEpochMilli(),
                    false
                );
                updatedRun = currentRun.withVerifiedPeer(verifiedPeer);
            } else {
                try {
                    localConnectionAddressService.save(connectionAddress);
                    logger.atInfo().log("Captured the local Nexori connection address as " + connectionAddress + ".");
                } catch (IOException | IllegalArgumentException exception) {
                    logger.atWarning().withCause(exception).log("Failed to persist the local Nexori connection address " + connectionAddress + ".");
                }
            }

            int nextPeerIndex = updatedRun.currentPeerIndex() + 1;
            if (nextPeerIndex >= updatedRun.peers().size()) {
                beginBundleInstallation(updatedRun, event);
                return;
            }

            ConfiguredPeer nextPeer = updatedRun.peers().get(nextPeerIndex);
            BootstrapChallenge nextChallenge = createChallenge(updatedRun.sessionId(), nextPeer.connectionAddress(), updatedRun.expiresAtEpochMillis());
            bootstrapRunStore.save(updatedRun.withCurrentChallenge(nextPeerIndex, nextChallenge));
            event.referToServer(
                nextPeer.host(),
                nextPeer.port(),
                payloadCodec.encode(BootstrapReferralPayload.request(
                    updatedRun.startedByPlayerUuid(),
                    nextChallenge,
                    nextPeerIndex,
                    updatedRun.peers().size()
                ))
            );
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to process Nexori proof response.");
            bootstrapRunStore.clear();
            queueStatus(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed while processing a proof response: " + exception.getMessage());
        }
    }

    private void beginBundleInstallation(@Nonnull BootstrapRun run, @Nonnull PlayerSetupConnectEvent event) throws IOException {
        long nextBundleVersion = bootstrapStateStore.getCurrentState().bundleVersion() + 1;
            TrustBundle bundle = trustBundleStore.saveVerifiedMembers(
                localIdentity,
                resolveLocalConnectionAddress(run),
                run.verifiedPeers(),
                nextBundleVersion
            );
        List<ConfiguredPeer> installPeers = peersForInstallation(bundle);

        if (installPeers.isEmpty()) {
            finishRun(run, bundle);
            return;
        }

        ConfiguredPeer nextPeer = installPeers.getFirst();
        BootstrapChallenge nextChallenge = createChallenge(run.sessionId(), nextPeer.connectionAddress(), run.expiresAtEpochMillis());
        bootstrapRunStore.save(run.withPhase(BootstrapPhase.INSTALL_BUNDLE, 0, installPeers, nextChallenge));
        event.referToServer(
            nextPeer.host(),
            nextPeer.port(),
            payloadCodec.encode(BootstrapReferralPayload.installBundle(
                run.startedByPlayerUuid(),
                nextChallenge,
                bundle,
                0,
                installPeers.size()
            ))
        );
    }

    private void handleBundleInstallRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload) {
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }

        try {
            if (payload.challenge() == null || payload.challenge().isExpired(Instant.now())) {
                bounceError(event, payload, "The Nexori bundle install request expired.");
                return;
            }
            if (payload.trustBundle() == null) {
                bounceError(event, payload, "The Nexori bundle install request was missing bundle data.");
                return;
            }
            if (!bundleContainsLocalIdentity(payload.trustBundle())) {
                bounceError(event, payload, "The Nexori bundle does not contain this server identity.");
                return;
            }

            TrustBundle installedBundle = trustBundleStore.installBundle(payload.trustBundle());
            bootstrapStateStore.markBundleInstalled(installedBundle.bundleVersion(), installedBundle.bundleHash());
            event.referToServer(
                referralSource.host,
                referralSource.port,
                payloadCodec.encode(BootstrapReferralPayload.installAck(
                    payload,
                    localIdentity.serverId().toString(),
                    installedBundle.bundleHash()
                ))
            );
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log("Failed to install Nexori trust bundle.");
            bounceError(event, payload, "Failed to install the Nexori trust bundle: " + exception.getMessage());
        }
    }

    private void handleBundleInstallAck(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        if (currentRun == null || currentRun.phase() != BootstrapPhase.INSTALL_BUNDLE) {
            return;
        }

        try {
            if (!matchesCurrentRun(currentRun, payload)) {
                queueStatus(currentRun.startedByPlayerUuid(), "Ignored a Nexori install acknowledgement that did not match the active session.");
                return;
            }

            TrustBundle currentBundle = trustBundleStore.getCurrentBundle();
            if (currentBundle.bundleHash().isBlank() || !currentBundle.bundleHash().equals(payload.acknowledgedBundleHash())) {
                bootstrapRunStore.clear();
                queueStatus(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed because one server acknowledged the wrong trust bundle.");
                return;
            }

            int nextPeerIndex = currentRun.currentPeerIndex() + 1;
            if (nextPeerIndex >= currentRun.peers().size()) {
                finishRun(currentRun, currentBundle);
                return;
            }

            ConfiguredPeer nextPeer = currentRun.peers().get(nextPeerIndex);
            BootstrapChallenge nextChallenge = createChallenge(currentRun.sessionId(), nextPeer.connectionAddress(), currentRun.expiresAtEpochMillis());
            bootstrapRunStore.save(currentRun.withCurrentChallenge(nextPeerIndex, nextChallenge));
            event.referToServer(
                nextPeer.host(),
                nextPeer.port(),
                payloadCodec.encode(BootstrapReferralPayload.installBundle(
                    currentRun.startedByPlayerUuid(),
                    nextChallenge,
                    currentBundle,
                    nextPeerIndex,
                    currentRun.peers().size()
                ))
            );
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to continue Nexori bundle installation.");
            bootstrapRunStore.clear();
            queueStatus(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed while distributing the trust bundle: " + exception.getMessage());
        }
    }

    private void handleError(@Nonnull BootstrapReferralPayload payload) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        if (currentRun == null || payload.challenge() == null || !currentRun.sessionId().equals(payload.challenge().sessionId())) {
            return;
        }

        bootstrapRunStore.clear();
        queueStatus(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed on peer "
            + (currentRun.currentPeer() == null ? "unknown" : currentRun.currentPeer().connectionAddress())
            + ": " + payload.errorMessage());
    }

    private void finishRun(@Nonnull BootstrapRun run, @Nonnull TrustBundle bundle) {
        bootstrapStateStore.markBundleInstalled(bundle.bundleVersion(), bundle.bundleHash());
        bootstrapRunStore.clear();
        queueStatus(run.startedByPlayerUuid(), "Nexori bootstrap verified "
            + run.verifiedPeers().size()
            + " remote peer(s) and installed bundle v"
            + bundle.bundleVersion()
            + " across "
            + run.peers().size()
            + " server(s).");
        logger.atInfo().log("Completed Nexori bootstrap session " + run.sessionId() + " with bundle " + bundle.bundleHash() + ".");
    }

    private boolean bundleContainsLocalIdentity(@Nonnull TrustBundle bundle) {
        for (BundleMember member : bundle.members()) {
            if (!localIdentity.serverId().toString().equals(member.serverId())) {
                continue;
            }
            return localIdentity.fingerprint().equals(member.fingerprint())
                && localIdentity.publicKeyBase64().equals(member.publicKeyBase64());
        }
        return false;
    }

    @Nonnull
    private String resolveLocalConnectionAddress(@Nonnull BootstrapRun run) {
        String persisted = localConnectionAddressService.getConnectionAddressOrBlank();
        if (!persisted.isBlank()) {
            return persisted;
        }

        List<ConfiguredPeer> unmatchedPeers = run.peers().stream()
            .filter(peer -> run.verifiedPeers().stream().noneMatch(member -> peer.connectionAddress().equalsIgnoreCase(member.connectionAddress())))
            .toList();

        if (unmatchedPeers.size() == 1) {
            String inferred = unmatchedPeers.getFirst().connectionAddress();
            try {
                localConnectionAddressService.save(inferred);
                logger.atInfo().log("Inferred the local Nexori connection address as " + inferred + " during bundle installation.");
            } catch (IOException | IllegalArgumentException exception) {
                logger.atWarning().withCause(exception).log("Failed to persist the inferred local Nexori connection address " + inferred + ".");
            }
            return inferred;
        }

        return "";
    }

    @Nonnull
    private List<ConfiguredPeer> peersForInstallation(@Nonnull TrustBundle bundle) {
        List<ConfiguredPeer> peers = new ArrayList<>();
        for (BundleMember member : bundle.members()) {
            if (member.local() || member.connectionAddress() == null || member.connectionAddress().isBlank()) {
                continue;
            }
            try {
                peers.add(ConfiguredPeer.parse(member.connectionAddress()));
            } catch (IllegalArgumentException exception) {
                logger.atWarning().log("Skipping Nexori install target with invalid connection address: " + member.connectionAddress());
            }
        }
        return peers;
    }

    private boolean matchesCurrentRun(@Nonnull BootstrapRun currentRun, @Nonnull BootstrapReferralPayload payload) {
        if (payload.challenge() == null || currentRun.currentChallenge() == null) {
            return false;
        }

        return currentRun.sessionId().equals(payload.challenge().sessionId())
            && currentRun.startedByPlayerUuid().equals(payload.startedByPlayerUuid())
            && currentRun.currentChallenge().canonicalPayload().equals(payload.challenge().canonicalPayload());
    }

    @Nonnull
    private BootstrapChallenge createChallenge(@Nonnull String sessionId, @Nonnull String targetDescriptor, long expiresAtEpochMillis) {
        return BootstrapChallenge.create(
            localIdentity.serverId(),
            targetDescriptor,
            sessionId,
            Instant.ofEpochMilli(expiresAtEpochMillis)
        );
    }

    private void bounceError(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload, @Nonnull String message) {
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }

        try {
            event.referToServer(
                referralSource.host,
                referralSource.port,
                payloadCodec.encode(BootstrapReferralPayload.error(payload, message))
            );
        } catch (IOException ignored) {
        }
    }

    private void queueStatus(@Nonnull String startedByPlayerUuid, @Nonnull String message) {
        try {
            pendingMessages.put(UUID.fromString(startedByPlayerUuid), message);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private BootstrapReferralPayload decode(@Nonnull PlayerSetupConnectEvent event) {
        if (!event.isReferralConnection()) {
            return null;
        }

        try {
            return payloadCodec.tryDecode(event.getReferralData()).orElse(null);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to decode Nexori referral payload.");
            return null;
        }
    }

    public record StartResult(boolean started, String message) {
        @Nonnull
        public static StartResult started(@Nonnull String message) {
            return new StartResult(true, message);
        }

        @Nonnull
        public static StartResult failed(@Nonnull String message) {
            return new StartResult(false, message);
        }
    }
}
