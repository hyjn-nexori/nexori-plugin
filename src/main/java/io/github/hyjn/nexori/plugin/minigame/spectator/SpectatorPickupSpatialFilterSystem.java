package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.ResourceType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerItemEntityPickupSystem;
import com.hypixel.hytale.server.core.modules.entity.system.PlayerSpatialSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

public final class SpectatorPickupSpatialFilterSystem extends TickingSystem<EntityStore> {

    private final SpectatorPickupSpatialGuard spatialGuard;
    private final ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> playerSpatialResourceType;
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, PlayerSpatialSystem.class, OrderPriority.CLOSEST),
        new SystemDependency<>(Order.BEFORE, PlayerItemEntityPickupSystem.class, OrderPriority.CLOSEST)
    );

    public SpectatorPickupSpatialFilterSystem(
        @Nonnull SpectatorPickupSpatialGuard spatialGuard,
        @Nonnull ResourceType<EntityStore, SpatialResource<Ref<EntityStore>, EntityStore>> playerSpatialResourceType
    ) {
        this.spatialGuard = spatialGuard;
        this.playerSpatialResourceType = playerSpatialResourceType;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Override
    public void tick(float delta, int parallelIndex, Store<EntityStore> store) {
        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource = store.getResource(playerSpatialResourceType);
        spatialGuard.filterRuntimeSpectators(store, playerSpatialResource);
    }
}
