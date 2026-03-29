package io.github.hyjn.nexori.plugin.ui;

import javax.annotation.Nonnull;

public record PortalSetupDraft(
    String portalId,
    int stepIndex,
    String selectedDestinationAddress,
    String selectedTargetId,
    String selectedTravelProfileId
) {

    @Nonnull
    public PortalSetupDraft normalized() {
        return new PortalSetupDraft(
            safe(portalId).trim().toLowerCase(),
            Math.max(0, stepIndex),
            safe(selectedDestinationAddress).trim().toLowerCase(),
            safe(selectedTargetId).trim().toLowerCase(),
            safe(selectedTravelProfileId).trim().toLowerCase()
        );
    }

    @Nonnull
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
