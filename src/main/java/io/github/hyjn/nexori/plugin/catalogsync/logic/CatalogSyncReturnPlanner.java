package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncRequestPayload;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncResultPayload;
import io.github.hyjn.nexori.plugin.catalogsync.CatalogSyncEntityType;

import javax.annotation.Nonnull;

public final class CatalogSyncReturnPlanner {

    @Nonnull
    public CatalogSyncReturnPlan buildAppliedReturn(
        int schemaVersion,
        @Nonnull String operationId,
        @Nonnull CatalogSyncEntityType entityType,
        @Nonnull String entityId,
        @Nonnull String targetServerId,
        @Nonnull String applyResult
    ) {
        String message = "Synced " + entityType.singularLabel() + " '" + entityId + "' to '" + targetServerId + "' (" + applyResult.toLowerCase() + ").";
        return CatalogSyncReturnPlan.sendReturnPayload(new CatalogEntitySyncResultPayload(
            schemaVersion,
            operationId,
            true,
            entityType,
            entityId,
            targetServerId,
            applyResult,
            message
        ));
    }

    @Nonnull
    public CatalogSyncReturnPlan buildRejectedReturn(
        int schemaVersion,
        @Nonnull CatalogEntitySyncRequestPayload requestPayload,
        @Nonnull String targetServerId,
        @Nonnull String reason
    ) {
        return CatalogSyncReturnPlan.sendReturnPayload(new CatalogEntitySyncResultPayload(
            schemaVersion,
            safe(requestPayload.operationId()),
            false,
            requestPayload.entityType(),
            safe(requestPayload.entityId()),
            targetServerId,
            "FAILED",
            reason
        ));
    }

    @Nonnull
    public CatalogSyncReturnPlan planReceivedReturn(
        CatalogEntitySyncResultPayload payload,
        boolean pendingRequestExists,
        boolean pendingRequestExpired,
        boolean pendingRequestPlayerMatches,
        @Nonnull String fallbackTargetServerId
    ) {
        if (payload == null || payload.operationId() == null || payload.operationId().isBlank()) {
            return CatalogSyncReturnPlan.ignoreStale("");
        }
        if (!pendingRequestExists || pendingRequestExpired || !pendingRequestPlayerMatches) {
            return CatalogSyncReturnPlan.ignoreStale(payload.operationId());
        }
        String message = payload.message() == null || payload.message().isBlank()
            ? defaultResultMessage(payload, fallbackTargetServerId)
            : payload.message();
        return CatalogSyncReturnPlan.acceptReturn(payload.operationId(), payload.success(), message);
    }

    @Nonnull
    public String defaultResultMessage(@Nonnull CatalogEntitySyncResultPayload payload, @Nonnull String fallbackTargetServerId) {
        String targetServerId = payload.targetServerId() == null || payload.targetServerId().isBlank()
            ? fallbackTargetServerId
            : payload.targetServerId();
        if (payload.success()) {
            return "Synced " + payload.entityType().singularLabel() + " '" + payload.entityId() + "' to '" + targetServerId + "'.";
        }
        return "Failed to sync " + payload.entityType().singularLabel() + " '" + payload.entityId() + "' to '" + targetServerId + "': The remote apply failed.";
    }

    @Nonnull
    private static String safe(String value) {
        return value == null || value.isBlank() ? "<blank>" : value;
    }
}
