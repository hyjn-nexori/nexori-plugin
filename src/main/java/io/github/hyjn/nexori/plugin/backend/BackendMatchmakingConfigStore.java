package io.github.hyjn.nexori.plugin.backend;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
        BackendMatchmakingConfig config = GSON.fromJson(json, BackendMatchmakingConfig.class);
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

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
