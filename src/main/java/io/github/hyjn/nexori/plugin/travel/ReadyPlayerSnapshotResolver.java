package io.github.hyjn.nexori.plugin.travel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

/**
 * Safely resolves a {@link ReadyPlayerSnapshot} from a {@link PlayerReadyEvent}.
 *
 * <p>All engine-side accesses (getStore, getComponent, getWorld) are wrapped in try/catch
 * so that a partially initialized engine entity never causes all Nexori handlers to throw.</p>
 */
public final class ReadyPlayerSnapshotResolver {

    @Nonnull
    public ReadyPlayerSnapshot resolve(@Nonnull PlayerReadyEvent event) {
        try {
            Ref<EntityStore> entityRef = event.getPlayerRef();
            if (entityRef == null) {
                return ReadyPlayerSnapshot.unsafe("ENTITY_REF_NULL");
            }

            Store<EntityStore> store;
            try {
                store = entityRef.getStore();
            } catch (Exception ex) {
                return ReadyPlayerSnapshot.unsafe("STORE_ACCESS_FAILED");
            }
            if (store == null) {
                return ReadyPlayerSnapshot.unsafe("STORE_NULL");
            }

            PlayerRef playerRef;
            try {
                playerRef = store.getComponent(entityRef, Universe.get().getPlayerRefComponentType());
            } catch (Exception ex) {
                return ReadyPlayerSnapshot.unsafe("PLAYER_REF_COMPONENT_FAILED");
            }
            if (playerRef == null) {
                return ReadyPlayerSnapshot.unsafe("PLAYER_REF_NULL");
            }

            java.util.UUID playerUuid = playerRef.getUuid();
            if (playerUuid == null) {
                return ReadyPlayerSnapshot.unsafe("PLAYER_UUID_NULL");
            }

            String username = playerRef.getUsername();
            if (username == null) {
                username = "";
            }

            Player player = null;
            World world = null;
            try {
                player = store.getComponent(entityRef, Player.getComponentType());
                if (player != null) {
                    world = player.getWorld();
                }
            } catch (Exception ex) {
                // player/world remain null — safe snapshot with null world is still valid
            }

            return ReadyPlayerSnapshot.safe(playerUuid, username, playerRef, entityRef, store, player, world);
        } catch (Exception ex) {
            return ReadyPlayerSnapshot.unsafe("UNEXPECTED_EXCEPTION:" + ex.getClass().getSimpleName());
        }
    }
}
