package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerResolutionOutcome;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure validation result for match result submission flows.
 */
public record MatchResultValidationResult(
    boolean valid,
    List<PlayerResult> players,
    Map<String, String> metadata,
    String reason,
    JsonObject customData,
    String message
) {

    @Nonnull
    public static MatchResultValidationResult valid(
        @Nonnull List<PlayerResult> players,
        @Nonnull Map<String, String> metadata,
        @Nonnull String reason,
        @Nonnull JsonObject customData
    ) {
        return new MatchResultValidationResult(true, List.copyOf(players), Map.copyOf(metadata), reason, customData.deepCopy(), "");
    }

    @Nonnull
    public static MatchResultValidationResult invalid(@Nonnull String message) {
        return new MatchResultValidationResult(false, List.of(), Map.of(), "", new JsonObject(), normalizeOptional(message, "Invalid match result."));
    }

    public MatchResultValidationResult {
        players = players == null ? List.of() : List.copyOf(players);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        reason = normalizeOptional(reason, "");
        customData = customData == null ? new JsonObject() : customData.deepCopy();
        message = normalizeOptional(message, "");
    }

    public record PlayerResult(
        UUID playerUuid,
        ArenaPlayerResolutionOutcome runtimeOutcome,
        String backendOutcome,
        String reason
    ) {

        public PlayerResult {
            backendOutcome = normalizeOptional(backendOutcome, runtimeOutcome == null ? "" : runtimeOutcome.name());
            reason = normalizeOptional(reason, "");
        }
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }
}
