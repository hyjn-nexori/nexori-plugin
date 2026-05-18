package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.protocol.GameMode;

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
            Set.copyOf(new LinkedHashSet<>(rawHiddenViewerUuids))
        );
    }
}
