package io.github.hyjn.nexori.plugin.bootstrap;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.HostAddress;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerMigrationService;
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
import java.util.function.Consumer;

public final class BootstrapCoordinator {

    private final HytaleLogger logger;
    private final ServerIdentityManager identityManager;
    private final ServerIdentity localIdentity;
    private final BootstrapStateStore bootstrapStateStore;
    private final ConfiguredPeerService configuredPeerService;
    private final ConfiguredPeerMigrationService configuredPeerMigrationService;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final BootstrapRunStore bootstrapRunStore;
    private final TrustBundleStore trustBundleStore;
    private final BootstrapPersistenceMigrationService persistenceMigrationService;
    private final BootstrapPayloadCodec payloadCodec;
    private final DiagnosticsService diagnosticsService;
    private final Map<UUID, String> pendingMessages = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> pendingMenuResumeRequests = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingMenuResumeStatuses = new ConcurrentHashMap<>();

    public BootstrapCoordinator(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentityManager identityManager,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull BootstrapStateStore bootstrapStateStore,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull ConfiguredPeerMigrationService configuredPeerMigrationService,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull BootstrapRunStore bootstrapRunStore,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull BootstrapPersistenceMigrationService persistenceMigrationService,
        @Nonnull DiagnosticsService diagnosticsService
    ) {
        this.logger = logger;
        this.identityManager = identityManager;
        this.localIdentity = localIdentity;
        this.bootstrapStateStore = bootstrapStateStore;
        this.configuredPeerService = configuredPeerService;
        this.configuredPeerMigrationService = configuredPeerMigrationService;
        this.localConnectionAddressService = localConnectionAddressService;
        this.bootstrapRunStore = bootstrapRunStore;
        this.trustBundleStore = trustBundleStore;
        this.persistenceMigrationService = persistenceMigrationService;
        this.diagnosticsService = diagnosticsService;
        this.payloadCodec = new BootstrapPayloadCodec();
    }

    @Nonnull
    public StartResult start(@Nonnull PlayerRef playerRef) {
        String operationId = diagnosticsService.newOperationId("bootstrap");
        List<ConfiguredPeer> peers = configuredPeerService.list();
        if (peers.isEmpty()) {
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_RUN_START,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.VALIDATION,
                DiagnosticsReasonCode.BOOTSTRAP_PEERS_EMPTY,
                "Add the bootstrap peers for this server first, including this server, before starting Nexori bootstrap.",
                event -> event.playerUuid(playerRef.getUuid().toString()).playerNameClaimed(playerRef.getUsername())
            );
            return StartResult.failed("Add the bootstrap peers for this server first, including this server, before starting Nexori bootstrap.");
        }

        BootstrapRun existingRun = bootstrapRunStore.getCurrentRun();
        if (existingRun != null && !existingRun.isExpired()) {
            recordBootstrap(
                "bootstrap:" + existingRun.sessionId(),
                DiagnosticsAction.BOOTSTRAP_RUN_START,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.VALIDATION,
                DiagnosticsReasonCode.BOOTSTRAP_RUN_ALREADY_ACTIVE,
                "A Nexori bootstrap run is already active for this server.",
                event -> event
                    .playerUuid(playerRef.getUuid().toString())
                    .playerNameClaimed(playerRef.getUsername())
                    .sessionId(existingRun.sessionId())
            );
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
                BootstrapMigrationPlan.empty(),
                challenge
            );
            bootstrapRunStore.save(run);
            String sessionOperationId = "bootstrap:" + state.sessionId();
            recordBootstrap(
                sessionOperationId,
                DiagnosticsAction.BOOTSTRAP_RUN_START,
                DiagnosticsOutcome.STARTED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.BOOTSTRAP_RUN_STARTED,
                "Started Nexori bootstrap from this server.",
                event -> event
                    .playerUuid(playerRef.getUuid().toString())
                    .playerNameClaimed(playerRef.getUsername())
                    .sessionId(state.sessionId())
                    .requestId(state.sessionId())
                    .remoteConnectionAddress(peers.getFirst().connectionAddress())
                    .addPreview("bootstrapPeerCount", Integer.toString(peers.size()))
                    .addPreview("firstPeer", peers.getFirst().connectionAddress())
            );

