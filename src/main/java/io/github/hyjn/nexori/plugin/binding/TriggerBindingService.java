package io.github.hyjn.nexori.plugin.binding;

import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;
import io.github.hyjn.nexori.plugin.profile.TravelProfileType;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class TriggerBindingService {

    private final TriggerBindingStore store;
    private final DiagnosticsService diagnosticsService;
    private final Map<String, TriggerBindingDefinition> bindingsById = new LinkedHashMap<>();

    public TriggerBindingService(@Nonnull TriggerBindingStore store, @Nonnull DiagnosticsService diagnosticsService) throws IOException {
        this.store = store;
        this.diagnosticsService = diagnosticsService;
        for (TriggerBindingDefinition binding : store.loadOrCreate()) {
            TriggerBindingDefinition normalized = normalizeAndValidate(binding);
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
        String normalizedSourceId = portalId.trim().toLowerCase(Locale.ROOT);
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
        return bindPortalCollisionTravel(portalId, destinationConnectionAddress, destinationTargetId, travelProfileId, contextJson);
    }

    @Nonnull
    public synchronized TriggerBindingDefinition bindPortalCollisionTravel(
        @Nonnull String portalId,
        @Nonnull String destinationConnectionAddress,
        @Nonnull String destinationTargetId,
        @Nonnull String travelProfileId,
        @Nonnull String contextJson
    ) throws IOException {
        TriggerBindingDefinition binding = normalizeAndValidate(new TriggerBindingDefinition(
            "",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            portalId,
            TriggerBindingAction.TRAVEL,
            "",
            destinationConnectionAddress,
            destinationTargetId,
            travelProfileId,
            contextJson,
            true
        ));
        bindingsById.put(binding.id(), binding);
        persist();
        recordBindingSaved(binding, "UPSERTED");
        return binding;
    }

    @Nonnull
    public synchronized TriggerBindingDefinition bindPortalCollisionQueue(
        @Nonnull String portalId,
        @Nonnull String queueId,
        @Nonnull TriggerBindingAction action
    ) throws IOException {
        if (action != TriggerBindingAction.JOIN_QUEUE && action != TriggerBindingAction.LEAVE_QUEUE) {
            throw new IllegalArgumentException("Queue portal bindings must use JOIN_QUEUE or LEAVE_QUEUE.");
        }
        TriggerBindingDefinition binding = normalizeAndValidate(new TriggerBindingDefinition(
            "",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            portalId,
            action,
            queueId,
            "",
            "",
            "",
            "{}",
            true
        ));
        bindingsById.put(binding.id(), binding);
        persist();
        recordBindingSaved(binding, "UPSERTED");
        return binding;
    }

    @Nonnull
    public synchronized TriggerBindingDefinition bindPortalCollisionLocalTarget(
        @Nonnull String portalId,
        @Nonnull String destinationTargetId
    ) throws IOException {
        TriggerBindingDefinition binding = normalizeAndValidate(new TriggerBindingDefinition(
            "",
            TriggerBindingKind.PORTAL_COLLISION_ENTER,
            portalId,
            TriggerBindingAction.LOCAL_TARGET,
            "",
            "",
            destinationTargetId,
            "",
            "{}",
            true
        ));
        bindingsById.put(binding.id(), binding);
        persist();
        recordBindingSaved(binding, "UPSERTED");
        return binding;
    }

    public synchronized boolean remove(@Nonnull String rawId) throws IOException {
        String normalizedId = TriggerBindingDefinition.normalizeId(rawId);
        TriggerBindingDefinition removed = bindingsById.remove(normalizedId);
        persist();
        if (removed != null) {
            recordBindingDeleted(removed, "DELETED");
        }
        return removed != null;
    }

    public synchronized boolean removePortalCollisionBinding(@Nonnull String portalId) throws IOException {
        Optional<TriggerBindingDefinition> binding = findPortalCollisionBinding(portalId);
        if (binding.isEmpty()) {
            return false;
        }
        TriggerBindingDefinition removed = binding.get();
        bindingsById.remove(removed.id());
        persist();
        recordBindingDeleted(removed, "DELETED");
        return true;
    }

    public synchronized void removeBindingsForSource(@Nonnull String sourceId) throws IOException {
        String normalizedSourceId = sourceId.trim().toLowerCase();
        List<TriggerBindingDefinition> removed = bindingsById.values().stream()
            .filter(binding -> binding.sourceId().equals(normalizedSourceId))
            .toList();
        bindingsById.values().removeIf(binding -> binding.sourceId().equals(normalizedSourceId));
        persist();
        for (TriggerBindingDefinition binding : removed) {
            recordBindingDeleted(binding, "DELETED");
        }
    }

    @Nonnull
    public synchronized TriggerBindingDefinition setEnabled(@Nonnull String rawId, boolean enabled) throws IOException {
        TriggerBindingDefinition current = find(rawId)
            .orElseThrow(() -> new IllegalArgumentException("That Nexori trigger binding does not exist."));
        TriggerBindingDefinition updated = new TriggerBindingDefinition(
            current.id(),
            current.triggerKind(),
            current.sourceId(),
            current.action(),
            current.queueId(),
            current.destinationConnectionAddress(),
            current.destinationTargetId(),
            current.travelProfileId(),
            current.contextJson(),
            enabled
        );
        updated = normalizeAndValidate(updated);
        bindingsById.put(updated.id(), updated);
        persist();
        recordBindingSaved(updated, "UPSERTED");
        return updated;
    }

    @Nonnull
    private TriggerBindingDefinition normalizeAndValidate(@Nonnull TriggerBindingDefinition definition) {
        TriggerBindingDefinition normalized = definition.normalized();
        if (normalized.action() == TriggerBindingAction.JOIN_QUEUE || normalized.action() == TriggerBindingAction.LEAVE_QUEUE) {
            if (normalized.queueId().isBlank()) {
                throw new IllegalArgumentException("Queue trigger bindings require a queue id.");
            }
            return normalized;
        }

        if (normalized.action() == TriggerBindingAction.LOCAL_TARGET) {
            if (normalized.destinationTargetId().isBlank()) {
                throw new IllegalArgumentException("Local target trigger bindings require a destination target id.");
            }
            return new TriggerBindingDefinition(
                normalized.id(),
                normalized.triggerKind(),
                normalized.sourceId(),
                TriggerBindingAction.LOCAL_TARGET,
                "",
                "",
                normalized.destinationTargetId(),
                "",
                "{}",
                normalized.enabled()
            );
        }

        ConfiguredPeer destination = ConfiguredPeer.parse(normalized.destinationConnectionAddress());
        if (normalized.destinationTargetId().isBlank()) {
            throw new IllegalArgumentException("Travel trigger bindings require a destination target id.");
        }
        TravelProfileType profile = TravelProfileType.parse(normalized.travelProfileId());
        return new TriggerBindingDefinition(
            normalized.id(),
            normalized.triggerKind(),
            normalized.sourceId(),
            TriggerBindingAction.TRAVEL,
            "",
            destination.connectionAddress(),
            normalized.destinationTargetId(),
            profile.id(),
            normalized.contextJson(),
            normalized.enabled()
        );
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(bindingsById.values()));
    }

    private void recordBindingSaved(@Nonnull TriggerBindingDefinition binding, @Nonnull String changeType) {
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            DiagnosticsAction.CONFIG_BINDING_SAVE,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BINDING_SAVED,
            "Saved a trigger binding on this server.",
            operationId,
            event -> event
                .entityType("TRIGGER_BINDING")
                .entityId(binding.id())
                .changeType(changeType)
                .bindingId(binding.id())
                .portalId(binding.sourceId())
                .addPreview("action", binding.action().name())
                .addPreview("queueId", binding.queueId())
                .targetId(binding.destinationTargetId())
                .travelProfileId(binding.travelProfileId())
                .remoteConnectionAddress(binding.destinationConnectionAddress())
                .addPreview("bindingId", binding.id())
                .addPreview("destination", binding.destinationConnectionAddress())
        );
    }

    private void recordBindingDeleted(@Nonnull TriggerBindingDefinition binding, @Nonnull String changeType) {
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            DiagnosticsAction.CONFIG_BINDING_DELETE,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.BINDING_DELETED,
            "Deleted a trigger binding from this server.",
            operationId,
            event -> event
                .entityType("TRIGGER_BINDING")
                .entityId(binding.id())
                .changeType(changeType)
                .bindingId(binding.id())
                .portalId(binding.sourceId())
                .addPreview("action", binding.action().name())
                .addPreview("queueId", binding.queueId())
                .targetId(binding.destinationTargetId())
                .travelProfileId(binding.travelProfileId())
                .remoteConnectionAddress(binding.destinationConnectionAddress())
                .addPreview("bindingId", binding.id())
        );
    }
}
