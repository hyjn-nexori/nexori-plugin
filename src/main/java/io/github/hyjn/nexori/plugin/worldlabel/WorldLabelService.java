package io.github.hyjn.nexori.plugin.worldlabel;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.query.Query;
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
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;

/**
 * Reconciles floating world labels by spawning or removing invisible carrier entities in each world.
 */
public final class WorldLabelService {

    private static final String CARRIER_BLOCK_TYPE = "Barrier";
    private static final long RECONCILE_INTERVAL_MILLIS = 2000L;

    private final HytaleLogger logger;
    private final WorldLabelSource source;
    private final Map<String, WorldLabelRuntime> runtimeById = new LinkedHashMap<>();
    private final Map<String, Long> nextReconcileAtByWorld = new HashMap<>();
    private final Set<String> pendingWorlds = new HashSet<>();

    /**
     * Creates the runtime label reconciler for the provided label source.
     */
    public WorldLabelService(
        @Nonnull HytaleLogger logger,
        @Nonnull WorldLabelSource source
    ) {
        this.logger = logger;
        this.source = source;
    }

    /**
     * Schedules reconciliation for one world when the reconcile interval elapses.
     */
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

    /**
     * Removes every barrier-based nameplate carrier from the current world.
     */
    public void clearBarrierNameplateCarriers(
        @Nonnull World world,
        @Nonnull IntConsumer onComplete
    ) {
        if (!world.isAlive()) {
            onComplete.accept(0);
            return;
        }

        String worldName = normalizeWorldName(world.getName());
        synchronized (this) {
            pendingWorlds.add(worldName);
            nextReconcileAtByWorld.remove(worldName);
            runtimeById.entrySet().removeIf(entry -> entry.getValue().worldName().equals(worldName));
        }

        world.execute(() -> {
            int removedCount = 0;
            try {
                Store<EntityStore> store = world.getEntityStore().getStore();
                List<Ref<EntityStore>> toRemove = new ArrayList<>();
                store.forEachChunk(
                    Query.any(),
                    (BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>>)
                        (chunk, commandBuffer) -> collectBarrierNameplateCarriers(chunk, store, toRemove)
                );
                for (Ref<EntityStore> ref : toRemove) {
                    try {
                        store.removeEntity(ref, RemoveReason.REMOVE);
                        removedCount++;
                    } catch (Exception exception) {
                        logger.atWarning().withCause(exception).log("Failed to remove Nexori world label carrier during cleanup in " + world.getName() + ".");
                    }
                }
            } catch (Exception exception) {
                logger.atWarning().withCause(exception).log("Failed to sweep Nexori world label carriers in " + world.getName() + ".");
            } finally {
                synchronized (WorldLabelService.this) {
                    pendingWorlds.remove(worldName);
                }
            }
            onComplete.accept(removedCount);
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

    private void collectBarrierNameplateCarriers(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<Ref<EntityStore>> toRemove
    ) {
        for (int entityId = 0; entityId < chunk.size(); entityId++) {
            Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
            if (ref == null) {
                continue;
            }
            BlockEntity blockEntity = store.getComponent(ref, BlockEntity.getComponentType());
            if (blockEntity == null || !CARRIER_BLOCK_TYPE.equalsIgnoreCase(blockEntity.getBlockTypeKey())) {
                continue;
            }
            if (store.getComponent(ref, Nameplate.getComponentType()) == null) {
                continue;
            }
            toRemove.add(ref);
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
            // Labels use a barrier block entity as a lightweight invisible carrier for the nameplate.
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
