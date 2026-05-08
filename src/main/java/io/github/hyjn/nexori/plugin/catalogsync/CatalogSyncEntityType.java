package io.github.hyjn.nexori.plugin.catalogsync;

import javax.annotation.Nonnull;
import java.util.Locale;
import java.util.Optional;

public enum CatalogSyncEntityType {
    GAME("GAMES", "game"),
    QUEUE("QUEUES", "queue");

    private final String label;
    private final String singularLabel;

    CatalogSyncEntityType(@Nonnull String label, @Nonnull String singularLabel) {
        this.label = label;
        this.singularLabel = singularLabel;
    }

    @Nonnull
    public String label() {
        return label;
    }

    @Nonnull
    public String singularLabel() {
        return singularLabel;
    }

    @Nonnull
    public static Optional<CatalogSyncEntityType> tryParse(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return Optional.empty();
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        for (CatalogSyncEntityType value : values()) {
            if (value.name().equals(normalized)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
