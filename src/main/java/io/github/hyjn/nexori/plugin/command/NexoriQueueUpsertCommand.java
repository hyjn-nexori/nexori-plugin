package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueService;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public final class NexoriQueueUpsertCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final QueueService queueService;
    private final RequiredArg<String> queueIdArg;
    private final RequiredArg<String> arenaIdsArg;
    private final RequiredArg<Integer> minPlayersArg;
    private final RequiredArg<Integer> maxPlayersArg;
    private final RequiredArg<Integer> countdownSecondsArg;
    private final RequiredArg<String> launchTravelProfileArg;
    private final OptionalArg<String> displayNameArg;

    public NexoriQueueUpsertCommand(@Nonnull NexoriPlugin plugin, @Nonnull QueueService queueService) {
        super("nexoriqueueupsert", "Creates or updates a persisted Nexori queue definition.");
        this.plugin = plugin;
        this.queueService = queueService;
        this.queueIdArg = withRequiredArg("queueId", "Queue id.", ArgTypes.STRING);
        this.arenaIdsArg = withRequiredArg("arenaIds", "Comma-separated arena ids.", ArgTypes.STRING);
        this.minPlayersArg = withRequiredArg("minPlayers", "Minimum players.", ArgTypes.INTEGER);
        this.maxPlayersArg = withRequiredArg("maxPlayers", "Maximum players.", ArgTypes.INTEGER);
        this.countdownSecondsArg = withRequiredArg("countdownSeconds", "Countdown seconds.", ArgTypes.INTEGER);
        this.launchTravelProfileArg = withRequiredArg("launchTravelProfile", "Launch travel profile.", ArgTypes.STRING);
        this.displayNameArg = withOptionalArg("displayName", "Optional display name.", ArgTypes.STRING);
        setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAdminPermission(context)) {
            return;
        }

        try {
            String queueId = context.get(queueIdArg);
            String displayName = context.provided(displayNameArg) ? context.get(displayNameArg) : queueId;
            String travelProfileId = TravelProfileType.parse(context.get(launchTravelProfileArg)).id();
            List<String> arenaIds = Arrays.stream(context.get(arenaIdsArg).split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();

            QueueDefinition saved = queueService.upsert(new QueueDefinition(
                queueId,
                displayName,
                arenaIds,
                context.get(minPlayersArg),
                context.get(maxPlayersArg),
                context.get(countdownSecondsArg),
                travelProfileId,
                true
            ));
            context.sendMessage(Message.raw(
                "Saved queue " + saved.queueId()
                    + " arenas=" + String.join(",", saved.arenaIds())
                    + " min=" + saved.minPlayers()
                    + " max=" + saved.maxPlayers()
                    + " countdown=" + saved.countdownSeconds()
                    + " profile=" + saved.launchTravelProfileId()
                    + "."
            ));
        } catch (IOException | IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Failed to save the Nexori queue: " + exception.getMessage()));
        }
    }

    private boolean hasAdminPermission(@Nonnull CommandContext context) {
        String permission = plugin.getBasePermission() + ".admin";
        if (context.sender().hasPermission("*") || context.sender().hasPermission(permission)) {
            return true;
        }
        context.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori queues."));
        return false;
    }
}
