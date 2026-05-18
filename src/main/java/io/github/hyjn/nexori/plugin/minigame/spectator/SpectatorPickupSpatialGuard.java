package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialData;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.IdentityHashMap;
import java.util.Map;

public final class SpectatorPickupSpatialGuard {

    private final SpectatorRuntimeController spectatorRuntimeController;
    private final Map<Store<EntityStore>, SpatialSnapshot> snapshotsByStore = new IdentityHashMap<>();

    public SpectatorPickupSpatialGuard(
        @Nonnull SpectatorRuntimeController spectatorRuntimeController
    ) {
        this.spectatorRuntimeController = spectatorRuntimeController;
    }

    void filterRuntimeSpectators(
        Store<EntityStore> store,
        SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource
    ) {
        if (store == null || playerSpatialResource == null || snapshotsByStore.containsKey(store)) {
            return;
        }

        SpatialData<Ref<EntityStore>> spatialData = playerSpatialResource.getSpatialData();
        int size = spatialData.size();
        if (size == 0) {
            return;
        }

        Vector3d[] vectors = new Vector3d[size];
        @SuppressWarnings("unchecked")
        Ref<EntityStore>[] refs = new Ref[size];
        int kept = 0;
        int filtered = 0;

        for (int index = 0; index < size; index++) {
            vectors[index] = spatialData.getVector(index);
            refs[index] = spatialData.getData(index);
            if (isRuntimeSpectator(store, refs[index])) {
                filtered++;
            } else {
                kept++;
            }
        }

        if (filtered == 0) {
            return;
        }

        snapshotsByStore.put(store, new SpatialSnapshot(vectors, refs));
        spatialData.clear();
        spatialData.addCapacity(kept);
        for (int index = 0; index < refs.length; index++) {
            if (!isRuntimeSpectator(store, refs[index])) {
                spatialData.append(vectors[index], refs[index]);
            }
        }
        playerSpatialResource.getSpatialStructure().rebuild(spatialData);
    }

    void restore(Store<EntityStore> store, SpatialResource<Ref<EntityStore>, EntityStore> playerSpatialResource) {
        if (store == null || playerSpatialResource == null) {
            return;
        }

        SpatialSnapshot snapshot = snapshotsByStore.remove(store);
        if (snapshot == null) {
            return;
        }

        SpatialData<Ref<EntityStore>> spatialData = playerSpatialResource.getSpatialData();
        spatialData.clear();
        spatialData.addCapacity(snapshot.refs().length);
        for (int index = 0; index < snapshot.refs().length; index++) {
            spatialData.append(snapshot.vectors()[index], snapshot.refs()[index]);
        }
        playerSpatialResource.getSpatialStructure().rebuild(spatialData);
    }

    private boolean isRuntimeSpectator(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (store == null || ref == null || !ref.isValid()) {
            return false;
        }
        PlayerRef playerRef = store.getComponent(ref, Universe.get().getPlayerRefComponentType());
        return playerRef != null && spectatorRuntimeController.isRuntimeSpectator(playerRef.getUuid());
    }

    private record SpatialSnapshot(
        Vector3d[] vectors,
        Ref<EntityStore>[] refs
    ) {
    }
}
