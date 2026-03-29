package io.github.hyjn.nexori.plugin.ui;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PortalSetupDraftService {

    private final Map<String, PortalSetupDraft> draftsByKey = new LinkedHashMap<>();

    @Nonnull
    public synchronized Optional<PortalSetupDraft> find(@Nonnull UUID playerUuid, @Nonnull String portalId) {
        return Optional.ofNullable(draftsByKey.get(key(playerUuid, portalId)));
    }

    @Nonnull
    public synchronized PortalSetupDraft save(@Nonnull UUID playerUuid, @Nonnull PortalSetupDraft draft) {
        PortalSetupDraft normalized = draft.normalized();
        if (normalized.portalId().isBlank()) {
            return normalized;
        }
        draftsByKey.put(key(playerUuid, normalized.portalId()), normalized);
        return normalized;
    }

    public synchronized void clear(@Nonnull UUID playerUuid, @Nonnull String portalId) {
        draftsByKey.remove(key(playerUuid, portalId));
    }

    @Nonnull
    private String key(@Nonnull UUID playerUuid, @Nonnull String portalId) {
        return playerUuid + ":" + portalId.trim().toLowerCase();
    }
}
