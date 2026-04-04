package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaService;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriArenaListCommand extends CommandBase {

    private final ArenaService arenaService;

    public NexoriArenaListCommand(@Nonnull ArenaService arenaService) {
        super("nexoriarenalist", "Lists persisted Nexori arenas on this server.");
        this.arenaService = arenaService;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<ArenaDefinition> arenas = arenaService.list();
        if (arenas.isEmpty()) {
            context.sendMessage(Message.raw("No Nexori arenas are registered on this server yet."));
            return;
        }

        context.sendMessage(Message.raw("Registered Nexori arenas:"));
        for (ArenaDefinition arena : arenas) {
            context.sendMessage(Message.raw(
                "- " + arena.arenaId()
                    + " destination=" + arena.destinationConnectionAddress()
                    + " -> " + arena.destinationTargetId()
                    + " instance=" + (arena.usesInstanceTemplate() ? arena.instanceTemplateId() : "direct")
                    + " trigger=" + (ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equals(arena.matchResolutionTriggerId())
                        ? "manual"
                        : arena.matchResolutionTriggerId())
                    + " maxPlayers=" + arena.maxSupportedPlayers()
                    + " enabled=" + arena.enabled()
            ));
        }
    }
}
