package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.command.NexoriOpAccess;

import javax.annotation.Nonnull;
import java.util.Locale;

public final class NexoriSpectatorProbeCommand extends AbstractPlayerCommand {

    private final NexoriSpectatorRuntimeProbeService probeService;
    private final RequiredArg<String> modeArg;

    public NexoriSpectatorProbeCommand(@Nonnull NexoriSpectatorRuntimeProbeService probeService) {
        super("nexorispectatorprobe", "Debug probe for experimental Nexori runtime spectator modes.");
        this.probeService = probeService;
        this.modeArg = withRequiredArg(
            "mode",
            "fly, collision, creative, freecam, safe, noattack, nointeract, nopickup, safeplus, or off.",
            ArgTypes.STRING
        );
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
        if (!NexoriOpAccess.requireOp(context)) {
            return;
        }

        String rawMode = context.get(modeArg);
        String normalizedMode = rawMode == null ? "" : rawMode.trim().toLowerCase(Locale.ROOT);
        NexoriSpectatorRuntimeProbeService.ProbeResult result;
        switch (normalizedMode) {
            case "fly" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.FLY_HIDDEN);
            case "collision" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.FLY_HIDDEN_COLLISION_OFF);
            case "creative" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.CREATIVE_HIDDEN);
            case "freecam" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.FREECAM_HIDDEN);
            case "safe" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.SAFE_FLY_HIDDEN);
            case "noattack" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.NO_ATTACK_TEST);
            case "nointeract" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.NO_INTERACT_TEST);
            case "nopickup" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.NO_PICKUP_TEST);
            case "safeplus" -> result = probeService.enterProbe(playerRef, SpectatorRuntimeMode.SAFE_PLUS_TEST);
            case "off" -> result = probeService.exitProbe(playerRef);
            default -> {
                context.sendMessage(Message.raw("Usage: /nexorispectatorprobe <fly|collision|creative|freecam|safe|noattack|nointeract|nopickup|safeplus|off>"));
                return;
            }
        }

        context.sendMessage(Message.raw(result.summary()));
    }
}
