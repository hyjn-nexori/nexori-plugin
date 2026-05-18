package io.github.hyjn.nexori.plugin.minigame.spectator;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public record SpectatorRuntimeResult(
    @Nonnull UUID playerUuid,
    @Nonnull List<String> applied,
    @Nonnull List<String> skipped,
    @Nonnull List<String> warnings,
    @Nonnull List<String> errors
) {
    public static SpectatorRuntimeResult success(@Nonnull UUID playerUuid) {
        return new SpectatorRuntimeResult(playerUuid, List.of(), List.of(), List.of(), List.of());
    }

    public boolean succeeded() {
        return errors.isEmpty();
    }

    @Nonnull
    public String summary() {
        StringBuilder builder = new StringBuilder();
        builder.append(succeeded() ? "Spectator runtime completed." : "Spectator runtime completed with errors.");
        appendList(builder, " Applied: ", applied);
        appendList(builder, " Skipped: ", skipped);
        appendList(builder, " Warnings: ", warnings);
        appendList(builder, " Errors: ", errors);
        return builder.toString();
    }

    private static void appendList(StringBuilder builder, String label, List<String> values) {
        if (!values.isEmpty()) {
            builder.append(label).append(String.join("; ", values)).append(".");
        }
    }
}
