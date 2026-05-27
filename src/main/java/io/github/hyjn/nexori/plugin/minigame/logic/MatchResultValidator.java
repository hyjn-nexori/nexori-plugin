package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonNull;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerResolutionOutcome;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pure validator for match result payloads submitted by rules mods.
 *
 * <p>The runtime service remains responsible for storing matches, hashing
 * accepted payloads, reporting results, and scheduling player returns.</p>
 */
public final class MatchResultValidator {

    public static final int MAX_RESULT_REASON_LENGTH = 512;
    public static final int MAX_RESULT_METADATA_ENTRIES = 32;
    public static final int MAX_RESULT_METADATA_KEY_LENGTH = 64;
    public static final int MAX_RESULT_METADATA_VALUE_LENGTH = 512;
    public static final int MAX_RESULT_PLAYER_REASON_LENGTH = 256;
    public static final int MAX_CUSTOM_DATA_BYTES = 32_768;
    public static final int MAX_CUSTOM_DATA_DEPTH = 8;
    public static final int MAX_CUSTOM_DATA_PROPERTIES = 256;
    public static final int MAX_CUSTOM_DATA_ARRAY_LENGTH = 128;
    public static final int MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH = 64;
    public static final int MAX_CUSTOM_DATA_STRING_LENGTH = 1024;

    private static final Gson GSON = new Gson();

    @Nonnull
    public MatchResultValidationResult validateSubmittedResult(
        @Nonnull ArenaActiveMatch match,
        List<MatchResultValidationResult.PlayerResult> rawPlayerResults,
        Map<String, String> rawMetadata,
        String rawReason,
        int returnDelaySeconds
    ) {
        if (returnDelaySeconds < 0) {
            return MatchResultValidationResult.invalid("returnDelaySeconds cannot be negative.");
        }
        String reason = normalizeOptional(rawReason);
        if (reason.length() > MAX_RESULT_REASON_LENGTH) {
            return MatchResultValidationResult.invalid("Result reason exceeds " + MAX_RESULT_REASON_LENGTH + " characters.");
        }
        MetadataValidationResult metadataResult = normalizeResultMetadata(rawMetadata);
        if (!metadataResult.valid()) {
            return MatchResultValidationResult.invalid(metadataResult.message());
        }
        if (rawPlayerResults == null || rawPlayerResults.isEmpty()) {
            return MatchResultValidationResult.invalid("Result must include one player outcome per required player.");
        }

        LinkedHashMap<UUID, MatchResultValidationResult.PlayerResult> playersByUuid = new LinkedHashMap<>();
        for (MatchResultValidationResult.PlayerResult playerResult : rawPlayerResults) {
            if (playerResult == null || playerResult.playerUuid() == null || playerResult.runtimeOutcome() == null) {
                return MatchResultValidationResult.invalid("Result contains an invalid player outcome.");
            }
            String playerReason = normalizeOptional(playerResult.reason());
            if (playerReason.length() > MAX_RESULT_PLAYER_REASON_LENGTH) {
                return MatchResultValidationResult.invalid("Player result reason exceeds " + MAX_RESULT_PLAYER_REASON_LENGTH + " characters.");
            }
            if (playersByUuid.put(playerResult.playerUuid(), new MatchResultValidationResult.PlayerResult(
                playerResult.playerUuid(),
                playerResult.runtimeOutcome(),
                normalizeOptional(playerResult.backendOutcome(), playerResult.runtimeOutcome().name()),
                playerReason
            )) != null) {
                return MatchResultValidationResult.invalid("Result contains duplicate player " + playerResult.playerUuid() + ".");
            }
        }

        List<UUID> requiredPlayers = buildRequiredResultPlayerUuids(match);
        MatchResultValidationResult requiredValidation = validateRequiredPlayers(requiredPlayers, playersByUuid);
        if (!requiredValidation.valid()) {
            return requiredValidation;
        }

        LinkedHashSet<UUID> requiredSet = new LinkedHashSet<>(requiredPlayers);
        List<MatchResultValidationResult.PlayerResult> orderedPlayers = new ArrayList<>();
        for (UUID requiredPlayer : requiredSet) {
            orderedPlayers.add(playersByUuid.get(requiredPlayer));
        }
        return MatchResultValidationResult.valid(orderedPlayers, metadataResult.metadata(), reason, new JsonObject());
    }

