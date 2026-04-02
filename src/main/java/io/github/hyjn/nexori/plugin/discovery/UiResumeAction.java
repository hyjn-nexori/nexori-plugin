package io.github.hyjn.nexori.plugin.discovery;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.security.GeneralSecurityException;

@FunctionalInterface
public interface UiResumeAction {

    default boolean continueDuringSetup(@Nonnull PlayerSetupConnectEvent event) throws IOException, GeneralSecurityException {
        return false;
    }

    void reopen(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Player player
    );
}
