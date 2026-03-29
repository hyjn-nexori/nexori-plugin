package io.github.hyjn.nexori.plugin.ui;

import javax.annotation.Nonnull;

public record TargetSetupDraft(
    int stepIndex,
    String targetId,
    String displayName,
    String arrivalPointId,
    String selectedKindId
) {

    @Nonnull
    public TargetSetupDraft normalized() {
        return new TargetSetupDraft(
            Math.max(0, stepIndex),
            safe(targetId).trim(),
            safe(displayName).trim(),
            safe(arrivalPointId).trim(),
            safe(selectedKindId).trim().toLowerCase()
        );
    }

    @Nonnull
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
