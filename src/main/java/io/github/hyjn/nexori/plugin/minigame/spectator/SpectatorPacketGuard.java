package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.CloseWindow;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

public final class SpectatorPacketGuard {

    private static volatile boolean registered;

    private final SpectatorRuntimeController spectatorRuntimeController;

    private SpectatorPacketGuard(@Nonnull SpectatorRuntimeController spectatorRuntimeController) {
        this.spectatorRuntimeController = spectatorRuntimeController;
    }

    public static void register(
        @Nonnull SpectatorRuntimeController spectatorRuntimeController,
        @Nonnull HytaleLogger logger
    ) {
        if (registered) {
            logger.atInfo().log("Nexori spectator packet guard was already registered.");
            return;
        }
        SpectatorPacketGuard guard = new SpectatorPacketGuard(spectatorRuntimeController);
        PacketAdapters.registerInbound((PlayerPacketFilter) guard::handleInbound);
        registered = true;
        logger.atInfo().log("Nexori spectator packet guard registered.");
    }

    private boolean handleInbound(PlayerRef playerRef, Packet packet) {
        if (playerRef == null || packet == null || !spectatorRuntimeController.isRuntimeSpectator(playerRef.getUuid())) {
            return false;
        }
        if (packet instanceof SyncInteractionChains) {
            return true;
        }
        if (packet instanceof ClientOpenWindow) {
            return true;
        }
        // CloseWindow is blocked because blocking ClientOpenWindow can otherwise produce client-side red error feedback.
        if (packet instanceof CloseWindow) {
            return true;
        }
        if (packet instanceof SmartMoveItemStack) {
            return true;
        }
        return packet instanceof DropItemStack;
    }
}
