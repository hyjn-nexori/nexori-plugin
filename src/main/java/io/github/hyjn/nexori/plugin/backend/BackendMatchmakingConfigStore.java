package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BackendMatchmakingConfigStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public BackendMatchmakingConfigStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized BackendMatchmakingConfig loadOrCreate() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            BackendMatchmakingConfig defaults = BackendMatchmakingConfig.defaults();
            save(defaults);
            return defaults;
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            BackendMatchmakingConfig defaults = BackendMatchmakingConfig.defaults();
            save(defaults);
            return defaults;
        }
        BackendMatchmakingConfig config = readConfig(json);
        if (config == null) {
            BackendMatchmakingConfig defaults = BackendMatchmakingConfig.defaults();
            save(defaults);
            return defaults;
        }
        BackendMatchmakingConfig normalized = config.normalized();
        save(normalized);
        return normalized;
    }

    public synchronized void save(@Nonnull BackendMatchmakingConfig config) throws IOException {
        ensureParent();
        String json = GSON.toJson(config.normalized());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
    }

    private BackendMatchmakingConfig readConfig(@Nonnull String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return new BackendMatchmakingConfig(
            intValue(root, "schemaVersion", BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION),
            booleanValue(root, "syncEnabled", false),
            stringValue(root, "baseUrl", ""),
            stringValue(root, "serverToken", ""),
            longValue(root, "syncIntervalMs", 0L),
            stringValue(root, "region", ""),
            longValue(root, "requestTimeoutMs", 0L),
            booleanValue(root, "resultReportingEnabled", false),
            longValue(root, "resultRetryIntervalMs", 0L)
        );
    }

    private static boolean booleanValue(@Nonnull JsonObject root, @Nonnull String key, boolean defaultValue) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsBoolean();
    }

    private static int intValue(@Nonnull JsonObject root, @Nonnull String key, int defaultValue) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsInt();
    }

    private static long longValue(@Nonnull JsonObject root, @Nonnull String key, long defaultValue) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsLong();
    }

    @Nonnull
    private static String stringValue(@Nonnull JsonObject root, @Nonnull String key, @Nonnull String defaultValue) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? defaultValue : element.getAsString();
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
