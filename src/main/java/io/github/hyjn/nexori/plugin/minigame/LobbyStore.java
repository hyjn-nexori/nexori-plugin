package io.github.hyjn.nexori.plugin.minigame;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class LobbyStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public LobbyStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<LobbyDefinition> loadOrCreate() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            save(List.of());
            return new ArrayList<>();
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            save(List.of());
            return new ArrayList<>();
        }

        LobbyConfigDocument document = GSON.fromJson(json, LobbyConfigDocument.class);
        if (document == null || document.lobbies() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<LobbyDefinition> normalized = new ArrayList<>();
        for (LobbyDefinition lobby : document.lobbies()) {
            if (lobby != null) {
                normalized.add(lobby.normalized());
            }
        }
        return normalized;
    }

    public synchronized void save(@Nonnull List<LobbyDefinition> lobbies) throws IOException {
        ensureParent();
        LobbyConfigDocument document = new LobbyConfigDocument(
            LobbyConfigDocument.CURRENT_SCHEMA_VERSION,
            lobbies
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

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private record LobbyConfigDocument(int schemaVersion, List<LobbyDefinition> lobbies) {
        private static final int CURRENT_SCHEMA_VERSION = 1;

        private LobbyConfigDocument {
            lobbies = lobbies == null ? List.of() : List.copyOf(lobbies);
        }
    }
}
