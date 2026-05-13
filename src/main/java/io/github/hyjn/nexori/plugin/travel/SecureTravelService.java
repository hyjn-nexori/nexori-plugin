package io.github.hyjn.nexori.plugin.travel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.builtin.instances.InstancesPlugin;
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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaInstanceRuntime;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotDefinition;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Owns Nexori's secure travel lifecycle from referral dispatch through destination arrival.
 */
public final class SecureTravelService implements SecureReferralHandler {

    public static final String PAYLOAD_TYPE = "travel.direct";
    private static final Gson GSON = new Gson();
    private static final Duration PORTAL_TRIGGER_SUPPRESSION_AFTER_ARRIVAL = Duration.ofSeconds(1L);

    private final HytaleLogger logger;
    private final ServerIdentity localIdentity;
    private final Path pluginDataDirectory;
    private final TrustBundleStore trustBundleStore;
    private final DestinationTargetService destinationTargetService;
    private final SecureReferralService secureReferralService;
    private final InventoryTransferService inventoryTransferService;
    private final DiagnosticsService diagnosticsService;
    private final InstanceSpawnSlotService instanceSpawnSlotService;
    private final Map<UUID, PendingArrival> pendingArrivals = new ConcurrentHashMap<>();
    private final Map<UUID, PendingArrival> recentArrivals = new ConcurrentHashMap<>();
    private final Map<UUID, PortalArrivalSuppression> recentPortalArrivals = new ConcurrentHashMap<>();

    /**
     * Creates the travel service used by portals, queues, and direct owner/admin travel actions.
     */
    public SecureTravelService(
        @Nonnull HytaleLogger logger,
        @Nonnull Path pluginDataDirectory,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull SecureReferralService secureReferralService,
        @Nonnull InventoryTransferService inventoryTransferService,
        @Nonnull DiagnosticsService diagnosticsService,
        @Nonnull InstanceSpawnSlotService instanceSpawnSlotService
    ) {
        this.logger = logger;
        this.pluginDataDirectory = pluginDataDirectory;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.destinationTargetService = destinationTargetService;
        this.secureReferralService = secureReferralService;
        this.inventoryTransferService = inventoryTransferService;
        this.diagnosticsService = diagnosticsService;
        this.instanceSpawnSlotService = instanceSpawnSlotService;
    }

    @Nonnull
    @Override
    public String payloadType() {
        return PAYLOAD_TYPE;
    }

    /**
     * Dispatches one secure cross-server travel referral to a concrete destination target.
     */
    public void travel(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String destinationTargetId,
        @Nonnull String arrivalPointId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson
    ) throws IOException, GeneralSecurityException {
        travel(playerRef, destination, destinationTargetId, arrivalPointId, travelProfileId, contextJson, diagnosticsService.newOperationId("travel"));
    }

    /**
     * Dispatches one secure cross-server travel referral using an existing diagnostics operation id.
     */
    public void travel(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String destinationTargetId,
        @Nonnull String arrivalPointId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson,
        @Nonnull String operationId
    ) throws IOException, GeneralSecurityException {
        dispatchTravel(playerRef, destination, destinationTargetId, arrivalPointId, travelProfileId, contextJson, operationId);
    }

    /**
     * Dispatches one secure cross-server server hop without a specific destination target.
     */
    public void travelToServer(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson
    ) throws IOException, GeneralSecurityException {
        travelToServer(playerRef, destination, travelProfileId, contextJson, diagnosticsService.newOperationId("travel"));
    }

    /**
     * Dispatches one secure cross-server server hop using an existing diagnostics operation id.
     */
    public void travelToServer(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson,
        @Nonnull String operationId
    ) throws IOException, GeneralSecurityException {
        dispatchTravel(playerRef, destination, "", "", travelProfileId, contextJson, operationId);
    }

