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
import java.util.Locale;

public final class NexoriRecoveryModeCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final InventoryTransferService inventoryTransferService;
    private final RequiredArg<String> actionArg;

    public NexoriRecoveryModeCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull InventoryTransferService inventoryTransferService
    ) {
        super("nexorirecoverymode", "Enables, disables, or shows Nexori inventory recovery access.");
        this.plugin = plugin;
        this.inventoryTransferService = inventoryTransferService;
        this.setPermissionGroup(GameMode.Adventure);
        this.actionArg = this.withRequiredArg("action", "status, enable, or disable", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String permission = plugin.getBasePermission() + ".admin";
        if (!ctx.sender().hasPermission(permission)) {
            ctx.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori inventory recovery mode."));
            return;
        }

        String action = ctx.get(actionArg).trim().toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "status" -> ctx.sendMessage(Message.raw(
                    "Nexori inventory recovery is currently "
                        + (inventoryTransferService.isRecoveryEnabled() ? "ENABLED" : "DISABLED") + "."
                ));
                case "enable", "on", "true" -> {
                    inventoryTransferService.setRecoveryEnabled(true);
                    ctx.sendMessage(Message.raw("Nexori inventory recovery is now ENABLED."));
                }
                case "disable", "off", "false" -> {
                    inventoryTransferService.setRecoveryEnabled(false);
                    ctx.sendMessage(Message.raw("Nexori inventory recovery is now DISABLED."));
                }
                default -> {
                    ctx.sendMessage(Message.raw("Unknown action '" + action + "'."));
                    ctx.sendMessage(Message.raw("Use /nexorirecoverymode status, /nexorirecoverymode enable, or /nexorirecoverymode disable."));
                }
            }
        } catch (IOException exception) {
            ctx.sendMessage(Message.raw("Failed to update Nexori recovery mode: " + exception.getMessage()));
        }
    }
}
