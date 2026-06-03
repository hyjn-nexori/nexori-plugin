package io.github.hyjn.nexori.plugin.minigame.transfer;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Single-flight registry for instance-world materialization, keyed by {@code matchId}.
 *
 * <p>Only ONE {@code InstancesPlugin.spawnInstance(...)} call may run per match. The first arriving
 * player of a match triggers it; every other player of the same match joins the SAME
 * {@link CompletableFuture} instead of spawning a duplicate world for the same {@code instanceWorldName}.
 * Per-player {@code ArenaInstanceRuntime.prepareInstanceForMatch(...)} is NOT handled here — each session
 * composes its own prepare on top of the shared world future, so the world is materialized once but every
 * player still gets their own spawn-slot assignment.</p>
 *
 * <p>{@code matchId} is the coordination key because {@code instanceWorldName} is derived 1:1 from it via
 * {@code ArenaInstanceRuntime.buildInstanceWorldName(matchId)}. The {@code instanceWorldName} and
 * {@code instanceTemplateId} are still stored and validated: a second arrival for the same {@code matchId}
 * but a different world name or template is a real mismatch and is rejected (failed future + severe log),
 * never joined silently.</p>
 *
 * <p><strong>Thread safety:</strong> every method is invoked from within the synchronized context of
 * {@code ArenaMatchService} (the same lock that guards {@link MinigameTransferService}). This class does
 * not synchronize on its own and never mutates its map from an async future callback — the only async work
 * is completion logging. Map eviction always happens on-lock via {@link #evict} / {@link #evictExpired}.</p>
 */
final class InstanceMaterializationRegistry {

    /**
     * TTL after which a SUCCESSFULLY-completed materialization entry may be evicted by {@link #evictExpired}.
     * Well above the 30s instance-world creating timeout, so an in-flight spawn is never evicted; failed
     * entries are NOT subject to this TTL (they are retained until explicit {@link #evict} at match close).
     * By the TTL the world is alive and late arrivals use the {@code Universe.getWorld(...).isAlive()} fast-path.
     */
    private static final long MATERIALIZATION_ENTRY_TTL_MS = 60_000L;

    private final HytaleLogger logger;
    private final Map<String, Materialization> byMatchId = new LinkedHashMap<>();

    InstanceMaterializationRegistry(@Nonnull HytaleLogger logger) {
        this.logger = logger;
    }

    /**
     * Returns the shared materialized-world future for the match, starting it via {@code spawnFn} on the
     * first call and joining the existing future on every subsequent call (no second spawn). A real
     * mismatch (same {@code matchId} but different {@code instanceWorldName}/{@code instanceTemplateId}) is
     * rejected with a failed future and a severe log; it is not joined.
     */
    @Nonnull
    CompletableFuture<World> materializeOrJoin(
        @Nonnull String matchId,
        @Nonnull String instanceWorldName,
        @Nonnull String instanceTemplateId,
        @Nonnull UUID playerUuid,
        long nowEpochMs,
        @Nonnull Supplier<CompletableFuture<World>> spawnFn
    ) {
        Materialization existing = byMatchId.get(matchId);
        if (existing != null) {
            if (!existing.instanceWorldName.equals(instanceWorldName)
                || !existing.instanceTemplateId.equals(instanceTemplateId)) {
                logger.atSevere().log(
                    "NEXORI_MATERIALIZATION_MISMATCH matchId=" + matchId
                        + " player=" + playerUuid
                        + " existingWorld=" + existing.instanceWorldName
                        + " newWorld=" + instanceWorldName
                        + " existingTemplate=" + existing.instanceTemplateId
                        + " newTemplate=" + instanceTemplateId
                );
                return CompletableFuture.failedFuture(new IllegalStateException(
                    "Instance materialization mismatch for matchId=" + matchId));
            }
            existing.joinCount++;
            logger.atInfo().log(
                "NEXORI_MATERIALIZATION_JOINED matchId=" + matchId
                    + " instanceWorldName=" + instanceWorldName
                    + " instanceTemplateId=" + instanceTemplateId
                    + " player=" + playerUuid
                    + " joinCount=" + existing.joinCount
            );
            if (!existing.worldFuture.isDone()) {
                logger.atInfo().log(
                    "NEXORI_MATERIALIZATION_DUPLICATE_SPAWN_PREVENTED matchId=" + matchId
                        + " instanceWorldName=" + instanceWorldName
                        + " instanceTemplateId=" + instanceTemplateId
                        + " player=" + playerUuid
                        + " joinCount=" + existing.joinCount
                );
            }
            return existing.worldFuture;
        }

        CompletableFuture<World> worldFuture = spawnFn.get();
        Materialization created = new Materialization(
            matchId, instanceWorldName, instanceTemplateId, worldFuture, nowEpochMs);
        byMatchId.put(matchId, created);
        logger.atInfo().log(
            "NEXORI_MATERIALIZATION_STARTED matchId=" + matchId
                + " instanceWorldName=" + instanceWorldName
                + " instanceTemplateId=" + instanceTemplateId
                + " player=" + playerUuid
        );
        // Async completion is LOG ONLY. The map is never mutated off-lock; eviction is on-lock.
        worldFuture.whenComplete((world, throwable) -> {
            if (throwable != null) {
                logger.atWarning().withCause(throwable).log(
                    "NEXORI_MATERIALIZATION_FAILED matchId=" + matchId
                        + " instanceWorldName=" + instanceWorldName
                        + " instanceTemplateId=" + instanceTemplateId
                );
            } else {
                logger.atInfo().log(
                    "NEXORI_MATERIALIZATION_COMPLETED matchId=" + matchId
                        + " instanceWorldName=" + (world != null ? world.getName() : instanceWorldName)
                        + " instanceTemplateId=" + instanceTemplateId
                );
            }
        });
        return worldFuture;
    }

    /**
     * Explicit on-lock eviction for a closed/cancelled/completed match. No-op if the match has no entry.
     * A FAILED entry is intentionally retained until this is called, so a late arrival joins the failed
     * future (consistent failure) instead of blindly re-spawning.
     */
    void evict(@Nonnull String matchId, @Nonnull String reason) {
        Materialization removed = byMatchId.remove(matchId);
        if (removed != null) {
            logger.atInfo().log(
                "NEXORI_MATERIALIZATION_EVICTED matchId=" + matchId
                    + " instanceWorldName=" + removed.instanceWorldName
                    + " instanceTemplateId=" + removed.instanceTemplateId
                    + " reason=" + reason
                    + " joinCount=" + removed.joinCount
            );
        }
    }

    /**
     * On-lock backstop eviction: removes only SUCCESSFULLY-completed entries older than the TTL.
     *
     * <p>Three states are distinguished directly from the shared future (no extra stored flag):</p>
     * <ul>
     *   <li>CREATING / in-flight ({@code !isDone()}) — never evicted; an in-progress spawn is never interrupted.</li>
     *   <li>SUCCEEDED ({@code isDone() && !isCompletedExceptionally()}) — eligible for TTL eviction; safe because
     *       the world is alive and late arrivals use the {@code Universe.getWorld(...).isAlive()} fast-path.</li>
     *   <li>FAILED ({@code isCompletedExceptionally()}) — NOT evicted here; retained until explicit
     *       {@link #evict} at match close, so a late arrival joins the same failed future instead of
     *       blindly re-spawning.</li>
     * </ul>
     */
    void evictExpired(long nowEpochMs) {
        if (byMatchId.isEmpty()) {
            return;
        }
        List<String> toEvict = null;
        for (Materialization m : byMatchId.values()) {
            boolean succeeded = m.worldFuture.isDone() && !m.worldFuture.isCompletedExceptionally();
            // Only evict succeeded entries past the TTL. Never in-flight (would risk a duplicate spawn);
            // never failed (must be retained until explicit match-close eviction).
            if (succeeded && nowEpochMs - m.startedAtEpochMs >= MATERIALIZATION_ENTRY_TTL_MS) {
                if (toEvict == null) {
                    toEvict = new ArrayList<>();
                }
                toEvict.add(m.matchId);
            }
        }
        if (toEvict != null) {
            for (String matchId : toEvict) {
                evict(matchId, "expired_succeeded");
            }
        }
    }

    /** Read-only: whether a materialization entry exists for the match. */
    boolean hasMaterialization(@Nonnull String matchId) {
        return byMatchId.containsKey(matchId);
    }

    /** Read-only: number of tracked materializations (testing/observability). */
    int size() {
        return byMatchId.size();
    }

    private static final class Materialization {
        final String matchId;
        final String instanceWorldName;
        final String instanceTemplateId;
        final CompletableFuture<World> worldFuture;
        final long startedAtEpochMs;
        int joinCount = 0;

        Materialization(
            @Nonnull String matchId,
            @Nonnull String instanceWorldName,
            @Nonnull String instanceTemplateId,
            @Nonnull CompletableFuture<World> worldFuture,
            long startedAtEpochMs
        ) {
            this.matchId = matchId;
            this.instanceWorldName = instanceWorldName;
            this.instanceTemplateId = instanceTemplateId;
            this.worldFuture = worldFuture;
            this.startedAtEpochMs = startedAtEpochMs;
        }
    }
}
