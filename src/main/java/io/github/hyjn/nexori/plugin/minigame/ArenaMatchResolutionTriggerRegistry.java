package io.github.hyjn.nexori.plugin.minigame;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ArenaMatchResolutionTriggerRegistry {

    private final Map<String, ArenaMatchResolutionTrigger> triggersById = new LinkedHashMap<>();

    public ArenaMatchResolutionTriggerRegistry() {
        register(new LastPlayerAliveArenaMatchResolutionTrigger());
    }

    public synchronized void register(@Nonnull ArenaMatchResolutionTrigger trigger) {
        triggersById.put(normalizeId(trigger.id()), trigger);
    }

    @Nonnull
    public synchronized Optional<ArenaMatchResolutionTrigger> find(@Nonnull String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(triggersById.get(normalizeId(rawId)));
    }

    @Nonnull
    private static String normalizeId(@Nonnull String rawId) {
        String normalized = rawId.trim().toLowerCase();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Arena match resolution trigger id cannot be blank.");
        }
        return normalized;
    }
}
