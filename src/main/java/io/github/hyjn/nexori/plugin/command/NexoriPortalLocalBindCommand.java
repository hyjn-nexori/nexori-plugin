package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriPortalLocalBindCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final PortalInstanceService portalInstanceService;
    private final DestinationTargetService destinationTargetService;
    private final TriggerBindingService triggerBindingService;
    private final RequiredArg<String> portalIdArg;
    private final RequiredArg<String> targetIdArg;

    public NexoriPortalLocalBindCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull DestinationTargetService destinationTargetService,
        @Nonnull TriggerBindingService triggerBindingService
    ) {
        super("nexoriportallocalbind", "Binds a placed Nexori portal to teleport locally to a Nexori target on this server.");
        this.plugin = plugin;
        this.portalInstanceService = portalInstanceService;
        this.destinationTargetService = destinationTargetService;
        this.triggerBindingService = triggerBindingService;
        this.portalIdArg = withRequiredArg("portalId", "Portal id.", ArgTypes.STRING);
        this.targetIdArg = withRequiredArg("targetId", "Local Nexori target id.", ArgTypes.STRING);
        setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission("*") && !context.sender().hasPermission(plugin.getBasePermission() + ".admin")) {
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to manage Nexori portals."));
            return;
        }

        String portalId = context.get(portalIdArg);
        String targetId = context.get(targetIdArg);
        if (portalInstanceService.findById(portalId).isEmpty()) {
            context.sendMessage(Message.raw("That Nexori portal does not exist on this server."));
            return;
        }
        if (destinationTargetService.find(targetId).isEmpty()) {
            context.sendMessage(Message.raw("That Nexori destination target does not exist on this server."));
            return;
        }

        try {
            TriggerBindingDefinition binding = triggerBindingService.bindPortalCollisionLocalTarget(portalId, targetId);
            context.sendMessage(Message.raw(
                "Bound portal " + portalId + " to LOCAL_TARGET -> " + binding.destinationTargetId() + "."
            ));
        } catch (IOException | IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Failed to bind the Nexori portal to a local target: " + exception.getMessage()));
        }
    }
}
