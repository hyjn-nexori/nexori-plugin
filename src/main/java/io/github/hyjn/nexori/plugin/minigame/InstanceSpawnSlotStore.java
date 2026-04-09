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

public final class InstanceSpawnSlotStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public InstanceSpawnSlotStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<InstanceSpawnSlotDefinition> loadOrCreate() throws IOException {
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

        SpawnSlotConfigDocument document = GSON.fromJson(json, SpawnSlotConfigDocument.class);
        if (document == null || document.spawnSlots() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<InstanceSpawnSlotDefinition> normalized = new ArrayList<>();
        for (InstanceSpawnSlotDefinition slot : document.spawnSlots()) {
            if (slot != null) {
                normalized.add(slot.normalized());
            }
        }
        return normalized;
    }

    public synchronized void save(@Nonnull List<InstanceSpawnSlotDefinition> spawnSlots) throws IOException {
        ensureParent();
        SpawnSlotConfigDocument document = new SpawnSlotConfigDocument(
            SpawnSlotConfigDocument.CURRENT_SCHEMA_VERSION,
            spawnSlots
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

    private record SpawnSlotConfigDocument(int schemaVersion, List<InstanceSpawnSlotDefinition> spawnSlots) {
        private static final int CURRENT_SCHEMA_VERSION = 1;

        private SpawnSlotConfigDocument {
            spawnSlots = spawnSlots == null ? List.of() : List.copyOf(spawnSlots);
        }
    }
}
