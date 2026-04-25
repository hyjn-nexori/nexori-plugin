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
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerResolutionOutcome;

import javax.annotation.Nonnull;
import java.util.UUID;

public final class NexoriMatchResolvePlayerCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final ArenaMatchService arenaMatchService;
    private final RequiredArg<String> matchIdArg;
    private final RequiredArg<String> playerArg;
    private final RequiredArg<String> outcomeArg;
    private final OptionalArg<Integer> delaySecondsArg;
    private final OptionalArg<String> reasonArg;

    public NexoriMatchResolvePlayerCommand(@Nonnull NexoriPlugin plugin, @Nonnull ArenaMatchService arenaMatchService) {
        super("nexorimatchresolveplayer", "Marks an active match player as WIN or LOSS and schedules their return.");
        this.plugin = plugin;
        this.arenaMatchService = arenaMatchService;
        this.matchIdArg = withRequiredArg("matchId", "Active match id.", ArgTypes.STRING);
        this.playerArg = withRequiredArg("player", "Active player UUID or username.", ArgTypes.STRING);
        this.outcomeArg = withRequiredArg("outcome", "WIN or LOSS.", ArgTypes.STRING);
        this.delaySecondsArg = withOptionalArg("delaySeconds", "Optional return delay in seconds.", ArgTypes.INTEGER);
        this.reasonArg = withOptionalArg("reason", "Optional resolution reason.", ArgTypes.STRING);
        setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAdminPermission(context)) {
            return;
        }

        String matchId = context.get(matchIdArg);
        String playerToken = context.get(playerArg);
        ArenaPlayerResolutionOutcome outcome;
        try {
            outcome = ArenaPlayerResolutionOutcome.parse(context.get(outcomeArg));
        } catch (IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Use WIN or LOSS for the match player outcome."));
            return;
        }

        UUID playerUuid = arenaMatchService.findActivePlayerUuid(matchId, playerToken).orElse(null);
        if (playerUuid == null) {
            context.sendMessage(Message.raw("That player is not active in the selected Nexori match."));
            return;
        }

        int delaySeconds = context.provided(delaySecondsArg) ? Math.max(context.get(delaySecondsArg), 0) : 10;
        String reason = context.provided(reasonArg) ? context.get(reasonArg) : outcome.name().toLowerCase();

        ArenaMatchService.ResolvePlayerResult result = arenaMatchService.resolvePlayerOutcome(
            matchId,
            playerUuid,
            outcome,
            delaySeconds,
            reason
        );

        switch (result.outcome()) {
            case UPDATED -> context.sendMessage(Message.raw(
                "Marked player " + playerUuid + " as " + outcome.name()
                    + " in match " + result.matchId()
                    + " with returnDelay=" + delaySeconds + "s."
            ));
            case MATCH_MISSING -> context.sendMessage(Message.raw(
                "That Nexori match does not exist on this server."
            ));
            case PLAYER_MISSING -> context.sendMessage(Message.raw(
                "That player is not active in the selected Nexori match."
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
