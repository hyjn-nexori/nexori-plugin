package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.UUID;

public final class NoopSpectatorRuntimeController implements SpectatorRuntimeController {

    public static final NoopSpectatorRuntimeController INSTANCE = new NoopSpectatorRuntimeController();

    private NoopSpectatorRuntimeController() {
    }

    @Override
    public SpectatorRuntimeResult enterSpectator(
        @Nonnull PlayerRef playerRef,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom,
        @Nonnull SpectatorRuntimeReason reason,
        @Nullable String spectatorModelId
    ) {
        return SpectatorRuntimeResult.success(playerRef.getUuid());
    }

    @Override
    public SpectatorRuntimeResult exitSpectator(@Nonnull PlayerRef playerRef, @Nonnull SpectatorRuntimeReason reason) {
        return SpectatorRuntimeResult.success(playerRef.getUuid());
    }

    @Override
    public SpectatorRuntimeResult restoreIfTracked(@Nonnull UUID playerUuid, @Nonnull SpectatorRuntimeReason reason) {
        return SpectatorRuntimeResult.success(playerUuid);
    }

    @Override
    public boolean isRuntimeSpectator(@Nonnull UUID playerUuid) {
        return false;
    }

    @Override
    public SpectatorRuntimeResult refreshHiddenViewers(
        @Nonnull UUID spectatorUuid,
        @Nonnull Collection<UUID> viewerUuidsToHideFrom
    ) {
        return SpectatorRuntimeResult.success(spectatorUuid);
    }
}
