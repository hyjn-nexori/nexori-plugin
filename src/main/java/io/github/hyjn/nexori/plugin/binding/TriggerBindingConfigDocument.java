package io.github.hyjn.nexori.plugin.binding;

import javax.annotation.Nonnull;
import java.util.List;

public record TriggerBindingConfigDocument(
    int schemaVersion,
    List<TriggerBindingDefinition> triggerBindings
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    @Nonnull
    public TriggerBindingConfigDocument {
        triggerBindings = triggerBindings == null ? List.of() : List.copyOf(triggerBindings);
    }
}