    @Nonnull
    public MatchResultValidationResult validateFinalResult(
        @Nonnull ArenaActiveMatch match,
        String rawReason,
        Map<String, String> rawMetadata,
        JsonObject rawCustomData
    ) {
        String reason = normalizeOptional(rawReason);
        if (reason.length() > MAX_RESULT_REASON_LENGTH) {
            return MatchResultValidationResult.invalid("Result reason exceeds " + MAX_RESULT_REASON_LENGTH + " characters.");
        }
        MetadataValidationResult metadataResult = normalizeResultMetadata(rawMetadata);
        if (!metadataResult.valid()) {
            return MatchResultValidationResult.invalid(metadataResult.message());
        }
        CustomDataValidationResult customDataResult = normalizeCustomData(rawCustomData);
        if (!customDataResult.valid()) {
            return MatchResultValidationResult.invalid(customDataResult.message());
        }

        List<UUID> requiredPlayers = buildRequiredResultPlayerUuids(match);
        if (requiredPlayers.isEmpty()) {
            return MatchResultValidationResult.invalid("Match has no required players to resolve.");
        }
        LinkedHashSet<UUID> requiredSet = new LinkedHashSet<>(requiredPlayers);
        List<MatchResultValidationResult.PlayerResult> orderedPlayers = new ArrayList<>();
        boolean hasWinner = false;
        boolean allNoContest = true;
        for (UUID requiredPlayer : requiredSet) {
            ArenaActiveMatch.ArenaPlayerOutcomeState outcome = match.playerOutcomeByUuid().get(requiredPlayer);
            if (outcome == null || outcome.outcome() == null) {
                return MatchResultValidationResult.invalid("Result is missing required player outcome " + requiredPlayer + ".");
            }
            if (outcome.outcome() == ArenaPlayerResolutionOutcome.WIN) {
                hasWinner = true;
            }
            if (outcome.outcome() != ArenaPlayerResolutionOutcome.NO_CONTEST) {
                allNoContest = false;
            }
            orderedPlayers.add(new MatchResultValidationResult.PlayerResult(
                requiredPlayer,
                outcome.outcome(),
                normalizeOptional(outcome.backendOutcome(), outcome.outcome().name()),
                normalizeOptional(outcome.reason())
            ));
        }
        for (UUID submittedPlayer : match.playerOutcomeByUuid().keySet()) {
            if (!requiredSet.contains(submittedPlayer)) {
                return MatchResultValidationResult.invalid("Result contains unexpected player outcome " + submittedPlayer + ".");
            }
        }
        if (!hasWinner && !allNoContest) {
            return MatchResultValidationResult.invalid("Final match result must include at least one WIN outcome unless all players are NO_CONTEST.");
        }
        return MatchResultValidationResult.valid(orderedPlayers, metadataResult.metadata(), reason, customDataResult.customData());
    }

