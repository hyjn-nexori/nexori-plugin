package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.minigame.LobbyDefinition;
import io.github.hyjn.nexori.plugin.minigame.LobbyService;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriLobbyUpsertCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final LobbyService lobbyService;
    private final RequiredArg<String> lobbyIdArg;
    private final RequiredArg<String> worldNameArg;
    private final RequiredArg<String> entryTargetIdArg;
    private final OptionalArg<String> returnTargetIdArg;
    private final OptionalArg<String> displayNameArg;

    public NexoriLobbyUpsertCommand(@Nonnull NexoriPlugin plugin, @Nonnull LobbyService lobbyService) {
        super("nexorilobbyupsert", "Creates or updates a persisted Nexori lobby definition.");
        this.plugin = plugin;
        this.lobbyService = lobbyService;
        this.lobbyIdArg = withRequiredArg("lobbyId", "Lobby id.", ArgTypes.STRING);
        this.worldNameArg = withRequiredArg("worldName", "Lobby world name.", ArgTypes.STRING);
        this.entryTargetIdArg = withRequiredArg("entryTargetId", "Entry target id.", ArgTypes.STRING);
        this.returnTargetIdArg = withOptionalArg("returnTargetId", "Optional return target id.", ArgTypes.STRING);
        this.displayNameArg = withOptionalArg("displayName", "Optional display name.", ArgTypes.STRING);
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAdminPermission(context)) {
            return;
        }

        try {
            String lobbyId = context.get(lobbyIdArg);
            String entryTargetId = context.get(entryTargetIdArg);
            String returnTargetId = context.provided(returnTargetIdArg) ? context.get(returnTargetIdArg) : entryTargetId;
            String displayName = context.provided(displayNameArg) ? context.get(displayNameArg) : lobbyId;

            LobbyDefinition saved = lobbyService.upsert(new LobbyDefinition(
                lobbyId,
                displayName,
                context.get(worldNameArg),
                entryTargetId,
                returnTargetId,
                true
            ));
            context.sendMessage(Message.raw(
                "Saved lobby " + saved.lobbyId()
                    + " world=" + saved.worldName()
                    + " entry=" + saved.entryTargetId()
                    + " return=" + saved.returnTargetId()
                    + "."
            ));
        } catch (IOException | IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Failed to save the Nexori lobby: " + exception.getMessage()));
        }
    }

    private boolean hasAdminPermission(@Nonnull CommandContext context) {
        String permission = plugin.getBasePermission() + ".admin";
        if (context.sender().hasPermission("*") || context.sender().hasPermission(permission)) {
            return true;
        }
        context.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori lobbies."));
        return false;
    }
}
