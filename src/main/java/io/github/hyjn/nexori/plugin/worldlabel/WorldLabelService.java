package io.github.hyjn.nexori.plugin.worldlabel;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.DespawnComponent;
import com.hypixel.hytale.server.core.modules.entity.hitboxcollision.HitboxCollision;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorldLabelService {

    private static final String CARRIER_BLOCK_TYPE = "Barrier";
    private static final long RECONCILE_INTERVAL_MILLIS = 2000L;

    private final HytaleLogger logger;
    private final WorldLabelSource source;
    private final Map<String, WorldLabelRuntime> runtimeById = new LinkedHashMap<>();
    private final Map<String, Long> nextReconcileAtByWorld = new HashMap<>();
    private final Set<String> pendingWorlds = new HashSet<>();

    public WorldLabelService(
        @Nonnull HytaleLogger logger,
        @Nonnull WorldLabelSource source
    ) {
        this.logger = logger;
        this.source = source;
    }

    public synchronized void handleWorldTick(@Nonnull World world, long nowEpochMillis) {
        if (!world.isAlive()) {
            return;
        }

        String worldName = normalizeWorldName(world.getName());
        if (worldName.isBlank()) {
            return;
        }
        if (pendingWorlds.contains(worldName)) {
            return;
        }
        long nextAllowed = nextReconcileAtByWorld.getOrDefault(worldName, 0L);
        if (nowEpochMillis < nextAllowed) {
            return;
        }

        nextReconcileAtByWorld.put(worldName, nowEpochMillis + RECONCILE_INTERVAL_MILLIS);
        pendingWorlds.add(worldName);
        world.execute(() -> {
            try {
                reconcileWorld(world);
            } catch (Exception exception) {
                logger.atWarning().withCause(exception).log("Failed to reconcile Nexori world labels for " + worldName + ".");
            } finally {
                synchronized (WorldLabelService.this) {
                    pendingWorlds.remove(worldName);
                }
            }
        });
    }

    private synchronized void reconcileWorld(@Nonnull World world) {
        if (!world.isAlive()) {
            return;
        }

        String worldName = normalizeWorldName(world.getName());
        List<WorldLabelDefinition> desiredDefinitions = source.listForWorld(worldName).stream()
            .map(WorldLabelDefinition::normalized)
            .filter(definition -> !definition.labelId().isBlank())
            .filter(definition -> !definition.worldName().isBlank())
            .filter(definition -> !definition.text().isBlank())
            .toList();

        Map<String, WorldLabelDefinition> desiredById = new LinkedHashMap<>();
        for (WorldLabelDefinition definition : desiredDefinitions) {
            desiredById.put(definition.labelId(), definition);
        }

        Store<EntityStore> store = world.getEntityStore().getStore();
        TimeResource timeResource = store.getResource(TimeResource.getResourceType());

        List<String> toRemove = new ArrayList<>();
        for (WorldLabelRuntime runtime : runtimeById.values()) {
            if (!runtime.worldName().equals(worldName)) {
                continue;
            }
            if (!desiredById.containsKey(runtime.labelId())) {
                toRemove.add(runtime.labelId());
                continue;
            }
            WorldLabelDefinition desired = desiredById.get(runtime.labelId());
            if (!runtime.matches(desired) || !isAlive(store, runtime.ref())) {
                toRemove.add(runtime.labelId());
            }
        }

        for (String labelId : toRemove) {
            WorldLabelRuntime runtime = runtimeById.remove(labelId);
            if (runtime != null) {
                removeRuntime(store, runtime);
            }
        }

        for (WorldLabelDefinition definition : desiredDefinitions) {
            if (runtimeById.containsKey(definition.labelId())) {
                continue;
            }
            WorldLabelRuntime runtime = spawnRuntime(store, timeResource, definition);
            if (runtime != null) {
                runtimeById.put(runtime.labelId(), runtime);
            }
        }
    }

    private boolean isAlive(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        try {
            return store.getComponent(ref, BlockEntity.getComponentType()) != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void removeRuntime(@Nonnull Store<EntityStore> store, @Nonnull WorldLabelRuntime runtime) {
        try {
            if (isAlive(store, runtime.ref())) {
                store.removeEntity(runtime.ref(), RemoveReason.REMOVE);
            }
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to remove Nexori world label carrier " + runtime.labelId() + ".");
        }
    }

    private WorldLabelRuntime spawnRuntime(
        @Nonnull Store<EntityStore> store,
        @Nonnull TimeResource timeResource,
        @Nonnull WorldLabelDefinition definition
    ) {
        try {
            Holder<EntityStore> holder = BlockEntity.assembleDefaultBlockEntity(
                timeResource,
                CARRIER_BLOCK_TYPE,
                definition.position()
            );
            Ref<EntityStore> ref = store.addEntity(holder, AddReason.SPAWN);
            store.removeComponentIfExists(ref, DespawnComponent.getComponentType());
            store.removeComponentIfExists(ref, HitboxCollision.getComponentType());
            Nameplate nameplate = store.ensureAndGetComponent(ref, Nameplate.getComponentType());
            nameplate.setText(definition.text());
            return new WorldLabelRuntime(
                definition.labelId(),
                definition.worldName(),
                definition.x(),
                definition.y(),
                definition.z(),
                definition.text(),
                ref
            );
        } catch (Exception exception) {
            logger.atWarning().withCause(exception).log("Failed to spawn Nexori world label carrier " + definition.labelId() + ".");
            return null;
        }
    }

    @Nonnull
    private static String normalizeWorldName(@Nonnull String worldName) {
        return worldName == null ? "" : worldName.trim().toLowerCase();
    }

    private record WorldLabelRuntime(
        @Nonnull String labelId,
        @Nonnull String worldName,
        double x,
        double y,
        double z,
        @Nonnull String text,
        @Nonnull Ref<EntityStore> ref
    ) {
        private boolean matches(@Nonnull WorldLabelDefinition definition) {
            return labelId.equals(definition.labelId())
                && worldName.equals(definition.worldName())
                && Double.compare(x, definition.x()) == 0
                && Double.compare(y, definition.y()) == 0
                && Double.compare(z, definition.z()) == 0
                && text.equals(definition.text());
        }
    }
}
