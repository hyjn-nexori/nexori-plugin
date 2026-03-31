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

public final class ServerRuleGroupStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;

    public ServerRuleGroupStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<ServerRuleGroupDefinition> loadOrCreate() throws IOException {
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

        ServerRuleGroupConfigDocument document = GSON.fromJson(json, ServerRuleGroupConfigDocument.class);
        if (document == null || document.ruleGroups() == null) {
            save(List.of());
            return new ArrayList<>();
        }

        List<ServerRuleGroupDefinition> normalizedGroups = new ArrayList<>();
        for (ServerRuleGroupDefinition group : document.ruleGroups()) {
            if (group != null) {
                normalizedGroups.add(group.normalized());
            }
        }
        return normalizedGroups;
    }

    public synchronized void save(@Nonnull List<ServerRuleGroupDefinition> ruleGroups) throws IOException {
        ensureParent();
        String json = GSON.toJson(new ServerRuleGroupConfigDocument(
            ServerRuleGroupConfigDocument.CURRENT_SCHEMA_VERSION,
            ruleGroups
        ));
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
