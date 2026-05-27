package io.github.hyjn.nexori.plugin.portal;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NexoriPortalBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private final HytaleLogger logger;
    private final PortalInstanceService portalInstanceService;

    public NexoriPortalBreakSystem(
        HytaleLogger logger,
        PortalInstanceService portalInstanceService
    ) {
        super(BreakBlockEvent.class);
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
        BreakBlockEvent event
    ) {
        if (!NexoriPortalIds.matchesAssetId(event.getBlockType().getId())) {
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

        try {
            boolean removed = portalInstanceService.removePlacedPortal(
                player.getWorld().getName(),
                new Vector3i(event.getTargetBlock().x, event.getTargetBlock().y, event.getTargetBlock().z)
            );
            if (removed) {
                logger.atInfo().log("Removed Nexori portal record at "
                    + player.getWorld().getName()
                    + " " + event.getTargetBlock());
            }
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to clean up a broken Nexori portal.");
        }
    }
}
