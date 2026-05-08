package io.github.hyjn.nexori.plugin.catalogsync;

import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;

public record CatalogEntitySyncRequestPayload(
    int schemaVersion,
    String operationId,
    String sourceServerId,
    String targetServerId,
    CatalogSyncEntityType entityType,
    String entityId,
    String entityHash,
    long sentAtEpochMs,
    ArenaDefinition arena,
    QueueDefinition queue
) {
}
