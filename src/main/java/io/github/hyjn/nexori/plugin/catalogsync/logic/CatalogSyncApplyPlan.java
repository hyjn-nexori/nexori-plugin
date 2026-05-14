package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;

import javax.annotation.Nonnull;

public record CatalogSyncApplyPlan(
    Action action,
    CatalogSyncEntityType entityType,
    String entityId,
    String reason,
    ArenaDefinition arena,
    QueueDefinition queue
) {

    public enum Action {
        APPLY_GAME,
        APPLY_QUEUE,
        REJECT
    }

    @Nonnull
    public static CatalogSyncApplyPlan applyGame(@Nonnull ArenaDefinition arena) {
        return new CatalogSyncApplyPlan(
            Action.APPLY_GAME,
            CatalogSyncEntityType.GAME,
            arena.arenaId(),
            "",
            arena,
            null
        );
    }

    @Nonnull
    public static CatalogSyncApplyPlan applyQueue(@Nonnull QueueDefinition queue) {
        return new CatalogSyncApplyPlan(
            Action.APPLY_QUEUE,
            CatalogSyncEntityType.QUEUE,
            queue.queueId(),
            "",
            null,
            queue
        );
    }

    @Nonnull
    public static CatalogSyncApplyPlan reject(
        CatalogSyncEntityType entityType,
        String entityId,
        @Nonnull String reason
    ) {
        return new CatalogSyncApplyPlan(
            Action.REJECT,
            entityType,
            entityId == null ? "" : entityId,
            reason,
            null,
            null
        );
    }

    public boolean shouldApply() {
        return action == Action.APPLY_GAME || action == Action.APPLY_QUEUE;
    }
}
