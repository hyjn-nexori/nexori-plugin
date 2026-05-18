package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

public interface SpectatorRuntimeController {

    @Nonnull
    default SpectatorRuntimeResult enterSpectator(
        @Nonnull PlayerRef playerRef,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom,
        @Nonnull SpectatorRuntimeReason reason
    ) {
        return enterSpectator(playerRef, viewerUuidsToHideFrom, reason, null);
    }

    @Nonnull
    SpectatorRuntimeResult enterSpectator(
        @Nonnull PlayerRef playerRef,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom,
        @Nonnull SpectatorRuntimeReason reason,
        @Nullable String spectatorModelId
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
