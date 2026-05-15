package io.github.hyjn.nexori.plugin.bootstrap.logic;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacement;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacementScope;

import javax.annotation.Nonnull;
import java.util.List;

public final class BootstrapTextMigrationPlanner {

    private BootstrapTextMigrationPlanner() {}

    @Nonnull
    public static String applyReplacements(
        @Nonnull String text,
        @Nonnull String fileName,
        @Nonnull List<BootstrapTextReplacement> replacements
    ) {
        String result = text;
        for (BootstrapTextReplacement replacement : replacements) {
            BootstrapTextReplacement normalized = replacement.normalized();
            if (!normalized.isEffective()) {
                continue;
            }
            if (!supportsScope(fileName, normalized.scope())) {
                continue;
            }
            result = result.replace(normalized.oldValue(), normalized.newValue());
        }
        return result;
    }

    public static boolean supportsScope(
        @Nonnull String fileName,
        @Nonnull BootstrapTextReplacementScope scope
    ) {
        if (scope == BootstrapTextReplacementScope.ALL_TEXT_FILES) {
            return true;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".json") || lower.endsWith(".jsonl");
    }
}
