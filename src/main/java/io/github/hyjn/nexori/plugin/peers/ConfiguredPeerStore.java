package io.github.hyjn.nexori.plugin.peers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class ConfiguredPeerStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();
    private static final Type LIST_TYPE = new TypeToken<List<ConfiguredPeer>>() { }.getType();

    private final Path file;

    public ConfiguredPeerStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized List<ConfiguredPeer> load() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            return new ArrayList<>();
        }

        List<ConfiguredPeer> loaded = GSON.fromJson(json, LIST_TYPE);
        return loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
    }

    public synchronized void save(@Nonnull List<ConfiguredPeer> peers) throws IOException {
        ensureParent();
        String json = GSON.toJson(peers, LIST_TYPE);
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
