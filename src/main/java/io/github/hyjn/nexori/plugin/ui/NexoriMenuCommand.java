package io.github.hyjn.nexori.plugin.ui;

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
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;

import javax.annotation.Nonnull;

public final class NexoriMenuCommand extends AbstractPlayerCommand {

    private final NexoriPlugin plugin;
    private final ConfiguredPeerService configuredPeerService;
    private final BootstrapCoordinator bootstrapCoordinator;

    public NexoriMenuCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull ConfiguredPeerService configuredPeerService,
        @Nonnull BootstrapCoordinator bootstrapCoordinator
    ) {
        super("nexorimenu", "Opens the Nexori peer manager UI.");
        this.plugin = plugin;
        this.configuredPeerService = configuredPeerService;
        this.bootstrapCoordinator = bootstrapCoordinator;
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
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("nexorimenu: could not resolve the live player entity."));
            return;
        }

        NexoriMenuPage.open(ref, store, playerRef, player, plugin.getBasePermission(), configuredPeerService, bootstrapCoordinator);
    }
}
