package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public record QueueDefinition(
    String queueId,
    String displayName,
    List<String> arenaIds,
    int minPlayers,
    int maxPlayers,
    int countdownSeconds,
    String launchTravelProfileId,
    boolean enabled
) {

    @Nonnull
    public QueueDefinition normalized() {
        String normalizedQueueId = normalizeRequired(queueId, "Queue id cannot be blank.");
        String normalizedDisplayName = normalizeDisplayName(displayName, normalizedQueueId);
        List<String> normalizedArenaIds = normalizeIds(arenaIds);
        String normalizedTravelProfileId = normalizeRequired(
            launchTravelProfileId,
            "Queue launch travel profile id cannot be blank."
        );
        return new QueueDefinition(
            normalizedQueueId,
            normalizedDisplayName,
            normalizedArenaIds,
            minPlayers,
            maxPlayers,
            countdownSeconds,
            normalizedTravelProfileId,
            enabled
        );
    }

    @Nonnull
    public static String normalizeId(@Nonnull String rawId) {
        return normalizeRequired(rawId, "Queue id cannot be blank.");
    }

    @Nonnull
    private static List<String> normalizeIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String rawId : rawIds) {
            String value = normalizeOptional(rawId, "");
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return List.copyOf(new ArrayList<>(normalized));
    }

    @Nonnull
    private static String normalizeRequired(String rawValue, @Nonnull String message) {
        String normalized = normalizeOptional(rawValue, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? defaultValue : normalized;
    }

    @Nonnull
    private static String normalizeDisplayName(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }
}
