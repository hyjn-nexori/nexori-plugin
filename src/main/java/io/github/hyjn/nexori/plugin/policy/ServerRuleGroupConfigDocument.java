package io.github.hyjn.nexori.plugin.policy;

import javax.annotation.Nonnull;
import java.util.List;

record ServerRuleGroupConfigDocument(
    int schemaVersion,
    List<ServerRuleGroupDefinition> ruleGroups
) {
    static final int CURRENT_SCHEMA_VERSION = 1;

    @Nonnull
    static ServerRuleGroupConfigDocument empty() {
        return new ServerRuleGroupConfigDocument(CURRENT_SCHEMA_VERSION, List.of());
    }
}
