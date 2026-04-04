package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.builtin.instances.removal.WorldEmptyCondition;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;

public final class ArenaInstanceRuntime {

    private ArenaInstanceRuntime() {
    }

    @Nonnull
    public static String buildInstanceWorldName(@Nonnull String rawMatchId) {
        return InstancesPlugin.safeName("nexori-match-" + rawMatchId);
    }

    public static void configureInstanceLifecycle(@Nonnull World world) {
        world.execute(() -> {
            var worldConfig = world.getWorldConfig();
            worldConfig.setDeleteOnRemove(true);
            InstanceWorldConfig instanceConfig = InstanceWorldConfig.ensureAndGet(worldConfig);
            instanceConfig.setRemovalConditions(new WorldEmptyCondition(20.0));
            worldConfig.markChanged();
        });
    }
}
