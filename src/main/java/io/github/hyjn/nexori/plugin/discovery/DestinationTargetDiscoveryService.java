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
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DestinationTargetDiscoveryService {

    public static final String REQUEST_PAYLOAD_TYPE = "destination-targets.discover.request";
    public static final String RESPONSE_PAYLOAD_TYPE = "destination-targets.discover.response";

    private final HytaleLogger logger;
    private final TrustBundleStore trustBundleStore;
    private final DestinationTargetService destinationTargetService;
    private final DiscoveredDestinationTargetCacheService cacheService;
    private final SecureReferralService secureReferralService;
    private final Map<String, PendingDiscoveryRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingDiscoveryReturn> pendingReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler requestHandler = new RequestHandler();
    private final SecureReferralHandler responseHandler = new ResponseHandler();

    public DestinationTargetDiscoveryService(
        @Nonnull HytaleLogger logger,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull DiscoveredDestinationTargetCacheService cacheService,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.trustBundleStore = trustBundleStore;
        this.destinationTargetService = destinationTargetService;
        this.cacheService = cacheService;
        this.secureReferralService = secureReferralService;
    }

    @Nonnull
    public SecureReferralHandler requestHandler() {
        return requestHandler;
    }

    @Nonnull
    public SecureReferralHandler responseHandler() {
        return responseHandler;
    }

    @Nonnull
    public DiscoveredDestinationTargetCacheService cacheService() {
        return cacheService;
    }

    public void discover(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform
    ) throws IOException, GeneralSecurityException {
        discover(playerRef, destination, originWorldName, originTransform, null);
    }

    public void discover(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        if (!isTrustedDestination(destination)) {
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
    }

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
        event.getPlayer().sendMessage(Message.raw(pendingReturn.message()));
        if (pendingReturn.resumeAction() != null) {
            pendingReturn.resumeAction().reopen(
                event.getPlayerRef(),
                event.getPlayerRef().getStore(),
                playerRef,
                event.getPlayer()
            );
        }
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

        List<DiscoveredDestinationTargetSummary> targets = destinationTargetService.list().stream()
            .map(DiscoveredDestinationTargetSummary::from)
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
            pendingReturns.put(event.getUuid(), new PendingDiscoveryReturn(
                pendingRequest.originWorldName(),
                pendingRequest.originTransform(),
                "Discovered " + saved.targets().size() + " Nexori destination target(s) from " + saved.connectionAddress() + ".",
                pendingRequest.resumeAction()
            ));
            logger.atInfo().log("Stored Nexori discovery response " + payload.requestId()
                + " for " + saved.connectionAddress()
                + " with " + saved.targets().size() + " target(s).");
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist discovered Nexori destination targets.");
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
