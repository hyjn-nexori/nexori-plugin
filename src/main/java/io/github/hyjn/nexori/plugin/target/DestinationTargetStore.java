package io.github.hyjn.nexori.plugin.target;

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

public final class DestinationTargetStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public DestinationTargetStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<DestinationTargetDefinition> loadOrCreate() throws IOException {
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

        DestinationTargetConfigDocument document = GSON.fromJson(json, DestinationTargetConfigDocument.class);
        if (document == null || document.destinationTargets() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<DestinationTargetDefinition> normalizedTargets = new ArrayList<>();
        for (DestinationTargetDefinition target : document.destinationTargets()) {
            if (target != null) {
                normalizedTargets.add(target.normalized());
            }
        }
        return normalizedTargets;
    }

    public synchronized void save(@Nonnull List<DestinationTargetDefinition> destinationTargets) throws IOException {
        ensureParent();
        DestinationTargetConfigDocument document = new DestinationTargetConfigDocument(
            DestinationTargetConfigDocument.CURRENT_SCHEMA_VERSION,
            destinationTargets
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
}
