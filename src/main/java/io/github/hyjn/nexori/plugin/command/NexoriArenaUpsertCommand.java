package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaService;

import javax.annotation.Nonnull;
import java.io.IOException;

public final class NexoriArenaUpsertCommand extends CommandBase {

    private final NexoriPlugin plugin;
    private final ArenaService arenaService;
    private final RequiredArg<String> arenaIdArg;
    private final RequiredArg<String> destinationArg;
    private final RequiredArg<String> targetIdArg;
    private final RequiredArg<Integer> maxPlayersArg;
    private final OptionalArg<String> displayNameArg;
    private final OptionalArg<String> instanceTemplateIdArg;
    private final OptionalArg<String> resolutionTriggerIdArg;
    private final OptionalArg<String> rulesEngineIdArg;

    public NexoriArenaUpsertCommand(@Nonnull NexoriPlugin plugin, @Nonnull ArenaService arenaService) {
        super("nexoriarenaupsert", "Creates or updates a persisted Nexori arena definition.");
        this.plugin = plugin;
        this.arenaService = arenaService;
        this.arenaIdArg = withRequiredArg("arenaId", "Arena id.", ArgTypes.STRING);
        this.destinationArg = withRequiredArg("destination", "Arena server destination in host:port format.", ArgTypes.STRING);
        this.targetIdArg = withRequiredArg("targetId", "Remote arena target id.", ArgTypes.STRING);
        this.maxPlayersArg = withRequiredArg("maxPlayers", "Maximum supported players.", ArgTypes.INTEGER);
        this.displayNameArg = withOptionalArg("displayName", "Optional display name.", ArgTypes.STRING);
        this.instanceTemplateIdArg = withOptionalArg(
            "instanceTemplateId",
            "Optional built-in instance template id. Defaults to 'none'.",
            ArgTypes.STRING
        );
        this.resolutionTriggerIdArg = withOptionalArg(
            "resolutionTriggerId",
            "Optional automatic arena resolution trigger id. Defaults to 'none'; use 'last_player_alive' to enable built-in auto resolution.",
            ArgTypes.STRING
        );
        this.rulesEngineIdArg = withOptionalArg(
            "rulesEngineId",
            "Optional external rules engine id for manual/custom matches.",
            ArgTypes.STRING
        );
        setPermissionGroups("OP");
    }

    @Override
    protected void executeSync(@Nonnull CommandContext context) {
        if (!hasAdminPermission(context)) {
            return;
        }

        try {
            String arenaId = context.get(arenaIdArg);
            String displayName = context.provided(displayNameArg) ? context.get(displayNameArg) : arenaId;
            String instanceTemplateId = ArenaDefinition.NO_INSTANCE_TEMPLATE_ID;
            if (context.provided(instanceTemplateIdArg)) {
                instanceTemplateId = context.get(instanceTemplateIdArg);
                if (ArenaDefinition.NO_INSTANCE_TEMPLATE_ID.equalsIgnoreCase(instanceTemplateId)) {
                    instanceTemplateId = ArenaDefinition.NO_INSTANCE_TEMPLATE_ID;
                }
            }
            String resolutionTriggerId = "";
            if (context.provided(resolutionTriggerIdArg)) {
                resolutionTriggerId = context.get(resolutionTriggerIdArg);
                if (ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equalsIgnoreCase(resolutionTriggerId)) {
                    resolutionTriggerId = ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID;
                }
            }
            String rulesEngineId = context.provided(rulesEngineIdArg) ? context.get(rulesEngineIdArg) : "";
            ArenaDefinition saved = arenaService.upsert(new ArenaDefinition(
                arenaId,
                displayName,
                context.get(destinationArg),
                context.get(targetIdArg),
                instanceTemplateId,
                resolutionTriggerId,
                rulesEngineId,
                context.get(maxPlayersArg),
                true
            ));
            context.sendMessage(Message.raw(
                "Saved arena " + saved.arenaId()
                    + " destination=" + saved.destinationConnectionAddress()
                    + " -> " + saved.destinationTargetId()
                    + " instance=" + (saved.usesInstanceTemplate() ? saved.instanceTemplateId() : "direct")
                    + " trigger=" + (ArenaDefinition.NO_MATCH_RESOLUTION_TRIGGER_ID.equals(saved.matchResolutionTriggerId())
                        ? "manual"
                        : saved.matchResolutionTriggerId())
                    + " rulesEngineId=" + (saved.rulesEngineId().isBlank() ? "<blank>" : saved.rulesEngineId())
                    + " maxPlayers=" + saved.maxSupportedPlayers()
                    + "."
            ));
        } catch (IOException | IllegalArgumentException exception) {
            context.sendMessage(Message.raw("Failed to save the Nexori arena: " + exception.getMessage()));
        }
    }

    private boolean hasAdminPermission(@Nonnull CommandContext context) {
        String permission = plugin.getBasePermission() + ".admin";
        if (context.sender().hasPermission("*") || context.sender().hasPermission(permission)) {
            return true;
        }
        context.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori arenas."));
        return false;
    }
}
