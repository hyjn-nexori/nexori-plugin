package io.github.hyjn.nexori.plugin.binding;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TriggerBindingService {

    private final TriggerBindingStore store;
    private final Map<String, TriggerBindingDefinition> bindingsById = new LinkedHashMap<>();

    public TriggerBindingService(@Nonnull TriggerBindingStore store) throws IOException {
        this.store = store;
        for (TriggerBindingDefinition binding : store.loadOrCreate()) {
            TriggerBindingDefinition normalized = binding.normalized();
            bindingsById.put(normalized.id(), normalized);
        }
    }

    @Nonnull
    public synchronized List<TriggerBindingDefinition> list() {
        return bindingsById.values().stream()
            .sorted(Comparator.comparing(TriggerBindingDefinition::id))
            .toList();
    }

    @Nonnull
    public synchronized Optional<TriggerBindingDefinition> find(@Nonnull String rawId) {
        try {
            return Optional.ofNullable(bindingsById.get(TriggerBindingDefinition.normalizeId(rawId)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Nonnull
    public synchronized Optional<TriggerBindingDefinition> findPortalCollisionBinding(@Nonnull String portalId) {
        String normalizedSourceId = portalId.trim().toLowerCase();
        return bindingsById.values().stream()
            .filter(binding -> binding.triggerKind() == TriggerBindingKind.PORTAL_COLLISION_ENTER)
            .filter(binding -> binding.sourceId().equals(normalizedSourceId))
            .findFirst();
    }

    @Nonnull
    public synchronized TriggerBindingDefinition bindPortalCollision(
        @Nonnull String portalId,
        @Nonnull String destinationConnectionAddress,
        @Nonnull String destinationTargetId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson
    ) throws IOException {
        ConfiguredPeer destination = ConfiguredPeer.parse(destinationConnectionAddress);
        TriggerBindingDefinition binding = new TriggerBindingDefinition(
            "",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            portalId,
            destination.connectionAddress(),
            destinationTargetId,
            travelProfileId,
            contextJson,
            true
        ).normalized();
        bindingsById.put(binding.id(), binding);
        persist();
        return binding;
    }

    public synchronized boolean remove(@Nonnull String rawId) throws IOException {
        String normalizedId = TriggerBindingDefinition.normalizeId(rawId);
        TriggerBindingDefinition removed = bindingsById.remove(normalizedId);
        persist();
        return removed != null;
    }

    public synchronized boolean removePortalCollisionBinding(@Nonnull String portalId) throws IOException {
        Optional<TriggerBindingDefinition> binding = findPortalCollisionBinding(portalId);
        if (binding.isEmpty()) {
            return false;
        }
        bindingsById.remove(binding.get().id());
        persist();
        return true;
    }

    public synchronized void removeBindingsForSource(@Nonnull String sourceId) throws IOException {
        String normalizedSourceId = sourceId.trim().toLowerCase();
        bindingsById.values().removeIf(binding -> binding.sourceId().equals(normalizedSourceId));
        persist();
    }

    @Nonnull
    public synchronized TriggerBindingDefinition setEnabled(@Nonnull String rawId, boolean enabled) throws IOException {
        TriggerBindingDefinition current = find(rawId)
            .orElseThrow(() -> new IllegalArgumentException("That Nexori trigger binding does not exist."));
        TriggerBindingDefinition updated = new TriggerBindingDefinition(
            current.id(),
            current.triggerKind(),
            current.sourceId(),
            current.destinationConnectionAddress(),
            current.destinationTargetId(),
            current.travelProfileId(),
            current.contextJson(),
            enabled
        ).normalized();
        bindingsById.put(updated.id(), updated);
        persist();
        return updated;
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(bindingsById.values()));
    }
}
