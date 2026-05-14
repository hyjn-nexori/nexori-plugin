package io.github.hyjn.nexori.plugin.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.HostAddress;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.inventory.logic.InventoryRecoveryFinalizePlan;
import io.github.hyjn.nexori.plugin.inventory.logic.InventoryRecoveryFinalizePlanner;
import io.github.hyjn.nexori.plugin.inventory.logic.InventoryRecoveryStartPlan;
import io.github.hyjn.nexori.plugin.inventory.logic.InventoryRecoveryStartPlanner;
import io.github.hyjn.nexori.plugin.inventory.logic.InventoryStateAnalyzer;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;
import io.github.hyjn.nexori.plugin.ui.NexoriRecoveryPage;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles Nexori inventory handoff, backup, receipt tracking, and recovery flows across secure travel.
 */
public final class InventoryTransferService {

    public static final String QUERY_PAYLOAD_TYPE = "inventory-transfer.receipt-query";
    public static final String REPLY_PAYLOAD_TYPE = "inventory-transfer.receipt-reply";
    private final HytaleLogger logger;
    private final InventoryTransferBackupStore backupStore;
    private final InventoryTransferReceiptStore receiptStore;
    private final InventoryTransferPolicyStore policyStore;
    private final PlayerSaveRepository playerSaveRepository;
    private final InventorySnapshotService inventorySnapshotService;
    private final SecureReferralService secureReferralService;
    private final DiagnosticsService diagnosticsService;
    private final InventoryRecoveryFinalizePlanner recoveryFinalizePlanner = new InventoryRecoveryFinalizePlanner();
    private final InventoryRecoveryStartPlanner recoveryStartPlanner = new InventoryRecoveryStartPlanner();
    private final InventoryStateAnalyzer inventoryStateAnalyzer = new InventoryStateAnalyzer();
    private final Map<UUID, InventoryTransferState> pendingRuntimeApplies = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRuntimeClears = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingRecoveryQuery> pendingRecoveryQueries = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRecoveryReturn> pendingRecoveryReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler queryHandler = new QueryHandler();
    private final SecureReferralHandler replyHandler = new ReplyHandler();

    /**
     * Creates the inventory transfer service for secure travel and recovery.
     */
    public InventoryTransferService(
        @Nonnull HytaleLogger logger,
        @Nonnull InventoryTransferBackupStore backupStore,
        @Nonnull InventoryTransferReceiptStore receiptStore,
        @Nonnull InventoryTransferPolicyStore policyStore,
        @Nonnull PlayerSaveRepository playerSaveRepository,
        @Nonnull InventorySnapshotService inventorySnapshotService,
        @Nonnull SecureReferralService secureReferralService,
        @Nonnull DiagnosticsService diagnosticsService
    ) {
        this.logger = logger;
        this.backupStore = backupStore;
        this.receiptStore = receiptStore;
        this.policyStore = policyStore;
        this.playerSaveRepository = playerSaveRepository;
        this.inventorySnapshotService = inventorySnapshotService;
        this.secureReferralService = secureReferralService;
        this.diagnosticsService = diagnosticsService;
    }

    /**
     * Returns the secure referral handler that answers remote receipt queries.
     */
    @Nonnull
    public SecureReferralHandler queryHandler() {
        return queryHandler;
    }

    /**
     * Returns the secure referral handler that processes receipt query replies on the origin server.
     */
    @Nonnull
    public SecureReferralHandler replyHandler() {
        return replyHandler;
    }

    /**
     * Captures the current live inventory state of the player.
     */
    @Nonnull
    public InventoryTransferState captureCurrentInventory(@Nonnull PlayerRef playerRef) {
        Player player = playerRef.getComponent(Player.getComponentType());
        if (player == null) {
            throw new IllegalStateException("Could not read the live player inventory for Nexori APPLY_INVENTORY travel.");
        }
        return inventorySnapshotService.capture(player);
    }

