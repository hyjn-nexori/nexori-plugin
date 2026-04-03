package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public final class ArenaMatchTickSystem extends EntityTickingSystem<EntityStore> {

    private final ArenaMatchService arenaMatchService;

    public ArenaMatchTickSystem(@Nonnull ArenaMatchService arenaMatchService) {
        this.arenaMatchService = arenaMatchService;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(
        float delta,
        int entityId,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null) {
            return;
        }
        if (store.getComponent(ref, Player.getComponentType()) == null) {
            return;
        }
        arenaMatchService.handlePlayerTick(ref, store, System.currentTimeMillis());
    }
}
