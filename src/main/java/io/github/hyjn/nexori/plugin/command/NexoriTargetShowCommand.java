package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;

import javax.annotation.Nonnull;

public final class NexoriTargetShowCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final RequiredArg<String> targetIdArg;

    public NexoriTargetShowCommand(@Nonnull NexoriPlugin plugin) {
        super("nexoritargetshow", "Shows one Nexori destination target.");
        this.plugin = plugin;
        this.targetIdArg = withRequiredArg("targetId", "Destination target id to inspect.", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        plugin.getDestinationTargetService().find(ctx.get(targetIdArg)).ifPresentOrElse(target -> {
            ctx.sendMessage(Message.raw("Target id: " + target.id()));
            ctx.sendMessage(Message.raw("Display: " + target.displayName()));
            ctx.sendMessage(Message.raw("Kind: " + target.kind().displayName()));
            ctx.sendMessage(Message.raw("World: " + target.worldName()));
            ctx.sendMessage(Message.raw("Arrival point: " + (target.arrivalPointId().isBlank() ? "<none>" : target.arrivalPointId())));
        }, () -> ctx.sendMessage(Message.raw("That Nexori destination target does not exist on this server.")));
    }
}
