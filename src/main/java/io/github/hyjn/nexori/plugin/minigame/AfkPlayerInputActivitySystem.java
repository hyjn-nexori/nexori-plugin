package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.OrderPriority;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSystems;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public final class AfkPlayerInputActivitySystem extends EntityTickingSystem<EntityStore> {

    private final AfkActivityService afkActivityService;

    public AfkPlayerInputActivitySystem(@Nonnull AfkActivityService afkActivityService) {
        this.afkActivityService = afkActivityService;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(new SystemDependency<>(
            Order.BEFORE,
            PlayerSystems.ProcessPlayerInput.class,
            OrderPriority.CLOSEST
        ));
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
        if (ref == null || store.getComponent(ref, Player.getComponentType()) == null) {
            return;
        }

        PlayerRef playerRef = store.getComponent(ref, Universe.get().getPlayerRefComponentType());
        if (playerRef == null) {
            return;
        }

        PlayerInput playerInput = store.getComponent(ref, PlayerInput.getComponentType());
        List<PlayerInput.InputUpdate> updates = playerInput == null ? null : playerInput.getMovementUpdateQueue();
        boolean hasInputActivity = updates != null && !updates.isEmpty();
        afkActivityService.handlePlayerInputTick(playerRef, hasInputActivity, System.currentTimeMillis());
    }
}
