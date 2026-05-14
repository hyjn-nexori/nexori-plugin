package io.github.hyjn.nexori.plugin.binding;

import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.binding.logic.TriggerBindingSelectionPolicy;
import io.github.hyjn.nexori.plugin.binding.logic.TriggerBindingValidationPolicy;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;

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
        return listPortalCollisionBindings(portalId).stream().findFirst();
    }

    @Nonnull
    public synchronized List<TriggerBindingDefinition> listPortalCollisionBindings(@Nonnull String portalId) {
        return TriggerBindingSelectionPolicy.listPortalCollisionBindings(bindingsById.values(), portalId);
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
        upsertBinding(binding);
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
        upsertBinding(binding);
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
        upsertBinding(binding);
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
        List<TriggerBindingDefinition> bindings = listPortalCollisionBindings(portalId);
        if (bindings.isEmpty()) {
            return false;
        }
        for (TriggerBindingDefinition binding : bindings) {
            bindingsById.remove(binding.id());
        }
        persist();
        for (TriggerBindingDefinition binding : bindings) {
            recordBindingDeleted(binding, "DELETED");
        }
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
        return TriggerBindingValidationPolicy.normalizeAndValidate(definition);
    }

    private void upsertBinding(@Nonnull TriggerBindingDefinition binding) {
        List<TriggerBindingDefinition> existingBindings = bindingsById.values().stream()
            .filter(existing -> existing.triggerKind() == binding.triggerKind())
            .filter(existing -> existing.sourceId().equals(binding.sourceId()))
            .toList();
        for (TriggerBindingDefinition existing : existingBindings) {
            if (existing.action() == binding.action()) {
                bindingsById.remove(existing.id());
                continue;
            }
            if (TriggerBindingSelectionPolicy.isQueueAction(existing.action()) && TriggerBindingSelectionPolicy.isQueueAction(binding.action())) {
                bindingsById.remove(existing.id());
                continue;
            }
            if (existing.action() == TriggerBindingAction.TRAVEL || binding.action() == TriggerBindingAction.TRAVEL) {
                throw new IllegalArgumentException(
                    "TRAVEL bindings cannot be combined with other portal actions. Remove the existing portal bindings first."
                );
            }
        }
        bindingsById.put(binding.id(), binding);
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
