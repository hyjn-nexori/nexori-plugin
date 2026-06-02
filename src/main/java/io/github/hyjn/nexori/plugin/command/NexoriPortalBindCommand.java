package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriPortalBindCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final PortalInstanceService portalInstanceService;
    private final TriggerBindingService triggerBindingService;
    private final RequiredArg<String> portalIdArg;
    private final RequiredArg<String> destinationArg;
    private final RequiredArg<String> targetIdArg;
    private final OptionalArg<String> travelProfileArg;

    public NexoriPortalBindCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService
    ) {
        super("nexoriportalbind", "Binds a placed Nexori portal to a trusted remote destination target.");
        this.plugin = plugin;
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        this.portalIdArg = withRequiredArg("portalId", "Portal id.", ArgTypes.STRING);
        this.destinationArg = withRequiredArg("destination", "Trusted destination in host:port format.", ArgTypes.STRING);
        this.targetIdArg = withRequiredArg("targetId", "Remote destination target id.", ArgTypes.STRING);
        this.travelProfileArg = withOptionalArg("travelProfile", "Optional travel profile id.", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!context.sender().hasPermission("*") && !context.sender().hasPermission(plugin.getBasePermission() + ".admin")) {
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to manage Nexori portals."));
            return;
        }

        String portalId = context.get(portalIdArg);
        if (portalInstanceService.findById(portalId).isEmpty()) {
            context.sendMessage(Message.raw("That Nexori portal does not exist on this server."));
            return;
        }

        try {
            TriggerBindingDefinition binding = triggerBindingService.bindPortalCollisionTravel(
                portalId,
                context.get(destinationArg),
                context.get(targetIdArg),
                TravelProfileType.parse(context.provided(travelProfileArg) ? context.get(travelProfileArg) : "").id(),
                "{}"
            );
            context.sendMessage(Message.raw("Bound portal " + portalId + " to "
                + binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId()
                + " with profile " + binding.travelProfileId() + "."));
        } catch (IOException | IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Failed to bind the Nexori portal: " + exception.getMessage()));
        }
    }
}
