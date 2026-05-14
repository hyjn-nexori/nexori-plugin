package io.github.hyjn.nexori.plugin.travel.logic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;

/**
 * Parses and classifies optional travel context JSON without touching runtime services.
 */
public final class TravelContextParser {

    private static final Gson GSON = new Gson();

    @Nonnull
    public TravelContextData parseContext(String rawContextJson) {
        if (rawContextJson == null || rawContextJson.isBlank()) {
            return new TravelContextData(null, false, null);
        }
        if ("null".equalsIgnoreCase(rawContextJson.trim())) {
            return new TravelContextData(null, false, null);
        }
        try {
            return new TravelContextData(GSON.fromJson(rawContextJson, JsonObject.class), false, null);
        } catch (Exception exception) {
            return new TravelContextData(null, true, exception);
        }
    }

    @Nonnull
    public String normalizeOptional(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String normalized = rawValue.trim();
        return normalized.isBlank() ? "" : normalized;
    }

    public boolean shouldUseDefaultWorldNaturalSpawnEntry(JsonObject context) {
        return context != null
            && context.has("serverEntryMode")
            && "default_world_natural_spawn".equalsIgnoreCase(normalizeOptional(context.get("serverEntryMode").getAsString()));
    }

    public boolean isMinigameLaunchContext(JsonObject context) {
        return context != null
            && context.has("flowType")
            && "minigame.launch".equalsIgnoreCase(normalizeOptional(context.get("flowType").getAsString()));
    }
}
