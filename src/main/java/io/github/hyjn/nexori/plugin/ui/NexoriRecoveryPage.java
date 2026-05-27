package io.github.hyjn.nexori.plugin.ui;

import au.ellie.hyui.builders.ButtonBuilder;
import au.ellie.hyui.builders.ContainerBuilder;
import au.ellie.hyui.builders.GroupBuilder;
import au.ellie.hyui.builders.HyUIAnchor;
import au.ellie.hyui.builders.HyUIPadding;
import au.ellie.hyui.builders.HyUIPatchStyle;
import au.ellie.hyui.builders.HyUIStyle;
import au.ellie.hyui.builders.LabelBuilder;
import au.ellie.hyui.builders.PageBuilder;
import au.ellie.hyui.builders.ReorderableListBuilder;
import au.ellie.hyui.types.ScrollbarStyle;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
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

public final class NexoriRecoveryPage {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private static final int PAGE_W = 1480;
    private static final int PAGE_H = 780;
    private static final int BODY_W = 1440;
    private static final int LEFT_W = 436;
    private static final int RIGHT_W = 988;
    private static final int PANEL_H = 640;

    private static final HyUIPatchStyle CARD_BG = new HyUIPatchStyle().setColor("#17273a");
    private static final HyUIPatchStyle ITEM_BG = new HyUIPatchStyle().setColor("#20354e");
    private static final HyUIPatchStyle STATUS_BG = new HyUIPatchStyle().setColor("#2d4f75");
    private static final HyUIPatchStyle SERVER_BUTTON_BG = new HyUIPatchStyle().setColor("#27405d");
    private static final HyUIPatchStyle SERVER_BUTTON_SELECTED_BG = new HyUIPatchStyle().setColor("#466f9f");
    private static final HyUIPatchStyle GOOD_BG = new HyUIPatchStyle().setColor("#1f5c35");
    private static final HyUIPatchStyle BAD_BG = new HyUIPatchStyle().setColor("#7b3742");

    private static final HyUIStyle TITLE = new HyUIStyle().setFontSize(17).setRenderBold(true).setTextColor("#f1f6ff");
    private static final HyUIStyle BODY = new HyUIStyle().setFontSize(14).setTextColor("#d6e5f7").setWrap(true);
    private static final HyUIStyle MUTED = new HyUIStyle().setFontSize(13).setTextColor("#8fa6c4").setWrap(true);
    private static final HyUIStyle LABEL = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#adc3de");
    private static final HyUIStyle GOOD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#7de3a6");
    private static final HyUIStyle BAD = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#ff8b9a");
    private static final HyUIStyle INFO = new HyUIStyle().setFontSize(13).setRenderBold(true).setTextColor("#8fc7ff");
    private static final ScrollbarStyle DEFAULT_SCROLLBAR = ScrollbarStyle.defaultExtraSpacingStyle()
        .withOnlyVisibleWhenHovered(false)
        .withSize(8)
        .withSpacing(4)
        .withBackground(new HyUIPatchStyle().setColor("#16283d"))
        .withHandle(new HyUIPatchStyle().setColor("#39577b"))
        .withHoveredHandle(new HyUIPatchStyle().setColor("#5d87bb"))
        .withDraggedHandle(new HyUIPatchStyle().setColor("#76a3dd"));

    private NexoriRecoveryPage() {
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
        if (player == null) {
            playerRef.sendMessage(Message.raw("nexorirecovery: could not resolve the live player entity."));
            return;
        }

        List<InventoryTransferBackupRecord> backups = inventoryTransferService.listBackups(playerRef.getUuid());
        String resolvedSelectedTransferId = resolveSelectedTransferId(backups, selectedTransferId);
        InventoryTransferBackupRecord selectedBackup = selectedBackup(backups, resolvedSelectedTransferId);

        ContainerBuilder root = ContainerBuilder.decoratedContainer()
            .withTitleText("NEXORI RECOVERY")
            .withAnchor(new HyUIAnchor().setWidth(PAGE_W).setHeight(PAGE_H));

        GroupBuilder content = GroupBuilder.group().withLayoutMode("Top").withPadding(HyUIPadding.all(14));
        content.addChild(label(
            "Review your recent inventory transfer backups and recover or claim them when a server-to-server trip did not finish the way you expected.",
            BODY,
            BODY_W
        ));
        content.addChild(spacerY(12));
        content.addChild(body(
            ref,
            store,
            playerRef,
            player,
            inventoryTransferService,
            backups,
            resolvedSelectedTransferId,
            selectedBackup,
            statusText
        ));
        root.addContentChild(content);

        PageBuilder.pageForPlayer(playerRef)
            .withLifetime(CustomPageLifetime.CanDismissOrCloseThroughInteraction)
            .addElement(root)
            .open(store);
    }

