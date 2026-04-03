package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;
import io.github.hyjn.nexori.plugin.minigame.QueueRuntimeState;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriQueueStatusCommand extends CommandBase {

    private final QueueCoordinatorService queueCoordinatorService;

    public NexoriQueueStatusCommand(@Nonnull QueueCoordinatorService queueCoordinatorService) {
        super("nexoriqueuestatus", "Shows the in-memory Nexori queue runtime state.");
        this.queueCoordinatorService = queueCoordinatorService;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<QueueRuntimeState> states = queueCoordinatorService.listQueueStates();
        if (states.isEmpty()) {
            context.sendMessage(Message.raw("No Nexori queues are registered on this server yet."));
            return;
        }

        long now = System.currentTimeMillis();
        context.sendMessage(Message.raw("Nexori queue runtime state:"));
        for (QueueRuntimeState state : states) {
            long countdownRemaining = state.countdownEndsAtEpochMs() <= 0L
                ? 0L
                : Math.max(0L, (state.countdownEndsAtEpochMs() - now + 999L) / 1000L);
            context.sendMessage(Message.raw(
                "- " + state.queueId()
                    + " phase=" + state.phase()
                    + " waiting=" + state.waitingMembers().size()
                    + " ready=" + state.readyMembers().size()
                    + " countdownRemaining=" + countdownRemaining + "s"
            ));
        }
    }
}
