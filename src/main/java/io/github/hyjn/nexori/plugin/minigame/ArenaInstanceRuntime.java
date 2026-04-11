package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ArenaInstanceRuntime {

    private ArenaInstanceRuntime() {
    }

    @Nonnull
    public static String buildInstanceWorldName(@Nonnull String rawMatchId) {
        return InstancesPlugin.safeName("nexori-match-" + rawMatchId);
    }

    @Nonnull
    public static CompletableFuture<World> prepareInstanceForMatch(
        @Nonnull World world,
        @Nonnull List<InstanceSpawnSlotDefinition> spawnSlots,
        @Nonnull UUID playerUuid,
        int launchIndex
    ) {
        CompletableFuture<World> prepared = new CompletableFuture<>();
        world.execute(() -> {
            var worldConfig = world.getWorldConfig();
            worldConfig.setDeleteOnRemove(true);
            InstanceWorldConfig instanceConfig = InstanceWorldConfig.ensureAndGet(worldConfig);
            instanceConfig.setRemovalConditions(new WorldEmptyCondition(20.0));
            if (!spawnSlots.isEmpty()) {
                Transform[] spawnPoints = spawnSlots.stream()
                    .map(slot -> new Transform(
                        slot.x(),
                        slot.y(),
                        slot.z(),
                        slot.pitch(),
                        slot.yaw(),
                        slot.roll()
                    ))
                    .toArray(Transform[]::new);
                int normalizedLaunchIndex = Math.floorMod(launchIndex, spawnPoints.length);
                NexoriAssignedSpawnProvider provider = worldConfig.getSpawnProvider() instanceof NexoriAssignedSpawnProvider existing
                        ? existing
                        : NexoriAssignedSpawnProvider.fromSpawnPoints(spawnPoints);
                worldConfig.setSpawnProvider(provider.withAssignment(playerUuid, normalizedLaunchIndex));
            }
            worldConfig.markChanged();
            prepared.complete(world);
        });
        return prepared;
    }
}
