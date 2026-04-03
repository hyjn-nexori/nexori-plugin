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

public final class ArenaStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public ArenaStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<ArenaDefinition> loadOrCreate() throws IOException {
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

        ArenaConfigDocument document = GSON.fromJson(json, ArenaConfigDocument.class);
        if (document == null || document.arenas() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<ArenaDefinition> normalized = new ArrayList<>();
        for (ArenaDefinition arena : document.arenas()) {
            if (arena != null) {
                normalized.add(arena.normalized());
            }
        }
        return normalized;
    }

    public synchronized void save(@Nonnull List<ArenaDefinition> arenas) throws IOException {
        ensureParent();
        ArenaConfigDocument document = new ArenaConfigDocument(
            ArenaConfigDocument.CURRENT_SCHEMA_VERSION,
            arenas
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

    private record ArenaConfigDocument(int schemaVersion, List<ArenaDefinition> arenas) {
        private static final int CURRENT_SCHEMA_VERSION = 1;

        private ArenaConfigDocument {
            arenas = arenas == null ? List.of() : List.copyOf(arenas);
        }
    }
}
