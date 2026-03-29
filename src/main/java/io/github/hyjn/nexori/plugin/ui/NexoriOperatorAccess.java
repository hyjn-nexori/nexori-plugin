package io.github.hyjn.nexori.plugin.ui;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import io.github.hyjn.nexori.plugin.access.NexoriAdminAccess;

import javax.annotation.Nonnull;

final class NexoriOperatorAccess {

    private NexoriOperatorAccess() {
    }

    static boolean canManage(@Nonnull PlayerRef playerRef, Player player, @Nonnull String adminPermission) {
        return NexoriAdminAccess.canManage(playerRef, player, adminPermission);
    }
}
