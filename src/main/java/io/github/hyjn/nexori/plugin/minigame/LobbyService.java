package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.target.DestinationTargetService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class LobbyService {

    private final LobbyStore store;
    private final DestinationTargetService destinationTargetService;
    private final Map<String, LobbyDefinition> lobbiesById = new LinkedHashMap<>();

    public LobbyService(@Nonnull LobbyStore store, @Nonnull DestinationTargetService destinationTargetService) throws IOException {
        this.store = store;
        this.destinationTargetService = destinationTargetService;
        for (LobbyDefinition lobby : store.loadOrCreate()) {
            LobbyDefinition normalized = lobby.normalized();
            validate(normalized);
            lobbiesById.put(normalized.lobbyId(), normalized);
        }
    }

    @Nonnull
    public synchronized List<LobbyDefinition> list() {
        return lobbiesById.values().stream()
            .sorted(Comparator.comparing(LobbyDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Nonnull
    public synchronized Optional<LobbyDefinition> find(@Nonnull String rawLobbyId) {
        if (rawLobbyId == null || rawLobbyId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(lobbiesById.get(LobbyDefinition.normalizeId(rawLobbyId)));
    }

    @Nonnull
    public synchronized Optional<LobbyDefinition> findByWorldName(@Nonnull String rawWorldName) {
        if (rawWorldName == null || rawWorldName.isBlank()) {
            return Optional.empty();
        }
        String normalizedWorldName = rawWorldName.trim().toLowerCase(Locale.ROOT);
        return lobbiesById.values().stream()
            .filter(lobby -> lobby.worldName().equals(normalizedWorldName))
            .findFirst();
    }

    @Nonnull
    public synchronized LobbyDefinition upsert(@Nonnull LobbyDefinition definition) throws IOException {
        LobbyDefinition normalized = definition.normalized();
        validate(normalized);
        validateUniqueWorldName(normalized);
        lobbiesById.put(normalized.lobbyId(), normalized);
        persist();
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawLobbyId) throws IOException {
        String normalizedLobbyId = LobbyDefinition.normalizeId(rawLobbyId);
        LobbyDefinition removed = lobbiesById.remove(normalizedLobbyId);
        persist();
        return removed != null;
    }

    private void validate(@Nonnull LobbyDefinition definition) {
        var entryTarget = destinationTargetService.find(definition.entryTargetId())
            .orElseThrow(() -> new IllegalArgumentException(
                "The lobby entry target '" + definition.entryTargetId() + "' does not exist on this server."
            ));
        var returnTarget = destinationTargetService.find(definition.returnTargetId())
            .orElseThrow(() -> new IllegalArgumentException(
                "The lobby return target '" + definition.returnTargetId() + "' does not exist on this server."
            ));

        if (!entryTarget.worldName().equals(definition.worldName())) {
            throw new IllegalArgumentException(
                "Lobby world '" + definition.worldName() + "' must match entry target world '" + entryTarget.worldName() + "'."
            );
        }
        if (!returnTarget.worldName().equals(definition.worldName())) {
            throw new IllegalArgumentException(
                "Lobby world '" + definition.worldName() + "' must match return target world '" + returnTarget.worldName() + "'."
            );
        }
    }

    private void validateUniqueWorldName(@Nonnull LobbyDefinition definition) {
        for (LobbyDefinition existing : lobbiesById.values()) {
            if (!existing.lobbyId().equals(definition.lobbyId())
                && existing.worldName().equals(definition.worldName())) {
                throw new IllegalArgumentException(
                    "World '" + definition.worldName() + "' is already registered to lobby '" + existing.lobbyId() + "'."
                );
            }
        }
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(lobbiesById.values()));
    }
}
