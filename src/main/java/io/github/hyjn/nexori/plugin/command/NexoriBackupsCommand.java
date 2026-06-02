package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.util.List;

public final class NexoriBackupsCommand extends NexoriSelfServicePlayerCommand {

    private final InventoryTransferService inventoryTransferService;

    public NexoriBackupsCommand(@Nonnull InventoryTransferService inventoryTransferService) {
        super("nexoribackups", "Lists your recent Nexori inventory transfer backups.");
        this.inventoryTransferService = inventoryTransferService;
        // Self-service: lists only the caller's own backups (scoped by their UUID). Gated by
        // isRecoveryEnabled(); no OP group / admin permission required.
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        if (!inventoryTransferService.isRecoveryEnabled()) {
            context.sendMessage(Message.raw("Nexori inventory recovery is currently disabled by this server's admin."));
            return;
        }

        List<InventoryTransferBackupRecord> backups = inventoryTransferService.listBackups(playerRef.getUuid());
        if (backups.isEmpty()) {
            context.sendMessage(Message.raw("You do not have any Nexori inventory transfer backups on this server."));
            return;
        }

        context.sendMessage(Message.raw("Your Nexori inventory transfer backups:"));
        for (InventoryTransferBackupRecord backup : backups) {
            context.sendMessage(Message.raw("- " + backup.transferId()
                + " -> " + backup.destinationConnectionAddress()
                + " target=" + backup.destinationTargetId()
                + " profile=" + backup.travelProfileId()
                + " createdAt=" + Instant.ofEpochMilli(backup.createdAtEpochMs())));
        }
    }
}
