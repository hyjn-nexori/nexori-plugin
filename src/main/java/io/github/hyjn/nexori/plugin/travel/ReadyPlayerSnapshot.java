package io.github.hyjn.nexori.plugin.travel;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A safely resolved snapshot of the engine-side player state observed at PlayerReadyEvent time.
 *
 * <p>Engine PlayerReadyEvents can fire with partially initialized or null player state.
 * All handlers must resolve a snapshot first and check {@link #safe()} before touching
 * engine APIs like getStore(), getComponent(), or getWorld().</p>
 *
 * <p>When {@link #safe()} is true, {@link #entityRef()} and {@link #store()} are guaranteed
 * non-null and stable.  {@link #world()} may still be null if the engine fired the event before
 * the player entity was placed in any world.</p>
 */
public record ReadyPlayerSnapshot(
    boolean safe,
    @Nonnull String unsafeReason,
    @Nullable UUID playerUuid,
    @Nonnull String username,
    @Nullable PlayerRef playerRef,
    @Nullable Ref<EntityStore> entityRef,
    @Nullable Store<EntityStore> store,
    @Nullable Player player,
    @Nullable World world
) {

    @Nonnull
    public static ReadyPlayerSnapshot unsafe(@Nonnull String reason) {
        return new ReadyPlayerSnapshot(false, reason, null, "", null, null, null, null, null);
    }

    @Nonnull
    public static ReadyPlayerSnapshot safe(
        @Nonnull UUID playerUuid,
        @Nonnull String username,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store,
        @Nullable Player player,
        @Nullable World world
    ) {
        if (playerRef == null) {
            throw new NullPointerException("A safe ReadyPlayerSnapshot requires a non-null playerRef.");
        }
        if (entityRef == null) {
            throw new NullPointerException("A safe ReadyPlayerSnapshot requires a non-null entityRef.");
        }
        if (store == null) {
            throw new NullPointerException("A safe ReadyPlayerSnapshot requires a non-null store.");
        }
        return new ReadyPlayerSnapshot(true, "", playerUuid, username, playerRef, entityRef, store, player, world);
    }

    /**
     * Creates a minimal snapshot for use in unit tests where no engine runtime is available.
     * Only {@link #playerUuid()} and {@link #username()} are populated; all engine references
     * are null.  Production code must never call this method.
     */
    @Nonnull
    static ReadyPlayerSnapshot forTest(@Nonnull UUID playerUuid, @Nonnull String username) {
        return new ReadyPlayerSnapshot(true, "", playerUuid, username, null, null, null, null, null);
    }
}
