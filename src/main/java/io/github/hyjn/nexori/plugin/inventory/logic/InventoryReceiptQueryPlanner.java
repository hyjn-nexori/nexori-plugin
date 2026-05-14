package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferQueryResult;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptQueryPayload;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptRecord;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptReplyPayload;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class InventoryReceiptQueryPlanner {

    @Nonnull
    public InventoryReceiptQueryPlan plan(
        @Nonnull InventoryTransferReceiptQueryPayload query,
        @Nullable InventoryTransferReceiptRecord receipt
    ) {
        InventoryTransferQueryResult result = receipt == null
            ? InventoryTransferQueryResult.NOT_FOUND
            : InventoryTransferQueryResult.APPLIED;
        return new InventoryReceiptQueryPlan(
            result,
            new InventoryTransferReceiptReplyPayload(query.transferId(), result.name())
        );
    }
}
