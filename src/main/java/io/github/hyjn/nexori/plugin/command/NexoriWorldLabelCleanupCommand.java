package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.worldlabel.WorldLabelService;

import javax.annotation.Nonnull;

public final class NexoriWorldLabelCleanupCommand extends AbstractPlayerCommand {

    private final NexoriPlugin plugin;
    private final WorldLabelService worldLabelService;

    public NexoriWorldLabelCleanupCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull WorldLabelService worldLabelService
    ) {
        super(
            "nexoriworldlabelcleanup",
            "Removes all Nexori barrier-based world-label carriers from your current world, including orphaned portal labels left behind after deleting persistence."
        );
        this.plugin = plugin;
        this.worldLabelService = worldLabelService;
        setPermissionGroups("OP");
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
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to clean Nexori world labels."));
            return;
        }

        context.sendMessage(Message.raw(
            "Started Nexori world-label cleanup in '" + world.getName()
                + "'. This removes all barrier-based Nexori label carriers in the current world, including orphaned portal labels."
        ));
        worldLabelService.clearBarrierNameplateCarriers(world, removedCount -> playerRef.sendMessage(Message.raw(
            "Nexori world-label cleanup finished in '" + world.getName() + "'. Removed " + removedCount
                + " barrier label carrier(s). Active portal labels may rebuild if their portals still exist."
        )));
    }
}
