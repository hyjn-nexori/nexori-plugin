package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriTargetListCommand extends CommandBase {

    private final NexoriPlugin plugin;

    public NexoriTargetListCommand(@Nonnull NexoriPlugin plugin) {
        super("nexoritargetlist", "Lists Nexori destination targets.");
        this.plugin = plugin;
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        List<DestinationTargetDefinition> targets = plugin.getDestinationTargetService().list();
        if (targets.isEmpty()) {
            ctx.sendMessage(Message.raw("No Nexori destination targets are set yet. Add one with /nexoritargetadd <targetId> <kind> <world> <arrivalPoint>."));
            return;
        }

        ctx.sendMessage(Message.raw("Configured Nexori destination targets:"));
        for (DestinationTargetDefinition target : targets) {
            ctx.sendMessage(Message.raw("- " + target.id()
                + " kind=" + target.kind()
                + " world=" + target.worldName()
                + " arrivalPoint=" + (target.arrivalPointId().isBlank() ? "<none>" : target.arrivalPointId())));
        }
    }
}