    private static GroupBuilder body(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull InventoryTransferService inventoryTransferService,
        @Nonnull List<InventoryTransferBackupRecord> backups,
        @Nonnull String selectedTransferId,
        InventoryTransferBackupRecord selectedBackup,
        @Nonnull String statusText
    ) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(BODY_W).setHeight(PANEL_H));

        GroupBuilder left = card(LEFT_W, PANEL_H, CARD_BG);
        left.addChild(label("Recent Backups", TITLE, LEFT_W - 32));
        left.addChild(spacerY(10));
        left.addChild(label(
            backups.isEmpty()
                ? "No Nexori inventory transfer backups are waiting on this server for your player."
                : backups.size() + " recoverable backup(s) are currently available for this player.",
            MUTED,
            LEFT_W - 32
        ));
        left.addChild(spacerY(12));

        int listHeight = Math.max(220, PANEL_H - 96);
        int listContentHeight = Math.max(listHeight, backups.isEmpty() ? 120 : backups.size() * 62 + 24);
        ReorderableListBuilder list = scrollList(LEFT_W - 32, listHeight, listContentHeight, "recovery-backups-list");
        if (backups.isEmpty()) {
            list.addChild(spacerY(12));
            list.addChild(label("Nothing to recover right now. New backups appear here whenever Nexori preserves inventory during a protected travel flow.", MUTED, LEFT_W - 56));
        } else {
            for (InventoryTransferBackupRecord backup : backups) {
                boolean selected = backup.transferId().equals(selectedTransferId);
                list.addChild(
                    (selected ? ButtonBuilder.textButton() : ButtonBuilder.secondaryTextButton())
                        .withText(describeBackupRow(backup))
                        .withBackground(selected ? SERVER_BUTTON_SELECTED_BG : SERVER_BUTTON_BG)
                        .withDisabled(selected)
                        .withAnchor(new HyUIAnchor().setWidth(LEFT_W - 32).setHeight(54))
                        .onClick((ignored, ctx) -> open(
                            ref,
                            store,
                            playerRef,
                            player,
                            inventoryTransferService,
                            backup.transferId(),
                            statusText
                        ))
                );
                list.addChild(spacerY(8));
            }
        }
        left.addChild(list);

        GroupBuilder right = card(RIGHT_W, PANEL_H, CARD_BG);
        buildDetails(
            ref,
            store,
            playerRef,
            player,
            inventoryTransferService,
            selectedTransferId,
            selectedBackup,
            statusText,
            right
        );

        row.addChild(left);
        row.addChild(spacerX(16));
        row.addChild(right);
        return row;
    }

    private static void buildDetails(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull InventoryTransferService inventoryTransferService,
        @Nonnull String selectedTransferId,
        InventoryTransferBackupRecord selectedBackup,
        @Nonnull String statusText,
        @Nonnull GroupBuilder right
    ) {
        right.addChild(label("Backup Details", TITLE, RIGHT_W - 32));
        right.addChild(spacerY(10));

        int detailsHostHeight = PANEL_H - 58;
        int detailsContentHeight = selectedBackup == null
            ? Math.max(detailsHostHeight, 120 + (statusText.isBlank() ? 0 : 76))
            : Math.max(
                detailsHostHeight + 40,
                (statusText.isBlank() ? 0 : 76)
                    + 208 + 12
                    + 84 + 12
                    + 240
            );
        ReorderableListBuilder detailsHost = scrollList(
            RIGHT_W - 32,
            detailsHostHeight,
            detailsContentHeight,
            "recovery-details-scroll"
        );

        if (!statusText.isBlank()) {
            detailsHost.addChild(statusPanel(statusText, RIGHT_W - 32));
            detailsHost.addChild(spacerY(12));
        }

        if (selectedBackup == null) {
            detailsHost.addChild(label("Select a backup on the left to inspect its destination, travel profile, and recovery action.", MUTED, RIGHT_W - 32));
            right.addChild(detailsHost);
            return;
        }

        GroupBuilder identityBlock = card(RIGHT_W - 32, 208, ITEM_BG);
        identityBlock.addChild(label("Selected Backup", TITLE, RIGHT_W - 64));
        identityBlock.addChild(spacerY(10));
        identityBlock.addChild(coloredStat("Action", actionLabel(selectedBackup), RIGHT_W - 32, backupMode(selectedBackup) == InventoryTransferBackupMode.LOCAL_RESTORE ? GOOD : INFO));
        identityBlock.addChild(spacerY(6));
        identityBlock.addChild(stat("Created", TIME_FORMATTER.format(Instant.ofEpochMilli(selectedBackup.createdAtEpochMs())), RIGHT_W - 32));
        identityBlock.addChild(spacerY(6));
        identityBlock.addChild(stat("Destination", displayDestination(selectedBackup), RIGHT_W - 32));
        identityBlock.addChild(spacerY(6));
        identityBlock.addChild(stat("Target", displayOrNone(selectedBackup.destinationTargetId()), RIGHT_W - 32));
        identityBlock.addChild(spacerY(6));
        identityBlock.addChild(stat("Travel Profile", displayOrNone(selectedBackup.travelProfileId()), RIGHT_W - 32));
        identityBlock.addChild(spacerY(6));
        identityBlock.addChild(stat("Transfer ID", selectedBackup.transferId(), RIGHT_W - 32));
        detailsHost.addChild(identityBlock);
        detailsHost.addChild(spacerY(12));

        GroupBuilder inventoryBlock = card(RIGHT_W - 32, 84, ITEM_BG);
        inventoryBlock.addChild(label("Inventory Snapshot", TITLE, RIGHT_W - 64));
        inventoryBlock.addChild(spacerY(10));
        inventoryBlock.addChild(stat("Captured Inventory", summarizeInventory(selectedBackup), RIGHT_W - 32));
        detailsHost.addChild(inventoryBlock);
        detailsHost.addChild(spacerY(12));

        GroupBuilder actionBlock = card(RIGHT_W - 32, 240, ITEM_BG);
        actionBlock.addChild(label("Recovery Action", TITLE, RIGHT_W - 64));
        actionBlock.addChild(spacerY(10));
        actionBlock.addChild(label(recoveryHint(selectedBackup), MUTED, RIGHT_W - 64));
        actionBlock.addChild(spacerY(18));
        GroupBuilder actionRow = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(RIGHT_W - 64).setHeight(42));
        int actionWidth = 220;
        actionRow.addChild(spacerX(Math.max(0, ((RIGHT_W - 64) - actionWidth) / 2)));
        actionRow.addChild(
            ButtonBuilder.textButton()
                .withText(actionLabel(selectedBackup))
                .withAnchor(new HyUIAnchor().setWidth(actionWidth).setHeight(42))
                .onClick((ignored, ctx) -> attemptRecovery(
                    ref,
                    store,
                    playerRef,
                    player,
                    inventoryTransferService,
                    selectedBackup,
                    selectedTransferId
                ))
        );
        actionBlock.addChild(actionRow);
        detailsHost.addChild(actionBlock);
        right.addChild(detailsHost);
    }

    private static void attemptRecovery(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player,
        @Nonnull InventoryTransferService inventoryTransferService,
        @Nonnull InventoryTransferBackupRecord backup,
        @Nonnull String selectedTransferId
    ) {
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

            Rotation3f rotation = transformComponent.getRotation();
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
                playerRef.sendMessage(Message.raw(result.message()));
            } else {
                open(ref, store, playerRef, player, inventoryTransferService, "", result.message());
            }
        } catch (IllegalArgumentException exception) {
            open(ref, store, playerRef, player, inventoryTransferService, selectedTransferId, exception.getMessage());
        } catch (IllegalStateException exception) {
            open(ref, store, playerRef, player, inventoryTransferService, selectedTransferId, exception.getMessage());
        } catch (IOException | GeneralSecurityException exception) {
            open(ref, store, playerRef, player, inventoryTransferService, selectedTransferId, "Failed to start Nexori inventory recovery: " + exception.getMessage());
        }
    }

    @Nonnull
    private static GroupBuilder statusPanel(@Nonnull String statusText, int width) {
        HyUIPatchStyle background = STATUS_BG;
        HyUIStyle style = INFO;
        String lowered = statusText.toLowerCase();
        if (lowered.contains("failed")
            || lowered.contains("could not")
            || lowered.contains("disabled")
            || lowered.contains("not empty")) {
            background = BAD_BG;
            style = BAD;
        } else if (lowered.contains("restored")
            || lowered.contains("claimed")
            || lowered.contains("completed")
            || lowered.contains("already landed")) {
            background = GOOD_BG;
            style = GOOD;
        }

        GroupBuilder panel = card(width, 64, background);
        panel.addChild(label(statusText, style, width - 32));
        return panel;
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

    private static InventoryTransferBackupRecord selectedBackup(
        @Nonnull List<InventoryTransferBackupRecord> backups,
        @Nonnull String selectedTransferId
    ) {
        for (InventoryTransferBackupRecord backup : backups) {
            if (backup.transferId().equals(selectedTransferId)) {
                return backup;
            }
        }
        return backups.isEmpty() ? null : backups.getFirst();
    }

    @Nonnull
    private static String describeBackupRow(@Nonnull InventoryTransferBackupRecord backup) {
        return TIME_FORMATTER.format(Instant.ofEpochMilli(backup.createdAtEpochMs()))
            + " | " + actionLabel(backup)
            + " | " + summarizeInventory(backup);
    }

    @Nonnull
    private static String displayDestination(@Nonnull InventoryTransferBackupRecord backup) {
        if (!backup.destinationConnectionAddress().isBlank()) {
            return backup.destinationConnectionAddress();
        }
        return backupMode(backup) == InventoryTransferBackupMode.LOCAL_RESTORE
            ? "This server"
            : "<unknown>";
    }

    @Nonnull
    private static String displayOrNone(@Nonnull String value) {
        return value.isBlank() ? "<none>" : value;
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
            ? "Claim Backup"
            : "Try Recover";
    }

    @Nonnull
    private static String recoveryHint(@Nonnull InventoryTransferBackupRecord backup) {
        if (backupMode(backup) == InventoryTransferBackupMode.LOCAL_RESTORE) {
            return "Claim restores this backup directly on this server because Nexori created it before overwriting an existing destination inventory. Your current inventory must be empty before you claim it.";
        }
        return "Try Recover asks the destination server whether this inventory transfer already landed. If it did not, Nexori restores the backup on this origin server. If it did, the backup is cleared because the transfer already succeeded. Your current inventory must be empty before you try recovery.";
    }

    @Nonnull
    private static InventoryTransferBackupMode backupMode(@Nonnull InventoryTransferBackupRecord backup) {
        return InventoryTransferBackupMode.parse(backup.backupModeId());
    }

    private static GroupBuilder card(int width, int height, HyUIPatchStyle bg) {
        return GroupBuilder.group()
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withPadding(HyUIPadding.all(16))
            .withBackground(bg);
    }

    private static ReorderableListBuilder scrollList(int width, int height, int contentHeight, @Nonnull String id) {
        return ReorderableListBuilder.reorderableList()
            .withId(id)
            .withLayoutMode("Top")
            .withAnchor(new HyUIAnchor().setWidth(width).setHeight(height))
            .withContentHeight(contentHeight)
            .withKeepScrollPosition(true)
            .withPadding(HyUIPadding.all(0))
            .withBackground(new HyUIPatchStyle().setColor("#132235"))
            .withScrollbarStyle(DEFAULT_SCROLLBAR);
    }

    private static LabelBuilder label(@Nonnull String text, @Nonnull HyUIStyle style, int width) {
        return LabelBuilder.label()
            .withText(text)
            .withAnchor(new HyUIAnchor().setWidth(width))
            .withStyle(style);
    }

    private static GroupBuilder stat(@Nonnull String label, @Nonnull String value, int width) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(24));
        row.addChild(LabelBuilder.label().withText(label).withAnchor(new HyUIAnchor().setWidth(180)).withStyle(LABEL));
        row.addChild(LabelBuilder.label().withText(value).withAnchor(new HyUIAnchor().setWidth(width - 214)).withStyle(BODY));
        return row;
    }

    private static GroupBuilder coloredStat(@Nonnull String label, @Nonnull String value, int width, @Nonnull HyUIStyle valueStyle) {
        GroupBuilder row = GroupBuilder.group().withLayoutMode("Left").withAnchor(new HyUIAnchor().setWidth(width - 32).setHeight(24));
        row.addChild(LabelBuilder.label().withText(label).withAnchor(new HyUIAnchor().setWidth(180)).withStyle(LABEL));
        row.addChild(LabelBuilder.label().withText(value).withAnchor(new HyUIAnchor().setWidth(width - 214)).withStyle(valueStyle));
        return row;
    }

    private static GroupBuilder spacerX(int width) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(width).setHeight(1));
    }

    private static GroupBuilder spacerY(int height) {
        return GroupBuilder.group().withAnchor(new HyUIAnchor().setWidth(1).setHeight(height));
    }
}
