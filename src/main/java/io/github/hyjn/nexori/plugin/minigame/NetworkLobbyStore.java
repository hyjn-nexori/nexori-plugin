package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

public final class NetworkLobbyStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public NetworkLobbyStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized Optional<NetworkLobbyDefinition> load() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            return Optional.empty();
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return Optional.empty();
        }

        NetworkLobbyDocument document = GSON.fromJson(json, NetworkLobbyDocument.class);
        if (document == null || document.definition() == null) {
            return Optional.empty();
        }
        return Optional.of(document.definition().normalized());
    }

    public synchronized void save(@Nonnull NetworkLobbyDefinition definition) throws IOException {
        ensureParent();
        NetworkLobbyDocument document = new NetworkLobbyDocument(
            NetworkLobbyDocument.CURRENT_SCHEMA_VERSION,
            definition.normalized()
        );
        String json = GSON.toJson(document);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
    }

    public synchronized void clear() throws IOException {
        ensureParent();
        Files.deleteIfExists(file);
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private record NetworkLobbyDocument(int schemaVersion, NetworkLobbyDefinition definition) {
        private static final int CURRENT_SCHEMA_VERSION = 1;
    }
}
