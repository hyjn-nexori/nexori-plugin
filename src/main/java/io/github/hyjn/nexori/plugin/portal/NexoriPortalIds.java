package io.github.hyjn.nexori.plugin.portal;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;

import javax.annotation.Nonnull;
import java.util.Locale;

public final class NexoriPortalIds {

    public static final String ITEM_ID = "Nexori_Portal";
    public static final String ADMIN_PAGE_ID = "NexoriPortalAdmin";
    public static final String TRAVERSE_PAGE_ID = "NexoriPortalTraverse";

    private NexoriPortalIds() {
    }

    public static boolean matchesItemId(String itemId) {
        return normalizeAssetId(itemId).endsWith("nexori_portal");
    }

    public static boolean matchesBlockType(BlockType blockType) {
        return blockType != null && matchesAssetId(blockType.getId());
    }

    public static boolean matchesAssetId(Object assetId) {
        return normalizeAssetId(assetId == null ? "" : assetId.toString()).endsWith("nexori_portal");
    }

    @Nonnull
    private static String normalizeAssetId(String rawValue) {
        return rawValue == null ? "" : rawValue.trim().toLowerCase(Locale.ROOT);
    }
}