            playerRef.referToServer(
                peers.getFirst().host(),
                peers.getFirst().port(),
                payloadCodec.encode(BootstrapReferralPayload.request(run.startedByPlayerUuid(), challenge, 0, peers.size()))
            );

            logger.atInfo().log("Started Nexori bootstrap session " + state.sessionId() + " with " + peers.size() + " bootstrap peer(s).");
            return StartResult.started("Started Nexori bootstrap with " + peers.size() + " peer(s).");
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to start Nexori bootstrap.");
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_RUN_START,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.IO,
                DiagnosticsReasonCode.BOOTSTRAP_PEER_ERROR,
                "Failed to start Nexori bootstrap: " + exception.getMessage(),
                event -> event
                    .playerUuid(playerRef.getUuid().toString())
                    .playerNameClaimed(playerRef.getUsername())
            );
            return StartResult.failed("Failed to start Nexori bootstrap: " + exception.getMessage());
        }
    }

    @Nonnull
    public StartResult resetActiveRun(@Nonnull PlayerRef playerRef) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        BootstrapState currentState = bootstrapStateStore.getCurrentState();
        if (currentRun == null && !currentState.hasActiveSession()) {
            return StartResult.failed("There is no active Nexori bootstrap run to reset on this server.");
        }

        bootstrapRunStore.clear();
        bootstrapStateStore.closeSession();
        clearPendingUiState(playerRef.getUuid());
        if (currentRun != null) {
            try {
                clearPendingUiState(UUID.fromString(currentRun.startedByPlayerUuid()));
            } catch (IllegalArgumentException ignored) {
            }
            logger.atInfo().log("Reset Nexori bootstrap session " + currentRun.sessionId() + " on the origin server.");
        } else {
            logger.atInfo().log("Reset Nexori bootstrap session state on the origin server without a persisted run payload.");
        }
        recordBootstrap(
            currentRun == null ? diagnosticsService.newOperationId("bootstrap") : "bootstrap:" + currentRun.sessionId(),
            DiagnosticsAction.BOOTSTRAP_RUN_RESET,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BOOTSTRAP_RUN_RESET,
            "Cleared the active Nexori bootstrap state on this origin server.",
            event -> event
                .playerUuid(playerRef.getUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .sessionId(currentRun == null ? "" : currentRun.sessionId())
        );
        return StartResult.started("Cleared the active Nexori bootstrap state on this origin server. You can run Initial Setup again.");
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

    public void requestMenuResume(@Nonnull UUID playerUuid) {
        pendingMenuResumeRequests.put(playerUuid, Boolean.TRUE);
    }

    @Nonnull
    public String consumePendingMenuResumeStatus(@Nonnull UUID playerUuid) {
        String status = pendingMenuResumeStatuses.remove(playerUuid);
        return status == null ? "" : status;
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
        String operationId = payload.challenge() == null ? diagnosticsService.newOperationId("bootstrap") : "bootstrap:" + payload.challenge().sessionId();

        try {
            if (payload.challenge() == null || payload.challenge().isExpired(Instant.now())) {
                recordBootstrap(
                    operationId,
                    DiagnosticsAction.BOOTSTRAP_PROOF_REQUEST_ANSWER,
                    DiagnosticsOutcome.EXPIRED,
                    DiagnosticsReasonClass.STALE,
                    DiagnosticsReasonCode.BOOTSTRAP_PEER_ERROR,
                    "The Nexori bootstrap challenge expired.",
                    diag -> diag.sessionId(payload.challenge() == null ? "" : payload.challenge().sessionId())
                );
                bounceError(event, payload, "The Nexori bootstrap challenge expired.");
                return;
            }
            if (!isVerifiedBootstrapOrigin(payload.challenge(), referralSource)) {
                recordBootstrap(
                    operationId,
                    DiagnosticsAction.BOOTSTRAP_PROOF_REQUEST_ANSWER,
                    DiagnosticsOutcome.DENIED,
                    DiagnosticsReasonClass.SECURITY,
                    DiagnosticsReasonCode.BOOTSTRAP_ORIGIN_NOT_VERIFIED,
                    "This server already belongs to a trusted Nexori network and will only answer bootstrap reruns from a server that is already verified in that network.",
                    diag -> diag
                        .sessionId(payload.challenge().sessionId())
                        .remoteConnectionAddress(referralSource.host + ":" + referralSource.port)
                );
                bounceError(event, payload, "This server already belongs to a trusted Nexori network and will only answer bootstrap reruns from a server that is already verified in that network.");
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
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_PROOF_REQUEST_ANSWER,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.PROOF_REQUEST_ANSWERED,
                "Answered a Nexori bootstrap proof request.",
                diag -> diag
                    .sessionId(payload.challenge().sessionId())
                    .remoteConnectionAddress(referralSource.host + ":" + referralSource.port)
                    .payloadType(payload.messageType().name())
            );
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori proof request.");
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_PROOF_REQUEST_ANSWER,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.IO,
                DiagnosticsReasonCode.BOOTSTRAP_PEER_ERROR,
                "Failed to sign the Nexori proof challenge.",
                diag -> diag
                    .sessionId(payload.challenge() == null ? "" : payload.challenge().sessionId())
                    .remoteConnectionAddress(referralSource.host + ":" + referralSource.port)
            );
            bounceError(event, payload, "Failed to sign the Nexori proof challenge.");
        }
    }

    private void handleProofResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        if (currentRun == null || currentRun.phase() != BootstrapPhase.COLLECT_PROOFS) {
            return;
        }
        String operationId = "bootstrap:" + currentRun.sessionId();

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
                recordBootstrap(
                    operationId,
                    DiagnosticsAction.BOOTSTRAP_PROOF_RESPONSE_VERIFY,
                    DiagnosticsOutcome.FAILED,
                    DiagnosticsReasonClass.SECURITY,
                    DiagnosticsReasonCode.PROOF_SIGNATURE_INVALID,
                    "Nexori bootstrap failed because one server returned an invalid signature.",
                    diag -> diag
                        .sessionId(currentRun.sessionId())
                        .remoteServerId(payload.responderServerId())
                );
                failRun(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed because one server returned an invalid signature.");
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
                    Instant.now().toEpochMilli()
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
            int verifiedPeerCount = updatedRun.verifiedPeers().size();
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_PROOF_RESPONSE_VERIFY,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.PROOF_SIGNATURE_VERIFIED,
                "Verified a Nexori bootstrap proof response.",
                diag -> diag
                    .sessionId(currentRun.sessionId())
                    .remoteServerId(payload.responderServerId())
                    .remoteConnectionAddress(connectionAddress)
                    .requestId(currentRun.sessionId())
                    .addPreview("verifiedPeerCount", Integer.toString(verifiedPeerCount))
            );

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
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_PROOF_RESPONSE_VERIFY,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.IO,
                DiagnosticsReasonCode.PROOF_RESPONSE_PROCESSING_FAILED,
                "Nexori bootstrap failed while processing a proof response: " + exception.getMessage(),
                diag -> diag.sessionId(currentRun.sessionId())
            );
            failRun(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed while processing a proof response: " + exception.getMessage());
        }
    }

    private void beginBundleInstallation(@Nonnull BootstrapRun run, @Nonnull PlayerSetupConnectEvent event) throws IOException {
        String localConnectionAddress = resolveLocalConnectionAddress(run);
        String operationId = "bootstrap:" + run.sessionId();
        if (localConnectionAddress.isBlank()) {
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_BUNDLE_BUILD,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.MISCONFIG,
                DiagnosticsReasonCode.LOCAL_CONNECTION_ADDRESS_MISSING,
                "Nexori verified the remote bootstrap peers, but it could not install the first trust bundle because this server was missing from Bootstrap Peers.",
                diag -> diag.sessionId(run.sessionId())
            );
            failRun(
                run.startedByPlayerUuid(),
                "Nexori verified the remote bootstrap peers, but it could not install the first trust bundle because this server was missing from Bootstrap Peers. Add the current server to Bootstrap Peers, keep every server that should belong to the secure network in that list, and run Initial Setup again."
            );
            return;
        }

        long nextBundleVersion = bootstrapStateStore.getCurrentState().bundleVersion() + 1;
        TrustBundle bundle = trustBundleStore.saveVerifiedMembers(
            localIdentity,
            localConnectionAddress,
            run.verifiedPeers(),
            nextBundleVersion
        );
        BootstrapMigrationPlan migrationPlan = configuredPeerMigrationService.buildPlan();
        recordBootstrap(
            operationId,
            DiagnosticsAction.BOOTSTRAP_BUNDLE_BUILD,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BUNDLE_BUILT,
            "Built the next active Nexori trust bundle.",
            diag -> diag
                .sessionId(run.sessionId())
                .bundleVersion(bundle.bundleVersion())
                .bundleHash(bundle.bundleHash())
                .addPreview("memberCount", Integer.toString(bundle.members().size()))
                .addPreview("migrationReplacementCount", Integer.toString(migrationPlan.replacements().size()))
        );
        persistLocalConnectionAddressFromBundle(bundle);
        applyMigrationPlan(operationId, migrationPlan);
        List<ConfiguredPeer> installPeers = peersForInstallation(bundle);

        if (installPeers.isEmpty()) {
            finishRun(run.withMigrationPlan(migrationPlan), bundle);
            return;
        }

        ConfiguredPeer nextPeer = installPeers.getFirst();
        BootstrapChallenge nextChallenge = createChallenge(run.sessionId(), nextPeer.connectionAddress(), run.expiresAtEpochMillis());
        BootstrapRun installRun = run.withMigrationPlan(migrationPlan).withPhase(BootstrapPhase.INSTALL_BUNDLE, 0, installPeers, nextChallenge);
        bootstrapRunStore.save(installRun);
        event.referToServer(
            nextPeer.host(),
            nextPeer.port(),
            payloadCodec.encode(BootstrapReferralPayload.installBundle(
                run.startedByPlayerUuid(),
                nextChallenge,
                bundle,
                migrationPlan,
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
        String operationId = payload.challenge() == null ? diagnosticsService.newOperationId("bootstrap") : "bootstrap:" + payload.challenge().sessionId();

        try {
            if (payload.challenge() == null || payload.challenge().isExpired(Instant.now())) {
                recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST, DiagnosticsOutcome.EXPIRED, DiagnosticsReasonClass.STALE, DiagnosticsReasonCode.BOOTSTRAP_PEER_ERROR, "The Nexori bundle install request expired.", diag -> diag.sessionId(payload.challenge() == null ? "" : payload.challenge().sessionId()));
                bounceError(event, payload, "The Nexori bundle install request expired.");
                return;
            }
            if (!isVerifiedBootstrapOrigin(payload.challenge(), referralSource)) {
                recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST, DiagnosticsOutcome.DENIED, DiagnosticsReasonClass.SECURITY, DiagnosticsReasonCode.BOOTSTRAP_ORIGIN_NOT_VERIFIED, "This server already belongs to a trusted Nexori network and will only install rerun bundles from a server that is already verified in that network.", diag -> diag.sessionId(payload.challenge().sessionId()).remoteConnectionAddress(referralSource.host + ":" + referralSource.port));
                bounceError(event, payload, "This server already belongs to a trusted Nexori network and will only install rerun bundles from a server that is already verified in that network.");
                return;
            }
            if (payload.trustBundle() == null) {
                recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST, DiagnosticsOutcome.FAILED, DiagnosticsReasonClass.VALIDATION, DiagnosticsReasonCode.BUNDLE_INSTALL_REQUEST_DENIED, "The Nexori bundle install request was missing bundle data.", diag -> diag.sessionId(payload.challenge().sessionId()));
                bounceError(event, payload, "The Nexori bundle install request was missing bundle data.");
                return;
            }
            if (!bundleContainsLocalIdentity(payload.trustBundle())) {
                recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST, DiagnosticsOutcome.DENIED, DiagnosticsReasonClass.SECURITY, DiagnosticsReasonCode.BUNDLE_INSTALL_REQUEST_DENIED, "The Nexori bundle does not contain this server identity.", diag -> diag.sessionId(payload.challenge().sessionId()));
                bounceError(event, payload, "The Nexori bundle does not contain this server identity.");
                return;
            }

            TrustBundle installedBundle = trustBundleStore.installBundle(payload.trustBundle());
            persistLocalConnectionAddressFromBundle(installedBundle);
            applyMigrationPlan(operationId, payload.migrationPlan());
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
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.BUNDLE_INSTALLED,
                "Installed the active Nexori trust bundle on this server.",
                diag -> diag
                    .sessionId(payload.challenge().sessionId())
                    .bundleVersion(installedBundle.bundleVersion())
                    .bundleHash(installedBundle.bundleHash())
            );
        } catch (IOException | IllegalStateException | IllegalArgumentException exception) {
            logger.atWarning().withCause(exception).log("Failed to install Nexori trust bundle.");
            recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST, DiagnosticsOutcome.FAILED, DiagnosticsReasonClass.IO, DiagnosticsReasonCode.BUNDLE_INSTALL_REQUEST_DENIED, "Failed to install the Nexori trust bundle: " + exception.getMessage(), diag -> diag.sessionId(payload.challenge() == null ? "" : payload.challenge().sessionId()));
            bounceError(event, payload, "Failed to install the Nexori trust bundle: " + exception.getMessage());
        }
    }

    private void handleBundleInstallAck(@Nonnull PlayerSetupConnectEvent event, @Nonnull BootstrapReferralPayload payload) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        if (currentRun == null || currentRun.phase() != BootstrapPhase.INSTALL_BUNDLE) {
            return;
        }
        String operationId = "bootstrap:" + currentRun.sessionId();

        try {
            if (!matchesCurrentRun(currentRun, payload)) {
                queueStatus(currentRun.startedByPlayerUuid(), "Ignored a Nexori install acknowledgement that did not match the active session.");
                return;
            }

            TrustBundle currentBundle = trustBundleStore.getCurrentBundle();
            if (currentBundle.bundleHash().isBlank() || !currentBundle.bundleHash().equals(payload.acknowledgedBundleHash())) {
                recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_ACK, DiagnosticsOutcome.FAILED, DiagnosticsReasonClass.VALIDATION, DiagnosticsReasonCode.BUNDLE_ACK_HASH_MISMATCH, "Nexori bootstrap failed because one server acknowledged the wrong trust bundle.", diag -> diag.sessionId(currentRun.sessionId()).bundleHash(payload.acknowledgedBundleHash()));
                failRun(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed because one server acknowledged the wrong trust bundle.");
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
                    currentRun.migrationPlan(),
                    nextPeerIndex,
                    currentRun.peers().size()
                ))
            );
            recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_ACK, DiagnosticsOutcome.SUCCEEDED, DiagnosticsReasonClass.NORMAL, DiagnosticsReasonCode.BUNDLE_INSTALLED, "Received a Nexori bundle installation acknowledgement.", diag -> diag.sessionId(currentRun.sessionId()).remoteServerId(payload.responderServerId()).bundleHash(payload.acknowledgedBundleHash()));
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to continue Nexori bundle installation.");
            recordBootstrap(operationId, DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_ACK, DiagnosticsOutcome.FAILED, DiagnosticsReasonClass.IO, DiagnosticsReasonCode.BUNDLE_DISTRIBUTION_FAILED, "Nexori bootstrap failed while distributing the trust bundle: " + exception.getMessage(), diag -> diag.sessionId(currentRun.sessionId()));
            failRun(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed while distributing the trust bundle: " + exception.getMessage());
        }
    }

    private void handleError(@Nonnull BootstrapReferralPayload payload) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        if (currentRun == null || payload.challenge() == null || !currentRun.sessionId().equals(payload.challenge().sessionId())) {
            return;
        }

        failRun(currentRun.startedByPlayerUuid(), "Nexori bootstrap failed on peer "
            + (currentRun.currentPeer() == null ? "unknown" : currentRun.currentPeer().connectionAddress())
            + ": " + payload.errorMessage());
    }

    private void finishRun(@Nonnull BootstrapRun run, @Nonnull TrustBundle bundle) {
        bootstrapStateStore.markBundleInstalled(bundle.bundleVersion(), bundle.bundleHash());
        bootstrapRunStore.clear();
        try {
            configuredPeerMigrationService.clear();
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to clear pending Nexori peer migration entries after bootstrap.");
        }
        recordBootstrap(
            "bootstrap:" + run.sessionId(),
            DiagnosticsAction.BOOTSTRAP_RUN_FINISH,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BOOTSTRAP_RUN_COMPLETED,
            "Completed Nexori bootstrap and installed the active trust bundle.",
            diag -> diag
                .sessionId(run.sessionId())
                .bundleVersion(bundle.bundleVersion())
                .bundleHash(bundle.bundleHash())
                .addPreview("verifiedPeerCount", Integer.toString(run.verifiedPeers().size()))
        );
        queueStatus(run.startedByPlayerUuid(), "Nexori bootstrap verified "
            + run.verifiedPeers().size()
            + " remote peer(s) and installed bundle v"
            + bundle.bundleVersion()
            + " across "
            + run.peers().size()
            + " server(s). Restart the servers so every runtime object rebuilds with the updated names and addresses.");
        logger.atInfo().log("Completed Nexori bootstrap session " + run.sessionId() + " with bundle " + bundle.bundleHash() + ".");
    }

    private void failRun(@Nonnull String startedByPlayerUuid, @Nonnull String message) {
        BootstrapRun currentRun = bootstrapRunStore.getCurrentRun();
        String operationId = currentRun == null ? diagnosticsService.newOperationId("bootstrap") : "bootstrap:" + currentRun.sessionId();
        recordBootstrap(
            operationId,
            DiagnosticsAction.BOOTSTRAP_RUN_FAIL,
            DiagnosticsOutcome.FAILED,
            DiagnosticsReasonClass.UNKNOWN,
            DiagnosticsReasonCode.BOOTSTRAP_PEER_ERROR,
            message,
            diag -> {
                if (currentRun != null) {
                    diag.sessionId(currentRun.sessionId());
                    if (currentRun.currentPeer() != null) {
                        diag.remoteConnectionAddress(currentRun.currentPeer().connectionAddress());
                    }
                }
            }
        );
        bootstrapRunStore.clear();
        bootstrapStateStore.recordFailure(message);
        queueStatus(startedByPlayerUuid, message);
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

    private boolean isVerifiedBootstrapOrigin(@Nonnull BootstrapChallenge challenge, @Nonnull HostAddress referralSource) {
        TrustBundle currentBundle = trustBundleStore.getCurrentBundle();
        if (currentBundle.bundleVersion() <= 0 || currentBundle.bundleHash().isBlank() || currentBundle.members().isEmpty()) {
            return true;
        }

        String referralConnectionAddress;
        try {
            referralConnectionAddress = ConfiguredPeer.parse(referralSource.host + ":" + referralSource.port).connectionAddress();
        } catch (IllegalArgumentException exception) {
            return false;
        }

        for (BundleMember member : currentBundle.members()) {
            if (!challenge.originServerId().equals(member.serverId())) {
                continue;
            }
            return member.connectionAddress() != null
                && !member.connectionAddress().isBlank()
                && referralConnectionAddress.equalsIgnoreCase(member.connectionAddress());
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

    private void persistLocalConnectionAddressFromBundle(@Nonnull TrustBundle bundle) {
        for (BundleMember member : bundle.members()) {
            if (!localIdentity.serverId().toString().equals(member.serverId())) {
                continue;
            }

            String connectionAddress = member.connectionAddress() == null ? "" : member.connectionAddress().trim();
            if (connectionAddress.isBlank()) {
                throw new IllegalArgumentException("The Nexori bundle does not include a connection address for this server.");
            }

            try {
                localConnectionAddressService.save(connectionAddress);
                logger.atInfo().log("Persisted the local Nexori connection address from the active trust bundle as " + connectionAddress + ".");
                return;
            } catch (IOException | IllegalArgumentException exception) {
                throw new IllegalStateException("Failed to persist the local Nexori connection address from the active trust bundle.", exception);
            }
        }

        throw new IllegalArgumentException("The Nexori bundle does not contain this server identity.");
    }

    @Nonnull
    private List<ConfiguredPeer> peersForInstallation(@Nonnull TrustBundle bundle) {
        List<ConfiguredPeer> peers = new ArrayList<>();
        for (BundleMember member : bundle.members()) {
            if (localIdentity.serverId().toString().equals(member.serverId())
                || member.connectionAddress() == null
                || member.connectionAddress().isBlank()) {
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
            UUID playerUuid = UUID.fromString(startedByPlayerUuid);
            if (pendingMenuResumeRequests.remove(playerUuid) != null) {
                pendingMenuResumeStatuses.put(playerUuid, message);
                return;
            }
            pendingMessages.put(playerUuid, message);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void clearPendingUiState(@Nonnull UUID playerUuid) {
        pendingMessages.remove(playerUuid);
        pendingMenuResumeRequests.remove(playerUuid);
        pendingMenuResumeStatuses.remove(playerUuid);
    }

    private void applyMigrationPlan(@Nonnull String operationId, BootstrapMigrationPlan migrationPlan) {
        BootstrapMigrationPlan plan = migrationPlan == null ? BootstrapMigrationPlan.empty() : migrationPlan;
        if (plan.isEmpty()) {
            return;
        }

        try {
            BootstrapPersistenceMigrationService.MigrationReport report = persistenceMigrationService.apply(plan);
            recordBootstrap(
                operationId,
                DiagnosticsAction.BOOTSTRAP_BUNDLE_INSTALL_REQUEST,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.BUNDLE_INSTALLED,
                "Applied Nexori persistence migration after trust bundle update.",
                diag -> diag
                    .addPreview("replacementCount", Integer.toString(plan.replacements().size()))
                    .addPreview("scannedFiles", Integer.toString(report.scannedFiles()))
                    .addPreview("changedFiles", Integer.toString(report.changedFiles()))
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to migrate Nexori persisted connection addresses after bootstrap.", exception);
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

    private void recordBootstrap(
        @Nonnull String operationId,
        @Nonnull String action,
        @Nonnull DiagnosticsOutcome outcome,
        @Nonnull DiagnosticsReasonClass reasonClass,
        @Nonnull String reasonCode,
        @Nonnull String message,
        Consumer<DiagnosticsEvent.Builder> customizer
    ) {
        diagnosticsService.record(
            DiagnosticsCategory.BOOTSTRAP,
            action,
            outcome,
            reasonClass,
            reasonCode,
            message,
            operationId,
            customizer
        );
    }
}