    @Nonnull
    public MetadataValidationResult normalizeResultMetadata(Map<String, String> rawMetadata) {
        if (rawMetadata == null || rawMetadata.isEmpty()) {
            return MetadataValidationResult.valid(Map.of());
        }
        if (rawMetadata.size() > MAX_RESULT_METADATA_ENTRIES) {
            return MetadataValidationResult.invalid("Result metadata cannot contain more than " + MAX_RESULT_METADATA_ENTRIES + " entries.");
        }
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawMetadata.entrySet()) {
            String key = normalizeOptional(entry.getKey());
            String value = normalizeOptional(entry.getValue());
            if (key.isBlank()) {
                return MetadataValidationResult.invalid("Result metadata keys cannot be blank.");
            }
            if (key.length() > MAX_RESULT_METADATA_KEY_LENGTH) {
                return MetadataValidationResult.invalid("Result metadata key exceeds " + MAX_RESULT_METADATA_KEY_LENGTH + " characters.");
            }
            if (value.length() > MAX_RESULT_METADATA_VALUE_LENGTH) {
                return MetadataValidationResult.invalid("Result metadata value exceeds " + MAX_RESULT_METADATA_VALUE_LENGTH + " characters.");
            }
            normalized.put(key, value);
        }
        LinkedHashMap<String, String> sorted = new LinkedHashMap<>();
        normalized.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return MetadataValidationResult.valid(Map.copyOf(sorted));
    }

    @Nonnull
    public CustomDataValidationResult normalizeCustomData(JsonObject rawCustomData) {
        JsonObject customData = rawCustomData == null ? new JsonObject() : rawCustomData;
        CustomDataCounter counter = new CustomDataCounter();
        String validationError = validateCustomDataElement(customData, 1, counter, true);
        if (!validationError.isBlank()) {
            return CustomDataValidationResult.invalid(validationError);
        }
        JsonObject canonical = canonicalizeJsonObject(customData);
        int bytes = GSON.toJson(canonical).getBytes(StandardCharsets.UTF_8).length;
        if (bytes > MAX_CUSTOM_DATA_BYTES) {
            return CustomDataValidationResult.invalid("customData exceeds " + MAX_CUSTOM_DATA_BYTES + " UTF-8 bytes.");
        }
        return CustomDataValidationResult.valid(canonical);
    }

    @Nonnull
    private MatchResultValidationResult validateRequiredPlayers(
        @Nonnull List<UUID> requiredPlayers,
        @Nonnull LinkedHashMap<UUID, MatchResultValidationResult.PlayerResult> playersByUuid
    ) {
        if (requiredPlayers.isEmpty()) {
            return MatchResultValidationResult.invalid("Match has no required players to resolve.");
        }
        LinkedHashSet<UUID> requiredSet = new LinkedHashSet<>(requiredPlayers);
        if (playersByUuid.size() != requiredSet.size()) {
            return MatchResultValidationResult.invalid("Result must include exactly " + requiredSet.size() + " player outcomes.");
        }
        for (UUID requiredPlayer : requiredSet) {
            if (!playersByUuid.containsKey(requiredPlayer)) {
                return MatchResultValidationResult.invalid("Result is missing required player " + requiredPlayer + ".");
            }
        }
        for (UUID submittedPlayer : playersByUuid.keySet()) {
            if (!requiredSet.contains(submittedPlayer)) {
                return MatchResultValidationResult.invalid("Result contains unexpected player " + submittedPlayer + ".");
            }
        }
        return MatchResultValidationResult.valid(List.of(), Map.of(), "", new JsonObject());
    }

    @Nonnull
    private List<UUID> buildRequiredResultPlayerUuids(@Nonnull ArenaActiveMatch match) {
        if (!match.expectedPlayerUuids().isEmpty()) {
            return match.expectedPlayerUuids();
        }
        LinkedHashSet<UUID> required = new LinkedHashSet<>();
        required.addAll(match.arrivedPlayerUuids());
        required.addAll(match.activePlayerUuids());
        required.addAll(match.eliminatedPlayerUuids());
        return List.copyOf(required);
    }

    @Nonnull
    private String validateCustomDataElement(
        JsonElement element,
        int depth,
        @Nonnull CustomDataCounter counter,
        boolean root
    ) {
        if (depth > MAX_CUSTOM_DATA_DEPTH) {
            return "customData exceeds max depth " + MAX_CUSTOM_DATA_DEPTH + ".";
        }
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (root && !element.isJsonObject()) {
            return "customData root must be a JSON object.";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = normalizeOptional(entry.getKey());
                if (key.isBlank()) {
                    return "customData property names cannot be blank.";
                }
                if (key.length() > MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH) {
                    return "customData property name exceeds " + MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH + " characters.";
                }
                counter.properties++;
                if (counter.properties > MAX_CUSTOM_DATA_PROPERTIES) {
                    return "customData cannot contain more than " + MAX_CUSTOM_DATA_PROPERTIES + " properties.";
                }
                String childError = validateCustomDataElement(entry.getValue(), depth + 1, counter, false);
                if (!childError.isBlank()) {
                    return childError;
                }
            }
            return "";
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() > MAX_CUSTOM_DATA_ARRAY_LENGTH) {
                return "customData arrays cannot contain more than " + MAX_CUSTOM_DATA_ARRAY_LENGTH + " elements.";
            }
            for (JsonElement child : array) {
                String childError = validateCustomDataElement(child, depth + 1, counter, false);
                if (!childError.isBlank()) {
                    return childError;
                }
            }
            return "";
        }
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            if (value != null && value.length() > MAX_CUSTOM_DATA_STRING_LENGTH) {
                return "customData string value exceeds " + MAX_CUSTOM_DATA_STRING_LENGTH + " characters.";
            }
        }
        return "";
    }

    @Nonnull
    private JsonObject canonicalizeJsonObject(@Nonnull JsonObject object) {
        JsonObject canonical = new JsonObject();
        object.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> canonical.add(entry.getKey().trim(), canonicalizeJsonElement(entry.getValue())));
        return canonical;
    }

    @Nonnull
    private JsonElement canonicalizeJsonElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return JsonNull.INSTANCE;
        }
        if (element.isJsonObject()) {
            return canonicalizeJsonObject(element.getAsJsonObject());
        }
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            for (JsonElement child : element.getAsJsonArray()) {
                array.add(canonicalizeJsonElement(child));
            }
            return array;
        }
        return element.deepCopy();
    }

    @Nonnull
    private static String normalizeOptional(String rawValue) {
        return normalizeOptional(rawValue, "");
    }

    @Nonnull
    private static String normalizeOptional(String rawValue, @Nonnull String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? defaultValue : normalized;
    }

    public record MetadataValidationResult(
        boolean valid,
        Map<String, String> metadata,
        String message
    ) {
        @Nonnull
        private static MetadataValidationResult valid(@Nonnull Map<String, String> metadata) {
            return new MetadataValidationResult(true, Map.copyOf(metadata), "");
        }

        @Nonnull
        private static MetadataValidationResult invalid(@Nonnull String message) {
            return new MetadataValidationResult(false, Map.of(), normalizeOptional(message, "Invalid result metadata."));
        }
    }

    public record CustomDataValidationResult(
        boolean valid,
        JsonObject customData,
        String message
    ) {
        @Nonnull
        private static CustomDataValidationResult valid(@Nonnull JsonObject customData) {
            return new CustomDataValidationResult(true, customData.deepCopy(), "");
        }

        @Nonnull
        private static CustomDataValidationResult invalid(@Nonnull String message) {
            return new CustomDataValidationResult(false, new JsonObject(), normalizeOptional(message, "Invalid customData."));
        }
    }

    private static final class CustomDataCounter {
        private int properties;
    }
}
