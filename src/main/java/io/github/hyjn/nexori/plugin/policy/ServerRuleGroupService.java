package io.github.hyjn.nexori.plugin.policy;

import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsAction;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsCategory;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsOutcome;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonClass;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsReasonCode;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages reusable server rule groups and their server assignments.
 */
public final class ServerRuleGroupService {

    private final ServerRuleGroupStore store;
    private final DiagnosticsService diagnosticsService;
    private final Map<String, ServerRuleGroupDefinition> groupsById = new LinkedHashMap<>();

    /**
     * Loads the persisted server rule groups for this server.
     */
    public ServerRuleGroupService(@Nonnull ServerRuleGroupStore store, @Nonnull DiagnosticsService diagnosticsService) throws IOException {
        this.store = store;
        this.diagnosticsService = diagnosticsService;
        for (ServerRuleGroupDefinition group : store.loadOrCreate()) {
            ServerRuleGroupDefinition normalized = group.normalized();
            groupsById.put(normalized.groupId(), normalized);
        }
    }

    /**
     * Lists all rule groups saved on this server.
     */
    @Nonnull
    public synchronized List<ServerRuleGroupDefinition> list() {
        return groupsById.values().stream()
            .sorted(Comparator.comparing(ServerRuleGroupDefinition::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * Finds a rule group by id.
     */
    @Nonnull
    public synchronized Optional<ServerRuleGroupDefinition> find(@Nonnull String groupId) {
        if (groupId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(groupsById.get(groupId.trim().toLowerCase()));
    }

    /**
     * Creates a new rule group.
     */
    @Nonnull
    public synchronized ServerRuleGroupDefinition create(
        @Nonnull String displayName,
        boolean recoveryEnabled,
        int maxBackupsPerPlayer
    ) throws IOException {
        String trimmedName = displayName == null ? "" : displayName.trim();
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("A rule group needs a display name.");
        }

        ServerRuleGroupDefinition created = new ServerRuleGroupDefinition(
            "group." + UUID.randomUUID().toString().replace("-", ""),
            trimmedName,
            recoveryEnabled,
            maxBackupsPerPlayer,
            List.of()
        ).normalized();
        groupsById.put(created.groupId(), created);
        persist();
        recordGroupChange(created, DiagnosticsAction.CONFIG_RULE_GROUP_SAVE, DiagnosticsReasonCode.RULE_GROUP_SAVED, "CREATED", "Created a server rule group.");
        return created;
    }

    /**
     * Deletes a rule group.
     */
    public synchronized boolean remove(@Nonnull String groupId) throws IOException {
        ServerRuleGroupDefinition removed = groupsById.remove(groupId.trim().toLowerCase());
        persist();
        if (removed != null) {
            recordGroupChange(removed, DiagnosticsAction.CONFIG_RULE_GROUP_DELETE, DiagnosticsReasonCode.RULE_GROUP_DELETED, "DELETED", "Deleted a server rule group.");
        }
        return removed != null;
    }

    /**
     * Renames a rule group.
     */
    @Nonnull
    public synchronized ServerRuleGroupDefinition rename(@Nonnull String groupId, @Nonnull String displayName) throws IOException {
        ServerRuleGroupDefinition current = requireGroup(groupId);
        String trimmedName = displayName == null ? "" : displayName.trim();
        if (trimmedName.isBlank()) {
            throw new IllegalArgumentException("A rule group needs a display name.");
        }
        ServerRuleGroupDefinition updated = current.withDisplayName(trimmedName);
        groupsById.put(updated.groupId(), updated);
        persist();
        recordGroupChange(updated, DiagnosticsAction.CONFIG_RULE_GROUP_SAVE, DiagnosticsReasonCode.RULE_GROUP_SAVED, "UPSERTED", "Updated a server rule group.");
        return updated;
    }

    /**
     * Enables or disables recovery for a rule group.
     */
    @Nonnull
    public synchronized ServerRuleGroupDefinition setRecoveryEnabled(@Nonnull String groupId, boolean enabled) throws IOException {
        ServerRuleGroupDefinition current = requireGroup(groupId);
        ServerRuleGroupDefinition updated = current.withRecoveryEnabled(enabled);
        groupsById.put(updated.groupId(), updated);
        persist();
        recordGroupChange(updated, DiagnosticsAction.CONFIG_RULE_GROUP_SAVE, DiagnosticsReasonCode.RULE_GROUP_SAVED, "UPSERTED", "Updated a server rule group.");
        return updated;
    }

    /**
     * Sets the maximum backups per player for a rule group.
     */
    @Nonnull
    public synchronized ServerRuleGroupDefinition setMaxBackupsPerPlayer(@Nonnull String groupId, int maxBackupsPerPlayer) throws IOException {
        ServerRuleGroupDefinition current = requireGroup(groupId);
        ServerRuleGroupDefinition updated = current.withMaxBackupsPerPlayer(maxBackupsPerPlayer);
        groupsById.put(updated.groupId(), updated);
        persist();
        recordGroupChange(updated, DiagnosticsAction.CONFIG_RULE_GROUP_SAVE, DiagnosticsReasonCode.RULE_GROUP_SAVED, "UPSERTED", "Updated a server rule group.");
        return updated;
    }

    /**
     * Assigns a server selection key to a rule group.
     */
    @Nonnull
    public synchronized ServerRuleGroupDefinition assignServer(@Nonnull String groupId, @Nonnull String serverSelectionKey) throws IOException {
        ServerRuleGroupDefinition current = requireGroup(groupId);
        String normalizedKey = ServerRuleGroupDefinition.normalizeServerKey(serverSelectionKey);
        if (normalizedKey.isBlank()) {
            throw new IllegalArgumentException("That server key is blank.");
        }

        Optional<ServerRuleGroupDefinition> existingAssignment = findAssignedGroup(normalizedKey);
        if (existingAssignment.isPresent() && !existingAssignment.get().groupId().equals(current.groupId())) {
            throw new IllegalStateException(
                "That server already belongs to the rule group '" + existingAssignment.get().displayName() + "'."
            );
        }

        ServerRuleGroupDefinition updated = current.assignServer(normalizedKey);
        groupsById.put(updated.groupId(), updated);
        persist();
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            DiagnosticsAction.CONFIG_RULE_GROUP_ASSIGN,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            DiagnosticsReasonCode.RULE_GROUP_SERVER_ASSIGNED,
            "Assigned a server to a rule group.",
            operationId,
            event -> event
                .entityType("RULE_GROUP")
                .entityId(updated.groupId())
                .changeType("ASSIGNED")
                .ruleGroupId(updated.groupId())
                .remoteConnectionAddress(normalizedKey)
                .addPreview("groupId", updated.groupId())
                .addPreview("serverKey", normalizedKey)
        );
        return updated;
    }

    /**
     * Removes a server selection key from a rule group.
     */
    @Nonnull
    public synchronized ServerRuleGroupDefinition unassignServer(@Nonnull String groupId, @Nonnull String serverSelectionKey) throws IOException {
        ServerRuleGroupDefinition current = requireGroup(groupId);
        ServerRuleGroupDefinition updated = current.unassignServer(serverSelectionKey);
        groupsById.put(updated.groupId(), updated);
        persist();
        recordGroupChange(updated, DiagnosticsAction.CONFIG_RULE_GROUP_SAVE, DiagnosticsReasonCode.RULE_GROUP_SAVED, "UPSERTED", "Updated a server rule group.");
        return updated;
    }

    /**
     * Removes one server selection key from every rule group that currently contains it.
     */
    public synchronized void removeServerAssignments(@Nonnull String serverSelectionKey) throws IOException {
        String normalizedKey = ServerRuleGroupDefinition.normalizeServerKey(serverSelectionKey);
        if (normalizedKey.isBlank()) {
            return;
        }

        boolean changed = false;
        for (ServerRuleGroupDefinition group : new ArrayList<>(groupsById.values())) {
            if (group.containsServer(normalizedKey)) {
                groupsById.put(group.groupId(), group.unassignServer(normalizedKey));
                changed = true;
            }
        }

        if (changed) {
            persist();
        }
    }

    /**
     * Finds the rule group that currently owns a server selection key.
     */
    @Nonnull
    public synchronized Optional<ServerRuleGroupDefinition> findAssignedGroup(@Nonnull String serverSelectionKey) {
        String normalizedKey = ServerRuleGroupDefinition.normalizeServerKey(serverSelectionKey);
        if (normalizedKey.isBlank()) {
            return Optional.empty();
        }

        return groupsById.values().stream()
            .filter(group -> group.containsServer(normalizedKey))
            .findFirst();
    }

    @Nonnull
    private ServerRuleGroupDefinition requireGroup(@Nonnull String groupId) {
        return find(groupId)
            .orElseThrow(() -> new IllegalArgumentException("That rule group does not exist."));
    }

    private void persist() throws IOException {
        store.save(new ArrayList<>(list()));
    }

    private void recordGroupChange(
        @Nonnull ServerRuleGroupDefinition group,
        @Nonnull String action,
        @Nonnull String reasonCode,
        @Nonnull String changeType,
        @Nonnull String message
    ) {
        String operationId = diagnosticsService.newOperationId("config");
        diagnosticsService.record(
            DiagnosticsCategory.CONFIG,
            action,
            DiagnosticsOutcome.SUCCEEDED,
            DiagnosticsReasonClass.NORMAL,
            reasonCode,
            message,
            operationId,
            event -> event
                .entityType("RULE_GROUP")
                .entityId(group.groupId())
                .changeType(changeType)
                .ruleGroupId(group.groupId())
                .addPreview("groupId", group.groupId())
                .addPreview("displayName", group.displayName())
        );
    }
}
