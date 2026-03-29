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
import java.util.Map;
import java.util.Optional;

public final class InventoryTransferReceiptStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;
    private final Map<String, InventoryTransferReceiptRecord> byTransferId = new LinkedHashMap<>();

    public InventoryTransferReceiptStore(@Nonnull Path file) throws IOException {
        this.file = file;
        load();
    }

    @Nonnull
    public synchronized InventoryTransferReceiptRecord save(@Nonnull InventoryTransferReceiptRecord record) throws IOException {
        InventoryTransferReceiptRecord normalized = record.normalized();
        byTransferId.put(normalized.transferId(), normalized);
        flush();
        return normalized;
    }

    @Nonnull
    public synchronized Optional<InventoryTransferReceiptRecord> find(@Nonnull String transferId) {
        return Optional.ofNullable(byTransferId.get(transferId.trim().toLowerCase()));
    }

    public synchronized boolean remove(@Nonnull String transferId) throws IOException {
        InventoryTransferReceiptRecord removed = byTransferId.remove(transferId.trim().toLowerCase());
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

        InventoryTransferReceiptConfigDocument document = GSON.fromJson(json, InventoryTransferReceiptConfigDocument.class);
        if (document == null || document.receipts() == null) {
            flush();
            return;
        }

        for (InventoryTransferReceiptRecord record : document.receipts()) {
            if (record == null) {
                continue;
            }
            InventoryTransferReceiptRecord normalized = record.normalized();
            byTransferId.put(normalized.transferId(), normalized);
        }
    }

    private void flush() throws IOException {
        ensureParent();
        InventoryTransferReceiptConfigDocument document = new InventoryTransferReceiptConfigDocument(
            InventoryTransferReceiptConfigDocument.CURRENT_SCHEMA_VERSION,
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
