package io.github.hyjn.nexori.plugin.minigame.spectator;

import com.hypixel.hytale.protocol.GameMode;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public record SpectatorRuntimeSnapshot(
    @Nonnull UUID playerUuid,
    @Nonnull SpectatorRuntimeMode mode,
    long createdAtEpochMs,
    @Nullable GameMode previousGameMode,
    boolean previousCanFly,
    boolean previousDefaultCanFly,
    boolean previousIntangible,
    boolean previousInvulnerable,
    @Nullable Integer previousCollisionByMaterial,
    @Nullable Boolean previousCharacterCollisions,
    @Nullable Boolean previousTriggerBlocks,
    @Nullable Boolean previousDamageBlocks,
    @Nullable Boolean previousDamageBlocking,
    @Nullable Boolean previousExecuteTriggers,
    @Nullable Boolean previousExecuteBlockDamage,
    @Nonnull Set<UUID> viewersHiddenByProbe
) {
}
