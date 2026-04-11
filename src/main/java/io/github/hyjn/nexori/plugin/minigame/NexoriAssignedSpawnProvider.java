package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.array.ArrayCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.math.util.HashUtil;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class NexoriAssignedSpawnProvider implements ISpawnProvider {

    public static final BuilderCodec<NexoriAssignedSpawnProvider> CODEC = BuilderCodec
            .builder(NexoriAssignedSpawnProvider.class, NexoriAssignedSpawnProvider::new)
            .append(
                    new KeyedCodec<>("SpawnPoints", new ArrayCodec<>(Transform.CODEC_DEGREES, Transform[]::new)),
                    (provider, spawnPoints) -> provider.spawnPoints = spawnPoints == null ? new Transform[0] : spawnPoints,
                    provider -> provider.spawnPoints
            )
            .documentation("The ordered list of Nexori spawn slots for this instance.")
            .add()
            .append(
                    new KeyedCodec<>("Assignments", new ArrayCodec<>(AssignedPlayerSpawn.CODEC, AssignedPlayerSpawn[]::new)),
                    (provider, assignments) -> provider.assignments = assignments == null ? new AssignedPlayerSpawn[0] : assignments,
                    provider -> provider.assignments
            )
            .documentation("Exact player-to-spawn-slot assignments for this instance runtime.")
            .add()
            .build();

    private Transform[] spawnPoints = new Transform[0];
    private AssignedPlayerSpawn[] assignments = new AssignedPlayerSpawn[0];

    public NexoriAssignedSpawnProvider() {
    }

    public NexoriAssignedSpawnProvider(@Nonnull Transform[] spawnPoints, @Nonnull AssignedPlayerSpawn[] assignments) {
        this.spawnPoints = cloneSpawnPoints(spawnPoints);
        this.assignments = cloneAssignments(assignments);
    }

    @Nonnull
    public static NexoriAssignedSpawnProvider fromSpawnPoints(@Nonnull Transform[] spawnPoints) {
        return new NexoriAssignedSpawnProvider(spawnPoints, new AssignedPlayerSpawn[0]);
    }

    @Nonnull
    public NexoriAssignedSpawnProvider withAssignment(@Nonnull UUID playerUuid, int slotIndex) {
        int normalizedSlotIndex = spawnPoints.length == 0 ? 0 : Math.floorMod(slotIndex, spawnPoints.length);

        List<AssignedPlayerSpawn> nextAssignments = new ArrayList<>();
        String playerUuidString = playerUuid.toString();
        boolean replaced = false;
        for (AssignedPlayerSpawn assignment : assignments) {
            if (assignment == null) {
                continue;
            }
            if (playerUuidString.equalsIgnoreCase(assignment.playerUuid)) {
                nextAssignments.add(new AssignedPlayerSpawn(playerUuidString, normalizedSlotIndex));
                replaced = true;
            } else {
                nextAssignments.add(new AssignedPlayerSpawn(
                        assignment.playerUuid,
                        assignment.slotIndex
                ));
            }
        }
        if (!replaced) {
            nextAssignments.add(new AssignedPlayerSpawn(playerUuidString, normalizedSlotIndex));
        }
        return new NexoriAssignedSpawnProvider(spawnPoints, nextAssignments.toArray(AssignedPlayerSpawn[]::new));
    }

    @Override
    @Nonnull
    public Transform getSpawnPoint(@Nonnull World world, @Nonnull UUID playerUuid) {
        String playerUuidString = playerUuid.toString();
        for (AssignedPlayerSpawn assignment : assignments) {
            if (assignment != null
                    && assignment.playerUuid != null
                    && playerUuidString.equalsIgnoreCase(assignment.playerUuid)
                    && assignment.slotIndex >= 0
                    && assignment.slotIndex < spawnPoints.length
                    && spawnPoints[assignment.slotIndex] != null) {
                return spawnPoints[assignment.slotIndex].clone();
            }
        }

        if (spawnPoints.length == 0) {
            return new Transform(0.0, 0.0, 0.0, 0.0f, 0.0f, 0.0f);
        }

        int index = Math.floorMod((int) HashUtil.hashUuid(playerUuid), spawnPoints.length);
        return spawnPoints[index].clone();
    }

    @Override
    @Nonnull
    public Transform[] getSpawnPoints() {
        return cloneSpawnPoints(spawnPoints);
    }

    @Override
    public boolean isWithinSpawnDistance(@Nonnull Vector3d position, double distance) {
        double maxDistanceSquared = distance * distance;
        for (Transform spawnPoint : spawnPoints) {
            if (spawnPoint != null && position.distanceSquaredTo(spawnPoint.getPosition()) < maxDistanceSquared) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static Transform[] cloneSpawnPoints(@Nonnull Transform[] source) {
        Transform[] copy = new Transform[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    @Nonnull
    private static AssignedPlayerSpawn[] cloneAssignments(@Nonnull AssignedPlayerSpawn[] source) {
        AssignedPlayerSpawn[] copy = new AssignedPlayerSpawn[source.length];
        for (int index = 0; index < source.length; index++) {
            AssignedPlayerSpawn assignment = source[index];
            copy[index] = assignment == null
                    ? null
                    : new AssignedPlayerSpawn(
                    assignment.playerUuid,
                    assignment.slotIndex
            );
        }
        return copy;
    }

    public static final class AssignedPlayerSpawn {

        public static final BuilderCodec<AssignedPlayerSpawn> CODEC = BuilderCodec
                .builder(AssignedPlayerSpawn.class, AssignedPlayerSpawn::new)
                .append(
                        new KeyedCodec<>("PlayerUuid", new StringCodec()),
                        (entry, playerUuid) -> entry.playerUuid = playerUuid,
                        entry -> entry.playerUuid
                )
                .documentation("The player uuid assigned to this spawn slot.")
                .add()
                .append(
                        new KeyedCodec<>("SlotIndex", new StringCodec()),
                        (entry, slotIndex) -> entry.slotIndex = parseSlotIndex(slotIndex),
                        entry -> Integer.toString(entry.slotIndex)
                )
                .documentation("The assigned spawn slot index inside SpawnPoints.")
                .add()
                .build();

        private String playerUuid = "";
        private int slotIndex = 0;

        public AssignedPlayerSpawn() {
        }

        public AssignedPlayerSpawn(String playerUuid, int slotIndex) {
            this.playerUuid = playerUuid == null ? "" : playerUuid;
            this.slotIndex = Math.max(slotIndex, 0);
        }

        private static int parseSlotIndex(String rawSlotIndex) {
            if (rawSlotIndex == null || rawSlotIndex.isBlank()) {
                return 0;
            }
            try {
                return Math.max(Integer.parseInt(rawSlotIndex), 0);
            } catch (NumberFormatException exception) {
                return 0;
            }
        }
    }
}