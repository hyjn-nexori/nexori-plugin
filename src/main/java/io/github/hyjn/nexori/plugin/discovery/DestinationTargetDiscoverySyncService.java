package io.github.hyjn.nexori.plugin.discovery;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.HostAddress;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DestinationTargetDiscoverySyncService {

    public static final String APPLY_REQUEST_PAYLOAD_TYPE = "destination-targets.sync.apply.request";
    public static final String RESPONSE_PAYLOAD_TYPE = "destination-targets.sync.apply.response";

    private final HytaleLogger logger;
    private final TrustBundleStore trustBundleStore;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final ServerIdentity localIdentity;
    private final DestinationTargetService destinationTargetService;
    private final PortalInstanceService portalInstanceService;
    private final DiscoveredDestinationTargetCacheService cacheService;
    private final SecureReferralService secureReferralService;
    private final Map<String, PendingApplyRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingApplyReturn> pendingReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler applyRequestHandler = new ApplyRequestHandler();
    private final SecureReferralHandler responseHandler = new ResponseHandler();

    public DestinationTargetDiscoverySyncService(
        @Nonnull HytaleLogger logger,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull DiscoveredDestinationTargetCacheService cacheService,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.trustBundleStore = trustBundleStore;
        this.localConnectionAddressService = localConnectionAddressService;
        this.localIdentity = localIdentity;
        this.destinationTargetService = destinationTargetService;
        this.portalInstanceService = portalInstanceService;
        this.cacheService = cacheService;
        this.secureReferralService = secureReferralService;
    }

    @Nonnull
    public SecureReferralHandler applyRequestHandler() {
        return applyRequestHandler;
    }

    @Nonnull
    public SecureReferralHandler responseHandler() {
        return responseHandler;
    }

    @Nonnull
    public List<DiscoveredDestinationTargetSet> currentNetworkSnapshot() {
        List<DiscoveredDestinationTargetSet> snapshot = new ArrayList<>();
        String localConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        Map<String, String> trustedServerIdsByAddress = new LinkedHashMap<>();
        for (BundleMember member : trustBundleStore.getCurrentBundle().members()) {
            if (member.connectionAddress() == null || member.connectionAddress().isBlank()) {
                continue;
            }
            trustedServerIdsByAddress.put(member.connectionAddress().trim().toLowerCase(), member.serverId());
        }
        if (!localConnectionAddress.isBlank()) {
            snapshot.add(new DiscoveredDestinationTargetSet(
                localConnectionAddress,
                localIdentity.serverId().toString(),
                Instant.now().toEpochMilli(),
                localTargetSummaries()
            ).normalized());
        }
        for (DiscoveredDestinationTargetSet discovery : cacheService.list()) {
            if (discovery == null) {
                continue;
            }
            if (!localConnectionAddress.isBlank() && localConnectionAddress.equalsIgnoreCase(discovery.connectionAddress())) {
                continue;
            }
            if (!trustedServerIdsByAddress.containsKey(discovery.connectionAddress().trim().toLowerCase())) {
                continue;
            }
            snapshot.add(discovery.normalized());
        }
        snapshot.sort(Comparator.comparing(DiscoveredDestinationTargetSet::connectionAddress));
        return snapshot;
    }

    public void apply(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        @Nonnull List<DiscoveredDestinationTargetSet> discoveries,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        String requestId = UUID.randomUUID().toString();
        if (!isTrustedDestination(destination)) {
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }
        pendingRequests.put(requestId, new PendingApplyRequest(
            requestId,
            playerRef.getUuid(),
            destination.connectionAddress(),
            originWorldName,
            originTransform.clone(),
            System.currentTimeMillis() + Duration.ofSeconds(60).toMillis(),
            resumeAction
        ));
        secureReferralService.referPlayer(
            playerRef,
            destination.host(),
            destination.port(),
            APPLY_REQUEST_PAYLOAD_TYPE,
            new DestinationTargetSyncApplyRequestPayload(requestId, discoveries),
            Duration.ofSeconds(60)
        );
    }

    public void apply(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        @Nonnull List<DiscoveredDestinationTargetSet> discoveries,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        String requestId = UUID.randomUUID().toString();
        if (!isTrustedDestination(destination)) {
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }
        pendingRequests.put(requestId, new PendingApplyRequest(
            requestId,
            event.getUuid(),
            destination.connectionAddress(),
            originWorldName,
            originTransform.clone(),
            System.currentTimeMillis() + Duration.ofSeconds(60).toMillis(),
            resumeAction
        ));

        byte[] encoded = secureReferralService.createPayload(
            event.getUuid(),
            event.getUsername(),
            APPLY_REQUEST_PAYLOAD_TYPE,
            new DestinationTargetSyncApplyRequestPayload(requestId, discoveries),
            Duration.ofSeconds(60)
        );
        event.referToServer(destination.host(), destination.port(), encoded);
    }

    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingApplyReturn pendingReturn = pendingReturns.remove(playerRef.getUuid());
        if (pendingReturn == null) {
            return;
        }

        World world = Universe.get().getWorld(pendingReturn.originWorldName());
        Teleport teleport = world == null
            ? Teleport.createForPlayer(pendingReturn.originTransform().clone())
            : Teleport.createForPlayer(world, pendingReturn.originTransform().clone());
        Ref<EntityStore> storeRef = event.getPlayerRef();
        storeRef.getStore().addComponent(storeRef, Teleport.getComponentType(), teleport);
        if (pendingReturn.resumeAction() != null) {
            pendingReturn.resumeAction().reopenWithStatus(
                storeRef,
                storeRef.getStore(),
                playerRef,
                event.getPlayer(),
                pendingReturn.message(),
                pendingReturn.success()
            );
            return;
        }
        if (!pendingReturn.message().isBlank()) {
            event.getPlayer().sendMessage(Message.raw(pendingReturn.message()));
        }
    }

    private boolean isTrustedDestination(@Nonnull ConfiguredPeer destination) {
        TrustBundle bundle = trustBundleStore.getCurrentBundle();
        return bundle.members().stream()
            .anyMatch(member -> destination.connectionAddress().equals(member.connectionAddress()));
    }

    @Nonnull
    private List<DiscoveredDestinationTargetSummary> localTargetSummaries() {
        Map<String, String> portalIdsByTargetId = new LinkedHashMap<>();
        for (PortalInstanceDefinition portal : portalInstanceService.list()) {
            if (!portal.autoDestinationTargetId().isBlank()) {
                portalIdsByTargetId.put(portal.autoDestinationTargetId(), portal.portalId());
            }
        }
        return destinationTargetService.list().stream()
            .map(target -> summarizeTarget(target, portalIdsByTargetId))
            .toList();
    }

    @Nonnull
    private static DiscoveredDestinationTargetSummary summarizeTarget(
        @Nonnull DestinationTargetDefinition target,
        @Nonnull Map<String, String> portalIdsByTargetId
    ) {
        if (target.kind() != DestinationTargetKind.PORTAL) {
            return DiscoveredDestinationTargetSummary.from(target);
        }
        return DiscoveredDestinationTargetSummary.from(
            target,
            portalIdsByTargetId.getOrDefault(target.id(), "")
        );
    }

    private void handleApplyRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DestinationTargetSyncApplyRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DestinationTargetSyncApplyRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null || payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        boolean success = true;
        String message;
        try {
            String localConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
            String localServerId = localIdentity.serverId().toString();
            List<DiscoveredDestinationTargetSet> filtered = (payload.discoveries() == null ? List.<DiscoveredDestinationTargetSet>of() : payload.discoveries()).stream()
                .filter(discovery -> discovery != null && discovery.connectionAddress() != null && !discovery.connectionAddress().isBlank())
                .map(DiscoveredDestinationTargetSet::normalized)
                .filter(discovery -> (localConnectionAddress.isBlank() || !localConnectionAddress.equalsIgnoreCase(discovery.connectionAddress()))
                    && (discovery.remoteServerId() == null || !localServerId.equals(discovery.remoteServerId().trim())))
                .toList();
            cacheService.replaceAll(filtered);
            message = "Synchronized portal and target info on " + (localConnectionAddress.isBlank() ? referral.issuer().connectionAddress() : localConnectionAddress) + ".";
        } catch (IOException | IllegalArgumentException exception) {
            success = false;
            message = "Could not synchronize portal info on " + referral.issuer().connectionAddress() + ": " + exception.getMessage();
        }

        try {
            byte[] responsePayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                RESPONSE_PAYLOAD_TYPE,
                new DestinationTargetSyncApplyResponsePayload(payload.requestId(), success, message),
                Duration.ofSeconds(60)
            );
            event.referToServer(referralSource.host, referralSource.port, responsePayload);
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori destination target sync apply request.");
        }
    }

    private void handleResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DestinationTargetSyncApplyResponsePayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DestinationTargetSyncApplyResponsePayload.class
        );
        if (payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        PendingApplyRequest pendingRequest = pendingRequests.remove(payload.requestId());
        if (pendingRequest == null || pendingRequest.isExpired() || !pendingRequest.playerUuid().equals(event.getUuid())) {
            return;
        }

        String message = payload.message() == null || payload.message().isBlank()
            ? (payload.success() ? "Synchronized portal and target info on " + pendingRequest.destinationConnectionAddress() + "." : "The remote portal sync request failed.")
            : payload.message();

        if (payload.success() && pendingRequest.resumeAction() != null) {
            try {
                if (pendingRequest.resumeAction().continueDuringSetup(event)) {
                    return;
                }
            } catch (IOException | GeneralSecurityException exception) {
                pendingReturns.put(event.getUuid(), new PendingApplyReturn(
                    pendingRequest.originWorldName(),
                    pendingRequest.originTransform(),
                    "Could not continue the cross-server portal sync flow: " + exception.getMessage(),
                    pendingRequest.resumeAction(),
                    false
                ));
                return;
            }
        }

        pendingReturns.put(event.getUuid(), new PendingApplyReturn(
            pendingRequest.originWorldName(),
            pendingRequest.originTransform(),
            message,
            pendingRequest.resumeAction(),
            payload.success()
        ));
    }

    private record PendingApplyRequest(
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

    private record PendingApplyReturn(
        String originWorldName,
        Transform originTransform,
        String message,
        UiResumeAction resumeAction,
        boolean success
    ) {
    }

    private final class ApplyRequestHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return APPLY_REQUEST_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleApplyRequest(event, referral);
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
