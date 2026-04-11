package io.github.hyjn.nexori.plugin.bootstrap;

import javax.annotation.Nonnull;
import java.util.List;

public record BootstrapMigrationPlan(
    List<BootstrapTextReplacement> replacements
) {

    @Nonnull
    public static BootstrapMigrationPlan empty() {
        return new BootstrapMigrationPlan(List.of());
    }

    public BootstrapMigrationPlan {
        replacements = replacements == null ? List.of() : List.copyOf(replacements);
    }

    public boolean isEmpty() {
        return replacements.isEmpty();
    }
}
