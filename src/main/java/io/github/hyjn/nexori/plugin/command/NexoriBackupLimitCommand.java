package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriBackupLimitCommand extends CommandBase {

    private static final int MIN_LIMIT = 1;
    private static final int MAX_LIMIT = 50;

    private final NexoriPlugin plugin;
    private final InventoryTransferService inventoryTransferService;
    private final RequiredArg<Integer> limitArg;

    public NexoriBackupLimitCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull InventoryTransferService inventoryTransferService
    ) {
        super("nexoribackuplimit", "Sets how many Nexori inventory transfer backups each player can keep.");
        this.plugin = plugin;
        this.inventoryTransferService = inventoryTransferService;
        this.setPermissionGroup(GameMode.Adventure);
        this.limitArg = this.withRequiredArg("limit", "Maximum backups per player.", ArgTypes.INTEGER);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String permission = plugin.getBasePermission() + ".admin";
        if (!ctx.sender().hasPermission(permission)) {
            ctx.sendMessage(Message.raw("You need the permission '" + permission + "' to manage the Nexori backup limit."));
            return;
        }

        int limit = ctx.get(limitArg);
        if (limit < MIN_LIMIT || limit > MAX_LIMIT) {
            ctx.sendMessage(Message.raw("Use a Nexori backup limit between " + MIN_LIMIT + " and " + MAX_LIMIT + "."));
            return;
        }

        try {
            inventoryTransferService.setMaxBackupsPerPlayer(limit);
            ctx.sendMessage(Message.raw("Nexori will now keep up to " + limit + " inventory backup(s) per player. Older backups beyond that limit were trimmed if needed."));
        } catch (IOException exception) {
            ctx.sendMessage(Message.raw("Failed to update the Nexori backup limit: " + exception.getMessage()));
        }
    }
}
