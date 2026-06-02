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
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<MatchSessionState> sessions = matchSessionService.list();
        if (sessions.isEmpty()) {
            context.sendMessage(Message.raw("No retained Nexori handoff records are active on this server."));
            return;
        }

        long now = System.currentTimeMillis();
        context.sendMessage(Message.raw("Retained Nexori handoff records:"));
        for (MatchSessionState session : sessions) {
            StringBuilder line = new StringBuilder(
                "- " + session.matchId()
                    + " queue=" + session.queueId()
                    + " arena=" + session.arenaId()
                    + " launched=" + session.launchedPlayerUuids().size()
                    + " observed=" + session.observedPlayerUuids().size()
                    + " handoff=" + (session.hasHandoffCompleted() ? "done" : "prepared")
            );
            if (session.expiresAtEpochMs() > 0L) {
                long secondsRemaining = Math.max((session.expiresAtEpochMs() - now + 999L) / 1000L, 0L);
                line.append(" expiresIn=").append(secondsRemaining).append("s");
            }
            if (!session.lastError().isBlank()) {
                line.append(" lastError=").append(session.lastError());
            }
            context.sendMessage(Message.raw(line.toString()));
        }
    }
}
