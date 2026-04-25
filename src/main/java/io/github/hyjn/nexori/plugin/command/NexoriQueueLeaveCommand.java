package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;

import javax.annotation.Nonnull;

public final class NexoriQueueLeaveCommand extends AbstractPlayerCommand {

    private final QueueCoordinatorService queueCoordinatorService;

    public NexoriQueueLeaveCommand(@Nonnull QueueCoordinatorService queueCoordinatorService) {
        super("nexoriqueueleave", "Leaves the current Nexori queue.");
        this.queueCoordinatorService = queueCoordinatorService;
        setPermissionGroups("OP");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        if (!NexoriOpAccess.requireOp(context)) {
            return;
        }
        QueueCoordinatorService.LeaveResult result = queueCoordinatorService.leaveCurrentQueue(playerRef.getUuid());
        if (result.outcome() == QueueCoordinatorService.LeaveOutcome.NOT_QUEUED) {
            context.sendMessage(Message.raw("You are not in a Nexori queue right now."));
            return;
        }

        context.sendMessage(Message.raw("Left Nexori queue " + result.queueId() + "."));
    }
}
