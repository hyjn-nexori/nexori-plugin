package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;

import javax.annotation.Nonnull;

public final class NexoriTargetRemoveCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final RequiredArg<String> targetIdArg;

    public NexoriTargetRemoveCommand(@Nonnull NexoriPlugin plugin) {
        super("nexoritargetremove", "Removes a Nexori destination target.");
        this.plugin = plugin;
        this.targetIdArg = withRequiredArg("targetId", "Destination target id to remove.", ArgTypes.STRING);
        this.setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        try {
            boolean removed = plugin.getDestinationTargetService().remove(ctx.get(targetIdArg));
            ctx.sendMessage(Message.raw(removed
                ? "Removed the Nexori destination target."
                : "That Nexori destination target was not configured on this server."));
        } catch (IllegalArgumentException exception) {
            ctx.sendMessage(Message.raw(exception.getMessage()));
        } catch (Exception exception) {
            ctx.sendMessage(Message.raw("Failed to remove the Nexori destination target: " + exception.getMessage()));
        }
    }
}
