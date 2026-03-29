package io.github.hyjn.nexori.plugin.travel;

import io.github.hyjn.nexori.plugin.inventory.InventoryTransferState;

public record SecureTravelPayload(
    String sourceServerId,
    String sourceConnectionAddress,
    String destinationTargetId,
    String arrivalPointId,
    String travelProfileId,
    String arrivalMessage,
    String contextJson,
    String inventoryTransferId,
    InventoryTransferState inventoryState
) {
}
