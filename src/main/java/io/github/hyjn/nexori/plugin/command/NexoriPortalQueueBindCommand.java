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
import io.github.hyjn.nexori.plugin.minigame.LobbyDefinition;
import io.github.hyjn.nexori.plugin.minigame.LobbyService;
import io.github.hyjn.nexori.plugin.minigame.QueueService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceDefinition;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriPortalQueueBindCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final PortalInstanceService portalInstanceService;
    private final LobbyService lobbyService;
    private final QueueService queueService;
    private final TriggerBindingService triggerBindingService;
    private final RequiredArg<String> portalIdArg;
    private final RequiredArg<String> queueIdArg;

    public NexoriPortalQueueBindCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull PortalInstanceService portalInstanceService,
        @Nonnull LobbyService lobbyService,
        @Nonnull QueueService queueService,
        @Nonnull TriggerBindingService triggerBindingService
    ) {
        super("nexoriportalqueuebind", "Binds a placed Nexori portal to JOIN_QUEUE for one persisted queue.");
        this.plugin = plugin;
        this.portalInstanceService = portalInstanceService;
        this.lobbyService = lobbyService;
        this.queueService = queueService;
        this.triggerBindingService = triggerBindingService;
        this.portalIdArg = withRequiredArg("portalId", "Portal id.", ArgTypes.STRING);
        this.queueIdArg = withRequiredArg("queueId", "Queue id.", ArgTypes.STRING);
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAdminPermission(context)) {
            return;
        }

        String portalId = context.get(portalIdArg);
        String queueId = context.get(queueIdArg);
        PortalInstanceDefinition portal = portalInstanceService.findById(portalId).orElse(null);
        if (portal == null) {
            context.sendMessage(Message.raw("That Nexori portal does not exist on this server."));
            return;
        }
        LobbyDefinition lobby = lobbyService.findByWorldName(portal.worldName()).orElse(null);
        if (lobby == null || !lobby.enabled()) {
            context.sendMessage(Message.raw("That portal is not in an enabled Nexori lobby world, so it cannot bind to JOIN_QUEUE."));
            return;
        }
        if (queueService.find(queueId).isEmpty()) {
            context.sendMessage(Message.raw("That Nexori queue does not exist on this server."));
            return;
        }

        try {
            TriggerBindingDefinition binding = triggerBindingService.bindPortalCollisionQueue(portalId, queueId);
            context.sendMessage(Message.raw(
                "Bound portal " + portalId + " to JOIN_QUEUE -> " + binding.queueId() + "."
            ));
        } catch (IOException | IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Failed to bind the Nexori portal to queue: " + exception.getMessage()));
        }
    }

    private boolean hasAdminPermission(@Nonnull CommandContext context) {
        String permission = plugin.getBasePermission() + ".admin";
        if (context.sender().hasPermission("*") || context.sender().hasPermission(permission)) {
            return true;
        }
        context.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori portals."));
        return false;
    }
}
