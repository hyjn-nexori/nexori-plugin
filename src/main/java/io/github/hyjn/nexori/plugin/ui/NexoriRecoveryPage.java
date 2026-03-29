package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.inventory.ContainerTransferState;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupMode;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class NexoriRecoveryPage extends InteractiveCustomUIPage<NexoriRecoveryPage.PageData> {

    private static final int BACKUP_INDEX_BASE = 1000;
    private static final int ACTION_RECOVER_INDEX = 2000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final PlayerRef playerRef;
    private final InventoryTransferService inventoryTransferService;
    private final List<InventoryTransferBackupRecord> backups;
    private final String selectedTransferId;
    private final String statusText;

    private NexoriRecoveryPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull InventoryTransferService inventoryTransferService,
        @Nonnull List<InventoryTransferBackupRecord> backups,
        @Nonnull String selectedTransferId,
        @Nonnull String statusText
    ) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.playerRef = playerRef;
        this.inventoryTransferService = inventoryTransferService;
        this.backups = backups;
        this.selectedTransferId = selectedTransferId;
        this.statusText = statusText;
    }

    public static void open(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        Player player,
        @Nonnull InventoryTransferService inventoryTransferService,
        String selectedTransferId,
        @Nonnull String statusText
    ) {
        PageManager pages = player == null ? null : player.getPageManager();
        if (pages == null) {
            return;
        }

        List<InventoryTransferBackupRecord> backups = inventoryTransferService.listBackups(playerRef.getUuid());
        String resolvedSelectedTransferId = resolveSelectedTransferId(backups, selectedTransferId);
        pages.openCustomPage(
            ref,
            store,
            new NexoriRecoveryPage(
                playerRef,
                inventoryTransferService,
                backups,
                resolvedSelectedTransferId,
                statusText
            )
        );
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commands,
        @Nonnull UIEventBuilder events,
        @Nonnull Store<EntityStore> store
    ) {
        InventoryTransferBackupRecord selectedBackup = selectedBackup();

        commands.append("Pages/Nexori/NexoriRecovery.ui");
        commands.set("#BackupCountText.Text", backups.size() + " recoverable backup(s)");
        commands.set("#StatusText.Text", statusText);
        commands.set("#StatusText.Visible", !statusText.isBlank());
        commands.set("#EmptyHint.Visible", backups.isEmpty());
        commands.set("#EmptyHint.Text", backups.isEmpty()
            ? "No Nexori inventory transfer backups are waiting on this server for your player."
            : "");

        bindIndex(events, "#TryRecoverButton", ACTION_RECOVER_INDEX);
        commands.set("#TryRecoverButton.Visible", selectedBackup != null);
        commands.set("#TryRecoverButtonLabel.Text", selectedBackup == null ? "Try Recover" : actionLabel(selectedBackup));

        for (int i = 0; i < backups.size(); i++) {
            InventoryTransferBackupRecord backup = backups.get(i);
            new NexoriRecoveryEntryElement(
                TIME_FORMATTER.format(Instant.ofEpochMilli(backup.createdAtEpochMs())),
                summarizeInventory(backup),
                backup.transferId().equals(selectedTransferId)
            ).addButton(commands, events, "#RecoveryList[" + i + "]", playerRef);
            bindIndex(events, "#RecoveryList[" + i + "]", BACKUP_INDEX_BASE + i);
        }

        commands.set("#SelectedTransferId.Text", selectedBackup == null ? "<none>" : selectedBackup.transferId());
        commands.set("#SelectedCreatedAt.Text", selectedBackup == null ? "<none>" : TIME_FORMATTER.format(Instant.ofEpochMilli(selectedBackup.createdAtEpochMs())));
        commands.set("#SelectedDestination.Text", selectedBackup == null ? "<none>" : selectedBackup.destinationConnectionAddress());
        commands.set("#SelectedTarget.Text", selectedBackup == null ? "<none>" : selectedBackup.destinationTargetId());
        commands.set("#SelectedProfile.Text", selectedBackup == null ? "<none>" : selectedBackup.travelProfileId());
        commands.set("#SelectedInventoryCount.Text", selectedBackup == null ? "<none>" : summarizeInventory(selectedBackup));
        commands.set("#SelectedRecoveryHint.Text", selectedBackup == null ? ""
            : recoveryHint(selectedBackup));
    }

    @Override
    public void handleDataEvent(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PageData data
    ) {
        Integer index = parseIndex(data.indexRaw);
        if (index == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }

        if (index >= BACKUP_INDEX_BASE && index < BACKUP_INDEX_BASE + backups.size()) {
            InventoryTransferBackupRecord selected = backups.get(index - BACKUP_INDEX_BASE);
            open(ref, store, playerRef, player, inventoryTransferService, selected.transferId(), statusText);
            return;
        }

        if (index != ACTION_RECOVER_INDEX) {
            return;
        }

        InventoryTransferBackupRecord backup = selectedBackup();
        if (backup == null) {
            open(ref, store, playerRef, player, inventoryTransferService, selectedTransferId, "Select a backup first.");
            return;
        }

        try {
            inventoryTransferService.requireRecoveryEnabled();
            inventoryTransferService.requireRecoveryInventoryEmpty(playerRef.getUuid(), player);

            TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
            if (transformComponent == null) {
                throw new IllegalStateException("Could not read your live position for Nexori recovery return.");
            }

            World world = player.getWorld();
            if (world == null) {
                throw new IllegalStateException("Could not read your live world for Nexori recovery return.");
            }

            Vector3f rotation = transformComponent.getRotation();
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
            if (headRotation != null) {
                rotation = headRotation.getRotation();
            }

            InventoryTransferService.RecoveryStartResult result = inventoryTransferService.startRecovery(
                playerRef,
                backup.transferId(),
                world.getName(),
                new Transform(transformComponent.getPosition(), rotation)
            );
            if (result.remoteTravelStarted()) {
                player.sendMessage(Message.raw(result.message()));
            } else {
                open(ref, store, playerRef, player, inventoryTransferService, "", result.message());
            }
        } catch (IllegalArgumentException exception) {
            open(ref, store, playerRef, player, inventoryTransferService, backup.transferId(), exception.getMessage());
        } catch (IllegalStateException exception) {
            open(ref, store, playerRef, player, inventoryTransferService, backup.transferId(), exception.getMessage());
        } catch (IOException | GeneralSecurityException exception) {
            open(ref, store, playerRef, player, inventoryTransferService, backup.transferId(), "Failed to start Nexori inventory recovery: " + exception.getMessage());
        }
    }

    private InventoryTransferBackupRecord selectedBackup() {
        for (InventoryTransferBackupRecord backup : backups) {
            if (backup.transferId().equals(selectedTransferId)) {
                return backup;
            }
        }
        return backups.isEmpty() ? null : backups.getFirst();
    }

    private void bindIndex(@Nonnull UIEventBuilder events, @Nonnull String selector, int index) {
        EventData data = EventData.of("Index", Integer.toString(index));
        String[] selectorCandidates = new String[] {selector, selector + " #Button"};

        for (String candidate : selectorCandidates) {
            try {
                events.addEventBinding(CustomUIEventBindingType.Activating, candidate, data, false);
                return;
            } catch (Exception ignored) {
            }
        }
    }

    private Integer parseIndex(String rawIndex) {
        if (rawIndex == null || rawIndex.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(rawIndex.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nonnull
    private static String resolveSelectedTransferId(
        @Nonnull List<InventoryTransferBackupRecord> backups,
        String selectedTransferId
    ) {
        String normalized = selectedTransferId == null ? "" : selectedTransferId.trim().toLowerCase();
        if (!normalized.isBlank()) {
            for (InventoryTransferBackupRecord backup : backups) {
                if (backup.transferId().equals(normalized)) {
                    return normalized;
                }
            }
        }
        return backups.isEmpty() ? "" : backups.getFirst().transferId();
    }

    @Nonnull
    private static String summarizeInventory(@Nonnull InventoryTransferBackupRecord backup) {
        int occupied = occupied(backup.inventoryState().storage())
            + occupied(backup.inventoryState().armor())
            + occupied(backup.inventoryState().hotBar())
            + occupied(backup.inventoryState().utility())
            + occupied(backup.inventoryState().backpack());
        int capacity = Math.max(backup.inventoryState().storage().capacity(), 0)
            + Math.max(backup.inventoryState().armor().capacity(), 0)
            + Math.max(backup.inventoryState().hotBar().capacity(), 0)
            + Math.max(backup.inventoryState().utility().capacity(), 0)
            + Math.max(backup.inventoryState().backpack().capacity(), 0);
        return occupied + "/" + capacity;
    }

    private static int occupied(@Nonnull ContainerTransferState container) {
        return (int) container.items().values().stream()
            .filter(item -> item != null && item.quantity() > 0)
            .count();
    }

    @Nonnull
    private static String actionLabel(@Nonnull InventoryTransferBackupRecord backup) {
        return backupMode(backup) == InventoryTransferBackupMode.LOCAL_RESTORE
            ? "Claim"
            : "Try Recover";
    }

    @Nonnull
    private static String recoveryHint(@Nonnull InventoryTransferBackupRecord backup) {
        if (backupMode(backup) == InventoryTransferBackupMode.LOCAL_RESTORE) {
            return "Claim restores this backup directly on this server because it was created before Nexori overwrote an existing destination inventory. Your current inventory must be empty before you claim it.";
        }
        return "Try Recover asks the destination server whether it applied this inventory transfer. If it says no, Nexori restores the backup on this origin server. If it says yes, the backup is cleared because the transfer already landed. Your current inventory must be empty before you try recovery.";
    }

    @Nonnull
    private static InventoryTransferBackupMode backupMode(@Nonnull InventoryTransferBackupRecord backup) {
        return InventoryTransferBackupMode.parse(backup.backupModeId());
    }

    public static final class PageData {

        private static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(
                new KeyedCodec<>("Index", Codec.STRING),
                (pageData, indexRaw) -> pageData.indexRaw = indexRaw,
                pageData -> pageData.indexRaw
            )
            .add()
            .build();

        private String indexRaw = "";
    }
}
