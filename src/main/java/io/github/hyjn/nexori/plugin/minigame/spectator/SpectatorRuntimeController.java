package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.UUID;

public interface SpectatorRuntimeController {

    @Nonnull
    SpectatorRuntimeResult enterSpectator(
        @Nonnull PlayerRef playerRef,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom,
        @Nonnull SpectatorRuntimeReason reason
    );

    @Nonnull
    SpectatorRuntimeResult exitSpectator(@Nonnull PlayerRef playerRef, @Nonnull SpectatorRuntimeReason reason);

    @Nonnull
    SpectatorRuntimeResult restoreIfTracked(@Nonnull UUID playerUuid, @Nonnull SpectatorRuntimeReason reason);

    boolean isRuntimeSpectator(@Nonnull UUID playerUuid);

    @Nonnull
    SpectatorRuntimeResult refreshHiddenViewers(
        @Nonnull UUID spectatorUuid,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom
    );
}
