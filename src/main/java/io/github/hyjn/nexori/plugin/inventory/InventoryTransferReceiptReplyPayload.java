package io.github.hyjn.nexori.plugin.inventory;

public record InventoryTransferReceiptReplyPayload(
    String transferId,
    String result
) {
}
