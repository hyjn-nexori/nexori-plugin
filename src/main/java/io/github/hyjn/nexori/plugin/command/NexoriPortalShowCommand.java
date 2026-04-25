package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriPortalShowCommand extends CommandBase {

    private final PortalInstanceService portalInstanceService;
    private final TriggerBindingService triggerBindingService;
    private final RequiredArg<String> portalIdArg;

    public NexoriPortalShowCommand(
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService
    ) {
        super("nexoriportalshow", "Shows the stored Nexori portal record for one portal id.");
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        this.portalIdArg = withRequiredArg("portalId", "Portal id.", ArgTypes.STRING);
        setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!NexoriOpAccess.requireOp(context)) {
            return;
        }
        PortalInstanceDefinition portal = portalInstanceService.findById(context.get(portalIdArg)).orElse(null);
        if (portal == null) {
            context.sendMessage(Message.raw("That Nexori portal does not exist on this server."));
            return;
        }

        List<TriggerBindingDefinition> bindings = triggerBindingService.listPortalCollisionBindings(portal.portalId());
        context.sendMessage(Message.raw("Portal id: " + portal.portalId()));
        context.sendMessage(Message.raw("Display name: " + portal.displayName()));
        context.sendMessage(Message.raw("Location: " + portal.worldName() + " (" + portal.blockX() + ", " + portal.blockY() + ", " + portal.blockZ() + ")"));
        context.sendMessage(Message.raw("Auto destination target: " + portal.autoDestinationTargetId()));
        context.sendMessage(Message.raw("Enabled: " + portal.enabled()));
        if (bindings.isEmpty()) {
            context.sendMessage(Message.raw("Collision binding: <none>"));
            return;
        }

        context.sendMessage(Message.raw("Collision bindings:"));
        for (TriggerBindingDefinition binding : bindings) {
            context.sendMessage(Message.raw("- action: " + binding.action()));
            switch (binding.action()) {
                case JOIN_QUEUE, LEAVE_QUEUE -> context.sendMessage(Message.raw("  queue: " + binding.queueId()));
                case LOCAL_TARGET -> context.sendMessage(Message.raw("  local target: " + binding.destinationTargetId()));
                case TRAVEL -> {
                    context.sendMessage(Message.raw("  destination: " + binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId()));
                    context.sendMessage(Message.raw("  travel profile: " + binding.travelProfileId()));
                }
            }
        }
    }
}
