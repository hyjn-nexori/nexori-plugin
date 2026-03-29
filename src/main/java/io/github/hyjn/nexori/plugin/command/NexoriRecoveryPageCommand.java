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
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;
import io.github.hyjn.nexori.plugin.ui.NexoriRecoveryPage;

import javax.annotation.Nonnull;

public final class NexoriRecoveryPageCommand extends AbstractPlayerCommand {

    private final InventoryTransferService inventoryTransferService;

    public NexoriRecoveryPageCommand(@Nonnull InventoryTransferService inventoryTransferService) {
        super("nexorirecovery", "Opens your Nexori inventory recovery page.");
        this.inventoryTransferService = inventoryTransferService;
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
        if (!inventoryTransferService.isRecoveryEnabled()) {
            context.sendMessage(Message.raw("Nexori inventory recovery is currently disabled by this server's admin."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("nexorirecovery: could not resolve the live player entity."));
            return;
        }

        NexoriRecoveryPage.open(ref, store, playerRef, player, inventoryTransferService, "", "");
    }
}
