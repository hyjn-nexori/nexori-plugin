package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ArenaService {

    private final ArenaStore store;
    private final LocalConnectionAddressService localConnectionAddressService;
    private final Map<String, ArenaDefinition> arenasById = new LinkedHashMap<>();

    public ArenaService(@Nonnull ArenaStore store, @Nonnull LocalConnectionAddressService localConnectionAddressService) throws IOException {
        this.store = store;
        this.localConnectionAddressService = localConnectionAddressService;
        for (ArenaDefinition arena : store.loadOrCreate()) {
            ArenaDefinition normalized = normalizeAndValidate(arena);
            arenasById.put(normalized.arenaId(), normalized);
        }
    }

    @Nonnull
    public synchronized List<ArenaDefinition> list() {
        return arenasById.values().stream()
            .sorted(Comparator.comparing(ArenaDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Nonnull
    public synchronized Optional<ArenaDefinition> find(@Nonnull String rawArenaId) {
        if (rawArenaId == null || rawArenaId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(arenasById.get(ArenaDefinition.normalizeId(rawArenaId)));
    }

    @Nonnull
    public synchronized ArenaDefinition upsert(@Nonnull ArenaDefinition definition) throws IOException {
        ArenaDefinition normalized = normalizeAndValidate(definition);
        arenasById.put(normalized.arenaId(), normalized);
        persist();
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawArenaId) throws IOException {
        String normalizedArenaId = ArenaDefinition.normalizeId(rawArenaId);
        ArenaDefinition removed = arenasById.remove(normalizedArenaId);
        persist();
        return removed != null;
    }

    @Nonnull
    private ArenaDefinition normalizeAndValidate(@Nonnull ArenaDefinition definition) {
        ArenaDefinition normalized = definition.normalized();
        ConfiguredPeer peer = ConfiguredPeer.parse(normalized.destinationConnectionAddress());
        if (!normalized.usesInstanceTemplate() && normalized.destinationTargetId().isBlank()) {
            throw new IllegalArgumentException("Arena destination target id cannot be blank.");
        }
        if (normalized.maxSupportedPlayers() < 1) {
            throw new IllegalArgumentException("Arena max supported players must be at least 1.");
        }
        if (normalized.usesInstanceTemplate() && !InstancesPlugin.doesInstanceAssetExist(normalized.instanceTemplateId())) {
            throw new IllegalArgumentException(
                "Arena instance template '" + normalized.instanceTemplateId() + "' does not exist in this server's loaded assets."
            );
        }
        String localConnectionAddress = localConnectionAddressService.getConnectionAddressOrBlank();
        if (!localConnectionAddress.isBlank() && peer.connectionAddress().equalsIgnoreCase(localConnectionAddress)) {
            throw new IllegalArgumentException("Arena destination must point to another server, not this server.");
        }
        return new ArenaDefinition(
            normalized.arenaId(),
            normalized.displayName(),
            peer.connectionAddress(),
            normalized.destinationTargetId(),
            normalized.instanceTemplateId(),
            normalized.matchResolutionTriggerId(),
            normalized.rulesEngineId(),
            normalized.maxSupportedPlayers(),
            normalized.enabled()
        );
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(arenasById.values()));
    }
}