    private void dispatchTravel(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String destinationTargetId,
        @Nonnull String arrivalPointId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson,
        @Nonnull String operationId
    ) throws IOException, GeneralSecurityException {

        if (!isTrustedDestination(destination)) {
            recordTravel(
                operationId,
                DiagnosticsAction.TRAVEL_DISPATCH,
                DiagnosticsOutcome.DENIED,
                DiagnosticsReasonClass.SECURITY,
                DiagnosticsReasonCode.DESTINATION_NOT_TRUSTED,
                "The destination is not in the current Nexori trust bundle.",
                event -> event
                    .playerUuid(playerRef.getUuid().toString())
                    .playerNameClaimed(playerRef.getUsername())
                    .remoteConnectionAddress(destination.connectionAddress())
                    .targetId(destinationTargetId)
            );
            throw new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle.");
        }

        TravelProfileType profileType = TravelProfileType.parse(travelProfileId);
        InventoryTransferState inventoryState = null;
        String inventoryTransferId = "";
        if (profileType == TravelProfileType.APPLY_INVENTORY) {
            inventoryState = inventoryTransferService.captureCurrentInventory(playerRef);
            inventoryTransferId = UUID.randomUUID().toString();
        }
        final String finalInventoryTransferId = inventoryTransferId;

        SecureTravelPayload payload = new SecureTravelPayload(
            operationId,
            localIdentity.serverId().toString(),
            "",
            destinationTargetId,
            arrivalPointId,
            profileType.id(),
            "Secure travel accepted from " + localIdentity.serverId() + ".",
            contextJson == null || contextJson.isBlank() ? "{}" : contextJson,
            finalInventoryTransferId,
            inventoryState
        );
        byte[] encodedPayload = secureReferralService.createPayload(playerRef, PAYLOAD_TYPE, payload, Duration.ofSeconds(30));

        if (profileType == TravelProfileType.APPLY_INVENTORY
            && inventoryState != null
            && !finalInventoryTransferId.isBlank()
            && inventoryTransferService.shouldTransferInventory(inventoryState)) {
            inventoryTransferService.saveOriginBackup(
                finalInventoryTransferId,
                playerRef,
                destination.connectionAddress(),
                destinationTargetId,
                profileType.id(),
                inventoryState
            );
            inventoryTransferService.clearOriginInventory(playerRef, inventoryState);
        }

        logger.atInfo().log(
                "NEXORI_TRAVEL_DISPATCH destination=" + destination.connectionAddress()
                        + " targetId=" + destinationTargetId
                        + " arrivalPointId=" + arrivalPointId
                        + " travelProfileId=" + profileType.id()
                        + " contextJson=" + (contextJson == null ? "{}" : contextJson)
        );

        playerRef.referToServer(destination.host(), destination.port(), encodedPayload);
        recordTravel(
            operationId,
            DiagnosticsAction.TRAVEL_DISPATCH,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.TRAVEL_DISPATCHED,
            "Dispatched a secure Nexori travel referral.",
            event -> event
                .playerUuid(playerRef.getUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .payloadType(PAYLOAD_TYPE)
                .payloadHash(hashPayload(encodedPayload))
                .remoteConnectionAddress(destination.connectionAddress())
                .targetId(destinationTargetId)
                .arrivalPointId(arrivalPointId)
                .travelProfileId(profileType.id())
                .transferId(finalInventoryTransferId)
                .addPreview("destination", destination.connectionAddress())
                .addPreview("targetId", destinationTargetId)
                .addPreview("travelProfileId", profileType.id())
        );
    }

    @Override
    public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        SecureTravelPayload payload = referral.decodePayload(secureReferralService.gson(), SecureTravelPayload.class);
        String operationId = payload.travelOperationId() == null || payload.travelOperationId().isBlank()
                ? diagnosticsService.newOperationId("travel")
                : payload.travelOperationId();

        JsonObject context = parseContext(payload.contextJson());
        ResolvedDestinationTarget resolvedTarget;
        String effectiveTargetIdForErrors = normalizeOptional(payload.destinationTargetId());

        logger.atInfo().log(
                "NEXORI_TRAVEL_HANDLE payloadTargetId=" + normalizeOptional(payload.destinationTargetId())
                        + " arrivalPointId=" + normalizeOptional(payload.arrivalPointId())
                        + " serverEntryMode=" + (context != null && context.has("serverEntryMode")
                        ? normalizeOptional(context.get("serverEntryMode").getAsString())
                        : "")
        );

        if (payload.destinationTargetId() == null || payload.destinationTargetId().isBlank()) {
            if (shouldUseDefaultWorldNaturalSpawnEntry(context) || isMinigameLaunchContext(context)) {
                resolvedTarget = resolveDefaultWorldNaturalSpawnEntry().orElse(null);
                if (resolvedTarget == null) {
                    recordTravel(
                            operationId,
                            DiagnosticsAction.TRAVEL_ACCEPT,
                            DiagnosticsOutcome.FAILED,
                            DiagnosticsReasonClass.MISCONFIG,
                            DiagnosticsReasonCode.DESTINATION_TARGET_MISSING,
                            "This Nexori minigame launch could not resolve the default-world natural spawn target on the destination server.",
                            eventDetails -> eventDetails
                                    .playerUuid(event.getUuid().toString())
                                    .playerNameClaimed(event.getUsername())
                                    .payloadType(PAYLOAD_TYPE)
                                    .remoteServerId(payload.sourceServerId())
                                    .remoteConnectionAddress(payload.sourceConnectionAddress())
                                    .travelProfileId(payload.travelProfileId())
                                    .transferId(payload.inventoryTransferId())
                    );
                    event.setCancelled(true);
                    event.setReason(Message.raw("This Nexori minigame launch could not resolve the destination server default world natural spawn target."));
                    return;
                }
                effectiveTargetIdForErrors = resolvedTarget.definition().id();
            } else {
                try {
                    TravelProfileType profileType = TravelProfileType.parse(payload.travelProfileId());
                    inventoryTransferService.prepareInboundArrival(
                            event.getUuid(),
                            profileType,
                            payload.inventoryTransferId(),
                            payload.inventoryState(),
                            payload.sourceServerId(),
                            payload.sourceConnectionAddress()
                    );
                    recordTravel(
                            operationId,
                            DiagnosticsAction.TRAVEL_ACCEPT,
                            DiagnosticsOutcome.ACCEPTED,
                            DiagnosticsReasonClass.NORMAL,
                            DiagnosticsReasonCode.TRAVEL_ACCEPTED,
                            "Accepted secure Nexori server travel on the destination server.",
                            eventDetails -> eventDetails
                                    .playerUuid(event.getUuid().toString())
                                    .playerNameClaimed(event.getUsername())
                                    .payloadType(PAYLOAD_TYPE)
                                    .remoteServerId(payload.sourceServerId())
                                    .remoteConnectionAddress(payload.sourceConnectionAddress())
                                    .travelProfileId(profileType.id())
                                    .transferId(payload.inventoryTransferId())
                    );
                    logger.atInfo().log("Accepted secure Nexori server travel for " + event.getUsername()
                            + " from server "
                            + payload.sourceServerId());
                } catch (IllegalArgumentException | IOException exception) {
                    recordTravel(
                            operationId,
                            DiagnosticsAction.TRAVEL_ACCEPT,
                            DiagnosticsOutcome.FAILED,
                            DiagnosticsReasonClass.IO,
                            DiagnosticsReasonCode.INVENTORY_PROFILE_APPLY_FAILED,
                            "This Nexori travel could not apply its inventory profile: " + exception.getMessage(),
                            eventDetails -> eventDetails
                                    .playerUuid(event.getUuid().toString())
                                    .playerNameClaimed(event.getUsername())
                                    .payloadType(PAYLOAD_TYPE)
                                    .remoteServerId(payload.sourceServerId())
                                    .remoteConnectionAddress(payload.sourceConnectionAddress())
                                    .travelProfileId(payload.travelProfileId())
                                    .transferId(payload.inventoryTransferId())
                    );
                    event.setCancelled(true);
                    event.setReason(Message.raw("This Nexori travel could not apply its inventory profile: " + exception.getMessage()));
                }
                return;
            }
        } else {
            resolvedTarget = destinationTargetService.resolve(payload.destinationTargetId(), payload.arrivalPointId()).orElse(null);
            if (resolvedTarget == null) {
                String finalEffectiveTargetIdForErrors = effectiveTargetIdForErrors;
                ResolvedDestinationTarget finalResolvedTarget = resolvedTarget;
                recordTravel(
                        operationId,
                        DiagnosticsAction.TRAVEL_ACCEPT,
                        DiagnosticsOutcome.FAILED,
                        DiagnosticsReasonClass.MISCONFIG,
                        DiagnosticsReasonCode.DESTINATION_TARGET_MISSING,
                        "This Nexori destination target is not configured on the destination server.",
                        eventDetails -> eventDetails
                                .playerUuid(event.getUuid().toString())
                                .playerNameClaimed(event.getUsername())
                                .payloadType(PAYLOAD_TYPE)
                                .remoteServerId(payload.sourceServerId())
                                .remoteConnectionAddress(payload.sourceConnectionAddress())
                                .targetId(finalEffectiveTargetIdForErrors)
                                .arrivalPointId(finalResolvedTarget == null ? payload.arrivalPointId() : finalResolvedTarget.effectiveArrivalPointId())
                                .travelProfileId(payload.travelProfileId())
                                .transferId(payload.inventoryTransferId())
                );
                event.setCancelled(true);
                event.setReason(Message.raw("This Nexori destination target is not configured on the destination server: " + payload.destinationTargetId()));
                return;
            }
        }

        TravelProfileType profileType;
        try {
            profileType = TravelProfileType.parse(payload.travelProfileId());
            inventoryTransferService.prepareInboundArrival(
                event.getUuid(),
                profileType,
                payload.inventoryTransferId(),
                payload.inventoryState(),
                payload.sourceServerId(),
                payload.sourceConnectionAddress()
            );
        } catch (IllegalArgumentException | IOException exception) {
            recordTravel(
                operationId,
                DiagnosticsAction.TRAVEL_ACCEPT,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.IO,
                DiagnosticsReasonCode.INVENTORY_PROFILE_APPLY_FAILED,
                "This Nexori travel could not apply its inventory profile: " + exception.getMessage(),
                eventDetails -> eventDetails
                    .playerUuid(event.getUuid().toString())
                    .playerNameClaimed(event.getUsername())
                    .payloadType(PAYLOAD_TYPE)
                    .remoteServerId(payload.sourceServerId())
                    .remoteConnectionAddress(payload.sourceConnectionAddress())
                    .targetId(payload.destinationTargetId())
                    .arrivalPointId(payload.arrivalPointId())
                    .travelProfileId(payload.travelProfileId())
                    .transferId(payload.inventoryTransferId())
            );
            event.setCancelled(true);
            event.setReason(Message.raw("This Nexori travel could not apply its inventory profile: " + exception.getMessage()));
            return;
        }

        String arrivalMessage = resolvedTarget.definition().arrivalMessage().isBlank()
            ? payload.arrivalMessage()
            : resolvedTarget.definition().arrivalMessage();
        pendingArrivals.put(event.getUuid(), new PendingArrival(
            operationId,
            payload.sourceServerId(),
            payload.sourceConnectionAddress(),
            resolvedTarget.definition().id(),
            resolvedTarget.definition().displayName(),
            resolvedTarget.definition().kind().name(),
            resolvedTarget.effectiveWorldName(),
            resolvedTarget.effectiveArrivalPointId(),
            profileType.id(),
            arrivalMessage,
            payload.contextJson(),
            resolvedTarget.definition().metadataJson()
        ));
        ResolvedDestinationTarget finalResolvedTarget1 = resolvedTarget;
        recordTravel(
            operationId,
            DiagnosticsAction.TRAVEL_ACCEPT,
            DiagnosticsOutcome.ACCEPTED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.TRAVEL_ACCEPTED,
            "Accepted secure Nexori travel on the destination server.",
            eventDetails -> eventDetails
                .playerUuid(event.getUuid().toString())
                .playerNameClaimed(event.getUsername())
                .payloadType(PAYLOAD_TYPE)
                .remoteServerId(payload.sourceServerId())
                .remoteConnectionAddress(payload.sourceConnectionAddress())
                .targetId(finalResolvedTarget1.definition().id())
                .targetDisplayName(finalResolvedTarget1.definition().displayName())
                .targetKind(finalResolvedTarget1.definition().kind().name())
                .arrivalPointId(finalResolvedTarget1.effectiveArrivalPointId())
                .worldName(finalResolvedTarget1.effectiveWorldName())
                .travelProfileId(profileType.id())
                .transferId(payload.inventoryTransferId())
        );
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
        inventoryTransferService.handlePlayerConnect(event);
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
            inventoryTransferService.handlePlayerReady(event);
            return;
        }

