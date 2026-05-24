package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.InventoryAction;
import com.hypixel.hytale.protocol.packets.inventory.MoveItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class AfkInventoryPacketActivityAdapter {

    private static volatile boolean registered;

    private final AfkActivityService afkActivityService;

    private AfkInventoryPacketActivityAdapter(@Nonnull AfkActivityService afkActivityService) {
        this.afkActivityService = afkActivityService;
    }

    public static void register(
        @Nonnull AfkActivityService afkActivityService,
        @Nonnull HytaleLogger logger
    ) {
        if (registered) {
            logger.atInfo().log("Nexori AFK inventory packet activity adapter was already registered.");
            return;
        }
        AfkInventoryPacketActivityAdapter adapter = new AfkInventoryPacketActivityAdapter(afkActivityService);
        PacketAdapters.registerInbound((PlayerPacketFilter) adapter::handleInbound);
        registered = true;
        logger.atInfo().log("Nexori AFK inventory packet activity adapter registered.");
    }

    private boolean handleInbound(PlayerRef playerRef, Packet packet) {
        if (playerRef != null && isInventoryActivityPacket(packet)) {
            afkActivityService.markInventoryActivity(playerRef, System.currentTimeMillis());
        }
        return false;
    }

    private static boolean isInventoryActivityPacket(Packet packet) {
        return packet instanceof MoveItemStack
            || packet instanceof SmartMoveItemStack
            || packet instanceof InventoryAction
            || packet instanceof DropItemStack;
    }
}
