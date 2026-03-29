package io.github.hyjn.nexori.plugin.travel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;
import io.github.hyjn.nexori.plugin.target.WorldSpawnResolver;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SecureTravelService implements SecureReferralHandler {

    public static final String PAYLOAD_TYPE = "travel.direct";
    private static final Gson GSON = new Gson();

    private final HytaleLogger logger;
    private final ServerIdentity localIdentity;
    private final TrustBundleStore trustBundleStore;
    private final DestinationTargetService destinationTargetService;
    private final SecureReferralService secureReferralService;
    private final Map<UUID, PendingArrival> pendingArrivals = new ConcurrentHashMap<>();

    public SecureTravelService(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.destinationTargetService = destinationTargetService;
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
        @Nonnull String destinationTargetId,
        @Nonnull String arrivalPointId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson
    ) throws IOException, GeneralSecurityException {
        if (destinationTargetId.isBlank()) {
            throw new IllegalArgumentException("Secure Nexori travel now requires a destinationTargetId that exists on the destination server.");
        }

        if (!isTrustedDestination(destination)) {
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }

        SecureTravelPayload payload = new SecureTravelPayload(
            localIdentity.serverId().toString(),
            "",
            destinationTargetId,
            arrivalPointId,
            travelProfileId,
            "Secure travel accepted from " + localIdentity.serverId() + ".",
            contextJson
        );
        secureReferralService.referPlayer(playerRef, destination.host(), destination.port(), PAYLOAD_TYPE, payload, Duration.ofSeconds(30));
    }

    @Override
    public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        SecureTravelPayload payload = referral.decodePayload(secureReferralService.gson(), SecureTravelPayload.class);
        ResolvedDestinationTarget resolvedTarget = destinationTargetService.resolve(payload.destinationTargetId(), payload.arrivalPointId()).orElse(null);
        if (resolvedTarget == null) {
            event.setCancelled(true);
            event.setReason(Message.raw("This Nexori destination target is not configured on the destination server: " + payload.destinationTargetId()));
            return;
        }

        String arrivalMessage = resolvedTarget.definition().arrivalMessage().isBlank()
            ? payload.arrivalMessage()
            : resolvedTarget.definition().arrivalMessage();
        pendingArrivals.put(event.getUuid(), new PendingArrival(
            payload.sourceServerId(),
            payload.sourceConnectionAddress(),
            resolvedTarget.definition().id(),
            resolvedTarget.definition().kind().name(),
            resolvedTarget.effectiveWorldName(),
            resolvedTarget.effectiveArrivalPointId(),
            payload.travelProfileId(),
            arrivalMessage,
            payload.contextJson(),
            resolvedTarget.definition().metadataJson()
        ));
        logger.atInfo().log("Accepted secure Nexori travel for " + event.getUsername()
            + " from server "
            + payload.sourceServerId()
            + " targetId="
            + resolvedTarget.definition().id()
            + " targetKind="
            + resolvedTarget.definition().kind());
    }

    public void handlePlayerConnect(@Nonnull PlayerConnectEvent event) {
        if (event.getPlayerRef() == null) {
            return;
        }
    }

    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingArrival arrival = pendingArrivals.remove(playerRef.getUuid());
        if (arrival == null) {
            return;
        }

        applyArrivalTeleport(event, playerRef, arrival);
        event.getPlayer().sendMessage(Message.raw(buildArrivalMessage(arrival)));
    }

    private void applyArrivalTeleport(@Nonnull PlayerReadyEvent event, @Nonnull PlayerRef playerRef, @Nonnull PendingArrival arrival) {
        Transform transform = resolveArrivalTransform(arrival, playerRef.getUuid());
        if (transform == null) {
            return;
        }

        World targetWorld = Universe.get().getWorld(arrival.worldName());
        Teleport teleport = targetWorld == null
            ? Teleport.createForPlayer(transform.clone())
            : Teleport.createForPlayer(targetWorld, transform.clone());
        event.getPlayerRef().getStore().addComponent(event.getPlayerRef(), Teleport.getComponentType(), teleport);

        logger.atInfo().log("Queued Nexori ready teleport for " + playerRef.getUsername()
            + " targetId=" + arrival.destinationTargetId()
            + " position=" + transform.getPosition());
    }

    @Nonnull
    private String buildArrivalMessage(@Nonnull PendingArrival arrival) {
        StringBuilder message = new StringBuilder();
        message.append(arrival.arrivalMessage().isBlank()
            ? "Secure Nexori travel accepted."
            : arrival.arrivalMessage());
        if (!arrival.destinationTargetId().isBlank()) {
            message.append(" target=").append(arrival.destinationTargetId());
        }
        if (!arrival.destinationTargetKind().isBlank()) {
            message.append(" kind=").append(arrival.destinationTargetKind());
        }
        if (!arrival.worldName().isBlank()) {
            message.append(" world=").append(arrival.worldName());
        }
        if (!arrival.arrivalPointId().isBlank()) {
            message.append(" arrivalPoint=").append(arrival.arrivalPointId());
        }
        if (!arrival.travelProfileId().isBlank()) {
            message.append(" travelProfile=").append(arrival.travelProfileId());
        }
        return message.toString();
    }

    private Transform resolveArrivalTransform(@Nonnull PendingArrival arrival, @Nonnull UUID playerUuid) {
        DestinationTargetKind targetKind = DestinationTargetKind.parse(arrival.destinationTargetKind());
        World world = Universe.get().getWorld(arrival.worldName());
        if (targetKind == DestinationTargetKind.NATURAL_SPAWN && world != null) {
            try {
                Transform spawnTransform = world.getWorldConfig().getSpawnProvider().getSpawnPoint(world, playerUuid);
                if (spawnTransform != null) {
                    return spawnTransform.clone();
                }
            } catch (Exception exception) {
                logger.atWarning().withCause(exception).log(
                    "Falling back to Nexori configured spawn resolution for world '" + arrival.worldName() + "'."
                );
            }

            Transform configuredSpawn = WorldSpawnResolver.resolveConfiguredSpawn(world).orElse(null);
            if (configuredSpawn != null) {
                return configuredSpawn.clone();
            }

            return new Transform(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f);
        }

        return resolveMetadataTransform(arrival.metadataJson());
    }

    private Transform resolveMetadataTransform(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }

        try {
            JsonObject root = GSON.fromJson(metadataJson, JsonObject.class);
            if (root == null || !root.has("position")) {
                return null;
            }

            JsonObject position = root.getAsJsonObject("position");
            if (position == null) {
                return null;
            }

            Vector3d positionVector = new Vector3d(
                getDouble(position, "x"),
                getDouble(position, "y"),
                getDouble(position, "z")
            );

            JsonObject rotation = root.has("rotation") ? root.getAsJsonObject("rotation") : null;
            Vector3f rotationVector = rotation == null
                ? new Vector3f(0.0f, 0.0f, 0.0f)
                : new Vector3f(
                    (float) getDouble(rotation, "pitch"),
                    (float) getDouble(rotation, "yaw"),
                    (float) getDouble(rotation, "roll")
                );

            return new Transform(positionVector, rotationVector);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to parse Nexori destination target metadata for arrival transform.");
            return null;
        }
    }

    private double getDouble(@Nonnull JsonObject object, @Nonnull String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0;
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
