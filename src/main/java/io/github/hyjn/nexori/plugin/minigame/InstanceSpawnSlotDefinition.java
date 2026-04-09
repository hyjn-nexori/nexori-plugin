package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.Locale;

public record InstanceSpawnSlotDefinition(
    String slotId,
    String instanceTemplateId,
    double x,
    double y,
    double z,
    float pitch,
    float yaw,
    float roll,
    long createdAtEpochMs
) {

    @Nonnull
    public InstanceSpawnSlotDefinition normalized() {
        long now = System.currentTimeMillis();
        return new InstanceSpawnSlotDefinition(
            normalizeRequired(slotId, "Spawn slot id cannot be blank."),
            normalizeRequiredTemplateId(instanceTemplateId),
            x,
            y,
            z,
            pitch,
            yaw,
            roll,
            createdAtEpochMs <= 0L ? now : createdAtEpochMs
        );
    }

    @Nonnull
    public static String normalizeSlotId(@Nonnull String rawSlotId) {
        return normalizeRequired(rawSlotId, "Spawn slot id cannot be blank.");
    }

    @Nonnull
    private static String normalizeRequiredTemplateId(String rawTemplateId) {
        if (rawTemplateId == null) {
            throw new IllegalArgumentException("Instance template id cannot be blank.");
        }
        String normalized = rawTemplateId.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Instance template id cannot be blank.");
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        if (rawValue == null) {
            throw new IllegalArgumentException(message);
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
