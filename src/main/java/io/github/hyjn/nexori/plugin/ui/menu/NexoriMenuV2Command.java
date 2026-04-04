package io.github.hyjn.nexori.plugin.ui.menu;

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

import javax.annotation.Nonnull;

public final class NexoriMenuV2Command extends AbstractPlayerCommand {

    private final NexoriPlugin plugin;

    public NexoriMenuV2Command(@Nonnull NexoriPlugin plugin) {
        super("nexorimenuv2", "Opens the new Nexori admin HyUI shell.");
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
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("nexorimenuv2: could not resolve the live player entity."));
            return;
        }

        NexoriMenuV2Page.open(ref, store, playerRef, player, plugin);
    }
}
