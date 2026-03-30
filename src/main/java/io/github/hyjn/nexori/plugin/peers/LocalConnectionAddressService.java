package io.github.hyjn.nexori.plugin.peers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class LocalConnectionAddressService {

    private final Path file;
    private String currentConnectionAddress = "";

    public LocalConnectionAddressService(@Nonnull Path file) throws IOException {
        this.file = file;
        loadOrCreate();
    }

    @Nonnull
    public synchronized Optional<ConfiguredPeer> getConfiguredPeer() {
        if (currentConnectionAddress.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ConfiguredPeer.parse(currentConnectionAddress));
    }

    @Nonnull
    public synchronized String getConnectionAddressOrBlank() {
        return currentConnectionAddress;
    }

    public synchronized void save(@Nonnull String rawConnectionAddress) throws IOException {
        ConfiguredPeer normalized = ConfiguredPeer.parse(rawConnectionAddress);
        this.currentConnectionAddress = normalized.connectionAddress();
        persist();
    }

    private void loadOrCreate() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!Files.exists(file)) {
            persist();
            return;
        }

        String raw = Files.readString(file, StandardCharsets.UTF_8).trim();
        if (raw.isBlank()) {
            currentConnectionAddress = "";
            return;
        }

        try {
            currentConnectionAddress = ConfiguredPeer.parse(raw).connectionAddress();
        } catch (IllegalArgumentException ignored) {
            currentConnectionAddress = "";
        }
    }

    private void persist() throws IOException {
        Files.writeString(file, currentConnectionAddress, StandardCharsets.UTF_8);
    }
}
