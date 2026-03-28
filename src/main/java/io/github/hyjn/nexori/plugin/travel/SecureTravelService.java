package io.github.hyjn.nexori.plugin.travel;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
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

public final class SecureTravelService implements SecureReferralHandler {

    public static final String PAYLOAD_TYPE = "travel.direct";

    private final HytaleLogger logger;
    private final ServerIdentity localIdentity;
    private final TrustBundleStore trustBundleStore;
    private final SecureReferralService secureReferralService;
    private final Map<UUID, PendingArrival> pendingArrivals = new ConcurrentHashMap<>();

    public SecureTravelService(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.secureReferralService = secureReferralService;
    }

    @Nonnull
    @Override
    public String payloadType() {
        return PAYLOAD_TYPE;
    }

    public void travel(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String routeKey,
        @Nonnull String entryPointId,
        @Nonnull String contextJson
    ) throws IOException, GeneralSecurityException {
        if (!isTrustedDestination(destination)) {
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }

        SecureTravelPayload payload = new SecureTravelPayload(
            localIdentity.serverId().toString(),
            "",
            routeKey,
            entryPointId,
            "Secure travel accepted from " + localIdentity.serverId() + ".",
            contextJson
        );
        secureReferralService.referPlayer(playerRef, destination.host(), destination.port(), PAYLOAD_TYPE, payload, Duration.ofSeconds(30));
    }

    @Override
    public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        SecureTravelPayload payload = referral.decodePayload(secureReferralService.gson(), SecureTravelPayload.class);
        pendingArrivals.put(event.getUuid(), new PendingArrival(
            payload.sourceServerId(),
            payload.sourceConnectionAddress(),
            payload.routeKey(),
            payload.entryPointId(),
            payload.arrivalMessage(),
            payload.contextJson()
        ));
        logger.atInfo().log("Accepted secure Nexori travel for " + event.getUsername()
            + " from server "
            + payload.sourceServerId()
            + " routeKey="
            + payload.routeKey());
    }

    public void handlePlayerConnect(@Nonnull PlayerConnectEvent event) {
        if (event.getPlayerRef() == null) {
            return;
        }

        PendingArrival arrival = pendingArrivals.remove(event.getPlayerRef().getUuid());
        if (arrival == null) {
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append(arrival.arrivalMessage().isBlank()
            ? "Secure Nexori travel accepted."
            : arrival.arrivalMessage());
        if (!arrival.routeKey().isBlank()) {
            message.append(" route=").append(arrival.routeKey());
        }
        if (!arrival.entryPointId().isBlank()) {
            message.append(" entry=").append(arrival.entryPointId());
        }

        event.getPlayerRef().sendMessage(Message.raw(message.toString()));
    }

    private boolean isTrustedDestination(@Nonnull ConfiguredPeer destination) {
        TrustBundle bundle = trustBundleStore.getCurrentBundle();
        for (var member : bundle.members()) {
            if (destination.connectionAddress().equals(member.connectionAddress())) {
                return true;
            }
        }
        return false;
    }
}
