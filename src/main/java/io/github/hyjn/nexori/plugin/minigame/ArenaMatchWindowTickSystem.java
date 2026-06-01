package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Set;

/**
 * Server-global tick that reconciles initial placement windows for every live arena match,
 * independently of whether any player entity is currently ticking.
 *
 * <p>{@link ArenaMatchTickSystem} is an {@code EntityTickingSystem} and therefore only runs when a
 * player entity ticks; if the only expected player is stuck in transfer/placement and never reaches
 * CONFIRMED (zero placed/active players), the window would never reconcile. This {@link TickingSystem}
 * runs once per server tick regardless of entity count, so the window still closes — opening the
 * start gate or cancelling for a shortfall — and never leaves a match stuck.</p>
 */
public final class ArenaMatchWindowTickSystem extends TickingSystem<EntityStore> {

    private final ArenaMatchService arenaMatchService;

    public ArenaMatchWindowTickSystem(@Nonnull ArenaMatchService arenaMatchService) {
        this.arenaMatchService = arenaMatchService;
    }

    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Set.of();
    }

    @Override
    public void tick(float delta, int parallelIndex, @Nonnull Store<EntityStore> store) {
        // Reconcile is server-global state; run it once per tick. The service throttles internally
        // and is idempotent, so running across multiple stores/ticks is safe.
        if (parallelIndex != 0) {
            return;
        }
        arenaMatchService.tickInitialPlacementWindows(System.currentTimeMillis());
    }
}
