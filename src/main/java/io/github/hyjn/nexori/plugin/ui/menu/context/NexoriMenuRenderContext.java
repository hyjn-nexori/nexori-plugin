package io.github.hyjn.nexori.plugin.ui.menu.context;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Common render inputs shared by Nexori V2 menu views.
 */
public record NexoriMenuRenderContext(
    Ref<EntityStore> ref,
    Store<EntityStore> store,
    PlayerRef playerRef,
    Player player,
    NexoriPlugin plugin,
    NexoriMenuV2State state,
    List<ConfiguredPeer> peers
) {

    @Nonnull
    public NexoriMenuRenderContext withState(@Nonnull NexoriMenuV2State nextState) {
        return new NexoriMenuRenderContext(ref, store, playerRef, player, plugin, nextState, peers);
    }
}
