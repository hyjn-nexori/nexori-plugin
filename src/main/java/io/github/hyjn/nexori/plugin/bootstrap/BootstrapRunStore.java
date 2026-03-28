package io.github.hyjn.nexori.plugin.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BootstrapRunStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;
    private BootstrapRun currentRun;

    public BootstrapRunStore(@Nonnull Path file) {
        this.file = file;
    }

    public synchronized BootstrapRun load() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            this.currentRun = null;
            return null;
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            this.currentRun = null;
            return null;
        }

        this.currentRun = GSON.fromJson(json, BootstrapRun.class);
        return currentRun;
    }

    public synchronized BootstrapRun getCurrentRun() {
        if (currentRun != null && currentRun.isExpired()) {
            clearUnchecked();
        }
        return currentRun;
    }

    @Nonnull
    public synchronized BootstrapRun save(@Nonnull BootstrapRun run) {
        try {
            ensureParent();
            String json = GSON.toJson(run);
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            try {
                Files.writeString(tmp, json, StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.writeString(file, json, StandardCharsets.UTF_8);
                Files.deleteIfExists(tmp);
            }
            this.currentRun = run;
            return run;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to persist Nexori bootstrap run", exception);
        }
    }

    public synchronized void clear() {
        clearUnchecked();
    }

    private void clearUnchecked() {
        try {
            Files.deleteIfExists(file);
            this.currentRun = null;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clear Nexori bootstrap run", exception);
        }
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
