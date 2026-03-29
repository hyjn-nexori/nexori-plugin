package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.ui.NexoriTargetManagerPage;

import javax.annotation.Nonnull;

public final class NexoriTargetWizardCommand extends AbstractPlayerCommand {

    private final NexoriPlugin plugin;

    public NexoriTargetWizardCommand(@Nonnull NexoriPlugin plugin) {
        super("nexoritargetwizard", "Opens the Nexori destination target manager and guided wizard.");
        this.plugin = plugin;
        setPermissionGroup(GameMode.Adventure);
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        if (!context.sender().hasPermission("*") && !context.sender().hasPermission(plugin.getBasePermission() + ".admin")) {
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to create Nexori destination targets from the in-game wizard."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("nexoritargetwizard: could not resolve the live player entity."));
            return;
        }

        NexoriTargetManagerPage.open(
            ref,
            store,
            playerRef,
            player,
            plugin.getDestinationTargetService(),
            plugin.getPortalInstanceService(),
            plugin.getPortalInteractionService(),
            plugin.getTargetSetupDraftService(),
            "",
            ""
        );
    }
}
