package io.github.hyjn.nexori.plugin.minigame.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.ArenaPlayerResolutionOutcome;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MatchResultValidatorTest {

    private static final long CREATED_AT = 500L;
    private static final long NOW = 1_000L;
    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PLAYER_THREE = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final MatchResultValidator validator = new MatchResultValidator();

    @Test
    void rejectsNegativeReturnDelay() {
        MatchResultValidationResult result = validateSubmitted(baseMatch(), validPlayerResults(), -1);

        assertInvalid(result, "returnDelaySeconds cannot be negative.");
    }

    @Test
    void rejectsTooLongResultReason() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            validPlayerResults(),
            Map.of(),
            "x".repeat(MatchResultValidator.MAX_RESULT_REASON_LENGTH + 1),
            0
        );

        assertInvalid(result, "Result reason exceeds " + MatchResultValidator.MAX_RESULT_REASON_LENGTH + " characters.");
    }

    @Test
    void rejectsMissingPlayerResults() {
        MatchResultValidationResult result = validator.validateSubmittedResult(baseMatch(), List.of(), Map.of(), "", 0);

        assertInvalid(result, "Result must include one player outcome per required player.");
    }

    @Test
    void rejectsInvalidPlayerResult() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            List.of(new MatchResultValidationResult.PlayerResult(PLAYER_ONE, null, "", "")),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Result contains an invalid player outcome.");
    }

    @Test
    void rejectsTooLongPlayerReason() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            List.of(
                playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "x".repeat(MatchResultValidator.MAX_RESULT_PLAYER_REASON_LENGTH + 1)),
                playerResult(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, "")
            ),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Player result reason exceeds " + MatchResultValidator.MAX_RESULT_PLAYER_REASON_LENGTH + " characters.");
    }

    @Test
    void rejectsDuplicatePlayerResult() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            List.of(
                playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, ""),
                playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.LOSS, "")
            ),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Result contains duplicate player " + PLAYER_ONE + ".");
    }

    @Test
    void rejectsWhenMatchHasNoRequiredPlayers() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(List.of(), List.of(), List.of(), List.of()),
            List.of(playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "")),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Match has no required players to resolve.");
    }

    @Test
    void rejectsMissingRequiredPlayer() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            List.of(playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "")),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Result must include exactly 2 player outcomes.");
    }

    @Test
    void rejectsUnexpectedPlayer() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            List.of(
                playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, ""),
                playerResult(PLAYER_THREE, ArenaPlayerResolutionOutcome.LOSS, "")
            ),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Result is missing required player " + PLAYER_TWO + ".");
    }

    @Test
    void rejectsWrongPlayerResultCount() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(),
            List.of(
                playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, ""),
                playerResult(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, ""),
                playerResult(PLAYER_THREE, ArenaPlayerResolutionOutcome.LOSS, "")
            ),
            Map.of(),
            "",
            0
        );

        assertInvalid(result, "Result must include exactly 2 player outcomes.");
    }

    @Test
    void ordersPlayerResultsByRequiredPlayers() {
        MatchResultValidationResult result = validator.validateSubmittedResult(
            baseMatch(List.of(PLAYER_TWO, PLAYER_ONE), List.of(), List.of(), List.of()),
            List.of(
                playerResult(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, "second"),
                playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "first")
            ),
            Map.of(),
            " done ",
            0
        );

        assertTrue(result.valid());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.players().stream().map(MatchResultValidationResult.PlayerResult::playerUuid).toList());
        assertEquals("done", result.reason());
    }

    @Test
    void acceptsValidResultMetadata() {
        MatchResultValidator.MetadataValidationResult result = validator.normalizeResultMetadata(Map.of(
            " z-key ", " z-value ",
            "a-key", "a-value"
        ));

        assertTrue(result.valid());
        assertTrue(result.metadata().containsKey("a-key"));
        assertTrue(result.metadata().containsKey("z-key"));
        assertEquals("z-value", result.metadata().get("z-key"));
    }

    @Test
    void rejectsTooManyMetadataEntries() {
        Map<String, String> metadata = new LinkedHashMap<>();
        for (int index = 0; index <= MatchResultValidator.MAX_RESULT_METADATA_ENTRIES; index++) {
            metadata.put("key-" + index, "value");
        }

        MatchResultValidator.MetadataValidationResult result = validator.normalizeResultMetadata(metadata);

        assertFalse(result.valid());
        assertEquals("Result metadata cannot contain more than " + MatchResultValidator.MAX_RESULT_METADATA_ENTRIES + " entries.", result.message());
    }

    @Test
    void rejectsTooLongMetadataKey() {
        MatchResultValidator.MetadataValidationResult result = validator.normalizeResultMetadata(Map.of(
            "k".repeat(MatchResultValidator.MAX_RESULT_METADATA_KEY_LENGTH + 1), "value"
        ));

        assertFalse(result.valid());
        assertEquals("Result metadata key exceeds " + MatchResultValidator.MAX_RESULT_METADATA_KEY_LENGTH + " characters.", result.message());
    }

    @Test
    void rejectsTooLongMetadataValue() {
        MatchResultValidator.MetadataValidationResult result = validator.normalizeResultMetadata(Map.of(
            "key", "v".repeat(MatchResultValidator.MAX_RESULT_METADATA_VALUE_LENGTH + 1)
        ));

        assertFalse(result.valid());
        assertEquals("Result metadata value exceeds " + MatchResultValidator.MAX_RESULT_METADATA_VALUE_LENGTH + " characters.", result.message());
    }

    @Test
    void rejectsTooLargeCustomData() {
        JsonObject customData = new JsonObject();
        for (int index = 0; index < 40; index++) {
            customData.addProperty("key-" + index, "x".repeat(900));
        }

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData exceeds " + MatchResultValidator.MAX_CUSTOM_DATA_BYTES + " UTF-8 bytes.", result.message());
    }

    @Test
    void rejectsTooDeepCustomData() {
        JsonObject customData = nestedCustomData(MatchResultValidator.MAX_CUSTOM_DATA_DEPTH + 1);

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData exceeds max depth " + MatchResultValidator.MAX_CUSTOM_DATA_DEPTH + ".", result.message());
    }

    @Test
    void rejectsTooManyCustomDataProperties() {
        JsonObject customData = new JsonObject();
        for (int index = 0; index <= MatchResultValidator.MAX_CUSTOM_DATA_PROPERTIES; index++) {
            customData.addProperty("key-" + index, "value");
        }

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData cannot contain more than " + MatchResultValidator.MAX_CUSTOM_DATA_PROPERTIES + " properties.", result.message());
    }

    @Test
    void acceptsValidCustomData() {
        JsonObject customData = new JsonObject();
        customData.addProperty("z-key", "z-value");
        JsonObject nested = new JsonObject();
        nested.addProperty("a-child", "value");
        customData.add("a-key", nested);

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertTrue(result.valid());
        assertEquals(List.of("a-key", "z-key"), result.customData().keySet().stream().toList());
    }

    @Test
    void acceptsValidFinalResultWithWinner() {
        JsonObject customData = new JsonObject();
        customData.addProperty(" z-key ", "z-value");
        ArenaActiveMatch match = baseMatchWithOutcomes(
            outcome(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, " second "),
            outcome(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, " first ")
        );

        MatchResultValidationResult result = validator.validateFinalResult(
            match,
            " completed ",
            Map.of(" mode ", " ctz "),
            customData
        );

        assertTrue(result.valid());
        assertEquals("completed", result.reason());
        assertEquals("ctz", result.metadata().get("mode"));
        assertEquals("z-value", result.customData().get("z-key").getAsString());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.players().stream().map(MatchResultValidationResult.PlayerResult::playerUuid).toList());
    }

    @Test
    void rejectsFinalResultWithoutWinner() {
        ArenaActiveMatch match = baseMatchWithOutcomes(
            outcome(PLAYER_ONE, ArenaPlayerResolutionOutcome.LOSS, ""),
            outcome(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, "")
        );

        MatchResultValidationResult result = validator.validateFinalResult(match, "", Map.of(), new JsonObject());

        assertInvalid(result, "Final match result must include at least one WIN outcome unless all players are NO_CONTEST.");
    }

    @Test
    void acceptsFinalResultWhenAllPlayersAreNoContest() {
        ArenaActiveMatch match = baseMatchWithOutcomes(
            outcome(PLAYER_ONE, ArenaPlayerResolutionOutcome.NO_CONTEST, "no contest"),
            outcome(PLAYER_TWO, ArenaPlayerResolutionOutcome.NO_CONTEST, "no contest")
        );

        MatchResultValidationResult result = validator.validateFinalResult(match, "NO_CONTEST_CANCEL", Map.of(), new JsonObject());

        assertTrue(result.valid());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.players().stream().map(MatchResultValidationResult.PlayerResult::playerUuid).toList());
    }

    @Test
    void rejectsFinalResultMissingRequiredPlayerOutcome() {
        ArenaActiveMatch match = baseMatchWithOutcomes(
            outcome(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "")
        );

        MatchResultValidationResult result = validator.validateFinalResult(match, "", Map.of(), new JsonObject());

        assertInvalid(result, "Result is missing required player outcome " + PLAYER_TWO + ".");
    }

    @Test
    void rejectsFinalResultUnexpectedPlayerOutcome() {
        // PLAYER_THREE is not in expectedPlayerUuids, arrivedPlayerUuids, activePlayerUuids, or
        // eliminatedPlayerUuids (NO_CONTEST does not add to eliminated), so it is genuinely unknown
        // to the match and its outcome should be rejected.
        ArenaActiveMatch match = baseMatchWithOutcomes(
            outcome(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, ""),
            outcome(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, ""),
            outcome(PLAYER_THREE, ArenaPlayerResolutionOutcome.NO_CONTEST, "")
        );

        MatchResultValidationResult result = validator.validateFinalResult(match, "", Map.of(), new JsonObject());

        assertInvalid(result, "Result contains unexpected player outcome " + PLAYER_THREE + ".");
    }

    @Test
    void ordersFinalResultPlayersByRequiredPlayers() {
        ArenaActiveMatch match = baseMatch(List.of(PLAYER_TWO, PLAYER_ONE), List.of(), List.of(), List.of());
        match = match
            .withPlayerOutcome(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, "LOSS", "second", NOW)
            .withPlayerOutcome(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "WIN", "first", NOW);

        MatchResultValidationResult result = validator.validateFinalResult(match, "", Map.of(), new JsonObject());

        assertTrue(result.valid());
        assertEquals(List.of(PLAYER_ONE, PLAYER_TWO), result.players().stream().map(MatchResultValidationResult.PlayerResult::playerUuid).toList());
    }

    @Test
    void rejectsTooLongCustomDataArray() {
        JsonArray array = new JsonArray();
        for (int index = 0; index <= MatchResultValidator.MAX_CUSTOM_DATA_ARRAY_LENGTH; index++) {
            array.add(index);
        }
        JsonObject customData = new JsonObject();
        customData.add("items", array);

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData arrays cannot contain more than " + MatchResultValidator.MAX_CUSTOM_DATA_ARRAY_LENGTH + " elements.", result.message());
    }

    @Test
    void rejectsBlankCustomDataPropertyName() {
        JsonObject customData = new JsonObject();
        customData.addProperty(" ", "value");

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData property names cannot be blank.", result.message());
    }

    @Test
    void rejectsTooLongCustomDataPropertyName() {
        JsonObject customData = new JsonObject();
        customData.addProperty("k".repeat(MatchResultValidator.MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH + 1), "value");

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData property name exceeds " + MatchResultValidator.MAX_CUSTOM_DATA_PROPERTY_NAME_LENGTH + " characters.", result.message());
    }

    @Test
    void rejectsTooLongCustomDataStringValue() {
        JsonObject customData = new JsonObject();
        customData.addProperty("value", "x".repeat(MatchResultValidator.MAX_CUSTOM_DATA_STRING_LENGTH + 1));

        MatchResultValidator.CustomDataValidationResult result = validator.normalizeCustomData(customData);

        assertFalse(result.valid());
        assertEquals("customData string value exceeds " + MatchResultValidator.MAX_CUSTOM_DATA_STRING_LENGTH + " characters.", result.message());
    }

    private MatchResultValidationResult validateSubmitted(
        ArenaActiveMatch match,
        List<MatchResultValidationResult.PlayerResult> playerResults,
        int returnDelaySeconds
    ) {
        return validator.validateSubmittedResult(match, playerResults, Map.of(), "", returnDelaySeconds);
    }

    private static void assertInvalid(MatchResultValidationResult result, String message) {
        assertFalse(result.valid());
        assertEquals(message, result.message());
    }

    private static MatchResultValidationResult.PlayerResult playerResult(
        UUID playerUuid,
        ArenaPlayerResolutionOutcome outcome,
        String reason
    ) {
        return new MatchResultValidationResult.PlayerResult(playerUuid, outcome, outcome == null ? "" : outcome.name(), reason);
    }

    private static List<MatchResultValidationResult.PlayerResult> validPlayerResults() {
        return List.of(
            playerResult(PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, ""),
            playerResult(PLAYER_TWO, ArenaPlayerResolutionOutcome.LOSS, "")
        );
    }

    private static JsonObject nestedCustomData(int depth) {
        JsonObject root = new JsonObject();
        JsonObject current = root;
        for (int index = 1; index < depth; index++) {
            JsonObject child = new JsonObject();
            current.add("child", child);
            current = child;
        }
        current.addProperty("value", "leaf");
        return root;
    }

    private static ArenaActiveMatch baseMatchWithOutcomes(ArenaActiveMatch.ArenaPlayerOutcomeState... outcomes) {
        ArenaActiveMatch match = baseMatch();
        for (ArenaActiveMatch.ArenaPlayerOutcomeState outcome : outcomes) {
            match = match.withPlayerOutcome(
                outcome.playerUuid(),
                outcome.outcome(),
                outcome.backendOutcome(),
                outcome.reason(),
                NOW
            );
        }
        return match;
    }

    private static ArenaActiveMatch.ArenaPlayerOutcomeState outcome(UUID playerUuid, ArenaPlayerResolutionOutcome outcome, String reason) {
        return new ArenaActiveMatch.ArenaPlayerOutcomeState(
            playerUuid,
            outcome,
            outcome.name(),
            reason,
            NOW
        );
    }

    private static ArenaActiveMatch baseMatch() {
        return baseMatch(List.of(PLAYER_ONE, PLAYER_TWO), List.of(), List.of(), List.of());
    }

    private static ArenaActiveMatch baseMatch(
        List<UUID> expectedPlayerUuids,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        List<UUID> eliminatedPlayerUuids
    ) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby.example:19132",
            "lobby-1.natural_spawn",
            "keep_inventory",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "",
            "rules-default",
            "assignment-1",
            "INITIAL_MATCH",
            "external-match-1",
            ArenaMatchSource.BACKEND_DRIVEN.id(),
            1,
            4,
            true,
            QueueBackfillMode.ACTIVE_WINDOW.id(),
            30,
            expectedPlayerUuids,
            expectedPlayerUuids.size(),
            arrivedPlayerUuids,
            activePlayerUuids,
            eliminatedPlayerUuids,
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            0L,
            0L,
            0L,
            0L,
            "",
            CREATED_AT,
            NOW,
            ""
        ).normalized();
    }
}
