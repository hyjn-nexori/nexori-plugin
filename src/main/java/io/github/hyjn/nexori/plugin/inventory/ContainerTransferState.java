package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nonnull;
import java.util.Map;

public record ContainerTransferState(
    @Nonnull String id,
    int capacity,
    @Nonnull Map<Integer, ItemTransferState> items
) {

    public boolean isEmpty() {
        return items.isEmpty() || "Empty".equalsIgnoreCase(id);
    }

    @Nonnull
    public static ContainerTransferState emptyLike(@Nonnull ContainerTransferState source) {
        return new ContainerTransferState(source.id(), source.capacity(), Map.of());
    }
}
