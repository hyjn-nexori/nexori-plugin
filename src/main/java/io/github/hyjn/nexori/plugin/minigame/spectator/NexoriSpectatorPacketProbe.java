package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.protocol.packets.interface_.ChatMessage;
import com.hypixel.hytale.protocol.packets.inventory.DropItemStack;
import com.hypixel.hytale.protocol.packets.inventory.SmartMoveItemStack;
import com.hypixel.hytale.protocol.packets.window.ClientOpenWindow;
import com.hypixel.hytale.protocol.packets.window.CloseWindow;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class NexoriSpectatorPacketProbe {

    private static final Set<String> loggedPacketClasses = ConcurrentHashMap.newKeySet();
    private static volatile boolean registered;

    private final NexoriSpectatorRuntimeProbeService probeService;
    private final HytaleLogger logger;

    private NexoriSpectatorPacketProbe(
        @Nonnull NexoriSpectatorRuntimeProbeService probeService,
        @Nonnull HytaleLogger logger
    ) {
        this.probeService = probeService;
        this.logger = logger;
    }

    public static void register(
        @Nonnull NexoriSpectatorRuntimeProbeService probeService,
        @Nonnull HytaleLogger logger
    ) {
        if (registered) {
            logger.atInfo().log("Nexori spectator packet probe was already registered.");
            return;
        }
        NexoriSpectatorPacketProbe probe = new NexoriSpectatorPacketProbe(probeService, logger);
        PacketAdapters.registerInbound((PlayerPacketFilter) probe::handleInbound);
        registered = true;
        logger.atInfo().log("Nexori spectator packet probe registered.");
    }

    private boolean handleInbound(PlayerRef playerRef, Packet packet) {
        if (playerRef == null || packet == null || !probeService.isTracked(playerRef.getUuid())) {
            return false;
        }

        logPacketClass(playerRef.getUuid(), packet);
        if (packet instanceof ChatMessage chatMessage && isHiddenGameModeCommand(chatMessage.message)) {
            logBlocked(playerRef.getUuid(), "hidden gamemode command", "message=" + sanitize(chatMessage.message));
            return true;
        }
        if (packet instanceof SyncInteractionChains) {
            logBlocked(playerRef.getUuid(), "interaction chains", packet.getClass().getName());
            return true;
        }
        if (packet instanceof ClientOpenWindow) {
            logBlocked(playerRef.getUuid(), "client open window", packet.getClass().getName());
            return true;
        }
        if (packet instanceof CloseWindow) {
            logBlocked(playerRef.getUuid(), "client close window", packet.getClass().getName());
            return true;
        }
        if (packet instanceof SmartMoveItemStack) {
            logBlocked(playerRef.getUuid(), "smart move item stack", packet.getClass().getName());
            return true;
        }
        if (packet instanceof DropItemStack) {
            logBlocked(playerRef.getUuid(), "drop item stack", packet.getClass().getName());
            return true;
        }
        return false;
    }

    private void logPacketClass(UUID playerUuid, Packet packet) {
        String packetClassName = packet.getClass().getName();
        String key = playerUuid + "|" + packetClassName;
        if (!loggedPacketClasses.add(key)) {
            return;
        }
        logger.atInfo().log(
            "NEXORI_SPECTATOR_PACKET_PROBE observed inbound packet playerUuid="
                + playerUuid
                + " packetClass="
                + packetClassName
        );
    }

    private void logBlocked(UUID playerUuid, String action, String detail) {
        logger.atInfo().log(
            "NEXORI_SPECTATOR_PACKET_PROBE blocked "
                + action
                + " playerUuid="
                + playerUuid
                + " "
                + detail
        );
    }

    private boolean isHiddenGameModeCommand(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("/gm c")
            || normalized.equals("/gm creative")
            || normalized.equals("/gm a")
            || normalized.equals("/gm adventure")
            || normalized.equals("/gamemode creative")
            || normalized.equals("/gamemode adventure");
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
