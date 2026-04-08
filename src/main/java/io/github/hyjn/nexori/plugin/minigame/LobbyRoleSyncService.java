package io.github.hyjn.nexori.plugin.minigame;

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
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
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

public final class LobbyRoleSyncService {

    public static final String APPLY_REQUEST_PAYLOAD_TYPE = "lobby-role.apply.request";
    public static final String RESPONSE_PAYLOAD_TYPE = "lobby-role.apply.response";

    private final HytaleLogger logger;
    private final TrustBundleStore trustBundleStore;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final LobbyService lobbyService;
    private final NetworkLobbyService networkLobbyService;
    private final SecureReferralService secureReferralService;
    private final Map<String, PendingApplyRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingApplyReturn> pendingReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler applyRequestHandler = new ApplyRequestHandler();
    private final SecureReferralHandler responseHandler = new ResponseHandler();

    public LobbyRoleSyncService(
        @Nonnull HytaleLogger logger,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull LocalConnectionAddressService localConnectionAddressService,
        @Nonnull LobbyService lobbyService,
        @Nonnull NetworkLobbyService networkLobbyService,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.trustBundleStore = trustBundleStore;
        this.localConnectionAddressService = localConnectionAddressService;
        this.lobbyService = lobbyService;
        this.networkLobbyService = networkLobbyService;
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

    public void apply(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        NetworkLobbyDefinition definition,
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
            System.currentTimeMillis() + Duration.ofSeconds(45).toMillis(),
            resumeAction
        ));
        secureReferralService.referPlayer(
            playerRef,
            destination.host(),
            destination.port(),
            APPLY_REQUEST_PAYLOAD_TYPE,
            toRequestPayload(requestId, definition),
            Duration.ofSeconds(45)
        );
    }

    public void apply(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        NetworkLobbyDefinition definition,
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
            System.currentTimeMillis() + Duration.ofSeconds(45).toMillis(),
            resumeAction
        ));
        byte[] encoded = secureReferralService.createPayload(
            event.getUuid(),
            event.getUsername(),
            APPLY_REQUEST_PAYLOAD_TYPE,
            toRequestPayload(requestId, definition),
            Duration.ofSeconds(45)
        );
        event.referToServer(destination.host(), destination.port(), encoded);
    }

    public void clear(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        apply(playerRef, destination, originWorldName, originTransform, null, resumeAction);
    }

    public void clear(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        apply(event, destination, originWorldName, originTransform, null, resumeAction);
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
    private static LobbyRoleApplyRequestPayload toRequestPayload(@Nonnull String requestId, NetworkLobbyDefinition definition) {
        if (definition == null) {
            return new LobbyRoleApplyRequestPayload(requestId, false, "", "", "", "");
        }
        NetworkLobbyDefinition normalized = definition.normalized();
        return new LobbyRoleApplyRequestPayload(
            requestId,
            true,
            normalized.lobbyConnectionAddress(),
            normalized.lobbyWorldName(),
            normalized.entryTargetId(),
            normalized.returnTargetId()
        );
    }

    private void handleApplyRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        LobbyRoleApplyRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            LobbyRoleApplyRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null || payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        boolean success = true;
        String message;
        try {
            if (!payload.configured()) {
                networkLobbyService.clear();
                lobbyService.clearAll();
                message = "Cleared the active Nexori lobby on " + effectiveConnectionAddress(referral);
            } else {
                NetworkLobbyDefinition definition = new NetworkLobbyDefinition(
                    payload.lobbyConnectionAddress(),
                    payload.lobbyWorldName(),
                    payload.entryTargetId(),
                    payload.returnTargetId(),
                    System.currentTimeMillis()
                ).normalized();
                networkLobbyService.save(definition);
                String localConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
                if (!localConnectionAddress.isBlank() && localConnectionAddress.equalsIgnoreCase(definition.lobbyConnectionAddress())) {
                    lobbyService.replaceAll(
                        java.util.List.of(new LobbyDefinition(
                            "main",
                            "Lobby",
                            definition.lobbyWorldName(),
                            definition.entryTargetId(),
                            definition.returnTargetId(),
                            true
                        ))
                    );
                } else {
                    lobbyService.clearAll();
                }
                message = "Marked " + definition.lobbyConnectionAddress() + " as the active Nexori lobby on " + effectiveConnectionAddress(referral) + ".";
            }
        } catch (IOException | IllegalArgumentException exception) {
            success = false;
            message = "Could not update the active Nexori lobby on " + effectiveConnectionAddress(referral) + ": " + exception.getMessage();
        }

        try {
            byte[] responsePayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                RESPONSE_PAYLOAD_TYPE,
                new LobbyRoleApplyResponsePayload(payload.requestId(), success, message),
                Duration.ofSeconds(45)
            );
            event.referToServer(referralSource.host, referralSource.port, responsePayload);
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori lobby role apply request.");
        }
    }

    private void handleResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        LobbyRoleApplyResponsePayload payload = referral.decodePayload(
            secureReferralService.gson(),
            LobbyRoleApplyResponsePayload.class
        );
        if (payload.requestId() == null || payload.requestId().isBlank()) {
            return;
        }

        PendingApplyRequest pendingRequest = pendingRequests.remove(payload.requestId());
        if (pendingRequest == null || pendingRequest.isExpired() || !pendingRequest.playerUuid().equals(event.getUuid())) {
            return;
        }

        String message = payload.message() == null || payload.message().isBlank()
            ? (payload.success() ? "Updated the active Nexori lobby on " + pendingRequest.destinationConnectionAddress() + "." : "The remote lobby update failed.")
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
                    "Could not continue the lobby update flow: " + exception.getMessage(),
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

    @Nonnull
    private String effectiveConnectionAddress(@Nonnull VerifiedSecureReferral referral) {
        String localConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        return localConnectionAddress.isBlank() ? referral.issuer().connectionAddress() : localConnectionAddress;
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
