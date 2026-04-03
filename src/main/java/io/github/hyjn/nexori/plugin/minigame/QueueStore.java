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

public final class QueueStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public QueueStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<QueueDefinition> loadOrCreate() throws IOException {
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

        QueueConfigDocument document = GSON.fromJson(json, QueueConfigDocument.class);
        if (document == null || document.queues() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<QueueDefinition> normalized = new ArrayList<>();
        for (QueueDefinition queue : document.queues()) {
            if (queue != null) {
                normalized.add(queue.normalized());
            }
        }
        return normalized;
    }

    public synchronized void save(@Nonnull List<QueueDefinition> queues) throws IOException {
        ensureParent();
        QueueConfigDocument document = new QueueConfigDocument(
            QueueConfigDocument.CURRENT_SCHEMA_VERSION,
            queues
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

    private record QueueConfigDocument(int schemaVersion, List<QueueDefinition> queues) {
        private static final int CURRENT_SCHEMA_VERSION = 1;

        private QueueConfigDocument {
            queues = queues == null ? List.of() : List.copyOf(queues);
        }
    }
}
