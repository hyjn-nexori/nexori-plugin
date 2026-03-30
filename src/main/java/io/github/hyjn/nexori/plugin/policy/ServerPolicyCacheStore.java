package io.github.hyjn.nexori.plugin.policy;

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

public final class ServerPolicyCacheStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public ServerPolicyCacheStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<ServerPolicySummary> loadOrCreate() throws IOException {
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

        ServerPolicyCacheDocument document = GSON.fromJson(json, ServerPolicyCacheDocument.class);
        if (document == null || document.policies() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<ServerPolicySummary> normalized = new ArrayList<>();
        for (ServerPolicySummary policy : document.policies()) {
            if (policy != null) {
                normalized.add(policy.normalized());
            }
        }
        return normalized;
    }

    public synchronized void save(@Nonnull List<ServerPolicySummary> policies) throws IOException {
        ensureParent();
        ServerPolicyCacheDocument document = new ServerPolicyCacheDocument(
            ServerPolicyCacheDocument.CURRENT_SCHEMA_VERSION,
            policies
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
