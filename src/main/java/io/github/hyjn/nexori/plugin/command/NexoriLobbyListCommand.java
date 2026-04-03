package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.minigame.LobbyDefinition;
import io.github.hyjn.nexori.plugin.minigame.LobbyService;

import javax.annotation.Nonnull;
import java.util.List;

public final class NexoriLobbyListCommand extends CommandBase {

    private final LobbyService lobbyService;

    public NexoriLobbyListCommand(@Nonnull LobbyService lobbyService) {
        super("nexorilobbylist", "Lists persisted Nexori lobbies on this server.");
        this.lobbyService = lobbyService;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        List<LobbyDefinition> lobbies = lobbyService.list();
        if (lobbies.isEmpty()) {
            context.sendMessage(Message.raw("No Nexori lobbies are registered on this server yet."));
            return;
        }

        context.sendMessage(Message.raw("Registered Nexori lobbies:"));
        for (LobbyDefinition lobby : lobbies) {
            context.sendMessage(Message.raw(
                "- " + lobby.lobbyId()
                    + " world=" + lobby.worldName()
                    + " entry=" + lobby.entryTargetId()
                    + " return=" + lobby.returnTargetId()
                    + " enabled=" + lobby.enabled()
            ));
        }
    }
}
