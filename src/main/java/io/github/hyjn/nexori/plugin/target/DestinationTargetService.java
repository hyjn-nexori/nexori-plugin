package io.github.hyjn.nexori.plugin.target;

import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.target.logic.DestinationTargetResolutionPlanner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DestinationTargetService {

    private static final DestinationTargetResolutionPlanner RESOLUTION_PLANNER = new DestinationTargetResolutionPlanner();

    private final DestinationTargetStore store;
    private final DiagnosticsService diagnosticsService;
    private final Map<String, DestinationTargetDefinition> targetsById = new LinkedHashMap<>();

    public DestinationTargetService(@Nonnull DestinationTargetStore store, @Nonnull DiagnosticsService diagnosticsService) throws IOException {
        this.store = store;
        this.diagnosticsService = diagnosticsService;
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
        return upsert(definition, true);
    }

    @Nonnull
    public synchronized DestinationTargetDefinition upsert(@Nonnull DestinationTargetDefinition definition, boolean recordDiagnostics) throws IOException {
        DestinationTargetDefinition normalized = definition.normalized();
        targetsById.put(normalized.id(), normalized);
        persist();
        if (recordDiagnostics) {
            String operationId = diagnosticsService.newOperationId("config");
            diagnosticsService.record(
                DiagnosticsCategory.CONFIG,
                DiagnosticsAction.CONFIG_TARGET_SAVE,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.TARGET_SAVED,
                "Saved a destination target on this server.",
                operationId,
                event -> event
                    .entityType("DESTINATION_TARGET")
                    .entityId(normalized.id())
                    .changeType("UPSERTED")
                    .targetId(normalized.id())
                    .targetDisplayName(normalized.displayName())
                    .targetKind(normalized.kind().name())
                    .worldName(normalized.worldName())
                    .arrivalPointId(normalized.arrivalPointId())
                    .addPreview("targetId", normalized.id())
                    .addPreview("kind", normalized.kind().name())
            );
        }
        return normalized;
    }

    public synchronized boolean remove(@Nonnull String rawId) throws IOException {
        String normalizedId = DestinationTargetDefinition.normalizeId(rawId);
        DestinationTargetDefinition removed = targetsById.remove(normalizedId);
        persist();
        if (removed != null) {
            String operationId = diagnosticsService.newOperationId("config");
            diagnosticsService.record(
                DiagnosticsCategory.CONFIG,
                DiagnosticsAction.CONFIG_TARGET_DELETE,
                DiagnosticsOutcome.SUCCEEDED,
                DiagnosticsReasonClass.NORMAL,
                DiagnosticsReasonCode.TARGET_DELETED,
                "Deleted a destination target from this server.",
                operationId,
                event -> event
                    .entityType("DESTINATION_TARGET")
                    .entityId(removed.id())
                    .changeType("DELETED")
                    .targetId(removed.id())
                    .targetDisplayName(removed.displayName())
                    .targetKind(removed.kind().name())
                    .worldName(removed.worldName())
                    .arrivalPointId(removed.arrivalPointId())
                    .addPreview("targetId", removed.id())
            );
        }
        return removed != null;
    }

    @Nonnull
    public synchronized Optional<ResolvedDestinationTarget> resolve(@Nonnull String rawTargetId, String rawArrivalPointId) {
        Optional<DestinationTargetDefinition> definition = find(rawTargetId);
        if (definition.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(RESOLUTION_PLANNER.plan(definition.get(), rawArrivalPointId));
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(targetsById.values()));
    }
}