    /**
     * Saves the origin backup created before APPLY_INVENTORY travel clears the source inventory.
     */
    @Nonnull
    public InventoryTransferBackupRecord saveOriginBackup(
        @Nonnull String transferId,
        @Nonnull PlayerRef playerRef,
        @Nonnull String destinationConnectionAddress,
        @Nonnull String destinationTargetId,
        @Nonnull String travelProfileId,
        @Nonnull InventoryTransferState inventoryState
    ) throws IOException {
        InventoryTransferBackupRecord saved = backupStore.save(new InventoryTransferBackupRecord(
            transferId,
            System.currentTimeMillis(),
            playerRef.getUuid(),
            InventoryTransferBackupMode.ORIGIN_QUERY.id(),
            destinationConnectionAddress,
            destinationTargetId,
            travelProfileId,
            inventoryState
        ));
        trimBackupsForPlayer(playerRef.getUuid());
        logger.atInfo().log("Saved Nexori inventory backup " + saved.transferId()
            + " for " + playerRef.getUsername()
            + " toward " + saved.destinationConnectionAddress()
            + " -> " + saved.destinationTargetId() + ".");
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_BACKUP_ORIGIN_SAVE,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.ORIGIN_BACKUP_SAVED,
            "Saved an origin inventory backup for APPLY_INVENTORY travel.",
            transferId,
            event -> event
                .playerUuid(playerRef.getUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .transferId(transferId)
                .targetId(destinationTargetId)
                .travelProfileId(travelProfileId)
                .remoteConnectionAddress(destinationConnectionAddress)
        );
        return saved;
    }

