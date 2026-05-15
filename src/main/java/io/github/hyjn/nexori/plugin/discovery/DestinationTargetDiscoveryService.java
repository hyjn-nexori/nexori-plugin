package io.github.hyjn.nexori.plugin.discovery;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.HostAddress;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.discovery.logic.DestinationTargetSummaryBuilder;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers destination targets from other trusted Nexori servers and returns the operator to the
 * origin server after the discovery response is stored locally.
 */
public final class DestinationTargetDiscoveryService {

    public static final String REQUEST_PAYLOAD_TYPE = "destination-targets.discover.request";
    public static final String RESPONSE_PAYLOAD_TYPE = "destination-targets.discover.response";

    private final HytaleLogger logger;
    private final TrustBundleStore trustBundleStore;
    private final DestinationTargetService destinationTargetService;
    private final PortalInstanceService portalInstanceService;
    private final DiscoveredDestinationTargetCacheService cacheService;
    private final SecureReferralService secureReferralService;
    private final DiagnosticsService diagnosticsService;
    private final Map<String, PendingDiscoveryRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDiscoveryReturn> pendingReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler requestHandler = new RequestHandler();
    private final SecureReferralHandler responseHandler = new ResponseHandler();

    /**
     * Creates the trusted discovery service for remote destination targets.
     */
    public DestinationTargetDiscoveryService(
        @Nonnull HytaleLogger logger,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull DiscoveredDestinationTargetCacheService cacheService,
        @Nonnull SecureReferralService secureReferralService,
        @Nonnull DiagnosticsService diagnosticsService
    ) {
        this.logger = logger;
        this.trustBundleStore = trustBundleStore;
        this.destinationTargetService = destinationTargetService;
        this.portalInstanceService = portalInstanceService;
        this.cacheService = cacheService;
        this.secureReferralService = secureReferralService;
        this.diagnosticsService = diagnosticsService;
    }

    /**
     * Returns the secure referral handler that answers discovery requests on the destination server.
     */
    @Nonnull
    public SecureReferralHandler requestHandler() {
        return requestHandler;
    }

    /**
     * Returns the secure referral handler that stores discovery responses on the origin server.
     */
    @Nonnull
    public SecureReferralHandler responseHandler() {
        return responseHandler;
    }

    /**
     * Exposes the local cache of discovered destination targets.
     */
    @Nonnull
    public DiscoveredDestinationTargetCacheService cacheService() {
        return cacheService;
    }

    /**
     * Starts destination target discovery from a live player ref.
     */
    public void discover(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform
    ) throws IOException, GeneralSecurityException {
        discover(playerRef, destination, originWorldName, originTransform, null);
    }

    /**
     * Starts destination target discovery from a live player ref and preserves a UI resume action.
     */
    public void discover(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        if (!isTrustedDestination(destination)) {
            String operationId = diagnosticsService.newOperationId("discovery");
            diagnosticsService.record(
                DiagnosticsCategory.DISCOVERY,
                DiagnosticsAction.DISCOVERY_REQUEST_SEND,
                DiagnosticsOutcome.DENIED,
                DiagnosticsReasonClass.SECURITY,
                DiagnosticsReasonCode.DISCOVERY_DESTINATION_NOT_TRUSTED,
                "The destination is not in the current Nexori trust bundle.",
                operationId,
                event -> event.remoteConnectionAddress(destination.connectionAddress())
            );
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }

        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingDiscoveryRequest(
            requestId,
            playerRef.getUuid(),
            destination.connectionAddress(),
            originWorldName,
            originTransform.clone(),
            Instant.now().plusSeconds(30).toEpochMilli(),
            resumeAction
        ));

        secureReferralService.referPlayer(
            playerRef,
            destination.host(),
            destination.port(),
            REQUEST_PAYLOAD_TYPE,
            new DestinationTargetDiscoveryRequestPayload(requestId),
            Duration.ofSeconds(30)
        );
        diagnosticsService.record(
            DiagnosticsCategory.DISCOVERY,
            DiagnosticsAction.DISCOVERY_REQUEST_SEND,
            DiagnosticsOutcome.STARTED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.DISCOVERY_REQUEST_SENT,
            "Started destination target discovery against a trusted server.",
            requestId,
            event -> event
                .requestId(requestId)
                .playerUuid(playerRef.getUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .remoteConnectionAddress(destination.connectionAddress())
        );
    }

