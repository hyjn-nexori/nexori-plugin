package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionService;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionState;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriMatchSessionStatusCommand extends CommandBase {

    private final MatchSessionService matchSessionService;

    public NexoriMatchSessionStatusCommand(@Nonnull MatchSessionService matchSessionService) {
        super("nexorimatchsessionstatus", "Shows the persisted Nexori match sessions on this server.");
        this.matchSessionService = matchSessionService;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<MatchSessionState> sessions = matchSessionService.list();
        if (sessions.isEmpty()) {
            context.sendMessage(Message.raw("No persisted Nexori match sessions are active on this server."));
            return;
        }

        context.sendMessage(Message.raw("Persisted Nexori match sessions:"));
        for (MatchSessionState session : sessions) {
            StringBuilder line = new StringBuilder(
                "- " + session.matchId()
                    + " queue=" + session.queueId()
                    + " arena=" + session.arenaId()
                    + " expected=" + session.expectedPlayerUuids().size()
                    + " returned=" + session.returnedPlayerUuids().size()
            );
            if (!session.lastError().isBlank()) {
                line.append(" lastError=").append(session.lastError());
            }
            context.sendMessage(Message.raw(line.toString()));
        }
    }
}