    /**
     * Saves a local overwrite backup before inbound APPLY_INVENTORY replaces the destination inventory.
     */
    @Nonnull
    public InventoryTransferBackupRecord saveLocalOverwriteBackup(
        @Nonnull UUID playerUuid,
        @Nonnull String relatedConnectionAddress,
        @Nonnull String relatedTargetId,
        @Nonnull String travelProfileId,
        @Nonnull InventoryTransferState inventoryState
    ) throws IOException {
        InventoryTransferBackupRecord saved = backupStore.save(new InventoryTransferBackupRecord(
            UUID.randomUUID().toString(),
            System.currentTimeMillis(),
            playerUuid,
            InventoryTransferBackupMode.LOCAL_RESTORE.id(),
            relatedConnectionAddress,
            relatedTargetId,
            travelProfileId,
            inventoryState
        ));
        trimBackupsForPlayer(playerUuid);
        logger.atInfo().log("Saved Nexori local overwrite backup " + saved.transferId()
            + " for " + playerUuid
            + " relatedServer=" + saved.destinationConnectionAddress() + ".");
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_BACKUP_LOCAL_OVERWRITE_SAVE,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.LOCAL_OVERWRITE_BACKUP_SAVED,
            "Saved a local overwrite backup before APPLY_INVENTORY replaced destination inventory.",
            saved.transferId(),
            event -> event
                .playerUuid(playerUuid.toString())
                .transferId(saved.transferId())
                .targetId(relatedTargetId)
                .travelProfileId(travelProfileId)
                .remoteConnectionAddress(relatedConnectionAddress)
        );
        return saved;
    }

    /**
     * Clears the origin inventory after the outbound backup has been saved.
     */
    public void clearOriginInventory(@Nonnull PlayerRef playerRef, @Nonnull InventoryTransferState sourceInventory) {
        InventoryTransferState emptied = InventoryTransferState.emptyLike(sourceInventory);
        playerSaveRepository.applyInventoryState(playerRef.getUuid(), emptied);

        Player player = playerRef.getComponent(Player.getComponentType());
        if (player != null) {
            inventorySnapshotService.applyToPlayer(player, emptied);
        }

        logger.atInfo().log("Cleared origin inventory for Nexori APPLY_INVENTORY travel player=" + playerRef.getUsername());
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_ORIGIN_CLEAR,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.ORIGIN_INVENTORY_CLEARED,
            "Cleared the origin inventory after saving the APPLY_INVENTORY backup.",
            diagnosticsService.newOperationId("recovery"),
            event -> event.playerUuid(playerRef.getUuid().toString()).playerNameClaimed(playerRef.getUsername())
        );
    }

    /**
     * Prepares the destination-side inventory state that should exist when the player arrives.
     */
    public void prepareInboundArrival(
        @Nonnull UUID playerUuid,
        @Nonnull TravelProfileType profileType,
        String transferId,
        InventoryTransferState inventoryState,
        String sourceServerId,
        String sourceConnectionAddress
    ) throws IOException {
        switch (profileType) {
            case KEEP_INVENTORY -> {
                return;
            }
            case CLEAR_INVENTORY -> clearDestinationInventory(playerUuid);
            case APPLY_INVENTORY -> {
                boolean hasTransferId = transferId != null && !transferId.isBlank();
                boolean hasInventorySnapshot = inventoryState != null;
                if (!hasTransferId && !hasInventorySnapshot) {
                    logger.atInfo().log(
                        "Skipping Nexori APPLY_INVENTORY inbound apply for " + playerUuid
                            + " because no visible inventory was transferred."
                    );
                    return;
                }

                applyInboundInventory(
                    playerUuid,
                    transferId,
                    inventoryState,
                    sourceServerId == null ? "" : sourceServerId,
                    sourceConnectionAddress == null ? "" : sourceConnectionAddress
                );
            }
        }
    }

    /**
     * Lists recovery backups available for one player.
     */
    @Nonnull
    public List<InventoryTransferBackupRecord> listBackups(@Nonnull UUID playerUuid) {
        return backupStore.listByPlayer(playerUuid);
    }

    /**
     * Finds one recovery backup owned by the given player.
     */
    @Nonnull
    public Optional<InventoryTransferBackupRecord> findPlayerBackup(@Nonnull UUID playerUuid, @Nonnull String transferId) {
        return backupStore.find(transferId)
            .filter(record -> playerUuid.equals(record.playerUuid()));
    }

    /**
     * Starts the recovery flow for the requested backup.
     */
    @Nonnull
    public RecoveryStartResult startRecovery(
        @Nonnull PlayerRef playerRef,
        @Nonnull String transferId,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform
    ) throws IOException, GeneralSecurityException {
        if (!policyStore.isApplyInventoryBackupsEnabled()) {
            InventoryRecoveryStartPlan plan = recoveryStartPlanner.plan(false, null);
            diagnosticsService.record(
                DiagnosticsCategory.RECOVERY,
                DiagnosticsAction.RECOVERY_QUERY_START,
                DiagnosticsOutcome.DENIED,
                DiagnosticsReasonClass.VALIDATION,
                DiagnosticsReasonCode.RECOVERY_DISABLED,
                plan.message(),
                transferId == null || transferId.isBlank() ? diagnosticsService.newOperationId("recovery") : transferId,
                event -> event.playerUuid(playerRef.getUuid().toString()).playerNameClaimed(playerRef.getUsername()).transferId(transferId == null ? "" : transferId)
            );
            throw new IllegalStateException(plan.message());
        }
        InventoryTransferBackupRecord backup = findPlayerBackup(playerRef.getUuid(), transferId).orElse(null);
        InventoryRecoveryStartPlan plan = recoveryStartPlanner.plan(true, backup);
        if (plan.action() == InventoryRecoveryStartPlan.Action.BACKUP_MISSING) {
            diagnosticsService.record(
                DiagnosticsCategory.RECOVERY,
                DiagnosticsAction.RECOVERY_QUERY_START,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.VALIDATION,
                DiagnosticsReasonCode.BACKUP_NOT_FOUND,
                "That Nexori inventory backup does not exist for this player.",
                transferId == null || transferId.isBlank() ? diagnosticsService.newOperationId("recovery") : transferId,
                event -> event.playerUuid(playerRef.getUuid().toString()).playerNameClaimed(playerRef.getUsername()).transferId(transferId == null ? "" : transferId)
            );
            throw new IllegalArgumentException(plan.message());
        }

        if (plan.action() == InventoryRecoveryStartPlan.Action.LOCAL_RESTORE) {
            restoreBackupForActivePlayer(playerRef, backup);
            backupStore.remove(backup.transferId());
            return new RecoveryStartResult(
                plan.remoteTravelStarted(),
                plan.message()
            );
        }

        ConfiguredPeer destination = ConfiguredPeer.parse(backup.destinationConnectionAddress());
        pendingRecoveryQueries.put(backup.transferId(), new PendingRecoveryQuery(
            backup.transferId(),
            playerRef.getUuid(),
            originWorldName,
            originTransform.clone(),
            System.currentTimeMillis() + Duration.ofSeconds(30).toMillis()
        ));
        secureReferralService.referPlayer(
            playerRef,
            destination.host(),
            destination.port(),
            QUERY_PAYLOAD_TYPE,
            new InventoryTransferReceiptQueryPayload(backup.transferId()),
            Duration.ofSeconds(30)
        );
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_QUERY_START,
            DiagnosticsOutcome.STARTED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.RECOVERY_QUERY_STARTED,
            "Started a remote Nexori recovery query for an origin backup.",
            backup.transferId(),
            event -> event
                .playerUuid(playerRef.getUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .transferId(backup.transferId())
                .targetId(backup.destinationTargetId())
                .travelProfileId(backup.travelProfileId())
                .remoteConnectionAddress(backup.destinationConnectionAddress())
        );
        return new RecoveryStartResult(
            plan.remoteTravelStarted(),
            plan.message()
        );
    }

    /**
     * Returns whether recovery is currently enabled by policy.
     */
    public boolean isRecoveryEnabled() {
        return policyStore.isApplyInventoryBackupsEnabled();
    }

    /**
     * Enables or disables recovery.
     */
    public void setRecoveryEnabled(boolean enabled) throws IOException {
        policyStore.setApplyInventoryBackupsEnabled(enabled);
    }

    /**
     * Returns the configured backup limit per player.
     */
    public int getMaxBackupsPerPlayer() {
        return policyStore.getMaxBackupsPerPlayer();
    }

    /**
     * Updates the configured backup limit per player.
     */
    public void setMaxBackupsPerPlayer(int maxBackupsPerPlayer) throws IOException {
        policyStore.setMaxBackupsPerPlayer(maxBackupsPerPlayer);
        trimAllBackupsToPolicy();
    }

    /**
     * Throws if recovery is currently disabled by policy.
     */
    public void requireRecoveryEnabled() {
        if (!policyStore.isApplyInventoryBackupsEnabled()) {
            throw new IllegalStateException("Nexori inventory recovery is currently disabled by this server's admin.");
        }
    }

    /**
     * Returns whether the supplied inventory state contains any visible items worth transferring.
     */
    public boolean shouldTransferInventory(InventoryTransferState state) {
        return inventoryStateAnalyzer.shouldTransferInventory(state);
    }

    /**
     * Ensures the player inventory is empty before recovery overwrites it.
     */
    public void requireRecoveryInventoryEmpty(@Nonnull UUID playerUuid, Player player) {
        InventoryTransferState currentState = null;
        if (player != null) {
            currentState = inventorySnapshotService.capture(player);
        } else {
            currentState = playerSaveRepository.readInventoryState(playerUuid).orElse(null);
        }

        if (currentState != null) {
            int occupied = inventoryStateAnalyzer.occupiedVisibleSlots(currentState);
            int capacity = inventoryStateAnalyzer.totalVisibleCapacity(currentState);
            if (occupied > 0) {
                throw new IllegalStateException("Your current inventory is not empty (" + occupied + "/" + capacity + "). Empty it before trying Nexori recovery because recovery overwrites your current origin inventory.");
            }
        }
    }

    /**
     * Flushes any deferred runtime inventory state as soon as the player is fully connected.
     */
    public void handlePlayerConnect(@Nonnull PlayerConnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID playerUuid = playerRef.getUuid();
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        flushPendingRuntimeInventory(playerUuid, player);
    }

    /**
     * Teleports the player back to the recovery origin and resumes the recovery UI when a recovery query finishes.
     */
    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        Player player = event.getPlayer();
        if (player != null) {
            flushPendingRuntimeInventory(playerRef.getUuid(), player);
        }

        PendingRecoveryReturn pendingReturn = pendingRecoveryReturns.remove(playerRef.getUuid());
        if (pendingReturn == null) {
            return;
        }

        World world = Universe.get().getWorld(pendingReturn.originWorldName());
        Teleport teleport = world == null
            ? Teleport.createForPlayer(pendingReturn.originTransform().clone())
            : Teleport.createForPlayer(world, pendingReturn.originTransform().clone());
        Ref<EntityStore> playerRefStoreRef = event.getPlayerRef();
        playerRefStoreRef.getStore().addComponent(playerRefStoreRef, Teleport.getComponentType(), teleport);
        NexoriRecoveryPage.open(
            playerRefStoreRef,
            playerRefStoreRef.getStore(),
            playerRef,
            event.getPlayer(),
            this,
            pendingReturn.selectedTransferId(),
            pendingReturn.message()
        );
    }

    private void clearDestinationInventory(@Nonnull UUID playerUuid) {
        Optional<InventoryTransferState> current = playerSaveRepository.readInventoryState(playerUuid);
        if (current.isPresent()) {
            if (!playerSaveRepository.applyInventoryState(playerUuid, InventoryTransferState.emptyLike(current.get()))) {
                pendingRuntimeClears.add(playerUuid);
            }
            return;
        }

        pendingRuntimeClears.add(playerUuid);
    }

    private void applyInboundInventory(
        @Nonnull UUID playerUuid,
        String transferId,
        InventoryTransferState inventoryState,
        @Nonnull String sourceServerId,
        @Nonnull String sourceConnectionAddress
    ) throws IOException {
        if (transferId == null || transferId.isBlank()) {
            throw new IllegalArgumentException("APPLY_INVENTORY travel requires a transferId.");
        }
        if (inventoryState == null) {
            throw new IllegalArgumentException("APPLY_INVENTORY travel requires an inventory snapshot.");
        }

        InventoryTransferState currentState = playerSaveRepository.readInventoryState(playerUuid).orElse(null);
        if (currentState != null && shouldTransferInventory(currentState)) {
            saveLocalOverwriteBackup(
                playerUuid,
                sourceConnectionAddress.isBlank() ? sourceServerId : sourceConnectionAddress,
                "incoming_apply_inventory",
                TravelProfileType.APPLY_INVENTORY.id(),
                currentState
            );
        }

        if (!playerSaveRepository.applyInventoryState(playerUuid, inventoryState)) {
            pendingRuntimeApplies.put(playerUuid, inventoryState);
        }

        receiptStore.save(new InventoryTransferReceiptRecord(
            transferId,
            System.currentTimeMillis(),
            playerUuid,
            sourceServerId,
            sourceConnectionAddress
        ));
        logger.atInfo().log("Persisted Nexori APPLY_INVENTORY receipt " + transferId + " for " + playerUuid + ".");
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_RECEIPT_SAVE,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.TRANSFER_RECEIPT_SAVED,
            "Persisted an APPLY_INVENTORY transfer receipt on the destination server.",
            transferId,
            event -> event
                .playerUuid(playerUuid.toString())
                .transferId(transferId)
                .remoteServerId(sourceServerId)
                .remoteConnectionAddress(sourceConnectionAddress)
        );
    }

    private void trimBackupsForPlayer(@Nonnull UUID playerUuid) throws IOException {
        int maxBackups = policyStore.getMaxBackupsPerPlayer();
        List<InventoryTransferBackupRecord> backups = backupStore.listByPlayer(playerUuid);
        for (int index = maxBackups; index < backups.size(); index++) {
            backupStore.remove(backups.get(index).transferId());
        }
    }

    private void trimAllBackupsToPolicy() throws IOException {
        Set<UUID> players = backupStore.listAll().stream()
            .map(InventoryTransferBackupRecord::playerUuid)
            .collect(java.util.stream.Collectors.toSet());
        for (UUID playerUuid : players) {
            trimBackupsForPlayer(playerUuid);
        }
    }

    private void handleQuery(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        InventoryTransferReceiptQueryPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            InventoryTransferReceiptQueryPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null || payload.transferId() == null || payload.transferId().isBlank()) {
            return;
        }

        InventoryTransferQueryResult result = receiptStore.find(payload.transferId()).isPresent()
            ? InventoryTransferQueryResult.APPLIED
            : InventoryTransferQueryResult.NOT_FOUND;

        try {
            byte[] replyPayload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                REPLY_PAYLOAD_TYPE,
                new InventoryTransferReceiptReplyPayload(payload.transferId(), result.name()),
                Duration.ofSeconds(30)
            );
            event.referToServer(referralSource.host, referralSource.port, replyPayload);
            logger.atInfo().log("Answered Nexori inventory transfer query " + payload.transferId() + " with " + result + ".");
            diagnosticsService.record(
                DiagnosticsCategory.RECOVERY,
                DiagnosticsAction.RECOVERY_QUERY_ANSWER,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                result == InventoryTransferQueryResult.APPLIED
                    ? DiagnosticsReasonCode.RECOVERY_QUERY_ANSWERED_APPLIED
                    : DiagnosticsReasonCode.RECOVERY_QUERY_ANSWERED_NOT_FOUND,
                "Answered a Nexori inventory transfer recovery query.",
                payload.transferId(),
                diag -> diag
                    .transferId(payload.transferId())
                    .remoteConnectionAddress(referralSource.host + ":" + referralSource.port)
            );
        } catch (IOException | GeneralSecurityException exception) {
            logger.atWarning().withCause(exception).log("Failed to answer Nexori inventory transfer query " + payload.transferId() + ".");
        }
    }

    private void handleReply(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        InventoryTransferReceiptReplyPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            InventoryTransferReceiptReplyPayload.class
        );
        if (payload.transferId() == null || payload.transferId().isBlank()) {
            return;
        }

        PendingRecoveryQuery pendingQuery = pendingRecoveryQueries.remove(payload.transferId().trim().toLowerCase());
        InventoryRecoveryFinalizePlan pendingPlan = recoveryFinalizePlanner.planPending(
            event.getUuid(),
            pendingQuery != null,
            pendingQuery != null && pendingQuery.isExpired(),
            pendingQuery == null ? null : pendingQuery.playerUuid()
        );
        if (pendingPlan.action() == InventoryRecoveryFinalizePlan.Action.IGNORE) {
            return;
        }

        InventoryTransferBackupRecord backup = findPlayerBackup(event.getUuid(), payload.transferId()).orElse(null);
        InventoryRecoveryFinalizePlan plan = recoveryFinalizePlanner.planFinalization(
            payload.transferId(),
            backup,
            payload.result()
        );

        try {
            if (plan.shouldRestoreBackup() && backup != null) {
                restoreOriginBackup(backup);
            }
            if (plan.shouldRemoveBackup() && backup != null) {
                backupStore.remove(backup.transferId());
            }

            pendingRecoveryReturns.put(event.getUuid(), new PendingRecoveryReturn(
                pendingQuery.originWorldName(),
                pendingQuery.originTransform(),
                plan.pendingReturnTransferId(),
                plan.message()
            ));
            diagnosticsService.record(
                DiagnosticsCategory.RECOVERY,
                DiagnosticsAction.RECOVERY_FINALIZE,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                plan.result() == InventoryTransferQueryResult.APPLIED
                    ? DiagnosticsReasonCode.RECOVERY_QUERY_ANSWERED_APPLIED
                    : DiagnosticsReasonCode.ORIGIN_BACKUP_RESTORED,
                plan.message(),
                payload.transferId(),
                diag -> diag
                    .playerUuid(event.getUuid().toString())
                    .playerNameClaimed(event.getUsername())
                    .transferId(payload.transferId())
            );
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to finalize Nexori inventory recovery for " + payload.transferId() + ".");
            diagnosticsService.record(
                DiagnosticsCategory.RECOVERY,
                DiagnosticsAction.RECOVERY_FINALIZE,
                DiagnosticsOutcome.FAILED,
                DiagnosticsReasonClass.IO,
                DiagnosticsReasonCode.RECOVERY_FINALIZE_FAILED,
                "The Nexori recovery reply was received, but applying the local resolution failed: " + exception.getMessage(),
                payload.transferId(),
                diag -> diag
                    .playerUuid(event.getUuid().toString())
                    .playerNameClaimed(event.getUsername())
                    .transferId(payload.transferId())
            );
            pendingRecoveryReturns.put(event.getUuid(), new PendingRecoveryReturn(
                pendingQuery.originWorldName(),
                pendingQuery.originTransform(),
                payload.transferId(),
                "The Nexori recovery reply was received, but applying the local resolution failed: " + exception.getMessage()
            ));
        }
    }

    private void restoreOriginBackup(@Nonnull InventoryTransferBackupRecord backup) {
        if (!playerSaveRepository.applyInventoryState(backup.playerUuid(), backup.inventoryState())) {
            pendingRuntimeApplies.put(backup.playerUuid(), backup.inventoryState());
        }
        logger.atInfo().log("Restored Nexori inventory backup " + backup.transferId() + " for " + backup.playerUuid() + ".");
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_RESTORE_ORIGIN,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.ORIGIN_BACKUP_RESTORED,
            "Restored an origin inventory backup after recovery.",
            backup.transferId(),
            event -> event
                .playerUuid(backup.playerUuid().toString())
                .transferId(backup.transferId())
        );
    }

    private void restoreBackupForActivePlayer(@Nonnull PlayerRef playerRef, @Nonnull InventoryTransferBackupRecord backup) {
        boolean persisted = playerSaveRepository.applyInventoryState(backup.playerUuid(), backup.inventoryState());
        Player player = playerRef.getComponent(Player.getComponentType());
        if (player != null) {
            inventorySnapshotService.applyToPlayer(player, backup.inventoryState());
        } else if (!persisted) {
            pendingRuntimeApplies.put(backup.playerUuid(), backup.inventoryState());
        }
        logger.atInfo().log("Claimed Nexori local inventory backup " + backup.transferId() + " for " + backup.playerUuid() + ".");
        diagnosticsService.record(
            DiagnosticsCategory.RECOVERY,
            DiagnosticsAction.RECOVERY_CLAIM_LOCAL,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.LOCAL_BACKUP_CLAIMED,
            "Claimed a saved local inventory backup on this server.",
            backup.transferId(),
            event -> event
                .playerUuid(backup.playerUuid().toString())
                .playerNameClaimed(playerRef.getUsername())
                .transferId(backup.transferId())
        );
    }

    private void flushPendingRuntimeInventory(@Nonnull UUID playerUuid, @Nonnull Player player) {
        InventoryTransferState pendingApply = pendingRuntimeApplies.get(playerUuid);
        if (pendingApply != null) {
            if (!inventorySnapshotService.canApplyToPlayer(player, pendingApply)) {
                return;
            }
            pendingRuntimeApplies.remove(playerUuid);
            inventorySnapshotService.applyToPlayer(player, pendingApply);
            logger.atInfo().log("Applied deferred Nexori inventory transfer state for " + playerUuid + ".");
            return;
        }

        if (!pendingRuntimeClears.contains(playerUuid)) {
            return;
        }

        InventoryTransferState current = inventorySnapshotService.capture(player);
        InventoryTransferState emptied = InventoryTransferState.emptyLike(current);
        if (!inventorySnapshotService.canApplyToPlayer(player, emptied)) {
            return;
        }
        pendingRuntimeClears.remove(playerUuid);
        inventorySnapshotService.applyToPlayer(player, emptied);
        logger.atInfo().log("Applied deferred Nexori inventory clear for " + playerUuid + ".");
    }

    private record PendingRecoveryQuery(
        String transferId,
        UUID playerUuid,
        String originWorldName,
        Transform originTransform,
        long expiresAtEpochMillis
    ) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtEpochMillis;
        }
    }

    private record PendingRecoveryReturn(
        String originWorldName,
        Transform originTransform,
        String selectedTransferId,
        String message
    ) {
    }

    public record RecoveryStartResult(
        boolean remoteTravelStarted,
        @Nonnull String message
    ) {
    }

    private final class QueryHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return QUERY_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleQuery(event, referral);
        }
    }

    private final class ReplyHandler implements SecureReferralHandler {
        @Nonnull
        @Override
        public String payloadType() {
            return REPLY_PAYLOAD_TYPE;
        }

        @Override
        public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
            handleReply(event, referral);
        }
    }
}
