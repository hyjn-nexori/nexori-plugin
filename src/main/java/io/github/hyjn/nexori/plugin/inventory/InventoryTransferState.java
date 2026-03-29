package io.github.hyjn.nexori.plugin.inventory;

import javax.annotation.Nonnull;

public record InventoryTransferState(
    int version,
    @Nonnull ContainerTransferState storage,
    @Nonnull ContainerTransferState armor,
    @Nonnull ContainerTransferState hotBar,
    @Nonnull ContainerTransferState utility,
    @Nonnull ContainerTransferState backpack,
    @Nonnull ContainerTransferState tool,
    int activeHotbarSlot,
    int activeToolsSlot,
    int activeUtilitySlot
) {

    public boolean isEmpty() {
        return storage.isEmpty()
            && armor.isEmpty()
            && hotBar.isEmpty()
            && utility.isEmpty()
            && backpack.isEmpty()
            && tool.isEmpty();
    }

    @Nonnull
    public static InventoryTransferState emptyLike(@Nonnull InventoryTransferState source) {
        return new InventoryTransferState(
            source.version(),
            ContainerTransferState.emptyLike(source.storage()),
            ContainerTransferState.emptyLike(source.armor()),
            ContainerTransferState.emptyLike(source.hotBar()),
            ContainerTransferState.emptyLike(source.utility()),
            ContainerTransferState.emptyLike(source.backpack()),
            ContainerTransferState.emptyLike(source.tool()),
            source.activeHotbarSlot(),
            source.activeToolsSlot(),
            source.activeUtilitySlot()
        );
    }
}
