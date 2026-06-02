package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalIds;

import javax.annotation.Nonnull;

public final class NexoriPortalGiveCommand extends AbstractPlayerCommand {

    private final NexoriPlugin plugin;
    private final OptionalArg<Integer> amountArg;

    public NexoriPortalGiveCommand(@Nonnull NexoriPlugin plugin) {
        super("nexoriportalgive", "Gives the Nexori portal block item to the executing player.");
        this.plugin = plugin;
        this.amountArg = withOptionalArg("amount", "How many portal items to give.", ArgTypes.INTEGER);
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
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to manage Nexori portals."));
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            context.sendMessage(Message.raw("Could not resolve the live player entity."));
            return;
        }

        int amount = context.provided(amountArg) ? Math.max(1, context.get(amountArg)) : 1;
        player.giveItem(new ItemStack(NexoriPortalIds.ITEM_ID, amount), ref, store);
        context.sendMessage(Message.raw("Gave " + amount + " Nexori portal item(s)."));
    }
}
