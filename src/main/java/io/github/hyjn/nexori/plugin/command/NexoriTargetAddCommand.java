package io.github.hyjn.nexori.plugin.command;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
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
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefinition;
import io.github.hyjn.nexori.plugin.target.DestinationTargetKind;

import javax.annotation.Nonnull;

public final class NexoriTargetAddCommand extends AbstractPlayerCommand {

    private static final Gson GSON = new Gson();

    private final NexoriPlugin plugin;
    private final RequiredArg<String> targetIdArg;
    private final RequiredArg<String> kindArg;
    private final RequiredArg<String> worldArg;
    private final RequiredArg<String> arrivalPointArg;

    public NexoriTargetAddCommand(@Nonnull NexoriPlugin plugin) {
        super("nexoritargetadd", "Adds or updates a Nexori destination target.");
        this.plugin = plugin;
        this.targetIdArg = withRequiredArg("targetId", "Destination target id.", ArgTypes.STRING);
        this.kindArg = withRequiredArg("kind", "Destination target kind.", ArgTypes.STRING);
        this.worldArg = withRequiredArg("world", "Destination world.", ArgTypes.STRING);
        this.arrivalPointArg = withRequiredArg("arrivalPoint", "Arrival point id.", ArgTypes.STRING);
        this.setPermissionGroups("OP");
    }

    @Override
    protected void execute(
        @Nonnull CommandContext ctx,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull PlayerRef playerRef,
        @Nonnull World world
    ) {
        if (!ctx.sender().hasPermission("*") && !ctx.sender().hasPermission(plugin.getBasePermission() + ".admin")) {
            ctx.sendMessage(Message.raw("You need the permission '" + plugin.getBasePermission() + ".admin' to manage Nexori destination targets."));
            return;
        }

        try {
            DestinationTargetKind kind = DestinationTargetKind.parse(ctx.get(kindArg));
            if (kind == DestinationTargetKind.NATURAL_SPAWN) {
                ctx.sendMessage(Message.raw("Natural spawn targets are generated automatically, one per world. Use the existing <world>.natural_spawn target instead of creating one manually."));
                return;
            }
            if (kind == DestinationTargetKind.PORTAL) {
                ctx.sendMessage(Message.raw("Portal targets are created automatically when you place a Nexori portal. Use the portal setup UI instead of creating one manually."));
                return;
            }
            DestinationTargetDefinition target = plugin.getDestinationTargetService().upsert(new DestinationTargetDefinition(
                ctx.get(targetIdArg),
                ctx.get(targetIdArg),
                kind,
                ctx.get(worldArg),
                ctx.get(arrivalPointArg),
                "",
                buildMetadataJson(kind, store, ref)
            ));
            ctx.sendMessage(Message.raw("Saved Nexori destination target " + target.id()
                + " kind=" + target.kind()
                + " world=" + target.worldName()
                + " arrivalPoint=" + (target.arrivalPointId().isBlank() ? "<none>" : target.arrivalPointId())));
        } catch (IllegalArgumentException exception) {
            ctx.sendMessage(Message.raw(exception.getMessage()));
        } catch (Exception exception) {
            ctx.sendMessage(Message.raw("Failed to save the Nexori destination target: " + exception.getMessage()));
        }
    }

    @Nonnull
    private String buildMetadataJson(
        @Nonnull DestinationTargetKind kind,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref
    ) {
        if (kind == DestinationTargetKind.NATURAL_SPAWN) {
            return "{}";
        }

        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            throw new IllegalStateException("Could not read the live player position to create this destination target.");
        }

        Vector3d position = transform.getPosition();
        Rotation3f rotation = transform.getRotation();
        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        if (headRotation != null) {
            rotation = headRotation.getRotation();
        }

        JsonObject root = new JsonObject();
        JsonObject positionObject = new JsonObject();
        positionObject.addProperty("x", position.x);
        positionObject.addProperty("y", position.y);
        positionObject.addProperty("z", position.z);
        root.add("position", positionObject);

        JsonObject rotationObject = new JsonObject();
        rotationObject.addProperty("pitch", rotation.x);
        rotationObject.addProperty("yaw", rotation.y);
        rotationObject.addProperty("roll", rotation.z);
        root.add("rotation", rotationObject);
        return GSON.toJson(root);
    }
}
