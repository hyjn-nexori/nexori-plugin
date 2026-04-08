package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Optional;

public final class NetworkLobbyService {

    private final NetworkLobbyStore store;
    private final LocalConnectionAddressService localConnectionAddressService;
    private NetworkLobbyDefinition current;

    public NetworkLobbyService(
        @Nonnull NetworkLobbyStore store,
        @Nonnull LocalConnectionAddressService localConnectionAddressService
    ) throws IOException {
        this.store = store;
        this.localConnectionAddressService = localConnectionAddressService;
        this.current = store.load().orElse(null);
    }

    @Nonnull
    public synchronized Optional<NetworkLobbyDefinition> current() {
        return Optional.ofNullable(current);
    }

    public synchronized boolean isConfigured() {
        return current != null;
    }

    public synchronized boolean isCurrentServerLobby() {
        if (current == null) {
            return false;
        }
        String localConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        return !localConnectionAddress.isBlank()
            && localConnectionAddress.equalsIgnoreCase(current.lobbyConnectionAddress());
    }

    @Nonnull
    public synchronized NetworkLobbyDefinition save(@Nonnull NetworkLobbyDefinition definition) throws IOException {
        NetworkLobbyDefinition normalized = definition.normalized();
        store.save(normalized);
        current = normalized;
        return normalized;
    }

    public synchronized void clear() throws IOException {
        store.clear();
        current = null;
    }
}
