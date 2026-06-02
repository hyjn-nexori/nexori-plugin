package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.util.Comparator;
import java.util.List;

public final class NexoriInstanceListCommand extends CommandBase {

    public NexoriInstanceListCommand() {
        super("nexoriinstancelist", "Lists loaded Hytale instance template ids available to Nexori on this server.");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<String> instanceIds = InstancesPlugin.get().getInstanceAssets().stream()
            .filter(id -> id != null && !id.isBlank())
            .sorted(Comparator.naturalOrder())
            .toList();
        if (instanceIds.isEmpty()) {
            context.sendMessage(Message.raw("No Hytale instance templates are loaded on this server."));
            return;
        }

        context.sendMessage(Message.raw("Loaded Hytale instance templates on this server:"));
        for (String instanceId : instanceIds) {
            context.sendMessage(Message.raw("- " + instanceId));
        }
        context.sendMessage(Message.raw("Use any of these exact ids with --instanceTemplateId=<id> in /nexoriarenaupsert."));
    }
}
