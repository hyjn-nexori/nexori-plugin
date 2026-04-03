package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriMatchStatusCommand extends CommandBase {

    private final ArenaMatchService arenaMatchService;

    public NexoriMatchStatusCommand(@Nonnull ArenaMatchService arenaMatchService) {
        super("nexorimatchstatus", "Shows the active Nexori arena matches on this server.");
        this.arenaMatchService = arenaMatchService;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<ArenaActiveMatch> matches = arenaMatchService.listMatches();
        if (matches.isEmpty()) {
            context.sendMessage(Message.raw("No active Nexori arena matches are running on this server."));
            return;
        }

        context.sendMessage(Message.raw("Active Nexori arena matches:"));
        for (ArenaActiveMatch match : matches) {
            StringBuilder line = new StringBuilder(
                "- " + match.matchId()
                    + " arena=" + match.arenaId()
                    + " queue=" + match.queueId()
                    + " players=" + match.playerUuids().size()
                    + " return=" + match.returnConnectionAddress()
                    + " -> " + match.returnFallbackTargetId()
            );
            if (!match.lastError().isBlank()) {
                line.append(" lastError=").append(match.lastError());
            }
            context.sendMessage(Message.raw(line.toString()));
        }
    }
}
