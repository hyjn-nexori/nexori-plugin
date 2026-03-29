package io.github.hyjn.nexori.plugin.binding;

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

public final class TriggerBindingStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public TriggerBindingStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<TriggerBindingDefinition> loadOrCreate() throws IOException {
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

        TriggerBindingConfigDocument document = GSON.fromJson(json, TriggerBindingConfigDocument.class);
        if (document == null || document.triggerBindings() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<TriggerBindingDefinition> normalizedBindings = new ArrayList<>();
        for (TriggerBindingDefinition binding : document.triggerBindings()) {
            if (binding != null) {
                normalizedBindings.add(binding.normalized());
            }
        }
        return normalizedBindings;
    }

    public synchronized void save(@Nonnull List<TriggerBindingDefinition> triggerBindings) throws IOException {
        ensureParent();
        TriggerBindingConfigDocument document = new TriggerBindingConfigDocument(
            TriggerBindingConfigDocument.CURRENT_SCHEMA_VERSION,
            triggerBindings
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
