package io.github.hyjn.nexori.plugin.policy;

import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record ServerRuleGroupDefinition(
    String groupId,
    String displayName,
    boolean recoveryEnabled,
    int maxBackupsPerPlayer,
    List<String> assignedServerKeys
) {

    public static final String LOCAL_SERVER_KEY = "__local__";

    @Nonnull
    public ServerRuleGroupDefinition normalized() {
        String normalizedGroupId = groupId == null || groupId.isBlank()
            ? "group." + UUID.randomUUID().toString().replace("-", "")
            : groupId.trim().toLowerCase();
        String normalizedDisplayName = displayName == null || displayName.isBlank()
            ? "Rule Group"
            : displayName.trim();

        LinkedHashSet<String> normalizedAssignments = new LinkedHashSet<>();
        if (assignedServerKeys != null) {
            for (String assignedServerKey : assignedServerKeys) {
                String normalizedServerKey = normalizeServerKey(assignedServerKey);
                if (!normalizedServerKey.isBlank()) {
                    normalizedAssignments.add(normalizedServerKey);
                }
            }
        }

        return new ServerRuleGroupDefinition(
            normalizedGroupId,
            normalizedDisplayName,
            recoveryEnabled,
            Math.max(1, maxBackupsPerPlayer),
            List.copyOf(normalizedAssignments)
        );
    }

    @Nonnull
    public ServerRuleGroupDefinition withDisplayName(@Nonnull String newDisplayName) {
        return new ServerRuleGroupDefinition(
            groupId,
            newDisplayName,
            recoveryEnabled,
            maxBackupsPerPlayer,
            assignedServerKeys
        ).normalized();
    }

    @Nonnull
    public ServerRuleGroupDefinition withRecoveryEnabled(boolean enabled) {
        return new ServerRuleGroupDefinition(
            groupId,
            displayName,
            enabled,
            maxBackupsPerPlayer,
            assignedServerKeys
        ).normalized();
    }

    @Nonnull
    public ServerRuleGroupDefinition withMaxBackupsPerPlayer(int maxBackups) {
        return new ServerRuleGroupDefinition(
            groupId,
            displayName,
            recoveryEnabled,
            maxBackups,
            assignedServerKeys
        ).normalized();
    }

    @Nonnull
    public ServerRuleGroupDefinition withAssignedServerKeys(@Nonnull List<String> newAssignedServerKeys) {
        return new ServerRuleGroupDefinition(
            groupId,
            displayName,
            recoveryEnabled,
            maxBackupsPerPlayer,
            newAssignedServerKeys
        ).normalized();
    }

    public boolean containsServer(@Nonnull String selectionKey) {
        String normalizedKey = normalizeServerKey(selectionKey);
        return !normalizedKey.isBlank() && assignedServerKeys != null && assignedServerKeys.contains(normalizedKey);
    }

    @Nonnull
    public ServerRuleGroupDefinition assignServer(@Nonnull String selectionKey) {
        String normalizedKey = normalizeServerKey(selectionKey);
        if (normalizedKey.isBlank()) {
            return normalized();
        }

        List<String> updatedAssignments = new ArrayList<>(assignedServerKeys == null ? List.of() : assignedServerKeys);
        if (!updatedAssignments.contains(normalizedKey)) {
            updatedAssignments.add(normalizedKey);
        }
        return withAssignedServerKeys(updatedAssignments);
    }

    @Nonnull
    public ServerRuleGroupDefinition unassignServer(@Nonnull String selectionKey) {
        String normalizedKey = normalizeServerKey(selectionKey);
        List<String> updatedAssignments = new ArrayList<>();
        for (String assignedServerKey : assignedServerKeys == null ? List.<String>of() : assignedServerKeys) {
            if (!normalizedKey.equals(assignedServerKey)) {
                updatedAssignments.add(assignedServerKey);
            }
        }
        return withAssignedServerKeys(updatedAssignments);
    }

    @Nonnull
    public static String normalizeServerKey(String rawSelectionKey) {
        if (rawSelectionKey == null || rawSelectionKey.isBlank()) {
            return "";
        }

        String trimmed = rawSelectionKey.trim();
        if (LOCAL_SERVER_KEY.equalsIgnoreCase(trimmed)) {
            return LOCAL_SERVER_KEY;
        }

        try {
            return ConfiguredPeer.parse(trimmed).connectionAddress();
        } catch (IllegalArgumentException exception) {
            return trimmed.toLowerCase();
        }
    }
}
