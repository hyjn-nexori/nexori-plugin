package io.github.hyjn.nexori.plugin.inventory.logic;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferQueryResult;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptReplyPayload;

import javax.annotation.Nonnull;

public record InventoryReceiptQueryPlan(
    @Nonnull InventoryTransferQueryResult result,
    @Nonnull InventoryTransferReceiptReplyPayload replyPayload
) {
}
