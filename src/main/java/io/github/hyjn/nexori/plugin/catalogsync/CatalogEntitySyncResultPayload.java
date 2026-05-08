package io.github.hyjn.nexori.plugin.catalogsync;

public record CatalogEntitySyncResultPayload(
    int schemaVersion,
    String operationId,
    boolean success,
    CatalogSyncEntityType entityType,
    String entityId,
    String targetServerId,
    String applyResult,
    String message
) {
}
