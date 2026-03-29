package io.github.hyjn.nexori.plugin.portal;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NexoriPortalPlaceSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    private final HytaleLogger logger;
    private final PortalInstanceService portalInstanceService;

    public NexoriPortalPlaceSystem(
        HytaleLogger logger,
        PortalInstanceService portalInstanceService
    ) {
        super(PlaceBlockEvent.class);
        this.logger = logger;
        this.portalInstanceService = portalInstanceService;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
        int entityId,
        ArchetypeChunk<EntityStore> chunk,
        Store<EntityStore> store,
        CommandBuffer<EntityStore> commandBuffer,
        PlaceBlockEvent event
    ) {
        if (event.getItemInHand() == null || !NexoriPortalIds.matchesItemId(event.getItemInHand().getItemId())) {
            return;
        }

        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null) {
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null || player.getWorld() == null) {
            return;
        }

        HeadRotation headRotation = store.getComponent(ref, HeadRotation.getComponentType());
        Vector3f rotation = headRotation == null
            ? new Vector3f(0.0f, 0.0f, 0.0f)
            : headRotation.getRotation().clone();

        try {
            PortalInstanceDefinition portal = portalInstanceService.registerPlacedPortal(
                player.getWorld().getName(),
                new Vector3i(event.getTargetBlock()),
                rotation
            );
            logger.atInfo().log("Registered Nexori portal " + portal.portalId()
                + " at " + portal.worldName()
                + " (" + portal.blockX() + ", " + portal.blockY() + ", " + portal.blockZ() + ")");
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to register a placed Nexori portal.");
        }
    }
}
