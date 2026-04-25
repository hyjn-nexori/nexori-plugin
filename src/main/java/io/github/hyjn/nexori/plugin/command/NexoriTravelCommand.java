package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class NexoriTravelCommand extends AbstractPlayerCommand {

    private final SecureTravelService secureTravelService;
    private final RequiredArg<String> destinationArg;
    private final OptionalArg<String> targetIdArg;
    private final OptionalArg<String> legacyRouteKeyArg;
    private final OptionalArg<String> entryPointArg;
    private final OptionalArg<String> travelProfileArg;

    public NexoriTravelCommand(@Nonnull SecureTravelService secureTravelService) {
        super("nexoritravel", "Starts a signed Nexori travel referral to one trusted destination.");
        this.secureTravelService = secureTravelService;
        this.destinationArg = withRequiredArg("destination", "Trusted destination in host:port format.", ArgTypes.STRING);
        this.targetIdArg = withOptionalArg("targetId", "Destination target id.", ArgTypes.STRING);
        this.legacyRouteKeyArg = withOptionalArg("routeKey", "Deprecated alias for targetId.", ArgTypes.STRING);
        this.entryPointArg = withOptionalArg("arrivalPoint", "Optional arrival point override.", ArgTypes.STRING);
        this.travelProfileArg = withOptionalArg("travelProfile", "Optional travel profile id.", ArgTypes.STRING);
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
        try {
            ConfiguredPeer destination = ConfiguredPeer.parse(context.get(destinationArg));
            String targetId = context.provided(targetIdArg)
                ? context.get(targetIdArg)
                : (context.provided(legacyRouteKeyArg) ? context.get(legacyRouteKeyArg) : "");
            String arrivalPoint = context.provided(entryPointArg) ? context.get(entryPointArg) : "";
            String travelProfile = TravelProfileType.parse(
                context.provided(travelProfileArg) ? context.get(travelProfileArg) : ""
            ).id();
            secureTravelService.travel(playerRef, destination, targetId, arrivalPoint, travelProfile, "");
            context.sendMessage(Message.raw("Started secure Nexori travel to " + destination.connectionAddress() + "."));
        } catch (IllegalArgumentException exception) {
            context.sendMessage(Message.raw(exception.getMessage()));
        } catch (IOException | GeneralSecurityException | IllegalStateException exception) {
            context.sendMessage(Message.raw("Failed to start secure Nexori travel: " + exception.getMessage()));
        }
    }
}
