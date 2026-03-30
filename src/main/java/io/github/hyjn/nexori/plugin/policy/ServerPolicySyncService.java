package io.github.hyjn.nexori.plugin.policy;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.HostAddress;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerPolicySyncService {

    public static final String FETCH_REQUEST_PAYLOAD_TYPE = "server-policy.fetch.request";
    public static final String APPLY_REQUEST_PAYLOAD_TYPE = "server-policy.apply.request";
    public static final String RESPONSE_PAYLOAD_TYPE = "server-policy.sync.response";

    private final HytaleLogger logger;
    private final TrustBundleStore trustBundleStore;
    private final InventoryTransferService inventoryTransferService;
    private final ServerPolicyCacheService cacheService;
    private final SecureReferralService secureReferralService;
    private final Map<String, PendingSyncRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingSyncReturn> pendingReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler fetchRequestHandler = new FetchRequestHandler();
    private final SecureReferralHandler applyRequestHandler = new ApplyRequestHandler();
    private final SecureReferralHandler responseHandler = new ResponseHandler();

    public ServerPolicySyncService(
        @Nonnull HytaleLogger logger,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull InventoryTransferService inventoryTransferService,
        @Nonnull ServerPolicyCacheService cacheService,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.trustBundleStore = trustBundleStore;
        this.inventoryTransferService = inventoryTransferService;
        this.cacheService = cacheService;
        this.secureReferralService = secureReferralService;
    }

    @Nonnull
    public SecureReferralHandler fetchRequestHandler() {
        return fetchRequestHandler;
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
    public ServerPolicySummary localSummary(@Nonnull String connectionAddress, @Nonnull String serverId) {
        return new ServerPolicySummary(
            connectionAddress,
            serverId,
            System.currentTimeMillis(),
            inventoryTransferService.isRecoveryEnabled(),
            inventoryTransferService.getMaxBackupsPerPlayer()
        ).normalized();
    }

    public void refresh(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        requireTrustedDestination(destination);

        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingSyncRequest(
            requestId,
            PendingRequestMode.FETCH,
            playerRef.getUuid(),
            destination.connectionAddress(),
            originWorldName,
            originTransform.clone(),
            System.currentTimeMillis() + Duration.ofSeconds(30).toMillis(),
            resumeAction
        ));

        secureReferralService.referPlayer(
            playerRef,
            destination.host(),
            destination.port(),
            FETCH_REQUEST_PAYLOAD_TYPE,
            new ServerPolicyFetchRequestPayload(requestId),
            Duration.ofSeconds(30)
        );
    }

    public void apply(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        boolean recoveryEnabled,
        int maxBackupsPerPlayer,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        requireTrustedDestination(destination);

        String requestId = UUID.randomUUID().toString();
        pendingRequests.put(requestId, new PendingSyncRequest(
            requestId,
            PendingRequestMode.APPLY,
            playerRef.getUuid(),
            destination.connectionAddress(),
            originWorldName,
            originTransform.clone(),
            System.currentTimeMillis() + Duration.ofSeconds(30).toMillis(),
            resumeAction
        ));

        secureReferralService.referPlayer(
            playerRef,
            destination.host(),
            destination.port(),
            APPLY_REQUEST_PAYLOAD_TYPE,
            new ServerPolicyApplyRequestPayload(
                requestId,
                recoveryEnabled,
                Math.max(1, maxBackupsPerPlayer)
            ),
            Duration.ofSeconds(30)
        );
    }

    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingSyncReturn pendingReturn = pendingReturns.remove(playerRef.getUuid());
        if (pendingReturn == null) {
            return;
        }

        World world = Universe.get().getWorld(pendingReturn.originWorldName());
        Teleport teleport = world == null
            ? Teleport.createForPlayer(pendingReturn.originTransform().clone())
            : Teleport.createForPlayer(world, pendingReturn.originTransform().clone());
        Ref<EntityStore> storeRef = event.getPlayerRef();
        storeRef.getStore().addComponent(storeRef, Teleport.getComponentType(), teleport);
        event.getPlayer().sendMessage(Message.raw(pendingReturn.message()));
        if (pendingReturn.resumeAction() != null) {
            pendingReturn.resumeAction().reopen(
                storeRef,
                storeRef.getStore(),
                playerRef,
                event.getPlayer()
            );
        }
    }

    private void requireTrustedDestination(@Nonnull ConfiguredPeer destination) {
        TrustBundle bundle = trustBundleStore.getCurrentBundle();
        boolean trusted = bundle.members().stream()
            .anyMatch(member -> destination.connectionAddress().equals(member.connectionAddress()));
        if (!trusted) {
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }
    }

    private void handleFetchRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        ServerPolicyFetchRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            ServerPolicyFetchRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null || payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        try {
            byte[] responsePayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                RESPONSE_PAYLOAD_TYPE,
                new ServerPolicySyncResponsePayload(
                    payload.requestId(),
                    inventoryTransferService.isRecoveryEnabled(),
                    inventoryTransferService.getMaxBackupsPerPlayer()
                ),
                Duration.ofSeconds(30)
            );
            event.referToServer(referralSource.host, referralSource.port, responsePayload);
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori server policy fetch request.");
        }
    }

    private void handleApplyRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        ServerPolicyApplyRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            ServerPolicyApplyRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null || payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        try {
            inventoryTransferService.setRecoveryEnabled(payload.recoveryEnabled());
            inventoryTransferService.setMaxBackupsPerPlayer(payload.maxBackupsPerPlayer());

            byte[] responsePayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                RESPONSE_PAYLOAD_TYPE,
                new ServerPolicySyncResponsePayload(
                    payload.requestId(),
                    inventoryTransferService.isRecoveryEnabled(),
                    inventoryTransferService.getMaxBackupsPerPlayer()
                ),
                Duration.ofSeconds(30)
            );
            event.referToServer(referralSource.host, referralSource.port, responsePayload);
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to apply Nexori server policy request.");
        }
    }

    private void handleResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        ServerPolicySyncResponsePayload payload = referral.decodePayload(
            secureReferralService.gson(),
            ServerPolicySyncResponsePayload.class
        );
        if (payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        PendingSyncRequest pendingRequest = pendingRequests.remove(payload.requestId());
        if (pendingRequest == null || pendingRequest.isExpired() || !pendingRequest.playerUuid().equals(event.getUuid())) {
            return;
        }

        try {
            ServerPolicySummary saved = cacheService.saveConfirmedPolicy(
                pendingRequest.destinationConnectionAddress(),
                referral.issuer().serverId(),
                payload.recoveryEnabled(),
                payload.maxBackupsPerPlayer()
            );
            String message = pendingRequest.mode() == PendingRequestMode.APPLY
                ? "Applied Nexori rules to " + saved.connectionAddress() + "."
                : "Refreshed Nexori rules from " + saved.connectionAddress() + ".";
            pendingReturns.put(event.getUuid(), new PendingSyncReturn(
                pendingRequest.originWorldName(),
                pendingRequest.originTransform(),
                message,
                pendingRequest.resumeAction()
            ));
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to persist the confirmed Nexori server rules.");
            pendingReturns.put(event.getUuid(), new PendingSyncReturn(
                pendingRequest.originWorldName(),
                pendingRequest.originTransform(),
                "The Nexori server rules were received, but saving the confirmed cache failed: " + exception.getMessage(),
                pendingRequest.resumeAction()
            ));
        }
    }

    private enum PendingRequestMode {
        FETCH,
        APPLY
    }

    private record PendingSyncRequest(
        String requestId,
        PendingRequestMode mode,
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

    private record PendingSyncReturn(
        String originWorldName,
        Transform originTransform,
        String message,
        UiResumeAction resumeAction
    ) {
    }

    private final class FetchRequestHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return FETCH_REQUEST_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleFetchRequest(event, referral);
        }
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
