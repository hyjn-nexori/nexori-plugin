package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;

final class NexoriOperatorAccess {

    private NexoriOperatorAccess() {
    }

    static boolean canManage(@Nonnull PlayerRef playerRef, Player player, @Nonnull String adminPermission) {
        if (player != null && (player.hasPermission("*") || player.hasPermission(adminPermission))) {
            return true;
        }

        PermissionsModule permissionsModule = PermissionsModule.get();
        return permissionsModule != null && permissionsModule.getGroupsForUser(playerRef.getUuid()).contains("OP");
    }
}
