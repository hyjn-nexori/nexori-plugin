package io.github.hyjn.nexori.plugin.ui;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TargetSetupDraftService {

    private final Map<UUID, TargetSetupDraft> draftsByPlayer = new LinkedHashMap<>();

    @Nonnull
    public synchronized Optional<TargetSetupDraft> find(@Nonnull UUID playerUuid) {
        return Optional.ofNullable(draftsByPlayer.get(playerUuid));
    }

    @Nonnull
    public synchronized TargetSetupDraft save(@Nonnull UUID playerUuid, @Nonnull TargetSetupDraft draft) {
        TargetSetupDraft normalized = draft.normalized();
        draftsByPlayer.put(playerUuid, normalized);
        return normalized;
    }

    public synchronized void clear(@Nonnull UUID playerUuid) {
        draftsByPlayer.remove(playerUuid);
    }
}
