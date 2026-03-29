package io.github.hyjn.nexori.plugin.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class InventoryTransferPolicyStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;
    private InventoryTransferPolicyConfigDocument current = InventoryTransferPolicyConfigDocument.defaults();

    public InventoryTransferPolicyStore(@Nonnull Path file) throws IOException {
        this.file = file;
        load();
    }

    public synchronized boolean isApplyInventoryBackupsEnabled() {
        return current.applyInventoryBackupsEnabled();
    }

    public synchronized void setApplyInventoryBackupsEnabled(boolean enabled) throws IOException {
        current = new InventoryTransferPolicyConfigDocument(
            InventoryTransferPolicyConfigDocument.CURRENT_SCHEMA_VERSION,
            enabled,
            current.maxBackupsPerPlayer()
        );
        flush();
    }

    public synchronized int getMaxBackupsPerPlayer() {
        return Math.max(1, current.maxBackupsPerPlayer());
    }

    public synchronized void setMaxBackupsPerPlayer(int maxBackupsPerPlayer) throws IOException {
        current = new InventoryTransferPolicyConfigDocument(
            InventoryTransferPolicyConfigDocument.CURRENT_SCHEMA_VERSION,
            current.applyInventoryBackupsEnabled(),
            Math.max(1, maxBackupsPerPlayer)
        );
        flush();
    }

    private void load() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            flush();
            return;
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            flush();
            return;
        }

        InventoryTransferPolicyConfigDocument document = GSON.fromJson(json, InventoryTransferPolicyConfigDocument.class);
        current = document == null
            ? InventoryTransferPolicyConfigDocument.defaults()
            : new InventoryTransferPolicyConfigDocument(
                InventoryTransferPolicyConfigDocument.CURRENT_SCHEMA_VERSION,
                document.applyInventoryBackupsEnabled(),
                document.maxBackupsPerPlayer() <= 0
                    ? InventoryTransferPolicyConfigDocument.DEFAULT_MAX_BACKUPS_PER_PLAYER
                    : document.maxBackupsPerPlayer()
            );
    }

    private void flush() throws IOException {
        ensureParent();
        String json = GSON.toJson(current);
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
