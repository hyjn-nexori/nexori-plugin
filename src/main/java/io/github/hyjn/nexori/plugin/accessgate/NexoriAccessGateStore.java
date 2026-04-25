package io.github.hyjn.nexori.plugin.accessgate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class NexoriAccessGateStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public NexoriAccessGateStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized NexoriAccessGateConfigDocument loadOrCreate() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            NexoriAccessGateConfigDocument defaults = NexoriAccessGateConfigDocument.defaults();
            save(defaults);
            return defaults;
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            NexoriAccessGateConfigDocument defaults = NexoriAccessGateConfigDocument.defaults();
            save(defaults);
            return defaults;
        }

        String migratedJson = migrateLegacyJson(json);
        NexoriAccessGateConfigDocument document = GSON.fromJson(migratedJson, NexoriAccessGateConfigDocument.class);
        if (document == null) {
            NexoriAccessGateConfigDocument defaults = NexoriAccessGateConfigDocument.defaults();
            save(defaults);
            return defaults;
        }

        NexoriAccessGateConfigDocument normalized = document.normalized();
        if (!normalized.equals(document)) {
            save(normalized);
        }
        return normalized;
    }

    public synchronized void save(@Nonnull NexoriAccessGateConfigDocument document) throws IOException {
        ensureParent();
        NexoriAccessGateConfigDocument normalized = document.normalized();
        String json = GSON.toJson(normalized);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    @Nonnull
    private static String migrateLegacyJson(@Nonnull String json) {
        JsonElement rootElement = JsonParser.parseString(json);
        if (!rootElement.isJsonObject()) {
            return json;
        }
        JsonObject root = rootElement.getAsJsonObject();

        List<NexoriAccessGateBypassPlayer> merged = new ArrayList<>();
        JsonArray existingPairs = root.getAsJsonArray("bypassPlayerUuids");
        if (existingPairs != null) {
            for (JsonElement element : existingPairs) {
                if (element != null && element.isJsonObject()) {
                    JsonObject pair = element.getAsJsonObject();
                    String uuid = pair.has("uuid") && pair.get("uuid").isJsonPrimitive()
                        ? pair.get("uuid").getAsString().trim().toLowerCase(Locale.ROOT)
                        : "";
                    String username = pair.has("username") && pair.get("username").isJsonPrimitive()
                        ? pair.get("username").getAsString().trim()
                        : "";
                    if (!uuid.isBlank()) {
                        merged.add(new NexoriAccessGateBypassPlayer(uuid, username));
                    }
                }
            }
        }

        JsonArray legacyPairs = root.getAsJsonArray("bypassPlayers");
        if (legacyPairs != null) {
            for (JsonElement element : legacyPairs) {
                if (element != null && element.isJsonObject()) {
                    JsonObject pair = element.getAsJsonObject();
                    String uuid = pair.has("uuid") && pair.get("uuid").isJsonPrimitive()
                        ? pair.get("uuid").getAsString().trim().toLowerCase(Locale.ROOT)
                        : "";
                    String username = pair.has("username") && pair.get("username").isJsonPrimitive()
                        ? pair.get("username").getAsString().trim()
                        : "";
                    if (!uuid.isBlank()) {
                        merged.add(new NexoriAccessGateBypassPlayer(uuid, username));
                    }
                }
            }
        }

        JsonArray legacyStrings = root.getAsJsonArray("bypassPlayerUuids");
        if (legacyStrings != null) {
            for (JsonElement element : legacyStrings) {
                if (element != null && element.isJsonPrimitive()) {
                    String uuid = element.getAsString().trim().toLowerCase(Locale.ROOT);
                    if (!uuid.isBlank()) {
                        merged.add(new NexoriAccessGateBypassPlayer(uuid, ""));
                    }
                }
            }
        }

        JsonArray normalizedPairs = new JsonArray();
        List<String> seen = new ArrayList<>();
        for (NexoriAccessGateBypassPlayer player : merged) {
            if (player == null || player.uuid() == null) {
                continue;
            }
            String token = player.uuid().trim().toLowerCase(Locale.ROOT);
            if (token.isBlank() || seen.contains(token)) {
                continue;
            }
            seen.add(token);
            JsonObject pair = new JsonObject();
            pair.addProperty("uuid", token);
            pair.addProperty("username", player.username() == null ? "" : player.username().trim());
            normalizedPairs.add(pair);
        }

        root.add("bypassPlayerUuids", normalizedPairs);
        root.remove("bypassPlayers");
        root.remove("bypassGroupNames");
        root.remove("bypassPermissions");
        return GSON.toJson(root);
    }
}
