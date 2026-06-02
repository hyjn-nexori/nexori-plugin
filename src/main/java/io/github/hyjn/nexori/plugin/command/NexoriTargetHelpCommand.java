package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

public final class NexoriTargetHelpCommand extends CommandBase {

    public NexoriTargetHelpCommand() {
        super("nexoritarget", "Shows Nexori destination target command help.");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("Nexori destination target commands:"));
        ctx.sendMessage(Message.raw("- /nexorimenu"));
        ctx.sendMessage(Message.raw("- /nexoritargetlist"));
        ctx.sendMessage(Message.raw("- /nexoritargetadd <targetId> <kind> <world> <arrivalPoint>"));
        ctx.sendMessage(Message.raw("- /nexoritargetshow <targetId>"));
        ctx.sendMessage(Message.raw("- /nexoritargetremove <targetId>"));
        ctx.sendMessage(Message.raw("Kinds: NATURAL_SPAWN, COORDINATE, PORTAL"));
        ctx.sendMessage(Message.raw("/nexorimenu opens the main HyUI admin page. Use its Targets tab to create, continue, edit, or remove targets."));
        ctx.sendMessage(Message.raw("/nexoritargetadd is now intended only for COORDINATE targets."));
        ctx.sendMessage(Message.raw("NATURAL_SPAWN is generated automatically once per world, and PORTAL targets are created automatically when you place a Nexori portal."));
    }
}