        recentArrivals.put(playerRef.getUuid(), arrival);
        rememberRecentPortalArrival(playerRef.getUuid(), arrival);
        if (!tryQueueInstanceArrival(event, playerRef, arrival)) {
            applyArrivalTeleport(event, playerRef, arrival);
        }
        event.getPlayer().sendMessage(Message.raw(buildArrivalMessage(arrival)));
        inventoryTransferService.handlePlayerReady(event);
    }

    @Nonnull
    /**
     * Returns and clears the most recent accepted arrival record for one player.
     */
    public Optional<PendingArrival> consumeRecentArrival(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(recentArrivals.remove(playerUuid));
    }

    @Nonnull
    /**
     * Returns the still-pending arrival for one player without clearing it.
     */
    public Optional<PendingArrival> peekPendingArrival(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(pendingArrivals.get(playerUuid));
    }

    @Nonnull
    /**
     * Removes and returns one pending arrival, if any.
     */
    public Optional<PendingArrival> removePendingArrival(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(pendingArrivals.remove(playerUuid));
    }

    /**
     * Prevents freshly arrived portal travelers from immediately retriggering the same portal.
     */
    public boolean shouldSuppressPortalTravel(@Nonnull UUID playerUuid, @Nonnull String destinationTargetId) {
        PortalArrivalSuppression suppression = recentPortalArrivals.get(playerUuid);
        if (suppression == null) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (suppression.expiresAtEpochMillis() <= now) {
            recentPortalArrivals.remove(playerUuid, suppression);
            return false;
        }

        String normalizedTargetId = destinationTargetId.trim().toLowerCase(Locale.ROOT);
        if (normalizedTargetId.isBlank()) {
            return false;
        }
        return suppression.destinationTargetId().equals(normalizedTargetId);
    }

    private boolean tryQueueInstanceArrival(
        @Nonnull PlayerReadyEvent event,
        @Nonnull PlayerRef playerRef,
        @Nonnull PendingArrival arrival
    ) {
        JsonObject context = parseContext(arrival.contextJson());
        if (context == null || !context.has("flowType") || !context.has("matchId")) {
            return false;
        }
        if (!"minigame.launch".equalsIgnoreCase(context.get("flowType").getAsString())) {
            return false;
        }

        String instanceTemplateId = context.has("instanceTemplateId")
            ? normalizeOptional(context.get("instanceTemplateId").getAsString())
            : "";
        if (instanceTemplateId.isBlank()
            || ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId)
            || !InstancesPlugin.doesInstanceAssetExist(instanceTemplateId)) {
            return false;
        }

        String matchId = normalizeOptional(context.get("matchId").getAsString()).toLowerCase();
        if (matchId.isBlank()) {
            return false;
        }
        int launchIndex = context.has("launchIndex")
            ? Math.max(context.get("launchIndex").getAsInt(), 0)
            : 0;

        Transform arrivalTransform = resolveArrivalTransform(arrival, playerRef.getUuid());
        World baseWorld = Universe.get().getWorld(arrival.worldName());
        if (arrivalTransform == null || baseWorld == null) {
            return false;
        }

        Ref<EntityStore> playerEntityRef = event.getPlayerRef();
        Store<EntityStore> store = playerEntityRef.getStore();
        String instanceWorldName = ArenaInstanceRuntime.buildInstanceWorldName(matchId);
        CompletableFuture<World> instanceFuture;
        List<InstanceSpawnSlotDefinition> spawnSlots = instanceSpawnSlotService.listByInstanceTemplateId(instanceTemplateId);
        try {
            World existingWorld = Universe.get().getWorld(instanceWorldName);
            CompletableFuture<World> materializedInstanceFuture = existingWorld != null && existingWorld.isAlive()
                ? CompletableFuture.completedFuture(existingWorld)
                : InstancesPlugin.get().spawnInstance(
                    instanceTemplateId,
                    instanceWorldName,
                    baseWorld,
                    arrivalTransform.clone()
                );
            // The instance is prepared before the player enters it so placement rules are already in place.
            instanceFuture = materializedInstanceFuture.thenCompose(instanceWorld ->
                ArenaInstanceRuntime.prepareInstanceForMatch(
                    instanceWorld,
                    spawnSlots,
                    playerRef.getUuid(),
                    launchIndex
                )
            );
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log(
                "Failed to prepare Nexori instance arrival for match " + matchId + "."
            );
            return false;
        }

        instanceFuture.whenComplete((instanceWorld, throwable) -> {
            if (throwable != null) {
                logger.atWarning().withCause(throwable).log(
                    "Failed to materialize Nexori instance world '" + instanceWorldName + "' for match " + matchId + "."
                );
                baseWorld.execute(() -> store.addComponent(
                    playerEntityRef,
                    Teleport.getComponentType(),
                    Teleport.createForPlayer(baseWorld, arrivalTransform.clone())
                ));
                return;
            }
        });
        InstancesPlugin.teleportPlayerToLoadingInstance(
            playerEntityRef,
            store,
            instanceFuture,
            arrivalTransform.clone()
        );
        return true;
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
        recordTravel(
            arrival.travelOperationId(),
            DiagnosticsAction.TRAVEL_ARRIVAL_TELEPORT,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.ARRIVAL_TELEPORT_QUEUED,
            "Queued Nexori arrival teleport for the ready player.",
            eventDetails -> eventDetails
                .playerUuid(playerRef.getUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .remoteServerId(arrival.sourceServerId())
                .remoteConnectionAddress(arrival.sourceConnectionAddress())
                .targetId(arrival.destinationTargetId())
                .targetDisplayName(arrival.destinationTargetDisplayName())
                .targetKind(arrival.destinationTargetKind())
                .worldName(arrival.worldName())
                .arrivalPointId(arrival.arrivalPointId())
                .travelProfileId(arrival.travelProfileId())
        );

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

        return resolveMetadataTransform(arrival.travelOperationId(), arrival.metadataJson());
    }

    private Transform resolveMetadataTransform(String operationId, String metadataJson) {
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
            recordTravel(
                operationId,
                DiagnosticsAction.TRAVEL_ARRIVAL_PREPARE,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.MISCONFIG,
                DiagnosticsReasonCode.ARRIVAL_METADATA_PARSE_FAILED,
                "Failed to parse Nexori destination target metadata for arrival transform.",
                event -> {
                }
            );
            return null;
        }
    }

    private double getDouble(@Nonnull JsonObject object, @Nonnull String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0;
    }

    private JsonObject parseContext(String rawContextJson) {
        if (rawContextJson == null || rawContextJson.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(rawContextJson, JsonObject.class);
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to parse Nexori travel context JSON.");
            return null;
        }
    }

    @Nonnull
    private String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    private boolean shouldUseDefaultWorldNaturalSpawnEntry(JsonObject context) {
        return context != null
                && context.has("serverEntryMode")
                && "default_world_natural_spawn".equalsIgnoreCase(normalizeOptional(context.get("serverEntryMode").getAsString()));
    }

    private boolean isMinigameLaunchContext(JsonObject context) {
        return context != null
                && context.has("flowType")
                && "minigame.launch".equalsIgnoreCase(normalizeOptional(context.get("flowType").getAsString()));
    }

    @Nonnull
    private Optional<ResolvedDestinationTarget> resolveDefaultWorldNaturalSpawnEntry() {
        Path serverDirectory = findServerDirectory();
        if (serverDirectory == null) {
            return Optional.empty();
        }

        Path configFile = serverDirectory.resolve("config.json");
        if (!Files.isRegularFile(configFile)) {
            return Optional.empty();
        }

        try {
            String json = Files.readString(configFile, StandardCharsets.UTF_8);
            JsonObject root = GSON.fromJson(json, JsonObject.class);
            if (root == null || !root.has("Defaults")) {
                return Optional.empty();
            }

            JsonObject defaults = root.getAsJsonObject("Defaults");
            if (defaults == null || !defaults.has("World")) {
                return Optional.empty();
            }

            String defaultWorldName = normalizeOptional(defaults.get("World").getAsString());
            if (defaultWorldName.isBlank()) {
                return Optional.empty();
            }

            String targetId = defaultWorldName.toLowerCase(Locale.ROOT) + ".natural_spawn";
            return destinationTargetService.resolve(targetId, "");
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to resolve the default-world natural spawn target for Nexori minigame launch.");
            return Optional.empty();
        }
    }

    private Path findServerDirectory() {
        Path current = pluginDataDirectory.toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("config.json"))
                    && Files.isDirectory(current.resolve("universe").resolve("worlds"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
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

    private void recordTravel(
        @Nonnull String operationId,
        @Nonnull String action,
        @Nonnull DiagnosticsOutcome outcome,
        @Nonnull DiagnosticsReasonClass reasonClass,
        @Nonnull String reasonCode,
        @Nonnull String message,
        java.util.function.Consumer<DiagnosticsEvent.Builder> customizer
    ) {
        diagnosticsService.record(
            DiagnosticsCategory.TRAVEL,
            action,
            outcome,
            reasonClass,
            reasonCode,
            message,
            operationId,
            customizer
        );
    }

    @Nonnull
    private String hashPayload(@Nonnull byte[] payloadBytes) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payloadBytes);
            StringBuilder out = new StringBuilder("sha256:");
            for (byte value : hash) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            return "";
        }
    }

    private void rememberRecentPortalArrival(@Nonnull UUID playerUuid, @Nonnull PendingArrival arrival) {
        DestinationTargetKind targetKind;
        try {
            targetKind = DestinationTargetKind.parse(arrival.destinationTargetKind());
        } catch (IllegalArgumentException exception) {
            return;
        }

        if (targetKind != DestinationTargetKind.PORTAL) {
            return;
        }

        String destinationTargetId = normalizeOptional(arrival.destinationTargetId()).toLowerCase(Locale.ROOT);
        if (destinationTargetId.isBlank()) {
            return;
        }

        recentPortalArrivals.put(
            playerUuid,
            new PortalArrivalSuppression(
                destinationTargetId,
                System.currentTimeMillis() + PORTAL_TRIGGER_SUPPRESSION_AFTER_ARRIVAL.toMillis()
            )
        );
    }

    private record PortalArrivalSuppression(
        String destinationTargetId,
        long expiresAtEpochMillis
    ) {
    }
}
