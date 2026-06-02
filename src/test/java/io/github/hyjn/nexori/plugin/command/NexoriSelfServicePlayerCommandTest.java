package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.junit.jupiter.api.Test;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that Nexori self-service player commands suppress the command framework's automatic
 * permission-node generation, while a plain player command still generates one. The auto-generated
 * node ({@code <basePermission>.<commandName>}) is what was blocking normal players from running the
 * public recovery commands.
 */
final class NexoriSelfServicePlayerCommandTest {

    private static boolean canGeneratePermission(@Nonnull AbstractPlayerCommand command) throws ReflectiveOperationException {
        Class<?> type = command.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("canGeneratePermission");
                method.setAccessible(true);
                return (boolean) method.invoke(command);
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchMethodException("canGeneratePermission not found in command hierarchy");
    }

    @Test
    void selfServiceCommandDoesNotGeneratePermissionNode() throws ReflectiveOperationException {
        NexoriSelfServicePlayerCommand command = new NexoriSelfServicePlayerCommand("nexoritest-selfservice", "test") {
            @Override
            protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                   PlayerRef playerRef, World world) {
            }
        };
        assertFalse(canGeneratePermission(command));
    }

    @Test
    void plainPlayerCommandGeneratesPermissionNodeByDefault() throws ReflectiveOperationException {
        AbstractPlayerCommand command = new AbstractPlayerCommand("nexoritest-default", "test") {
            @Override
            protected void execute(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref,
                                   PlayerRef playerRef, World world) {
            }
        };
        assertTrue(canGeneratePermission(command));
    }
}
