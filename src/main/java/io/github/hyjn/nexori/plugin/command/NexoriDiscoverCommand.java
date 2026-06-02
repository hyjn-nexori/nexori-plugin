package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.discovery.DestinationTargetDiscoveryService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class NexoriDiscoverCommand extends AbstractPlayerCommand {

    private final NexoriPlugin plugin;
    private final DestinationTargetDiscoveryService discoveryService;
    private final RequiredArg<String> destinationArg;

    public NexoriDiscoverCommand(
        @Nonnull NexoriPlugin plugin,
        @Nonnull DestinationTargetDiscoveryService discoveryService
    ) {
        super("nexoridiscover", "Discovers destination targets from one trusted remote server.");
        this.plugin = plugin;
        this.discoveryService = discoveryService;
        this.destinationArg = withRequiredArg("destination", "Trusted destination in host:port format.", ArgTypes.STRING);
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
            context.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to discover remote Nexori targets."));
            return;
        }

        try {
            TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
            if (transformComponent == null) {
                throw new IllegalStateException("Could not read the live player position for Nexori discovery return.");
            }

            Rotation3f rotation = transformComponent.getRotation();
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
            if (headRotation != null) {
                rotation = headRotation.getRotation();
            }

            ConfiguredPeer destination = ConfiguredPeer.parse(context.get(destinationArg));
            discoveryService.discover(
                playerRef,
                destination,
                world.getName(),
                new Transform(transformComponent.getPosition(), rotation)
            );
            context.sendMessage(Message.raw("Started Nexori destination target discovery for " + destination.connectionAddress() + "."));
        } catch (IllegalArgumentException exception) {
            context.sendMessage(Message.raw(exception.getMessage()));
        } catch (IOException | GeneralSecurityException | IllegalStateException exception) {
            context.sendMessage(Message.raw("Failed to start Nexori destination target discovery: " + exception.getMessage()));
        }
    }
}
