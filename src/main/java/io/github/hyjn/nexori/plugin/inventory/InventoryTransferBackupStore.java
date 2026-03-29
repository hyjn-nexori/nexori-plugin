package io.github.hyjn.nexori.plugin.inventory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InventoryTransferBackupStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;
    private final Map<String, InventoryTransferBackupRecord> byTransferId = new LinkedHashMap<>();

    public InventoryTransferBackupStore(@Nonnull Path file) throws IOException {
        this.file = file;
        load();
    }

    @Nonnull
    public synchronized InventoryTransferBackupRecord save(@Nonnull InventoryTransferBackupRecord record) throws IOException {
        InventoryTransferBackupRecord normalized = record.normalized();
        byTransferId.put(normalized.transferId(), normalized);
        flush();
        return normalized;
    }

    @Nonnull
    public synchronized Optional<InventoryTransferBackupRecord> find(@Nonnull String transferId) {
        return Optional.ofNullable(byTransferId.get(transferId.trim().toLowerCase()));
    }

    @Nonnull
    public synchronized List<InventoryTransferBackupRecord> listByPlayer(@Nonnull UUID playerUuid) {
        return byTransferId.values().stream()
            .filter(record -> playerUuid.equals(record.playerUuid()))
            .sorted((left, right) -> Long.compare(right.createdAtEpochMs(), left.createdAtEpochMs()))
            .toList();
    }

    @Nonnull
    public synchronized List<InventoryTransferBackupRecord> listAll() {
        return new ArrayList<>(byTransferId.values());
    }

    public synchronized boolean remove(@Nonnull String transferId) throws IOException {
        InventoryTransferBackupRecord removed = byTransferId.remove(transferId.trim().toLowerCase());
        flush();
        return removed != null;
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

        InventoryTransferBackupConfigDocument document = GSON.fromJson(json, InventoryTransferBackupConfigDocument.class);
        if (document == null || document.backups() == null) {
            flush();
            return;
        }

        for (InventoryTransferBackupRecord record : document.backups()) {
            if (record == null) {
                continue;
            }
            InventoryTransferBackupRecord normalized = record.normalized();
            byTransferId.put(normalized.transferId(), normalized);
        }
    }

    private void flush() throws IOException {
        ensureParent();
        InventoryTransferBackupConfigDocument document = new InventoryTransferBackupConfigDocument(
            InventoryTransferBackupConfigDocument.CURRENT_SCHEMA_VERSION,
            new ArrayList<>(byTransferId.values())
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
