package io.github.hyjn.nexori.plugin.travel.logic;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TravelContextParserTest {

    private final TravelContextParser parser = new TravelContextParser();

    @Test
    void parseReturnsEmptyForNullRawContext() {
        TravelContextData data = parser.parseContext(null);

        assertTrue(data.contextOptional().isEmpty());
        assertFalse(data.parseFailed());
    }

    @Test
    void parseReturnsEmptyForBlankRawContext() {
        TravelContextData data = parser.parseContext("   ");

        assertTrue(data.contextOptional().isEmpty());
        assertFalse(data.parseFailed());
    }

    @Test
    void parseReturnsEmptyForInvalidJson() {
        TravelContextData data = parser.parseContext("{not-json");

        assertTrue(data.contextOptional().isEmpty());
        assertTrue(data.parseFailed());
        assertTrue(data.parseExceptionOptional().isPresent());
    }

    @Test
    void parseReturnsContextForValidJson() {
        TravelContextData data = parser.parseContext("{\"flowType\":\"minigame.launch\"}");

        assertTrue(data.contextOptional().isPresent());
        assertEquals("minigame.launch", data.contextOptional().get().get("flowType").getAsString());
        assertFalse(data.parseFailed());
    }

    @Test
    void parseJsonNullReturnsEmptyWithoutParseFailureAccordingToCurrentBehavior() {
        TravelContextData data = parser.parseContext("null");

        assertTrue(data.contextOptional().isEmpty());
        assertFalse(data.parseFailed());
    }

    @Test
    void normalizeOptionalReturnsBlankForNull() {
        assertEquals("", parser.normalizeOptional(null));
    }

    @Test
    void normalizeOptionalReturnsBlankForWhitespace() {
        assertEquals("", parser.normalizeOptional("   "));
    }

    @Test
    void normalizeOptionalTrimsValue() {
        assertEquals("value", parser.normalizeOptional(" value "));
    }

    @Test
    void detectsDefaultWorldNaturalSpawnEntryCaseInsensitive() {
        JsonObject context = new JsonObject();
        context.addProperty("serverEntryMode", " DEFAULT_WORLD_NATURAL_SPAWN ");

        assertTrue(parser.shouldUseDefaultWorldNaturalSpawnEntry(context));
    }

    @Test
    void doesNotDetectDefaultWorldNaturalSpawnEntryWhenMissing() {
        assertFalse(parser.shouldUseDefaultWorldNaturalSpawnEntry(new JsonObject()));
    }

    @Test
    void doesNotDetectDefaultWorldNaturalSpawnEntryWhenBlank() {
        JsonObject context = new JsonObject();
        context.addProperty("serverEntryMode", "   ");

        assertFalse(parser.shouldUseDefaultWorldNaturalSpawnEntry(context));
    }

    @Test
    void classifiersReturnFalseForNullContext() {
        assertFalse(parser.shouldUseDefaultWorldNaturalSpawnEntry(null));
        assertFalse(parser.isMinigameLaunchContext(null));
    }

    @Test
    void detectsMinigameLaunchContextCaseInsensitive() {
        JsonObject context = new JsonObject();
        context.addProperty("flowType", " MINIGAME.LAUNCH ");

        assertTrue(parser.isMinigameLaunchContext(context));
    }

    @Test
    void doesNotDetectMinigameLaunchContextWhenMissing() {
        assertFalse(parser.isMinigameLaunchContext(new JsonObject()));
    }

    @Test
    void doesNotDetectMinigameLaunchContextWhenBlank() {
        JsonObject context = new JsonObject();
        context.addProperty("flowType", "   ");

        assertFalse(parser.isMinigameLaunchContext(context));
    }
}
