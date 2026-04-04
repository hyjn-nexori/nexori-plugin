package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingAction;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingDefinition;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriPortalListCommand extends CommandBase {

    private final PortalInstanceService portalInstanceService;
    private final TriggerBindingService triggerBindingService;

    public NexoriPortalListCommand(
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull TriggerBindingService triggerBindingService
    ) {
        super("nexoriportallist", "Lists Nexori portal instances on this server.");
        this.portalInstanceService = portalInstanceService;
        this.triggerBindingService = triggerBindingService;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<PortalInstanceDefinition> portals = portalInstanceService.list();
        if (portals.isEmpty()) {
            context.sendMessage(Message.raw("No Nexori portals have been placed on this server yet."));
            return;
        }

        context.sendMessage(Message.raw("Nexori portals on this server:"));
        for (PortalInstanceDefinition portal : portals) {
            List<TriggerBindingDefinition> bindings = triggerBindingService.listPortalCollisionBindings(portal.portalId());
            context.sendMessage(Message.raw("- " + portal.portalId()
                + " @ " + portal.worldName()
                + " (" + portal.blockX() + ", " + portal.blockY() + ", " + portal.blockZ() + ")"
                + " target=" + portal.autoDestinationTargetId()
                + " enabled=" + portal.enabled()
                + " bound=" + describeBindings(bindings)));
        }
    }

    @Nonnull
    private String describeBindings(@Nonnull List<TriggerBindingDefinition> bindings) {
        if (bindings.isEmpty()) {
            return "no";
        }
        return bindings.stream()
            .map(binding -> switch (binding.action()) {
                case JOIN_QUEUE, LEAVE_QUEUE -> binding.action() + " -> " + binding.queueId();
                case LOCAL_TARGET -> "LOCAL_TARGET -> " + binding.destinationTargetId();
                case TRAVEL -> "TRAVEL -> " + binding.destinationConnectionAddress() + " -> " + binding.destinationTargetId();
            })
            .reduce((left, right) -> left + ", " + right)
            .orElse("no");
    }
}
