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
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;

import javax.annotation.Nonnull;

public final class NexoriStartCommand extends AbstractPlayerCommand {

    private final BootstrapCoordinator bootstrapCoordinator;
    private final String adminPermission;

    public NexoriStartCommand(@Nonnull BootstrapCoordinator bootstrapCoordinator, @Nonnull String adminPermission) {
        super("nexoristart", "Starts Nexori bootstrap using the saved peer IP list.");
        this.bootstrapCoordinator = bootstrapCoordinator;
        this.adminPermission = adminPermission;
    }

    @Override
    protected void execute(
        @Nonnull CommandContext context,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        if (!context.sender().hasPermission("*") && !context.sender().hasPermission(adminPermission)) {
            context.sendMessage(Message.raw("You need the permission '" + adminPermission + "' to start Nexori bootstrap."));
            return;
        }

        BootstrapCoordinator.StartResult result = bootstrapCoordinator.start(playerRef);
        context.sendMessage(Message.raw(result.message()));
    }
}
