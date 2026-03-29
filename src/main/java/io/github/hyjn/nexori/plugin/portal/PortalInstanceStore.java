package io.github.hyjn.nexori.plugin.portal;

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

public final class PortalInstanceStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public PortalInstanceStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<PortalInstanceDefinition> loadOrCreate() throws IOException {
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

        PortalInstanceConfigDocument document = GSON.fromJson(json, PortalInstanceConfigDocument.class);
        if (document == null || document.portalInstances() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<PortalInstanceDefinition> normalizedInstances = new ArrayList<>();
        for (PortalInstanceDefinition portal : document.portalInstances()) {
            if (portal != null) {
                normalizedInstances.add(portal.normalized());
            }
        }
        return normalizedInstances;
    }

    public synchronized void save(@Nonnull List<PortalInstanceDefinition> portalInstances) throws IOException {
        ensureParent();
        PortalInstanceConfigDocument document = new PortalInstanceConfigDocument(
            PortalInstanceConfigDocument.CURRENT_SCHEMA_VERSION,
            portalInstances
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
