package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;

import javax.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;

public final class NexoriOpAccess {

    private NexoriOpAccess() {
    }

    public static boolean requireOp(@Nonnull CommandContext context) {
        if (isOp(context)) {
            return true;
        }
        context.sendMessage(Message.raw("You do not have permission to run this command."));
        return false;
    }

    private static boolean isOp(@Nonnull CommandContext context) {
        PermissionsModule permissionsModule = PermissionsModule.get();
        if (permissionsModule == null || context.sender() == null) {
            return false;
        }

        UUID senderUuid = context.sender().getUuid();
        if (senderUuid == null) {
            return false;
        }

        Set<String> groups = permissionsModule.getGroupsForUser(senderUuid);
        for (String group : groups) {
            if (group != null && "OP".equalsIgnoreCase(group.trim())) {
                return true;
            }
        }
        return false;
    }

}
