package io.github.hyjn.nexori.plugin.catalogsync.logic;

import io.github.hyjn.nexori.plugin.catalogsync.CatalogEntitySyncResultPayload;

import javax.annotation.Nonnull;

public record CatalogSyncReturnPlan(
    Action action,
    String operationId,
    boolean success,
    String message,
    CatalogEntitySyncResultPayload resultPayload
) {

    public enum Action {
        ACCEPT_RETURN,
        IGNORE_STALE,
        SEND_RETURN_PAYLOAD
    }

    @Nonnull
    public static CatalogSyncReturnPlan acceptReturn(
        @Nonnull String operationId,
        boolean success,
        @Nonnull String message
    ) {
        return new CatalogSyncReturnPlan(Action.ACCEPT_RETURN, operationId, success, message, null);
    }

    @Nonnull
    public static CatalogSyncReturnPlan ignoreStale(String operationId) {
        return new CatalogSyncReturnPlan(Action.IGNORE_STALE, operationId == null ? "" : operationId, false, "", null);
    }

    @Nonnull
    public static CatalogSyncReturnPlan sendReturnPayload(@Nonnull CatalogEntitySyncResultPayload resultPayload) {
        return new CatalogSyncReturnPlan(
            Action.SEND_RETURN_PAYLOAD,
            resultPayload.operationId(),
            resultPayload.success(),
            resultPayload.message(),
            resultPayload
        );
    }

    public boolean shouldRecordPendingReturn() {
        return action == Action.ACCEPT_RETURN;
    }

    public boolean shouldSendReturnPayload() {
        return action == Action.SEND_RETURN_PAYLOAD;
    }
}
