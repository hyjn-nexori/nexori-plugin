package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;

public record CatalogSyncRequestBuildInput(
    int schemaVersion,
    String operationId,
    String sourceServerId,
    String targetServerId,
    CatalogSyncEntityType entityType,
    long sentAtEpochMs,
    ArenaDefinition arena,
    QueueDefinition queue
) {
}
