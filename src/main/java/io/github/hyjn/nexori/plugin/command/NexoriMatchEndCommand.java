package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;

import javax.annotation.Nonnull;

public final class NexoriMatchEndCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final ArenaMatchService arenaMatchService;
    private final RequiredArg<String> matchIdArg;
    private final OptionalArg<String> reasonArg;

    public NexoriMatchEndCommand(@Nonnull NexoriPlugin plugin, @Nonnull ArenaMatchService arenaMatchService) {
        super("nexorimatchend", "Ends an active Nexori arena match and returns its players to the lobby.");
        this.plugin = plugin;
        this.arenaMatchService = arenaMatchService;
        this.matchIdArg = withRequiredArg("matchId", "Active match id.", ArgTypes.STRING);
        this.reasonArg = withOptionalArg("reason", "Optional return reason.", ArgTypes.STRING);
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAdminPermission(context)) {
            return;
        }

        String matchId = context.get(matchIdArg);
        String reason = context.provided(reasonArg) ? context.get(reasonArg) : "MATCH_ENDED";
        ArenaMatchService.EndMatchResult result = arenaMatchService.endMatch(matchId, reason);
        switch (result.outcome()) {
            case COMPLETED -> context.sendMessage(Message.raw(
                "Ended Nexori match " + result.matchId() + " and returned " + result.returnedPlayerCount() + " player(s)."
            ));
            case MATCH_MISSING -> context.sendMessage(Message.raw(
                "That Nexori match does not exist on this server."
            ));
            case FAILED -> context.sendMessage(Message.raw(
                "Failed to end Nexori match " + result.matchId() + ": " + result.errorMessage()
            ));
        }
    }

    private boolean hasAdminPermission(@Nonnull CommandContext context) {
        String permission = plugin.getBasePermission() + ".admin";
        if (context.sender().hasPermission(permission)) {
            return true;
        }
        context.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori matches."));
        return false;
    }
}
