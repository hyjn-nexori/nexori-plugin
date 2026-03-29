package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;

public final class NexoriTargetHelpCommand extends CommandBase {

    public NexoriTargetHelpCommand() {
        super("nexoritarget", "Shows Nexori destination target command help.");
        this.setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        ctx.sendMessage(Message.raw("Nexori destination target commands:"));
        ctx.sendMessage(Message.raw("- /nexoritargetwizard"));
        ctx.sendMessage(Message.raw("- /nexoritargetlist"));
        ctx.sendMessage(Message.raw("- /nexoritargetadd <targetId> <kind> <world> <arrivalPoint>"));
        ctx.sendMessage(Message.raw("- /nexoritargetshow <targetId>"));
        ctx.sendMessage(Message.raw("- /nexoritargetremove <targetId>"));
        ctx.sendMessage(Message.raw("Kinds: NATURAL_SPAWN, COORDINATE, PORTAL"));
        ctx.sendMessage(Message.raw("/nexoritargetwizard opens the guided in-game wizard."));
        ctx.sendMessage(Message.raw("COORDINATE and PORTAL capture your current position and look direction when you run /nexoritargetadd."));
        ctx.sendMessage(Message.raw("NATURAL_SPAWN uses the world spawn and does not need saved coordinates."));
    }
}
