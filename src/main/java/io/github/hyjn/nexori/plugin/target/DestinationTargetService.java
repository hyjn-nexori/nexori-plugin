package io.github.hyjn.nexori.plugin.target;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DestinationTargetService {

    private final DestinationTargetStore store;
    private final Map<String, DestinationTargetDefinition> targetsById = new LinkedHashMap<>();

    public DestinationTargetService(@Nonnull DestinationTargetStore store) throws IOException {
        this.store = store;
        for (DestinationTargetDefinition target : store.loadOrCreate()) {
            DestinationTargetDefinition normalized = target.normalized();
            targetsById.put(normalized.id(), normalized);
        }
    }

    @Nonnull
    public synchronized List<DestinationTargetDefinition> list() {
        return targetsById.values().stream()
            .sorted(Comparator.comparing(DestinationTargetDefinition::id))
            .toList();
    }

    public synchronized int size() {
        return targetsById.size();
    }

    @Nonnull
    public synchronized Optional<DestinationTargetDefinition> find(@Nonnull String rawId) {
        try {
            return Optional.ofNullable(targetsById.get(DestinationTargetDefinition.normalizeId(rawId)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    @Nonnull
    public synchronized DestinationTargetDefinition upsert(@Nonnull DestinationTargetDefinition definition) throws IOException {
        DestinationTargetDefinition normalized = definition.normalized();
        targetsById.put(normalized.id(), normalized);
        persist();
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawId) throws IOException {
        String normalizedId = DestinationTargetDefinition.normalizeId(rawId);
        DestinationTargetDefinition removed = targetsById.remove(normalizedId);
        persist();
        return removed != null;
    }

    @Nonnull
    public synchronized Optional<ResolvedDestinationTarget> resolve(@Nonnull String rawTargetId, String rawArrivalPointId) {
        Optional<DestinationTargetDefinition> definition = find(rawTargetId);
        if (definition.isEmpty()) {
            return Optional.empty();
        }

        String effectiveArrivalPointId = rawArrivalPointId == null ? "" : rawArrivalPointId.trim();
        if (effectiveArrivalPointId.isBlank()) {
            effectiveArrivalPointId = definition.get().arrivalPointId();
        }

        return Optional.of(new ResolvedDestinationTarget(
            definition.get(),
            definition.get().worldName(),
            effectiveArrivalPointId
        ));
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(targetsById.values()));
    }
}
