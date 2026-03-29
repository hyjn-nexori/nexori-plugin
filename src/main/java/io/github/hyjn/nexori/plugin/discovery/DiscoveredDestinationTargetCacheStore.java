package io.github.hyjn.nexori.plugin.discovery;

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

public final class DiscoveredDestinationTargetCacheStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public DiscoveredDestinationTargetCacheStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<DiscoveredDestinationTargetSet> loadOrCreate() throws IOException {
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

        DiscoveredDestinationTargetCacheDocument document = GSON.fromJson(json, DiscoveredDestinationTargetCacheDocument.class);
        if (document == null || document.discoveries() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<DiscoveredDestinationTargetSet> normalizedDiscoveries = new ArrayList<>();
        for (DiscoveredDestinationTargetSet discovery : document.discoveries()) {
            if (discovery != null) {
                normalizedDiscoveries.add(discovery.normalized());
            }
        }
        return normalizedDiscoveries;
    }

    public synchronized void save(@Nonnull List<DiscoveredDestinationTargetSet> discoveries) throws IOException {
        ensureParent();
        DiscoveredDestinationTargetCacheDocument document = new DiscoveredDestinationTargetCacheDocument(
            DiscoveredDestinationTargetCacheDocument.CURRENT_SCHEMA_VERSION,
            discoveries
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
