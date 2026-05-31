package io.github.hyjn.nexori.plugin.travel;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import org.joml.Vector3d;
import com.hypixel.hytale.math.vector.Rotation3f;
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
import io.github.hyjn.nexori.plugin.inventory.logic.InventoryOutboundTransferPlan;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;
import io.github.hyjn.nexori.plugin.target.ResolvedDestinationTarget;
import io.github.hyjn.nexori.plugin.target.WorldSpawnResolver;
import io.github.hyjn.nexori.plugin.travel.logic.TravelContextData;
import io.github.hyjn.nexori.plugin.travel.logic.TravelContextParser;
import io.github.hyjn.nexori.plugin.travel.logic.SecureTravelDispatchPlan;
import io.github.hyjn.nexori.plugin.travel.logic.SecureTravelDispatchPlanner;
import io.github.hyjn.nexori.plugin.travel.logic.TravelArrivalPlan;
import io.github.hyjn.nexori.plugin.travel.logic.TravelArrivalPlanner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
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
    private final TravelContextParser travelContextParser = new TravelContextParser();
    private final SecureTravelDispatchPlanner dispatchPlanner = new SecureTravelDispatchPlanner();
    private final TravelArrivalPlanner arrivalPlanner = new TravelArrivalPlanner();
    private final ReadyPlayerSnapshotResolver snapshotResolver = new ReadyPlayerSnapshotResolver();
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
        @Nonnull DiagnosticsService diagnosticsService
    ) {
        this.logger = logger;
        this.pluginDataDirectory = pluginDataDirectory;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.destinationTargetService = destinationTargetService;
        this.secureReferralService = secureReferralService;
        this.inventoryTransferService = inventoryTransferService;
        this.diagnosticsService = diagnosticsService;
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

        TravelProfileType profileType = dispatchPlanner.travelProfileType(travelProfileId);
        InventoryTransferState inventoryState = null;
        String inventoryTransferId = "";
        if (dispatchPlanner.requiresInventoryCapture(travelProfileId)) {
            inventoryState = inventoryTransferService.captureCurrentInventory(playerRef);
            inventoryTransferId = UUID.randomUUID().toString();
        }
        final String finalInventoryTransferId = inventoryTransferId;
        SecureTravelDispatchPlan dispatchPlan = dispatchPlanner.plan(
            operationId,
            localIdentity.serverId().toString(),
            destinationTargetId,
            arrivalPointId,
            travelProfileId,
            contextJson,
            finalInventoryTransferId,
            inventoryState
        );
        SecureTravelPayload payload = dispatchPlan.payload();
        byte[] encodedPayload = secureReferralService.createPayload(playerRef, PAYLOAD_TYPE, payload, Duration.ofSeconds(30));

        InventoryOutboundTransferPlan outboundTransferPlan = inventoryTransferService.planOutboundTransfer(
            profileType,
            finalInventoryTransferId,
            playerRef.getUuid(),
            inventoryState,
            destination.connectionAddress(),
            destinationTargetId
        );
        if (dispatchPlan.shouldPrepareOriginInventoryTransfer()
            && outboundTransferPlan.shouldSaveBackup()) {
            inventoryTransferService.saveOriginBackup(outboundTransferPlan, playerRef);
        }
        if (dispatchPlan.shouldPrepareOriginInventoryTransfer()
            && outboundTransferPlan.shouldClearOriginInventory()) {
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
        TravelArrivalPlan routePlan = arrivalPlanner.route(
            payload,
            travelContextParser.shouldUseDefaultWorldNaturalSpawnEntry(context),
            travelContextParser.isMinigameLaunchContext(context)
        );
        String effectiveTargetIdForErrors = routePlan.effectiveTargetIdForErrors();

        logger.atInfo().log(
                "NEXORI_TRAVEL_HANDLE payloadTargetId=" + normalizeOptional(payload.destinationTargetId())
                        + " arrivalPointId=" + normalizeOptional(payload.arrivalPointId())
                        + " serverEntryMode=" + (context != null && context.has("serverEntryMode")
                        ? normalizeOptional(context.get("serverEntryMode").getAsString())
                        : "")
        );

        if (routePlan.route() == TravelArrivalPlan.Route.RESOLVE_DEFAULT_WORLD_NATURAL_SPAWN) {
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
        } else if (routePlan.route() == TravelArrivalPlan.Route.SERVER_HOP_WITHOUT_TARGET) {
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

        TravelArrivalPlan acceptedPlan = arrivalPlanner.acceptResolvedTarget(
            operationId,
            payload,
            profileType,
            resolvedTarget
        );
        pendingArrivals.put(event.getUuid(), acceptedPlan.pendingArrival());
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
        ReadyPlayerSnapshot snapshot = snapshotResolver.resolve(event);
        if (!snapshot.safe()) {
            logger.atInfo().log("NEXORI_TRAVEL_UNSAFE_READY reason=" + snapshot.unsafeReason());
            return;
        }

        // If the engine fires PlayerReadyEvent while the player entity has no world yet
        // (e.g. temporarily placed in skywars_nexori_template), do not remove the pending
        // arrival or issue any teleport.  The arrival stays in pendingArrivals and will be
        // processed on the next safe ready event with a valid world.
        if (snapshot.world() == null) {
            logger.atInfo().log(
                "NEXORI_TRAVEL_UNSAFE_READY player=" + snapshot.playerUuid()
                    + " username=" + snapshot.username()
                    + " reason=NULL_WORLD"
            );
            return;
        }

        PlayerRef playerRef = snapshot.playerRef();
        PendingArrival arrival = pendingArrivals.get(playerRef.getUuid());
        if (arrival == null) {
            inventoryTransferService.handlePlayerReady(event);
            return;
        }

        MinigameLaunchStagingDecision stagingDecision = decideMinigameLaunchStaging(arrival, snapshot.world().getName());
        if (stagingDecision == MinigameLaunchStagingDecision.STAGING_REQUIRED) {
            logger.atInfo().log(
                "NEXORI_TRAVEL_STAGING_REQUIRED player=" + playerRef.getUuid()
                    + " username=" + playerRef.getUsername()
                    + " currentWorld=" + snapshot.world().getName()
                    + " targetWorld=" + normalizeOptional(arrival.worldName())
            );
            if (applyArrivalTeleport(snapshot, playerRef, arrival)) {
                logger.atInfo().log(
                    "NEXORI_TRAVEL_STAGING_TELEPORT_ISSUED player=" + playerRef.getUuid()
                        + " username=" + playerRef.getUsername()
                        + " target=" + normalizeOptional(arrival.destinationTargetId())
                );
            }
            return;
        }

        if (!pendingArrivals.remove(playerRef.getUuid(), arrival)) {
            return;
        }

        if (stagingDecision == MinigameLaunchStagingDecision.STAGING_ALREADY_READY) {
            logger.atInfo().log(
                "NEXORI_TRAVEL_STAGING_ALREADY_READY player=" + playerRef.getUuid()
                    + " username=" + playerRef.getUsername()
                    + " currentWorld=" + snapshot.world().getName()
                    + " targetWorld=" + normalizeOptional(arrival.worldName())
            );
            markArrivalReady(playerRef.getUuid(), arrival);
            logger.atInfo().log(
                "NEXORI_TRAVEL_STAGING_READY player=" + playerRef.getUuid()
                    + " username=" + playerRef.getUsername()
                    + " target=" + normalizeOptional(arrival.destinationTargetId())
            );
            playerRef.sendMessage(Message.raw(arrivalPlanner.buildArrivalMessage(arrival)));
            inventoryTransferService.handlePlayerReady(event);
            return;
        }

        markArrivalReady(playerRef.getUuid(), arrival);
        applyArrivalTeleport(snapshot, playerRef, arrival);

        playerRef.sendMessage(Message.raw(arrivalPlanner.buildArrivalMessage(arrival)));
        inventoryTransferService.handlePlayerReady(event);
    }

    private void markArrivalReady(@Nonnull UUID playerUuid, @Nonnull PendingArrival arrival) {
        recentArrivals.put(playerUuid, arrival);
        rememberRecentPortalArrival(playerUuid, arrival);
    }

    /**
     * Returns the most recent accepted arrival for one player without removing it.
     * Callers that intend to consume the arrival must follow up with
     * {@link #acknowledgeRecentArrival(UUID, PendingArrival)}.
     */
    @Nonnull
    public Optional<PendingArrival> peekRecentArrival(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(recentArrivals.get(playerUuid));
    }

    /**
     * Removes a specific recent arrival once the caller has taken ownership of it.
     * Uses the exact {@code arrival} reference to guard against races where the map
     * entry was already replaced by a newer arrival.
     */
    public void acknowledgeRecentArrival(@Nonnull UUID playerUuid, @Nonnull PendingArrival arrival) {
        recentArrivals.remove(playerUuid, arrival);
    }

    /**
     * Returns and clears the most recent accepted arrival record for one player.
     *
     * @deprecated Prefer {@link #peekRecentArrival} + {@link #acknowledgeRecentArrival} so that
     *     the arrival is not lost if the consumer decides not to take ownership.
     */
    @Deprecated
    @Nonnull
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

    private boolean applyArrivalTeleport(@Nonnull ReadyPlayerSnapshot snapshot, @Nonnull PlayerRef playerRef, @Nonnull PendingArrival arrival) {
        Transform transform = resolveArrivalTransform(arrival, playerRef.getUuid());
        if (transform == null) {
            return false;
        }

        World targetWorld = Universe.get().getWorld(arrival.worldName());
        Teleport teleport = targetWorld == null
            ? Teleport.createForPlayer(transform.clone())
            : Teleport.createForPlayer(targetWorld, transform.clone());
        snapshot.store().addComponent(snapshot.entityRef(), Teleport.getComponentType(), teleport);
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
        return true;
    }

    @Nonnull
    static MinigameLaunchStagingDecision decideMinigameLaunchStaging(
        @Nonnull PendingArrival arrival,
        @Nonnull String currentWorldName
    ) {
        TravelContextParser parser = new TravelContextParser();
        JsonObject context = parser.parseContext(arrival.contextJson()).context();
        if (!parser.isMinigameLaunchContext(context)
            || !parser.shouldUseDefaultWorldNaturalSpawnEntry(context)
            || !hasInstanceTemplateId(parser, context)) {
            return MinigameLaunchStagingDecision.NOT_APPLICABLE;
        }

        String targetWorldName = parser.normalizeOptional(arrival.worldName());
        String normalizedCurrentWorldName = parser.normalizeOptional(currentWorldName);
        if (!targetWorldName.isBlank()
            && targetWorldName.equalsIgnoreCase(normalizedCurrentWorldName)) {
            return MinigameLaunchStagingDecision.STAGING_ALREADY_READY;
        }
        return MinigameLaunchStagingDecision.STAGING_REQUIRED;
    }

    private static boolean hasInstanceTemplateId(@Nonnull TravelContextParser parser, JsonObject context) {
        return context != null
            && context.has("instanceTemplateId")
            && !parser.normalizeOptional(context.get("instanceTemplateId").getAsString()).isBlank();
    }

    enum MinigameLaunchStagingDecision {
        NOT_APPLICABLE,
        STAGING_REQUIRED,
        STAGING_ALREADY_READY
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
            Rotation3f rotationVector = rotation == null
                ? new Rotation3f(0.0f, 0.0f, 0.0f)
                : new Rotation3f(
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
        TravelContextData data = travelContextParser.parseContext(rawContextJson);
        if (data.parseFailed()) {
            data.parseExceptionOptional()
                .ifPresentOrElse(
                    exception -> logger.atWarning().withCause(exception).log("Failed to parse Nexori travel context JSON."),
                    () -> logger.atWarning().log("Failed to parse Nexori travel context JSON.")
                );
        }
        return data.contextOptional().orElse(null);
    }

    @Nonnull
    private String normalizeOptional(String rawValue) {
        return travelContextParser.normalizeOptional(rawValue);
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
