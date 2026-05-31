package io.github.hyjn.nexori.plugin.travel;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * Test-only factory for {@link ReadyPlayerSnapshot}.
 *
 * <p>Lives in the same package as {@link ReadyPlayerSnapshot} so it can call the
 * package-private {@link ReadyPlayerSnapshot#forTest} factory without bypassing the
 * public {@code safe()} invariant checks via the canonical record constructor.</p>
 *
 * <p>This class must never be imported from production {@code main} sources.</p>
 */
public final class ReadyPlayerSnapshotTestHelper {

    private ReadyPlayerSnapshotTestHelper() {
    }

    /**
     * Creates a minimal test snapshot. Only {@code playerUuid} and {@code username} are
     * populated; all engine references ({@code playerRef}, {@code entityRef}, {@code store},
     * {@code player}, {@code world}) are {@code null}.  Safe for pure-logic tests that do not
     * touch engine APIs.
     */
    @Nonnull
    public static ReadyPlayerSnapshot forTest(@Nonnull UUID playerUuid) {
        return ReadyPlayerSnapshot.forTest(playerUuid, "player_" + playerUuid.toString().substring(0, 4));
    }

    @Nonnull
    public static ReadyPlayerSnapshot forTest(@Nonnull UUID playerUuid, @Nonnull String username) {
        return ReadyPlayerSnapshot.forTest(playerUuid, username);
    }
}
