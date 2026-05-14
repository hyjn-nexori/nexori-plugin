package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncRequestPayload;
import io.github.hyjn.nexori.plugin.minigame.ArenaDefinition;
import io.github.hyjn.nexori.plugin.minigame.QueueDefinition;

import javax.annotation.Nonnull;

public final class CatalogSyncApplyPlanner {

    private final CatalogSyncRequestBuilder requestBuilder = new CatalogSyncRequestBuilder();

    @Nonnull
    public CatalogSyncApplyPlan plan(
        @Nonnull CatalogEntitySyncRequestPayload payload,
        @Nonnull String trustedSourceServerId,
        @Nonnull String expectedTargetServerId,
        int expectedSchemaVersion
    ) {
        if (payload.schemaVersion() != expectedSchemaVersion) {
            return reject(payload, "Unsupported catalog sync schemaVersion: " + payload.schemaVersion() + ".");
        }
        if (payload.operationId() == null || payload.operationId().isBlank()) {
            return reject(payload, "Catalog sync operationId cannot be blank.");
        }
        if (payload.sourceServerId() == null || payload.sourceServerId().isBlank()) {
            return reject(payload, "Catalog sync sourceServerId cannot be blank.");
        }
        if (!trustedSourceServerId.equals(payload.sourceServerId())) {
            return reject(payload, "Catalog sync sourceServerId does not match the trusted sender.");
        }
        if (payload.targetServerId() == null || payload.targetServerId().isBlank()) {
            return reject(payload, "Catalog sync targetServerId cannot be blank.");
        }
        if (!expectedTargetServerId.equals(payload.targetServerId())) {
            return reject(payload, "Catalog sync targetServerId does not match this server.");
        }
        if (payload.entityType() == null) {
            return reject(payload, "Catalog sync entityType cannot be blank.");
        }
        if (payload.entityId() == null || payload.entityId().isBlank()) {
            return reject(payload, "Catalog sync entityId cannot be blank.");
        }
        if (payload.entityHash() == null || payload.entityHash().isBlank()) {
            return reject(payload, "Catalog sync entityHash cannot be blank.");
        }

        return switch (payload.entityType()) {
            case GAME -> planGame(payload);
            case QUEUE -> planQueue(payload);
        };
    }

    @Nonnull
    private CatalogSyncApplyPlan planGame(@Nonnull CatalogEntitySyncRequestPayload payload) {
        if (payload.arena() == null) {
            return reject(payload, "Catalog sync game payload cannot be blank.");
        }
        if (payload.queue() != null) {
            return reject(payload, "Catalog sync game payload cannot include queue data.");
        }
        ArenaDefinition normalized = payload.arena().normalized();
        if (!normalized.arenaId().equals(payload.entityId())) {
            return reject(payload, "Catalog sync entityId does not match the game payload.");
        }
        String computedHash = requestBuilder.hashArena(normalized);
        if (!computedHash.equals(payload.entityHash())) {
            return reject(payload, "Catalog sync game hash validation failed.");
        }
        return CatalogSyncApplyPlan.applyGame(normalized);
    }

    @Nonnull
    private CatalogSyncApplyPlan planQueue(@Nonnull CatalogEntitySyncRequestPayload payload) {
        if (payload.queue() == null) {
            return reject(payload, "Catalog sync queue payload cannot be blank.");
        }
        if (payload.arena() != null) {
            return reject(payload, "Catalog sync queue payload cannot include game data.");
        }
        QueueDefinition normalized = payload.queue().normalized();
        if (!normalized.queueId().equals(payload.entityId())) {
            return reject(payload, "Catalog sync entityId does not match the queue payload.");
        }
        String computedHash = requestBuilder.hashQueue(normalized);
        if (!computedHash.equals(payload.entityHash())) {
            return reject(payload, "Catalog sync queue hash validation failed.");
        }
        return CatalogSyncApplyPlan.applyQueue(normalized);
    }

    @Nonnull
    private static CatalogSyncApplyPlan reject(
        @Nonnull CatalogEntitySyncRequestPayload payload,
        @Nonnull String reason
    ) {
        return CatalogSyncApplyPlan.reject(payload.entityType(), payload.entityId(), reason);
    }
}
