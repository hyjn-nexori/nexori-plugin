package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nullable;

public record ItemTransferState(
    String id,
    int quantity,
    double durability,
    double maxDurability,
    boolean overrideDroppedItemAnimation,
    @Nullable String metadataJson
) {
}
