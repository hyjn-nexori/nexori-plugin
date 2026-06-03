package io.github.hyjn.nexori.plugin.minigame.transfer;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure single-flight tests for {@link InstanceMaterializationRegistry}.
 *
 * <p>These cover the registry contract directly with a {@code Supplier<CompletableFuture<World>>} stand-in
 * for {@code InstancesPlugin.spawnInstance(...)}. The service-layer "world already alive" fast-path (case F)
 * lives in {@code MinigameTransferService.onMinigameLaunchSafeReady} and is intentionally NOT exercised here
 * because it bypasses the registry entirely (it never calls {@code materializeOrJoin}); a service-level
 * integration test that mocks {@code Universe}/{@code InstancesPlugin} remains pending.</p>
 */
final class InstanceMaterializationRegistryTest {

    private static final String MATCH = "match-1";
    private static final String WORLD = "nexori-match-match-1";
    private static final String TEMPLATE = "tmpl-1";

    private static UUID uuid(int n) {
        // Last UUID group is exactly 12 hex digits; pad so n up to 100+ stays a valid, distinct UUID.
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", n));
    }

    private static InstanceMaterializationRegistry newRegistry() {
        return new InstanceMaterializationRegistry(HytaleLogger.getLogger());
    }

    // A. Single-flight: spawnFn runs exactly once; every caller gets the same future.
    @Test
    void singleFlightCallsSpawnOnceAndSharesFuture() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        CompletableFuture<World> shared = new CompletableFuture<>();
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return shared;
        };

        CompletableFuture<World> f1 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        CompletableFuture<World> f2 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(2), 1L, spawnFn);
        CompletableFuture<World> f3 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(3), 2L, spawnFn);

        assertEquals(1, spawnCalls.get(), "spawnInstance must run exactly once per match");
        assertSame(f1, f2, "second player must join the same world future");
        assertSame(f1, f3, "third player must join the same world future");
        assertEquals(1, registry.size());
    }

    // E. 100 initial players, same match, future still pending -> one spawn, 99 joins.
    @Test
    void hundredPlayersOneSpawnNinetyNineJoins() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        CompletableFuture<World> shared = new CompletableFuture<>();
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return shared;
        };

        CompletableFuture<World> first = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        for (int i = 2; i <= 100; i++) {
            CompletableFuture<World> joined = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(i), i, spawnFn);
            assertSame(first, joined);
        }

        assertEquals(1, spawnCalls.get(), "only one spawnInstance for 100 players of the same match");
        assertEquals(1, registry.size());
    }

    // B. Mismatch on instanceWorldName for the same matchId -> rejected (failed future), not joined.
    @Test
    void mismatchWorldNameIsRejectedNotJoined() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        CompletableFuture<World> shared = new CompletableFuture<>();
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return shared;
        };

        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        CompletableFuture<World> mismatch =
            registry.materializeOrJoin(MATCH, "different-world", TEMPLATE, uuid(2), 1L, spawnFn);

        assertTrue(mismatch.isCompletedExceptionally(), "world-name mismatch must yield a failed future");
        assertEquals(1, spawnCalls.get(), "mismatch must not trigger a second spawn");
    }

    // B. Mismatch on instanceTemplateId for the same matchId -> rejected (failed future), not joined.
    @Test
    void mismatchTemplateIsRejectedNotJoined() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        CompletableFuture<World> shared = new CompletableFuture<>();
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return shared;
        };

        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        CompletableFuture<World> mismatch =
            registry.materializeOrJoin(MATCH, WORLD, "different-template", uuid(2), 1L, spawnFn);

        assertTrue(mismatch.isCompletedExceptionally(), "template mismatch must yield a failed future");
        assertEquals(1, spawnCalls.get(), "mismatch must not trigger a second spawn");
    }

    // C. Failure retained: a failed first spawn is reused by later arrivals; no blind re-spawn.
    @Test
    void failureRetainedNoRespawn() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        CompletableFuture<World> shared = new CompletableFuture<>();
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return shared;
        };

        CompletableFuture<World> f1 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        shared.completeExceptionally(new RuntimeException("spawn failed"));

        CompletableFuture<World> f2 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(2), 1L, spawnFn);

        assertEquals(1, spawnCalls.get(), "a failed materialization must not be retried by a later arrival");
        assertSame(f1, f2, "later arrival must receive the same (failed) future");
        assertTrue(f2.isCompletedExceptionally());
    }

    // Evict removes the entry, allowing a genuinely fresh attempt later.
    @Test
    void evictRemovesEntry() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return new CompletableFuture<>();
        };

        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        assertTrue(registry.hasMaterialization(MATCH));

        registry.evict(MATCH, "match_closed");
        assertFalse(registry.hasMaterialization(MATCH));
        assertEquals(0, registry.size());

        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(2), 1L, spawnFn);
        assertEquals(2, spawnCalls.get(), "after eviction a new arrival may spawn a fresh world");
    }

    // 1. evictExpired must never drop an in-flight future, and DOES drop succeeded ones past the TTL.
    @Test
    void evictExpiredOnlyEvictsSucceededEntriesPastTtl() {
        InstanceMaterializationRegistry registry = newRegistry();
        CompletableFuture<World> shared = new CompletableFuture<>();
        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, () -> shared);

        // Long past the TTL but the future is still in flight -> must NOT be evicted.
        registry.evictExpired(10_000_000L);
        assertTrue(registry.hasMaterialization(MATCH), "in-flight materialization must never be evicted");

        // Once it succeeds, the same expired sweep removes it.
        shared.complete(null);
        registry.evictExpired(10_000_000L);
        assertFalse(registry.hasMaterialization(MATCH), "succeeded entry past TTL is evicted");
    }

    // A young succeeded entry is not evicted (TTL not reached yet).
    @Test
    void evictExpiredKeepsYoungSucceededEntry() {
        InstanceMaterializationRegistry registry = newRegistry();
        CompletableFuture<World> shared = new CompletableFuture<>();
        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, () -> shared);
        shared.complete(null);

        registry.evictExpired(1_000L); // well under the 60s TTL
        assertTrue(registry.hasMaterialization(MATCH), "young succeeded entry must be retained");
    }

    // 2. evictExpired must NOT evict a FAILED entry even long past the TTL.
    @Test
    void evictExpiredDoesNotEvictFailedEntry() {
        InstanceMaterializationRegistry registry = newRegistry();
        CompletableFuture<World> shared = new CompletableFuture<>();
        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, () -> shared);
        shared.completeExceptionally(new RuntimeException("spawn failed"));

        registry.evictExpired(10_000_000L); // far past TTL
        assertTrue(registry.hasMaterialization(MATCH), "failed entry must be retained until explicit eviction");
    }

    // 3. After a failure AND past the TTL, the same matchId must NOT re-spawn; it joins the failed future.
    @Test
    void failedEntryPastTtlStillJoinsAndDoesNotRespawn() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        CompletableFuture<World> shared = new CompletableFuture<>();
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            return shared;
        };

        CompletableFuture<World> f1 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        shared.completeExceptionally(new RuntimeException("spawn failed"));

        // TTL sweep runs but must not remove the failed entry.
        registry.evictExpired(10_000_000L);

        CompletableFuture<World> f2 = registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(2), 10_000_001L, spawnFn);

        assertEquals(1, spawnCalls.get(), "a failed materialization must never be retried, even past the TTL");
        assertSame(f1, f2, "late arrival joins the same failed future");
        assertTrue(f2.isCompletedExceptionally());
    }

    // 4 & 5. Explicit evict removes a failed entry; afterwards a fresh materializeOrJoin may spawn again.
    @Test
    void explicitEvictRemovesFailedEntryThenSpawnMayRunAgain() {
        InstanceMaterializationRegistry registry = newRegistry();
        AtomicInteger spawnCalls = new AtomicInteger(0);
        Supplier<CompletableFuture<World>> spawnFn = () -> {
            spawnCalls.incrementAndGet();
            CompletableFuture<World> f = new CompletableFuture<>();
            f.completeExceptionally(new RuntimeException("spawn failed"));
            return f;
        };

        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(1), 0L, spawnFn);
        assertTrue(registry.hasMaterialization(MATCH));

        registry.evict(MATCH, "match_closed"); // explicit cleanup removes even a failed entry
        assertFalse(registry.hasMaterialization(MATCH));

        registry.materializeOrJoin(MATCH, WORLD, TEMPLATE, uuid(2), 1L, spawnFn);
        assertEquals(2, spawnCalls.get(), "after explicit eviction a brand-new attempt may spawn again");
    }
}
