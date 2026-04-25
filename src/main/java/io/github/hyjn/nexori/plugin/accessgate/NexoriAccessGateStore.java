package io.github.hyjn.nexori.plugin.accessgate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
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

        NexoriAccessGateConfigDocument document = GSON.fromJson(json, NexoriAccessGateConfigDocument.class);
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
}