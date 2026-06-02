package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;

import javax.annotation.Nonnull;

/**
 * Base class for Nexori self-service player commands that any connected player may run.
 *
 * <p>By default the command framework auto-generates a permission node for a command during
 * registration (in {@code AbstractCommand#setOwner}: when no explicit permission was set and
 * {@link #canGeneratePermission()} returns {@code true}, it assigns
 * {@code <basePermission>.<commandName>}, e.g. {@code nexori.nexoriplugin.nexorirecovery}). That node
 * would block normal players even though these commands are intentionally public.</p>
 *
 * <p>Overriding {@link #canGeneratePermission()} to return {@code false} leaves the command's
 * permission unset, so it requires no permission node, no OP group, and no admin permission. These
 * commands must therefore enforce their own functional gating (recovery enabled, and acting only on
 * the caller's own data — ownership is validated by {@code InventoryTransferService} using the
 * caller's UUID).</p>
 */
public abstract class NexoriSelfServicePlayerCommand extends AbstractPlayerCommand {

    protected NexoriSelfServicePlayerCommand(@Nonnull String name, @Nonnull String description) {
        super(name, description);
    }

    @Override
    protected boolean canGeneratePermission() {
        return false;
    }
}
