package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriPortalUnbindCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final TriggerBindingService triggerBindingService;
    private final RequiredArg<String> portalIdArg;

    public NexoriPortalUnbindCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull TriggerBindingService triggerBindingService
    ) {
        super("nexoriportalunbind", "Removes the collision binding from a Nexori portal.");
        this.plugin = plugin;
        this.triggerBindingService = triggerBindingService;
        this.portalIdArg = withRequiredArg("portalId", "Portal id.", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission("*") && !context.sender().hasPermission(plugin.getBasePermission() + ".admin")) {
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to manage Nexori portals."));
            return;
        }

        try {
            boolean removed = triggerBindingService.removePortalCollisionBinding(context.get(portalIdArg));
            context.sendMessage(Message.raw(removed
                ? "Removed the Nexori portal binding."
                : "That Nexori portal did not have a collision binding."));
        } catch (IOException exception) {
            context.sendMessage(Message.raw("Failed to remove the Nexori portal binding: " + exception.getMessage()));
        }
    }
}
