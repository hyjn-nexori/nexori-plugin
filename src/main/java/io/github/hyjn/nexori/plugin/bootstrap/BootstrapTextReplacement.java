package io.github.hyjn.nexori.plugin.bootstrap;

import javax.annotation.Nonnull;

public record BootstrapTextReplacement(
    String oldValue,
    String newValue,
    BootstrapTextReplacementScope scope
) {

    @Nonnull
    public BootstrapTextReplacement normalized() {
        return new BootstrapTextReplacement(
            normalize(oldValue),
            normalize(newValue),
            scope == null ? BootstrapTextReplacementScope.ALL_TEXT_FILES : scope
        );
    }

    public boolean isEffective() {
        return !oldValue.isBlank() && !newValue.isBlank() && !oldValue.equals(newValue);
    }

    @Nonnull
    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
