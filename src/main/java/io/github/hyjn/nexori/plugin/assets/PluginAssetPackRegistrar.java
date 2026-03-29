package io.github.hyjn.nexori.plugin.assets;

import com.hypixel.hytale.server.core.asset.AssetModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;

public final class PluginAssetPackRegistrar {

    private PluginAssetPackRegistrar() {
    }

    public static void registerSelfAsAssetPack(JavaPlugin plugin) {
        String packName = plugin.getIdentifier().toString() + ":assets";
        AssetModule assets = AssetModule.get();
        assets.registerPack(packName, plugin.getFile(), plugin.getManifest(), true);
        assets.initPendingStores();
    }
}