    /**
     * Starts destination target discovery while the operator is still inside setup connect.
     */
    public void discover(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        if (!isTrustedDestination(destination)) {
            String operationId = diagnosticsService.newOperationId("discovery");
            diagnosticsService.record(
                DiagnosticsCategory.DISCOVERY,
                DiagnosticsAction.DISCOVERY_REQUEST_SEND,
                DiagnosticsOutcome.DENIED,
                DiagnosticsReasonClass.SECURITY,
                DiagnosticsReasonCode.DISCOVERY_DESTINATION_NOT_TRUSTED,
                "The destination is not in the current Nexori trust bundle.",
                operationId,
                diag -> diag.remoteConnectionAddress(destination.connectionAddress())
            );
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }

        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingDiscoveryRequest(
            requestId,
            event.getUuid(),
            destination.connectionAddress(),
            originWorldName,
            originTransform.clone(),
            Instant.now().plusSeconds(30).toEpochMilli(),
            resumeAction
        ));

        byte[] encoded = secureReferralService.createPayload(
            event.getUuid(),
            event.getUsername(),
            REQUEST_PAYLOAD_TYPE,
            new DestinationTargetDiscoveryRequestPayload(requestId),
            Duration.ofSeconds(30)
        );
        event.referToServer(destination.host(), destination.port(), encoded);
        diagnosticsService.record(
            DiagnosticsCategory.DISCOVERY,
            DiagnosticsAction.DISCOVERY_REQUEST_SEND,
            DiagnosticsOutcome.STARTED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.DISCOVERY_REQUEST_SENT,
            "Started destination target discovery against a trusted server.",
            requestId,
            diag -> diag
                .requestId(requestId)
                .playerUuid(event.getUuid().toString())
                .playerNameClaimed(event.getUsername())
                .remoteConnectionAddress(destination.connectionAddress())
        );
    }

    /**
     * Teleports the operator back to the origin location and optionally resumes the UI after discovery finishes.
     */
    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            com.hypixel.hytale.server.core.universe.Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingDiscoveryReturn pendingReturn = pendingReturns.remove(playerRef.getUuid());
        if (pendingReturn == null) {
            return;
        }

        World world = com.hypixel.hytale.server.core.universe.Universe.get().getWorld(pendingReturn.originWorldName());
        Teleport teleport = world == null
            ? Teleport.createForPlayer(pendingReturn.originTransform().clone())
            : Teleport.createForPlayer(world, pendingReturn.originTransform().clone());
        event.getPlayerRef().getStore().addComponent(event.getPlayerRef(), Teleport.getComponentType(), teleport);
        if (pendingReturn.resumeAction() != null) {
            pendingReturn.resumeAction().reopenWithStatus(
                event.getPlayerRef(),
                event.getPlayerRef().getStore(),
                playerRef,
                event.getPlayer(),
                pendingReturn.message()
            );
            return;
        }
        event.getPlayer().sendMessage(Message.raw(pendingReturn.message()));
    }

    private void handleRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DestinationTargetDiscoveryRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DestinationTargetDiscoveryRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }

        Map<String, String> portalIdsByTargetId = new ConcurrentHashMap<>();
        for (PortalInstanceDefinition portal : portalInstanceService.list()) {
            if (!portal.autoDestinationTargetId().isBlank()) {
                portalIdsByTargetId.put(portal.autoDestinationTargetId(), portal.portalId());
            }
        }

        // Portal-owned targets keep their portal id in the discovered cache so the UI can preserve portal-specific actions.
        List<DiscoveredDestinationTargetSummary> targets = destinationTargetService.list().stream()
            .map(target -> DestinationTargetSummaryBuilder.summarize(target, portalIdsByTargetId))
            .toList();

        try {
            byte[] responsePayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                RESPONSE_PAYLOAD_TYPE,
                new DestinationTargetDiscoveryResponsePayload(payload.requestId(), targets),
                Duration.ofSeconds(30)
            );
            event.referToServer(referralSource.host, referralSource.port, responsePayload);
            logger.atInfo().log("Answered Nexori destination target discovery request " + payload.requestId()
                + " with " + targets.size() + " target(s).");
            diagnosticsService.record(
                DiagnosticsCategory.DISCOVERY,
                DiagnosticsAction.DISCOVERY_REQUEST_ANSWER,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.DISCOVERY_REQUEST_ANSWERED,
                "Answered a destination target discovery request.",
                payload.requestId(),
                diag -> diag
                    .requestId(payload.requestId())
                    .remoteConnectionAddress(referralSource.host + ":" + referralSource.port)
                    .addPreview("targetCount", Integer.toString(targets.size()))
            );
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori destination target discovery request.");
        }
    }

    private void handleResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DestinationTargetDiscoveryResponsePayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DestinationTargetDiscoveryResponsePayload.class
        );

        PendingDiscoveryRequest pendingRequest = pendingRequests.remove(payload.requestId());
        if (pendingRequest == null || pendingRequest.isExpired() || !pendingRequest.playerUuid().equals(event.getUuid())) {
            return;
        }

        try {
            DiscoveredDestinationTargetSet saved = cacheService.saveDiscovery(
                pendingRequest.destinationConnectionAddress(),
                referral.issuer().serverId(),
                payload.targets() == null ? List.of() : payload.targets()
            );
            if (pendingRequest.resumeAction() != null) {
                try {
                    if (pendingRequest.resumeAction().continueDuringSetup(event)) {
                        return;
                    }
                } catch (IOException | GeneralSecurityException exception) {
                    logger.atWarning().withCause(exception).log("Received Nexori discovery response, but continuing the chained discovery during setup failed.");
                    pendingReturns.put(event.getUuid(), new PendingDiscoveryReturn(
                        pendingRequest.originWorldName(),
                        pendingRequest.originTransform(),
                        "The discovery response arrived, but continuing the sync flow failed: " + exception.getMessage(),
                        pendingRequest.resumeAction()
                    ));
                    return;
                }
            }
            pendingReturns.put(event.getUuid(), new PendingDiscoveryReturn(
                pendingRequest.originWorldName(),
                pendingRequest.originTransform(),
                "Discovered " + saved.targets().size() + " Nexori destination target(s) from " + saved.connectionAddress() + ".",
                pendingRequest.resumeAction()
            ));
            logger.atInfo().log("Stored Nexori discovery response " + payload.requestId()
                + " for " + saved.connectionAddress()
                + " with " + saved.targets().size() + " target(s).");
            diagnosticsService.record(
                DiagnosticsCategory.DISCOVERY,
                DiagnosticsAction.DISCOVERY_RESPONSE_STORE,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.DISCOVERY_RESPONSE_STORED,
                "Stored discovered destination targets from a trusted server.",
                payload.requestId(),
                diag -> diag
                    .requestId(payload.requestId())
                    .remoteServerId(referral.issuer().serverId())
                    .remoteConnectionAddress(saved.connectionAddress())
                    .addPreview("targetCount", Integer.toString(saved.targets().size()))
            );
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist discovered Nexori destination targets.");
            diagnosticsService.record(
                DiagnosticsCategory.DISCOVERY,
                DiagnosticsAction.DISCOVERY_RESPONSE_STORE,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.IO,
                DiagnosticsReasonCode.DISCOVERY_RESPONSE_SAVE_FAILED,
                "The discovery response was received, but saving the discovered targets failed: " + exception.getMessage(),
                payload.requestId(),
                diag -> diag
                    .requestId(payload.requestId())
                    .remoteServerId(referral.issuer().serverId())
                    .remoteConnectionAddress(pendingRequest.destinationConnectionAddress())
            );
            pendingReturns.put(event.getUuid(), new PendingDiscoveryReturn(
                pendingRequest.originWorldName(),
                pendingRequest.originTransform(),
                "The Nexori discovery response was received, but saving the discovered targets failed: " + exception.getMessage(),
                pendingRequest.resumeAction()
            ));
        }
    }

    private boolean isTrustedDestination(@Nonnull ConfiguredPeer destination) {
        TrustBundle bundle = trustBundleStore.getCurrentBundle();
        return bundle.members().stream().anyMatch(member -> destination.connectionAddress().equals(member.connectionAddress()));
    }

    private record PendingDiscoveryRequest(
        String requestId,
        UUID playerUuid,
        String destinationConnectionAddress,
        String originWorldName,
        Transform originTransform,
        long expiresAtEpochMillis,
        UiResumeAction resumeAction
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtEpochMillis;
        }
    }

    private record PendingDiscoveryReturn(
        String originWorldName,
        Transform originTransform,
        String message,
        UiResumeAction resumeAction
    ) {
    }

    private final class RequestHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return REQUEST_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleRequest(event, referral);
        }
    }

    private final class ResponseHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return RESPONSE_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleResponse(event, referral);
        }
    }
}
