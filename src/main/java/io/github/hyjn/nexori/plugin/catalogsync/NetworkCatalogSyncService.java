package io.github.hyjn.nexori.plugin.catalogsync;

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
import io.github.hyjn.nexori.plugin.catalogsync.logic.CatalogSyncApplyPlan;
import io.github.hyjn.nexori.plugin.catalogsync.logic.CatalogSyncApplyPlanner;
import io.github.hyjn.nexori.plugin.catalogsync.logic.CatalogSyncRequestBuildInput;
import io.github.hyjn.nexori.plugin.catalogsync.logic.CatalogSyncRequestBuilder;
import io.github.hyjn.nexori.plugin.catalogsync.logic.CatalogSyncReturnPlan;
import io.github.hyjn.nexori.plugin.catalogsync.logic.CatalogSyncReturnPlanner;
import io.github.hyjn.nexori.plugin.discovery.UiResumeAction;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaService;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkCatalogSyncService {

    public static final String APPLY_REQUEST_PAYLOAD_TYPE = "catalog-sync.apply.request";
    public static final String RESULT_PAYLOAD_TYPE = "catalog-sync.apply.result";
    public static final int SCHEMA_VERSION = 1;
    private static final Duration REFERRAL_TTL = Duration.ofSeconds(30);
    private static final int TARGET_OPERATIONAL_ENCODED_BYTES = 3500;

    private final HytaleLogger logger;
    private final ServerIdentity localIdentity;
    private final TrustBundleStore trustBundleStore;
    private final SecureReferralService secureReferralService;
    private final ArenaService arenaService;
    private final QueueService queueService;
    private final CatalogSyncApplyPlanner applyPlanner = new CatalogSyncApplyPlanner();
    private final CatalogSyncRequestBuilder requestBuilder = new CatalogSyncRequestBuilder();
    private final CatalogSyncReturnPlanner returnPlanner = new CatalogSyncReturnPlanner();
    private final Map<String, PendingCatalogSyncRequest> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCatalogSyncReturn> pendingReturns = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingLocalFailureMessages = new ConcurrentHashMap<>();
    private final SecureReferralHandler applyRequestHandler = new ApplyRequestHandler();
    private final SecureReferralHandler resultHandler = new ResultHandler();

    public NetworkCatalogSyncService(
        @Nonnull HytaleLogger logger,
        @Nonnull ServerIdentity localIdentity,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull SecureReferralService secureReferralService,
        @Nonnull ArenaService arenaService,
        @Nonnull QueueService queueService
    ) {
        this.logger = logger;
        this.localIdentity = localIdentity;
        this.trustBundleStore = trustBundleStore;
        this.secureReferralService = secureReferralService;
        this.arenaService = arenaService;
        this.queueService = queueService;
    }

    @Nonnull
    public SecureReferralHandler applyRequestHandler() {
        return applyRequestHandler;
    }

    @Nonnull
    public SecureReferralHandler resultHandler() {
        return resultHandler;
    }

    public void syncArena(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        @Nonnull ArenaDefinition arena,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        startSync(
            playerRef,
            destination,
            originWorldName,
            originTransform,
            CatalogSyncEntityType.GAME,
            arena.normalized(),
            null,
            resumeAction
        );
    }

    public void syncQueue(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        @Nonnull QueueDefinition queue,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        startSync(
            playerRef,
            destination,
            originWorldName,
            originTransform,
            CatalogSyncEntityType.QUEUE,
            null,
            queue.normalized(),
            resumeAction
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

        PendingCatalogSyncReturn pendingReturn = pendingReturns.remove(playerRef.getUuid());
        if (pendingReturn == null) {
            String localFailureMessage = pendingLocalFailureMessages.remove(playerRef.getUuid());
            if (localFailureMessage != null && !localFailureMessage.isBlank()) {
                playerRef.sendMessage(Message.raw(localFailureMessage));
            }
            return;
        }

        World world = Universe.get().getWorld(pendingReturn.originWorldName());
        Teleport teleport = world == null
            ? Teleport.createForPlayer(pendingReturn.originTransform().clone())
            : Teleport.createForPlayer(world, pendingReturn.originTransform().clone());
        Ref<EntityStore> storeRef = event.getPlayerRef();
        Store<EntityStore> store = storeRef.getStore();
        store.addComponent(storeRef, Teleport.getComponentType(), teleport);
        if (pendingReturn.resumeAction() != null) {
            pendingReturn.resumeAction().reopenWithStatus(
                storeRef,
                store,
                playerRef,
                event.getPlayer(),
                pendingReturn.message(),
                pendingReturn.success()
            );
            return;
        }
        if (!pendingReturn.message().isBlank()) {
            playerRef.sendMessage(Message.raw(pendingReturn.message()));
        }
    }

    private void startSync(
        @Nonnull PlayerRef playerRef,
        @Nonnull ConfiguredPeer destination,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        @Nonnull CatalogSyncEntityType entityType,
        ArenaDefinition arena,
        QueueDefinition queue,
        UiResumeAction resumeAction
    ) throws IOException, GeneralSecurityException {
        BundleMember trustedMember = trustedDestinationMember(destination)
            .orElseThrow(() -> new IllegalStateException("The destination " + destination.connectionAddress() + " is not in the current Nexori trust bundle."));

        String operationId = UUID.randomUUID().toString();
        CatalogEntitySyncRequestPayload payload = requestBuilder.build(new CatalogSyncRequestBuildInput(
            SCHEMA_VERSION,
            operationId,
            localIdentity.serverId().toString(),
            trustedMember.serverId(),
            entityType,
            System.currentTimeMillis(),
            arena,
            queue
        ));

        byte[] encoded = secureReferralService.createPayload(
            playerRef,
            APPLY_REQUEST_PAYLOAD_TYPE,
            payload,
            REFERRAL_TTL
        );
        if (encoded.length > TARGET_OPERATIONAL_ENCODED_BYTES) {
            throw new IllegalStateException(
                "The encoded " + entityType.singularLabel() + " sync payload is too large for the safe referral budget (" + encoded.length + " bytes > " + TARGET_OPERATIONAL_ENCODED_BYTES + ")."
            );
        }

        pendingRequests.put(operationId, new PendingCatalogSyncRequest(
            operationId,
            playerRef.getUuid(),
            destination.connectionAddress(),
            trustedMember.serverId(),
            entityType,
            payload.entityId(),
            originWorldName,
            originTransform.clone(),
            System.currentTimeMillis() + REFERRAL_TTL.toMillis(),
            resumeAction
        ));

        logger.atInfo().log(
            "Starting catalog sync operation "
                + operationId
                + " type="
                + entityType.name()
                + " entityId="
                + payload.entityId()
                + " targetServerId="
                + trustedMember.serverId()
                + " targetConnectionAddress="
                + destination.connectionAddress()
                + " payloadBytes="
                + encoded.length
        );
        playerRef.referToServer(destination.host(), destination.port(), encoded);
    }

    private void handleApplyRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        CatalogEntitySyncRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            CatalogEntitySyncRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            logger.atWarning().log("Rejected catalog sync request without a valid referral source.");
            return;
        }

        String sourceServerId = referral.issuer().serverId();
        logger.atInfo().log(
            "Received catalog sync request operationId="
                + safe(payload.operationId())
                + " type="
                + (payload.entityType() == null ? "<null>" : payload.entityType().name())
                + " entityId="
                + safe(payload.entityId())
                + " sourceServerId="
                + sourceServerId
        );

        CatalogEntitySyncResultPayload result;
        try {
            CatalogSyncApplyPlan plan = applyPlanner.plan(
                payload,
                sourceServerId,
                localIdentity.serverId().toString(),
                SCHEMA_VERSION
            );
            if (plan.action() == CatalogSyncApplyPlan.Action.REJECT) {
                throw new IllegalArgumentException(plan.reason());
            }
            result = applyRequestPayload(payload, plan);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            logger.atWarning().withCause(exception).log(
                "Catalog sync request failed validation or apply. operationId="
                    + safe(payload.operationId())
                    + " type="
                    + (payload.entityType() == null ? "<null>" : payload.entityType().name())
                    + " entityId="
                    + safe(payload.entityId())
            );
            result = returnPlanner.buildRejectedReturn(
                SCHEMA_VERSION,
                payload,
                localIdentity.serverId().toString(),
                exception.getMessage()
            ).resultPayload();
        }

        try {
            byte[] responsePayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                RESULT_PAYLOAD_TYPE,
                result,
                REFERRAL_TTL
            );
            event.referToServer(referralSource.host, referralSource.port, responsePayload);
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to return catalog sync result to the source server.");
            pendingLocalFailureMessages.put(
                event.getUuid(),
                "Catalog sync finished on this server but the result could not be returned automatically to the source server: "
                    + result.message()
            );
        }
    }

    private void handleResult(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        CatalogEntitySyncResultPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            CatalogEntitySyncResultPayload.class
        );
        if (payload.operationId() == null || payload.operationId().isBlank()) {
            return;
        }

        PendingCatalogSyncRequest pendingRequest = pendingRequests.remove(payload.operationId());
        CatalogSyncReturnPlan plan = returnPlanner.planReceivedReturn(
            payload,
            pendingRequest != null,
            pendingRequest != null && pendingRequest.isExpired(),
            pendingRequest != null && pendingRequest.playerUuid().equals(event.getUuid()),
            pendingRequest == null ? "" : pendingRequest.targetServerId()
        );
        if (plan.action() == CatalogSyncReturnPlan.Action.IGNORE_STALE) {
            return;
        }

        logger.atInfo().log(
            "Completed catalog sync operation "
                + payload.operationId()
                + " success="
                + payload.success()
                + " applyResult="
                + safe(payload.applyResult())
                + " entityId="
                + safe(payload.entityId())
                + " targetServerId="
                + safe(payload.targetServerId())
        );
        pendingReturns.put(event.getUuid(), new PendingCatalogSyncReturn(
            pendingRequest.originWorldName(),
            pendingRequest.originTransform(),
            plan.message(),
            pendingRequest.resumeAction(),
            plan.success()
        ));
    }

    @Nonnull
    private CatalogEntitySyncResultPayload applyRequestPayload(
        @Nonnull CatalogEntitySyncRequestPayload payload,
        @Nonnull CatalogSyncApplyPlan plan
    ) {
        return switch (plan.action()) {
            case APPLY_GAME -> applyArenaPayload(payload, plan.arena());
            case APPLY_QUEUE -> applyQueuePayload(payload, plan.queue());
            case REJECT -> throw new IllegalArgumentException(plan.reason());
        };
    }

    @Nonnull
    private CatalogEntitySyncResultPayload applyArenaPayload(
        @Nonnull CatalogEntitySyncRequestPayload payload,
        @Nonnull ArenaDefinition arena
    ) {
        try {
            boolean existed = arenaService.find(arena.arenaId()).isPresent();
            arenaService.upsert(arena);
            String applyResult = existed ? "UPDATED" : "CREATED";
            logger.atInfo().log(
                "Applied catalog sync game operationId="
                    + payload.operationId()
                    + " arenaId="
                    + arena.arenaId()
                    + " applyResult="
                    + applyResult
            );
            return returnPlanner.buildAppliedReturn(
                SCHEMA_VERSION,
                payload.operationId(),
                CatalogSyncEntityType.GAME,
                arena.arenaId(),
                localIdentity.serverId().toString(),
                applyResult
            ).resultPayload();
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Failed to sync game '" + arena.arenaId() + "' to '" + localIdentity.serverId() + "': " + exception.getMessage(),
                exception
            );
        }
    }

    @Nonnull
    private CatalogEntitySyncResultPayload applyQueuePayload(
        @Nonnull CatalogEntitySyncRequestPayload payload,
        @Nonnull QueueDefinition queue
    ) {
        List<String> missingArenaIds = new ArrayList<>();
        for (String arenaId : queue.arenaIds()) {
            if (arenaService.find(arenaId).isEmpty()) {
                missingArenaIds.add(arenaId);
            }
        }
        if (!missingArenaIds.isEmpty()) {
            throw new IllegalStateException(
                "Missing referenced games on target: " + String.join(", ", missingArenaIds) + ". Sync the games first, then sync this queue."
            );
        }
        try {
            boolean existed = queueService.find(queue.queueId()).isPresent();
            queueService.upsert(queue);
            String applyResult = existed ? "UPDATED" : "CREATED";
            logger.atInfo().log(
                "Applied catalog sync queue operationId="
                    + payload.operationId()
                    + " queueId="
                    + queue.queueId()
                    + " applyResult="
                    + applyResult
            );
            return returnPlanner.buildAppliedReturn(
                SCHEMA_VERSION,
                payload.operationId(),
                CatalogSyncEntityType.QUEUE,
                queue.queueId(),
                localIdentity.serverId().toString(),
                applyResult
            ).resultPayload();
        } catch (IOException | IllegalArgumentException exception) {
            throw new IllegalStateException(
                "Failed to sync queue '" + queue.queueId() + "' to '" + localIdentity.serverId() + "': " + exception.getMessage(),
                exception
            );
        }
    }

    @Nonnull
    private Optional<BundleMember> trustedDestinationMember(@Nonnull ConfiguredPeer destination) {
        TrustBundle bundle = trustBundleStore.getCurrentBundle();
        return bundle.members().stream()
            .filter(member -> destination.connectionAddress().equalsIgnoreCase(member.connectionAddress()))
            .findFirst();
    }

    @Nonnull
    private static String safe(String value) {
        return value == null || value.isBlank() ? "<blank>" : value;
    }

    private record PendingCatalogSyncRequest(
        String operationId,
        UUID playerUuid,
        String targetConnectionAddress,
        String targetServerId,
        CatalogSyncEntityType entityType,
        String entityId,
        String originWorldName,
        Transform originTransform,
        long expiresAtEpochMillis,
        UiResumeAction resumeAction
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtEpochMillis;
        }
    }

    private record PendingCatalogSyncReturn(
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

    private final class ResultHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return RESULT_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleResult(event, referral);
        }
    }
}
