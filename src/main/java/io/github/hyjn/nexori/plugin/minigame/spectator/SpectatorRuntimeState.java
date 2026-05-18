package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;

import javax.annotation.Nonnull;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record SpectatorRuntimeState(
    @Nonnull UUID playerUuid,
    long createdAtEpochMs,
    @Nonnull SpectatorRuntimeReason reason,
    GameMode previousGameMode,
    boolean previousCanFly,
    boolean previousDefaultCanFly,
    boolean previousIntangible,
    boolean previousInvulnerable,
    Integer previousCollisionByMaterial,
    Boolean previousCharacterCollisions,
    Boolean previousTriggerBlocks,
    Boolean previousDamageBlocks,
    Boolean previousDamageBlocking,
    Boolean previousExecuteTriggers,
    Boolean previousExecuteBlockDamage,
    Model previousModel,
    @Nonnull Set<UUID> hiddenViewerUuids
) {
    @Nonnull
    public SpectatorRuntimeState withHiddenViewerUuids(@Nonnull Set<UUID> rawHiddenViewerUuids) {
        return new SpectatorRuntimeState(
            playerUuid,
            createdAtEpochMs,
            reason,
            previousGameMode,
            previousCanFly,
            previousDefaultCanFly,
            previousIntangible,
            previousInvulnerable,
            previousCollisionByMaterial,
            previousCharacterCollisions,
            previousTriggerBlocks,
            previousDamageBlocks,
            previousDamageBlocking,
            previousExecuteTriggers,
            previousExecuteBlockDamage,
            previousModel,
            Set.copyOf(new LinkedHashSet<>(rawHiddenViewerUuids))
        );
    }
}
