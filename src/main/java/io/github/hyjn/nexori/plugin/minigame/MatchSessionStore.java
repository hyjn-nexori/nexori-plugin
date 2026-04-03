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

public final class MatchSessionStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public MatchSessionStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<MatchSessionState> loadOrCreate() throws IOException {
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

        MatchSessionConfigDocument document = GSON.fromJson(json, MatchSessionConfigDocument.class);
        if (document == null || document.sessions() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<MatchSessionState> normalized = new ArrayList<>();
        for (MatchSessionState session : document.sessions()) {
            if (session != null) {
                normalized.add(session.normalized());
            }
        }
        return normalized;
    }

    public synchronized void save(@Nonnull List<MatchSessionState> sessions) throws IOException {
        ensureParent();
        MatchSessionConfigDocument document = new MatchSessionConfigDocument(
            MatchSessionConfigDocument.CURRENT_SCHEMA_VERSION,
            sessions
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

    private record MatchSessionConfigDocument(int schemaVersion, List<MatchSessionState> sessions) {
        private static final int CURRENT_SCHEMA_VERSION = 1;

        private MatchSessionConfigDocument {
            sessions = sessions == null ? List.of() : List.copyOf(sessions);
        }
    }
}
