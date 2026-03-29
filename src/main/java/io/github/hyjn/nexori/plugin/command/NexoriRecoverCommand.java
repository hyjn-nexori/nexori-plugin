package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;

public final class NexoriRecoverCommand extends AbstractPlayerCommand {

    private final InventoryTransferService inventoryTransferService;
    private final RequiredArg<String> transferIdArg;

    public NexoriRecoverCommand(@Nonnull InventoryTransferService inventoryTransferService) {
        super("nexorirecover", "Queries the destination for one of your Nexori inventory transfer backups and restores it if needed.");
        this.inventoryTransferService = inventoryTransferService;
        this.transferIdArg = withRequiredArg("transferId", "The transfer id shown by /nexoribackups.", ArgTypes.STRING);
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
        try {
            Player player = store.getComponent(ref, Player.getComponentType());
            inventoryTransferService.requireRecoveryInventoryEmpty(playerRef.getUuid(), player);

            TransformComponent transformComponent = store.getComponent(ref, TransformComponent.getComponentType());
            if (transformComponent == null) {
                throw new IllegalStateException("Could not read your live position for Nexori recovery return.");
            }

            Vector3f rotation = transformComponent.getRotation();
            HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
            if (headRotation != null) {
                rotation = headRotation.getRotation();
            }

            InventoryTransferService.RecoveryStartResult result = inventoryTransferService.startRecovery(
                playerRef,
                context.get(transferIdArg),
                world.getName(),
                new Transform(transformComponent.getPosition(), rotation)
            );
            context.sendMessage(Message.raw(result.message()));
        } catch (IllegalArgumentException exception) {
            context.sendMessage(Message.raw(exception.getMessage()));
        } catch (IllegalStateException exception) {
            context.sendMessage(Message.raw(exception.getMessage()));
        } catch (IOException | GeneralSecurityException exception) {
            context.sendMessage(Message.raw("Failed to start Nexori inventory recovery: " + exception.getMessage()));
        }
    }
}
