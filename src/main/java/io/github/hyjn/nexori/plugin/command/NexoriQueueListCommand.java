package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueService;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriQueueListCommand extends CommandBase {

    private final QueueService queueService;

    public NexoriQueueListCommand(@Nonnull QueueService queueService) {
        super("nexoriqueuelist", "Lists persisted Nexori queues on this server.");
        this.queueService = queueService;
        setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!NexoriOpAccess.requireOp(context)) {
            return;
        }
        List<QueueDefinition> queues = queueService.list();
        if (queues.isEmpty()) {
            context.sendMessage(Message.raw("No Nexori queues are registered on this server yet."));
            return;
        }

        context.sendMessage(Message.raw("Registered Nexori queues:"));
        for (QueueDefinition queue : queues) {
            context.sendMessage(Message.raw(
                "- " + queue.queueId()
                    + " arenas=" + String.join(",", queue.arenaIds())
                    + " min=" + queue.minPlayers()
                    + " max=" + queue.maxPlayers()
                    + " countdown=" + queue.countdownSeconds()
                    + " profile=" + queue.launchTravelProfileId()
                    + " enabled=" + queue.enabled()
            ));
        }
    }
}
