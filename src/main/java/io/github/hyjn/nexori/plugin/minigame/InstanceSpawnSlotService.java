package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.builtin.instances.InstancesPlugin;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InstanceSpawnSlotService {

    private final InstanceSpawnSlotStore store;
    private final Map<String, InstanceSpawnSlotDefinition> slotsById = new LinkedHashMap<>();

    public InstanceSpawnSlotService(@Nonnull InstanceSpawnSlotStore store) throws IOException {
        this.store = store;
        for (InstanceSpawnSlotDefinition slot : store.loadOrCreate()) {
            InstanceSpawnSlotDefinition normalized = normalizeAndValidate(slot);
            slotsById.put(normalized.slotId(), normalized);
        }
    }

    @Nonnull
    public synchronized List<InstanceSpawnSlotDefinition> list() {
        return slotsById.values().stream()
            .sorted(Comparator
                .comparing(InstanceSpawnSlotDefinition::instanceTemplateId, String.CASE_INSENSITIVE_ORDER)
                .thenComparingLong(InstanceSpawnSlotDefinition::createdAtEpochMs))
            .toList();
    }

    @Nonnull
    public synchronized List<InstanceSpawnSlotDefinition> listByInstanceTemplateId(@Nonnull String rawInstanceTemplateId) {
        if (rawInstanceTemplateId == null || rawInstanceTemplateId.isBlank()) {
            return List.of();
        }
        return slotsById.values().stream()
            .filter(slot -> slot.instanceTemplateId().equalsIgnoreCase(rawInstanceTemplateId.trim()))
            .sorted(Comparator.comparingLong(InstanceSpawnSlotDefinition::createdAtEpochMs))
            .toList();
    }

    @Nonnull
    public synchronized Optional<InstanceSpawnSlotDefinition> find(@Nonnull String rawSlotId) {
        if (rawSlotId == null || rawSlotId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(slotsById.get(InstanceSpawnSlotDefinition.normalizeSlotId(rawSlotId)));
    }

    @Nonnull
    public synchronized InstanceSpawnSlotDefinition upsert(@Nonnull InstanceSpawnSlotDefinition definition) throws IOException {
        InstanceSpawnSlotDefinition normalized = normalizeAndValidate(definition);
        slotsById.put(normalized.slotId(), normalized);
        persist();
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawSlotId) throws IOException {
        InstanceSpawnSlotDefinition removed = slotsById.remove(InstanceSpawnSlotDefinition.normalizeSlotId(rawSlotId));
        persist();
        return removed != null;
    }

    @Nonnull
    private InstanceSpawnSlotDefinition normalizeAndValidate(@Nonnull InstanceSpawnSlotDefinition definition) {
        InstanceSpawnSlotDefinition normalized = definition.normalized();
        if (!InstancesPlugin.doesInstanceAssetExist(normalized.instanceTemplateId())) {
            throw new IllegalArgumentException(
                "Spawn slot instance template '" + normalized.instanceTemplateId() + "' does not exist in this server's loaded assets."
            );
        }
        return normalized;
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(slotsById.values()));
    }
}
