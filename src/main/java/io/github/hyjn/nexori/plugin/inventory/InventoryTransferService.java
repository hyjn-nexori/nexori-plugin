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
    private final Map<UUID, InventoryTransferState> pendingRuntimeApplies = new ConcurrentHashMap<>();
    private final Set<UUID> pendingRuntimeClears = ConcurrentHashMap.newKeySet();
    private final Map<String, PendingRecoveryQuery> pendingRecoveryQueries = new ConcurrentHashMap<>();
    private final Map<UUID, PendingRecoveryReturn> pendingRecoveryReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler queryHandler = new QueryHandler();
    private final SecureReferralHandler replyHandler = new ReplyHandler();

    public InventoryTransferService(
        @Nonnull HytaleLogger logger,
        @Nonnull InventoryTransferBackupStore backupStore,
        @Nonnull InventoryTransferReceiptStore receiptStore,
        @Nonnull InventoryTransferPolicyStore policyStore,
        @Nonnull PlayerSaveRepository playerSaveRepository,
        @Nonnull InventorySnapshotService inventorySnapshotService,
        @Nonnull SecureReferralService secureReferralService
    ) {
        this.logger = logger;
        this.backupStore = backupStore;
        this.receiptStore = receiptStore;
        this.policyStore = policyStore;
        this.playerSaveRepository = playerSaveRepository;
        this.inventorySnapshotService = inventorySnapshotService;
        this.secureReferralService = secureReferralService;
    }

    @Nonnull
    public SecureReferralHandler queryHandler() {
        return queryHandler;
    }

    @Nonnull
    public SecureReferralHandler replyHandler() {
        return replyHandler;
    }

    @Nonnull
    public InventoryTransferState captureCurrentInventory(@Nonnull PlayerRef playerRef) {
        Player player = playerRef.getComponent(Player.getComponentType());
        if (player == null) {
            throw new IllegalStateException("Could not read the live player inventory for Nexori APPLY_INVENTORY travel.");
        }
        return inventorySnapshotService.capture(player);
    }

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
        return saved;
    }

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
        return saved;
    }

    public void clearOriginInventory(@Nonnull PlayerRef playerRef, @Nonnull InventoryTransferState sourceInventory) {
        InventoryTransferState emptied = InventoryTransferState.emptyLike(sourceInventory);
        playerSaveRepository.applyInventoryState(playerRef.getUuid(), emptied);

        Player player = playerRef.getComponent(Player.getComponentType());
        if (player != null) {
            inventorySnapshotService.applyToPlayer(player, emptied);
        }

        logger.atInfo().log("Cleared origin inventory for Nexori APPLY_INVENTORY travel player=" + playerRef.getUsername());
    }

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

    @Nonnull
    public List<InventoryTransferBackupRecord> listBackups(@Nonnull UUID playerUuid) {
        return backupStore.listByPlayer(playerUuid);
    }

    @Nonnull
    public Optional<InventoryTransferBackupRecord> findPlayerBackup(@Nonnull UUID playerUuid, @Nonnull String transferId) {
        return backupStore.find(transferId)
            .filter(record -> playerUuid.equals(record.playerUuid()));
    }

    @Nonnull
    public RecoveryStartResult startRecovery(
        @Nonnull PlayerRef playerRef,
        @Nonnull String transferId,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform
    ) throws IOException, GeneralSecurityException {
        requireRecoveryEnabled();
        InventoryTransferBackupRecord backup = findPlayerBackup(playerRef.getUuid(), transferId)
            .orElseThrow(() -> new IllegalArgumentException("That Nexori inventory backup does not exist for your player."));

        InventoryTransferBackupMode backupMode = InventoryTransferBackupMode.parse(backup.backupModeId());
        if (backupMode == InventoryTransferBackupMode.LOCAL_RESTORE) {
            restoreBackupForActivePlayer(playerRef, backup);
            backupStore.remove(backup.transferId());
            return new RecoveryStartResult(
                false,
                "Nexori claim restored your saved local inventory backup on this server."
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
        return new RecoveryStartResult(
            true,
            "Started Nexori inventory recovery query. If the destination is reachable, you will be sent there and back to resolve it."
        );
    }

    public boolean isRecoveryEnabled() {
        return policyStore.isApplyInventoryBackupsEnabled();
    }

    public void setRecoveryEnabled(boolean enabled) throws IOException {
        policyStore.setApplyInventoryBackupsEnabled(enabled);
    }

    public int getMaxBackupsPerPlayer() {
        return policyStore.getMaxBackupsPerPlayer();
    }

    public void setMaxBackupsPerPlayer(int maxBackupsPerPlayer) throws IOException {
        policyStore.setMaxBackupsPerPlayer(maxBackupsPerPlayer);
        trimAllBackupsToPolicy();
    }

    public void requireRecoveryEnabled() {
        if (!policyStore.isApplyInventoryBackupsEnabled()) {
            throw new IllegalStateException("Nexori inventory recovery is currently disabled by this server's admin.");
        }
    }

    public boolean shouldTransferInventory(InventoryTransferState state) {
        return state != null && occupiedVisibleSlots(state) > 0;
    }

    public void requireRecoveryInventoryEmpty(@Nonnull UUID playerUuid, Player player) {
        InventoryTransferState currentState = null;
        if (player != null) {
            currentState = inventorySnapshotService.capture(player);
        } else {
            currentState = playerSaveRepository.readInventoryState(playerUuid).orElse(null);
        }

        if (currentState != null) {
            int occupied = occupiedVisibleSlots(currentState);
            int capacity = totalVisibleCapacity(currentState);
            if (occupied > 0) {
                throw new IllegalStateException("Your current inventory is not empty (" + occupied + "/" + capacity + "). Empty it before trying Nexori recovery because recovery overwrites your current origin inventory.");
            }
        }
    }

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

        InventoryTransferState pendingApply = pendingRuntimeApplies.remove(playerUuid);
        if (pendingApply != null) {
            inventorySnapshotService.applyToPlayer(player, pendingApply);
            logger.atInfo().log("Applied deferred Nexori inventory transfer state for " + playerUuid + ".");
            return;
        }

        if (pendingRuntimeClears.remove(playerUuid)) {
            InventoryTransferState current = inventorySnapshotService.capture(player);
            inventorySnapshotService.applyToPlayer(player, InventoryTransferState.emptyLike(current));
            logger.atInfo().log("Applied deferred Nexori inventory clear for " + playerUuid + ".");
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
        if (pendingQuery == null || pendingQuery.isExpired() || !pendingQuery.playerUuid().equals(event.getUuid())) {
            return;
        }

        InventoryTransferBackupRecord backup = findPlayerBackup(event.getUuid(), payload.transferId()).orElse(null);
        InventoryTransferQueryResult result = InventoryTransferQueryResult.parse(payload.result());

        try {
            String message;
            if (result == InventoryTransferQueryResult.APPLIED) {
                if (backup != null) {
                    backupStore.remove(backup.transferId());
                }
                message = "Nexori recovery confirmed that the destination already applied your inventory transfer.";
            } else {
                if (backup == null) {
                    message = "Nexori recovery could not restore that backup because it no longer exists on this origin server.";
                } else {
                    restoreOriginBackup(backup);
                    backupStore.remove(backup.transferId());
                    message = "Nexori recovery restored your origin inventory because the destination did not report an applied transfer.";
                }
            }

            pendingRecoveryReturns.put(event.getUuid(), new PendingRecoveryReturn(
                pendingQuery.originWorldName(),
                pendingQuery.originTransform(),
                backup != null && result != InventoryTransferQueryResult.APPLIED ? backup.transferId() : "",
                message
            ));
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to finalize Nexori inventory recovery for " + payload.transferId() + ".");
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
    }

    private static int occupiedVisibleSlots(@Nonnull InventoryTransferState state) {
        return occupiedSlots(state.storage())
            + occupiedSlots(state.armor())
            + occupiedSlots(state.hotBar())
            + occupiedSlots(state.utility())
            + occupiedSlots(state.backpack());
    }

    private static int totalVisibleCapacity(@Nonnull InventoryTransferState state) {
        return Math.max(state.storage().capacity(), 0)
            + Math.max(state.armor().capacity(), 0)
            + Math.max(state.hotBar().capacity(), 0)
            + Math.max(state.utility().capacity(), 0)
            + Math.max(state.backpack().capacity(), 0);
    }

    private static int occupiedSlots(@Nonnull ContainerTransferState container) {
        return (int) container.items().values().stream()
            .filter(item -> item != null && item.quantity() > 0)
            .count();
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
