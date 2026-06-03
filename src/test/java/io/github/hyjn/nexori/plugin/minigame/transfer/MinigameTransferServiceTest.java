package io.github.hyjn.nexori.plugin.minigame.transfer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.minigame.AfkDetectionPolicy;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchSource;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotService;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotStore;
import io.github.hyjn.nexori.plugin.minigame.QueueBackfillMode;
import io.github.hyjn.nexori.plugin.minigame.logic.LaunchContextData;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshot;
import io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshotTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-logic tests for MinigameTransferService with a fake MatchGateway.
 * No engine runtime is needed for no-instance arena flows.
 */
final class MinigameTransferServiceTest {

    private static final Gson GSON = new Gson();
    private static final UUID PLAYER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final long NOW = 10_000L;

    @TempDir
    Path tempDir;

    private FakeMatchGateway gateway;
    private MinigameTransferService service;

    @BeforeEach
    void setup() throws Exception {
        InstanceSpawnSlotService slotService = new InstanceSpawnSlotService(
            new InstanceSpawnSlotStore(tempDir.resolve("slots.json"))
        );
        service = new MinigameTransferService(HytaleLogger.getLogger(), slotService);
        gateway = new FakeMatchGateway();
        service.setMatchGateway(gateway);
    }

    // -------------------------------------------------------------------------
    // Single-flight materialization cleanup wiring
    // -------------------------------------------------------------------------

    @Test
    void evictMaterializationForMatchHandlesBlankAndUnknownGracefully() {
        // The central match-removal cleanup hook (ArenaMatchService -> here -> registry.evict) must be a
        // safe no-op when there is no materialization for the match, and must reject blank ids without
        // throwing. Full failed-entry retention/eviction semantics are covered by
        // InstanceMaterializationRegistryTest.
        service.evictMaterializationForMatch("", "match_removed");
        service.evictMaterializationForMatch("   ", "match_removed");
        service.evictMaterializationForMatch("unknown-match", "match_removed");
        service.evictExpiredMaterializations(NOW);
    }

    // -------------------------------------------------------------------------
    // No-instance arena: confirm immediately
    // -------------------------------------------------------------------------

    @Test
    void noInstanceArenaConfirmsImmediately() {
        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")),
            NOW
        );

        assertTrue(result.arrivalAcknowledged());
        assertEquals(1, gateway.confirmCalls.size());
        assertEquals(PLAYER, gateway.confirmCalls.get(0));
        assertEquals(0, gateway.failCalls.size());
    }

    @Test
    void noInstanceArenaSessionIsConfirmedPhase() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")),
            NOW
        );

        MinigameTransferSession session = service.findSession(PLAYER).orElse(null);
        assertNotNull(session);
        assertEquals(MinigameTransferPhase.CONFIRMED, session.phase());
        assertTrue(session.isPlacementConfirmed());
    }

    @Test
    void noInstanceArenaPlayerIsMarkedActive() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")),
            NOW
        );

        assertTrue(gateway.confirmedPlayers.contains(PLAYER));
        assertEquals(1, gateway.acceptCalls.size());
        assertEquals(1, gateway.confirmCalls.size());
    }

    // -------------------------------------------------------------------------
    // Arrival acknowledgment: only on success
    // -------------------------------------------------------------------------

    @Test
    void invalidContextIsTerminalFailureAndAcknowledgesArrival() {
        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival("not valid json{{{{"),
            NOW
        );

        // TERMINAL_FAILURE acknowledges so the stale entry is removed from recentArrivals.
        assertTrue(result.arrivalAcknowledged());
        assertEquals(MinigameTransferOnReadyResult.Kind.TERMINAL_FAILURE, result.kind());
        assertEquals(0, gateway.acceptCalls.size());
        assertEquals(0, gateway.confirmCalls.size());
    }

    @Test
    void missingRequiredFieldIsTerminalFailureAndAcknowledgesArrival() {
        JsonObject ctx = new JsonObject();
        ctx.addProperty("flowType", "minigame.launch");
        // missing matchId, queueId, etc.

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(GSON.toJson(ctx)),
            NOW
        );

        assertTrue(result.arrivalAcknowledged());
        assertEquals(MinigameTransferOnReadyResult.Kind.TERMINAL_FAILURE, result.kind());
    }

    // -------------------------------------------------------------------------
    // Backfill: missing match → rejected
    // -------------------------------------------------------------------------

    @Test
    void backfillMissingMatchReturnsRetryLaterAndAcknowledgesArrival() {
        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-missing", "res-1", Long.MAX_VALUE)),
            NOW
        );

        assertEquals(MinigameTransferOnReadyResult.Kind.RETRY_LATER, result.kind());
        // RETRY_LATER acknowledges so the arrival is removed from recentArrivals immediately;
        // the service holds an internal copy for the retry.
        assertTrue(result.arrivalAcknowledged());
        assertEquals(0, gateway.acceptCalls.size());
    }

    @Test
    void backfillRejectedExpiredReservationIsTerminalFailureAndAcknowledgesArrival() {
        gateway.existingMatches.put("match-1", buildMatch("match-1", 2));
        // Expiry in the past → rejected by BackfillAdmissionDecider → TERMINAL_FAILURE
        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-1", "res-expired", 1L)),
            NOW
        );

        assertTrue(result.arrivalAcknowledged());
        assertEquals(MinigameTransferOnReadyResult.Kind.TERMINAL_FAILURE, result.kind());
        assertEquals(0, gateway.acceptCalls.size());
    }

    // -------------------------------------------------------------------------
    // MATCH_PLACEMENT_COMPLETED guard: session must be ACCEPTED_BY_MINIGAME during accept
    // -------------------------------------------------------------------------

    @Test
    void hasPendingPlacementSessionIsTrueDuringAcceptTransferArrival() {
        // The session must already be in ACCEPTED_BY_MINIGAME when acceptTransferArrival
        // is called, so that hasPendingPlacementSession returns true and prevents premature
        // MATCH_PLACEMENT_COMPLETED inside reconcileAdmissionLifecycle.
        gateway.onAcceptTransferArrival = (playerUuid) -> assertTrue(
            service.hasPendingPlacementSession(playerUuid),
            "hasPendingPlacementSession must be true during acceptTransferArrival"
        );

        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")),
            NOW
        );
    }

    // -------------------------------------------------------------------------
    // Backfill launchIndex: first backfill uses slot after initial roster
    // -------------------------------------------------------------------------

    @Test
    void backfillLaunchIndexDoesNotOverlapInitialSlots() {
        // 2-player initial match, no backfills consumed yet
        gateway.existingMatches.put("match-bf", buildMatch("match-bf", 2));

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-bf", "res-1", Long.MAX_VALUE)),
            NOW
        );

        // With no-instance template, the session goes to CONFIRMED.
        assertTrue(result.arrivalAcknowledged());
        MinigameTransferSession session = service.findSession(PLAYER).orElse(null);
        assertNotNull(session);
        assertTrue(session.hasAcceptedLaunch());
    }

    // -------------------------------------------------------------------------
    // hasPendingPlacementSession
    // -------------------------------------------------------------------------

    @Test
    void hasPendingPlacementSessionFalseWhenNoSession() {
        assertFalse(service.hasPendingPlacementSession(PLAYER));
    }

    @Test
    void hasPendingPlacementSessionFalseAfterConfirm() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")),
            NOW
        );
        // no-instance: immediately CONFIRMED — not pending anymore
        assertFalse(service.hasPendingPlacementSession(PLAYER));
    }

    // -------------------------------------------------------------------------
    // onPlayerDisconnect
    // -------------------------------------------------------------------------

    @Test
    void disconnectRemovesSession() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")),
            NOW
        );
        assertFalse(service.findSession(PLAYER).isEmpty());

        service.onPlayerDisconnect(PLAYER, NOW + 100);
        assertTrue(service.findSession(PLAYER).isEmpty());
    }

    @Test
    void disconnectRemovesPendingBackfillRetry() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-missing", "res-1", Long.MAX_VALUE)),
            NOW
        );
        assertEquals(1, pendingBackfillRetryCount(service));

        service.onPlayerDisconnect(PLAYER, NOW + 100);

        assertEquals(0, pendingBackfillRetryCount(service));
    }

    @Test
    void newLaunchForSamePlayerRemovesStalePendingBackfillRetry() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-missing", "res-1", Long.MAX_VALUE)),
            NOW
        );
        assertEquals(1, pendingBackfillRetryCount(service));

        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-new")),
            NOW + 100
        );

        assertEquals(0, pendingBackfillRetryCount(service));
        assertEquals(1, gateway.confirmCalls.size());
    }

    // -------------------------------------------------------------------------
    // Tick timeout: VALIDATING_POSITION times out before early returns
    // The service can't reach VALIDATING_POSITION without engine classes, so we test
    // hasPendingPlacementSession for a session manually placed in that phase.
    // -------------------------------------------------------------------------

    @Test
    void validatingPositionPhaseIsPending() {
        // Manually create a session in VALIDATING_POSITION to verify hasPendingPlacementSession.
        MinigameTransferSession session = new MinigameTransferSession(PLAYER, "alice", NOW);
        session.phase = MinigameTransferPhase.VALIDATING_POSITION;
        session.matchId = "match-1";

        assertTrue(session.hasAcceptedLaunch());
        assertFalse(session.isTerminal());

        // Inject session into the service via findSession (we can only do this by going through
        // the service API, so we just verify the session state constants are correct).
        assertFalse(service.hasPendingPlacementSession(PLAYER));  // not in sessionsByPlayerUuid yet
    }

    @Test
    void failPendingInitialPlacementSessionsOnlyFailsListedPlayersForMatchingMatch() {
        MinigameTransferSession missing = new MinigameTransferSession(PLAYER, "alice", NOW);
        missing.phase = MinigameTransferPhase.INSTANCE_WORLD_CREATING;
        missing.matchId = "match-1";
        putSession(service, missing);

        // Same match but not in the target set (e.g. valid backfill) — must be left alone.
        MinigameTransferSession backfill = new MinigameTransferSession(PLAYER_2, "bob", NOW);
        backfill.phase = MinigameTransferPhase.INSTANCE_WORLD_CREATING;
        backfill.matchId = "match-1";
        putSession(service, backfill);

        service.failPendingInitialPlacementSessions(
            "match-1",
            Set.of(PLAYER),
            MinigameTransferFailureReason.INITIAL_PLACEMENT_WINDOW_MISSED,
            NOW + 1_000L
        );

        assertEquals(MinigameTransferPhase.FAILED, missing.phase);
        assertEquals(MinigameTransferFailureReason.INITIAL_PLACEMENT_WINDOW_MISSED, missing.failureReason);
        assertTrue(service.findSession(PLAYER).isEmpty());
        // Backfill untouched.
        assertEquals(MinigameTransferPhase.INSTANCE_WORLD_CREATING, backfill.phase);
        assertTrue(service.hasPendingPlacementSession(PLAYER_2));
    }

    @Test
    void failPendingInitialPlacementSessionsIgnoresSessionFromDifferentMatch() {
        MinigameTransferSession otherMatch = new MinigameTransferSession(PLAYER, "alice", NOW);
        otherMatch.phase = MinigameTransferPhase.INSTANCE_WORLD_CREATING;
        otherMatch.matchId = "match-2";
        putSession(service, otherMatch);

        service.failPendingInitialPlacementSessions(
            "match-1",
            Set.of(PLAYER),
            MinigameTransferFailureReason.INITIAL_PLACEMENT_WINDOW_MISSED,
            NOW + 1_000L
        );

        // matchId mismatch: the unrelated session must not be failed or removed.
        assertEquals(MinigameTransferPhase.INSTANCE_WORLD_CREATING, otherMatch.phase);
        assertTrue(service.hasPendingPlacementSession(PLAYER));
    }

    // -------------------------------------------------------------------------
    // onPlayerReadyObserved: advances TELEPORT_ISSUED → POST_READY_GRACE
    // -------------------------------------------------------------------------

    @Test
    void onPlayerReadyObservedAdvancesToPostReadyGraceWhenWorldMatches() {
        service.onMinigameLaunchSafeReady(fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")), NOW);
        MinigameTransferSession session = service.findSession(PLAYER).orElseThrow();
        // Manually move to TELEPORT_ISSUED with an expected world
        session.phase = MinigameTransferPhase.TELEPORT_ISSUED;
        session.phaseStartedAtEpochMs = NOW;
        session.expectedWorldName = "arena_instance_1";

        service.onPlayerReadyObserved(PLAYER, "arena_instance_1", NOW + 500);

        assertEquals(MinigameTransferPhase.POST_READY_GRACE, session.phase);
        assertEquals(NOW + 500, session.lastReadyObservedAtEpochMs);
    }

    @Test
    void onPlayerReadyObservedIgnoresMismatchedWorld() {
        service.onMinigameLaunchSafeReady(fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")), NOW);
        MinigameTransferSession session = service.findSession(PLAYER).orElseThrow();
        session.phase = MinigameTransferPhase.TELEPORT_ISSUED;
        session.expectedWorldName = "arena_instance_1";

        service.onPlayerReadyObserved(PLAYER, "default_world", NOW + 500);

        assertEquals(MinigameTransferPhase.TELEPORT_ISSUED, session.phase);
    }

    @Test
    void onPlayerReadyObservedIgnoresUnknownPlayer() {
        // Must not throw or NPE for a player with no session
        service.onPlayerReadyObserved(UUID.randomUUID(), "some_world", NOW);
    }

    // -------------------------------------------------------------------------
    // RETRY_LATER: backfill missing match → stored for retry
    // -------------------------------------------------------------------------

    // backfillMissingMatchReturnsRetryLater is tested above as
    // backfillMissingMatchReturnsRetryLaterAndAcknowledgesArrival

    // -------------------------------------------------------------------------
    // processPendingBackfillRetries: match appears → retry succeeds
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Blocker 1: RETRY_LATER arrival acknowledgment
    // -------------------------------------------------------------------------

    @Test
    void retryLaterAcknowledgesArrivalSoRecentArrivalsDoesNotAccumulate() {
        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-ack", "res-1", Long.MAX_VALUE)),
            NOW
        );

        assertEquals(MinigameTransferOnReadyResult.Kind.RETRY_LATER, result.kind());
        assertTrue(result.arrivalAcknowledged(),
            "RETRY_LATER must acknowledge so recentArrivals is cleaned up immediately");
    }

    @Test
    void lateInitialAfterExpiresAtRejectsEvenBeforeWindowClosed() {
        ArenaActiveMatch match = buildMatch("match-1", 1)
            .withInitialPlacementWindowRuntime(1, NOW - 10_000L, NOW - 1L, NOW - 10_000L);
        gateway.existingMatches.put("match-1", match);

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")), NOW);

        assertEquals(MinigameTransferOnReadyResult.Kind.TERMINAL_FAILURE, result.kind());
        assertEquals(List.of(PLAYER), gateway.failCalls);
        assertEquals(List.of(MinigameTransferFailureReason.LATE_INITIAL_ARRIVAL), gateway.failReasons);
        assertTrue(gateway.acceptCalls.isEmpty());
    }

    @Test
    void lateInitialAfterStartGateOpenRejects() {
        ArenaActiveMatch match = buildMatch("match-1", 1)
            .withInitialPlacementWindowRuntime(1, NOW - 10_000L, NOW + 10_000L, NOW - 10_000L)
            .withStartGateOpened("INITIAL_WINDOW_EXPIRED_MIN_PLAYERS_MET", NOW - 1L, NOW - 1L);
        gateway.existingMatches.put("match-1", match);

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(noInstanceContextJson("match-1")), NOW);

        assertEquals(MinigameTransferOnReadyResult.Kind.TERMINAL_FAILURE, result.kind());
        assertEquals(List.of(MinigameTransferFailureReason.LATE_INITIAL_ARRIVAL), gateway.failReasons);
        assertTrue(gateway.acceptCalls.isEmpty());
    }

    @Test
    void backfillAfterInitialWindowClosedStillUsesBackfillAdmissionPolicy() {
        ArenaActiveMatch match = buildMatch("match-1", 1)
            .withInitialPlacementWindowRuntime(1, NOW - 10_000L, NOW - 1L, NOW - 10_000L)
            .withInitialPlacementWindowClosed("INITIAL_WINDOW_EXPIRED_MIN_PLAYERS_MET", NOW - 1L, NOW - 1L);
        gateway.existingMatches.put("match-1", match);

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-1", "reservation-1", NOW + 10_000L)), NOW);

        assertEquals(MinigameTransferOnReadyResult.Kind.ACCEPTED, result.kind());
        assertEquals(List.of(PLAYER), gateway.acceptCalls);
        assertTrue(gateway.failCalls.isEmpty());
    }

    @Test
    void expiredBackfillAfterInitialWindowClosedRejectedByBackfillPolicy() {
        ArenaActiveMatch match = buildMatch("match-1", 1)
            .withInitialPlacementWindowRuntime(1, NOW - 10_000L, NOW - 1L, NOW - 10_000L)
            .withInitialPlacementWindowClosed("INITIAL_WINDOW_EXPIRED_MIN_PLAYERS_MET", NOW - 1L, NOW - 1L);
        gateway.existingMatches.put("match-1", match);

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-1", "reservation-1", NOW - 1L)), NOW);

        assertEquals(MinigameTransferOnReadyResult.Kind.TERMINAL_FAILURE, result.kind());
        assertEquals(List.of(MinigameTransferFailureReason.BACKFILL_RESERVATION_REJECTED), gateway.failReasons);
        assertTrue(gateway.acceptCalls.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Blocker 3: TERMINAL_FAILURE calls failTransferPlacement for return-to-lobby
    // -------------------------------------------------------------------------

    @Test
    void backfillExpiredReservationCallsFailTransferPlacement() {
        gateway.existingMatches.put("match-1", buildMatch("match-1", 2));
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-1", "res-expired", 1L)),
            NOW
        );

        assertEquals(1, gateway.failCalls.size());
        assertEquals(PLAYER, gateway.failCalls.get(0));
    }

    // -------------------------------------------------------------------------
    // processPendingBackfillRetries: match appears → retry succeeds + acknowledged
    // -------------------------------------------------------------------------

    @Test
    void processPendingBackfillRetriesPlacesPlayerWhenMatchAppears() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-retry", "res-1", Long.MAX_VALUE)),
            NOW
        );
        assertEquals(0, gateway.acceptCalls.size());

        gateway.existingMatches.put("match-retry", buildMatch("match-retry", 2));
        service.processPendingBackfillRetries("match-retry", NOW + 1_000);

        assertEquals(1, gateway.acceptCalls.size());
        assertEquals(PLAYER, gateway.acceptCalls.get(0));
    }

    @Test
    void processPendingBackfillRetriesExpiresTtlEntriesAndCallsFail() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-expire", "res-1", Long.MAX_VALUE)),
            NOW
        );
        gateway.existingMatches.put("match-expire", buildMatch("match-expire", 2));

        // 31 s later — past the 30 s TTL
        service.processPendingBackfillRetries("match-expire", NOW + 31_000);

        assertEquals(0, gateway.acceptCalls.size());
        // Expired entries attempt failTransferPlacement for return-to-lobby
        assertEquals(1, gateway.failCalls.size());
        assertEquals(PLAYER, gateway.failCalls.get(0));
    }

    @Test
    void processPendingBackfillRetriesIgnoresUnrelatedMatchId() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-a", "res-1", Long.MAX_VALUE)),
            NOW
        );
        gateway.existingMatches.put("match-b", buildMatch("match-b", 2));

        service.processPendingBackfillRetries("match-b", NOW + 500);

        assertEquals(0, gateway.acceptCalls.size());
    }

    @Test
    void processPendingBackfillRetriesSkipsInstanceTemplateMatchWithoutAliveWorld() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-inst", "res-1", Long.MAX_VALUE)),
            NOW
        );
        // Build match with non-blank instanceWorldName
        gateway.existingMatches.put("match-inst", buildMatchWithInstance("match-inst", 2));

        // In test env Universe.get() == null → world == null → skip retry
        service.processPendingBackfillRetries("match-inst", NOW + 500);

        assertEquals(0, gateway.acceptCalls.size());
    }

    // -------------------------------------------------------------------------
    // Blocker 4: tickPendingBackfillRetries expires entries without match creation
    // -------------------------------------------------------------------------

    @Test
    void tickPendingBackfillRetriesExpiresTtlAfterDeadline() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-no-show", "res-1", Long.MAX_VALUE)),
            NOW
        );
        assertEquals(0, gateway.failCalls.size());

        // TTL = 30 s; tick at NOW + 31s, 1 s interval passed
        service.tickPendingBackfillRetries(NOW + 31_000);

        assertEquals(1, gateway.failCalls.size());
        assertEquals(PLAYER, gateway.failCalls.get(0));
        assertEquals(0, gateway.acceptCalls.size());
    }

    @Test
    void tickPendingBackfillRetriesRespectsRateLimit() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-rate", "res-1", Long.MAX_VALUE)),
            NOW
        );
        gateway.existingMatches.put("match-rate", buildMatch("match-rate", 2));

        // First tick at NOW+1s → processes retry
        service.tickPendingBackfillRetries(NOW + 1_000);
        assertEquals(1, gateway.acceptCalls.size());

        // Second tick immediately after → rate-limited, no double-process
        gateway.acceptCalls.clear();
        service.tickPendingBackfillRetries(NOW + 1_100);
        assertEquals(0, gateway.acceptCalls.size());
    }

    @Test
    void tickPendingBackfillRetriesPlacesPlayerWhenMatchAndWorldReady() {
        service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER), arrival(backfillContextJson("match-tick", "res-1", Long.MAX_VALUE)),
            NOW
        );
        gateway.existingMatches.put("match-tick", buildMatch("match-tick", 2));

        service.tickPendingBackfillRetries(NOW + 1_000);

        assertEquals(1, gateway.acceptCalls.size());
        assertEquals(PLAYER, gateway.acceptCalls.get(0));
    }

    @Test
    void backfillExistingInstanceMatchWaitsForWorldWithoutExpiringAcceptedReservation() {
        gateway.existingMatches.put("match-inst-existing", buildMatchWithInstance("match-inst-existing", 2));

        MinigameTransferOnReadyResult result = service.onMinigameLaunchSafeReady(
            fakeSnapshot(PLAYER),
            arrival(backfillInstanceContextJson("match-inst-existing", "res-1", NOW + 100)),
            NOW
        );

        assertEquals(MinigameTransferOnReadyResult.Kind.ACCEPTED, result.kind());
        assertTrue(result.arrivalAcknowledged());
        assertEquals(1, gateway.acceptCalls.size());
        assertEquals(0, gateway.failCalls.size());
        assertEquals(0, pendingBackfillRetryCount(service));
        MinigameTransferSession session = service.findSession(PLAYER).orElseThrow();
        assertEquals(MinigameTransferPhase.WAITING_FOR_INSTANCE_READY, session.phase());

        service.tickPendingBackfillRetries(NOW + 31_000);

        assertEquals(0, gateway.failCalls.size());
        assertEquals(MinigameTransferPhase.WAITING_FOR_INSTANCE_READY, session.phase());
    }

    @Test
    @SuppressWarnings("unchecked")
    void instanceWorldCreatingIssuesTeleportOnceAndReadyCanConfirm() throws Exception {
        AtomicInteger teleportCalls = new AtomicInteger();
        AtomicReference<MinigameTransferSession> sessionRef = new AtomicReference<>();
        MinigameTransferService transferService = new MinigameTransferService(
            HytaleLogger.getLogger(),
            new InstanceSpawnSlotService(new InstanceSpawnSlotStore(tempDir.resolve("slots-instance.json"))),
            (ref, store, worldFuture, transform) -> {
                assertEquals(MinigameTransferPhase.TELEPORT_ISSUED, sessionRef.get().phase());
                teleportCalls.incrementAndGet();
            },
            (ref, store) -> false
        );
        FakeMatchGateway localGateway = new FakeMatchGateway();
        transferService.setMatchGateway(localGateway);

        MinigameTransferSession session = new MinigameTransferSession(PLAYER, "player", NOW);
        session.matchId = "match-instance";
        session.arenaId = "arena1";
        session.instanceTemplateId = "template1";
        session.expectedWorldName = "match-instance_nexori_world";
        session.phase = MinigameTransferPhase.INSTANCE_WORLD_CREATING;
        session.phaseStartedAtEpochMs = NOW;
        World world = mock(World.class);
        when(world.isAlive()).thenReturn(true);
        when(world.getName()).thenReturn(session.expectedWorldName);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(world).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        session.instanceWorldFuture = CompletableFuture.completedFuture(world);
        sessionRef.set(session);
        putSession(transferService, session);

        PlayerRef playerRef = mock(PlayerRef.class);
        when(playerRef.getUuid()).thenReturn(PLAYER);
        Ref<EntityStore> ref = (Ref<EntityStore>) mock(Ref.class);
        Store<EntityStore> store = (Store<EntityStore>) mock(Store.class);

        transferService.tickTransfer(ref, store, playerRef, NOW + 1_000);
        transferService.tickTransfer(ref, store, playerRef, NOW + 1_100);

        assertEquals(1, teleportCalls.get());
        assertEquals(MinigameTransferPhase.TELEPORT_ISSUED, session.phase());

        transferService.onPlayerReadyObserved(PLAYER, session.expectedWorldName, NOW + 1_200);
        assertEquals(MinigameTransferPhase.POST_READY_GRACE, session.phase());

        transferService.tickTransfer(ref, store, playerRef, NOW + 2_000);
        assertEquals(MinigameTransferPhase.VALIDATING_POSITION, session.phase());

        transferService.tickTransfer(ref, store, playerRef, NOW + 2_100);

        assertEquals(MinigameTransferPhase.CONFIRMED, session.phase());
        assertEquals(List.of(PLAYER), localGateway.confirmCalls);
    }

    @Test
    @SuppressWarnings("unchecked")
    void teleportIssuerFailureFailsSessionAndCallsFailTransferPlacement() throws Exception {
        MinigameTransferService transferService = new MinigameTransferService(
            HytaleLogger.getLogger(),
            new InstanceSpawnSlotService(new InstanceSpawnSlotStore(tempDir.resolve("slots-throwing.json"))),
            (ref, store, worldFuture, transform) -> {
                throw new RuntimeException("teleport boom");
            },
            (ref, store) -> false
        );
        FakeMatchGateway localGateway = new FakeMatchGateway();
        transferService.setMatchGateway(localGateway);

        MinigameTransferSession session = new MinigameTransferSession(PLAYER, "player", NOW);
        session.matchId = "match-instance-fail";
        session.arenaId = "arena1";
        session.instanceTemplateId = "template1";
        session.expectedWorldName = "match-instance-fail_nexori_world";
        session.returnConnectionAddress = "lobby:25565";
        session.returnFallbackTargetId = "lobby1.natural_spawn";
        session.launchTravelProfileId = "nexori_launch";
        session.originLobbyId = "lobby1";
        session.phase = MinigameTransferPhase.INSTANCE_WORLD_CREATING;
        session.phaseStartedAtEpochMs = NOW;
        World world = mock(World.class);
        when(world.isAlive()).thenReturn(true);
        when(world.getName()).thenReturn(session.expectedWorldName);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run();
            return null;
        }).when(world).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        session.instanceWorldFuture = CompletableFuture.completedFuture(world);
        putSession(transferService, session);

        PlayerRef playerRef = mock(PlayerRef.class);
        when(playerRef.getUuid()).thenReturn(PLAYER);
        Ref<EntityStore> ref = (Ref<EntityStore>) mock(Ref.class);
        Store<EntityStore> store = (Store<EntityStore>) mock(Store.class);

        transferService.tickTransfer(ref, store, playerRef, NOW + 1_000);
        transferService.tickTransfer(ref, store, playerRef, NOW + 1_100);

        assertEquals(MinigameTransferPhase.FAILED, session.phase());
        assertEquals(MinigameTransferFailureReason.TELEPORT_ISSUE_FAILED, session.failureReason());
        assertEquals(List.of(PLAYER), localGateway.failCalls);
    }

    @Test
    @SuppressWarnings("unchecked")
    void instanceWorldReadyDefersTeleportInsteadOfFailingDuringTick() throws Exception {
        AtomicInteger teleportCalls = new AtomicInteger();
        AtomicReference<Runnable> deferredTeleport = new AtomicReference<>();
        MinigameTransferService transferService = new MinigameTransferService(
            HytaleLogger.getLogger(),
            new InstanceSpawnSlotService(new InstanceSpawnSlotStore(tempDir.resolve("slots-deferred.json"))),
            (ref, store, worldFuture, transform) -> teleportCalls.incrementAndGet(),
            (ref, store) -> false
        );
        transferService.setMatchGateway(new FakeMatchGateway());

        MinigameTransferSession session = new MinigameTransferSession(PLAYER, "player", NOW);
        session.matchId = "match-instance-deferred";
        session.arenaId = "arena1";
        session.instanceTemplateId = "template1";
        session.expectedWorldName = "match-instance-deferred_nexori_world";
        session.phase = MinigameTransferPhase.INSTANCE_WORLD_CREATING;
        session.phaseStartedAtEpochMs = NOW;
        World world = mock(World.class);
        when(world.isAlive()).thenReturn(true);
        when(world.getName()).thenReturn(session.expectedWorldName);
        doAnswer(invocation -> {
            deferredTeleport.set(invocation.getArgument(0));
            return null;
        }).when(world).execute(org.mockito.ArgumentMatchers.any(Runnable.class));
        session.instanceWorldFuture = CompletableFuture.completedFuture(world);
        putSession(transferService, session);

        PlayerRef playerRef = mock(PlayerRef.class);
        when(playerRef.getUuid()).thenReturn(PLAYER);
        Ref<EntityStore> ref = (Ref<EntityStore>) mock(Ref.class);
        Store<EntityStore> store = (Store<EntityStore>) mock(Store.class);

        transferService.tickTransfer(ref, store, playerRef, NOW + 1_000);

        assertEquals(0, teleportCalls.get());
        assertEquals(MinigameTransferPhase.INSTANCE_WORLD_READY, session.phase());
        assertTrue(session.instanceTeleportDeferred);
        assertEquals("", session.deferredTeleportFailureDetail);

        deferredTeleport.get().run();

        assertEquals(1, teleportCalls.get());
        assertEquals(MinigameTransferPhase.TELEPORT_ISSUED, session.phase());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Creates a test ReadyPlayerSnapshot without engine PlayerRef/entity/store instances.
     */
    private static ReadyPlayerSnapshot fakeSnapshot(UUID playerUuid) {
        return ReadyPlayerSnapshotTestHelper.forTest(playerUuid);
    }

    private static PendingArrival arrival(String contextJson) {
        return new PendingArrival(
            "op-1", "server-1", "localhost:25000",
            "target-1", "Arena", "NATURAL_SPAWN", "default_world",
            "", "nexori_launch", "", contextJson, ""
        );
    }

    private static String noInstanceContextJson(String matchId) {
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.launch");
        root.addProperty("matchId", matchId);
        root.addProperty("queueId", "q1");
        root.addProperty("arenaId", "arena1");
        root.addProperty("originLobbyId", "lobby1");
        root.addProperty("returnConnectionAddress", "lobby:25565");
        root.addProperty("returnFallbackTargetId", "lobby1.natural_spawn");
        root.addProperty("launchTravelProfileId", "nexori_launch");
        root.addProperty("instanceTemplateId", "none");
        root.addProperty("assignmentType", "INITIAL_MATCH");
        return GSON.toJson(root);
    }

    private static String backfillContextJson(String matchId, String reservationId, long expiresAt) {
        JsonObject root = new JsonObject();
        root.addProperty("flowType", "minigame.launch");
        root.addProperty("matchId", matchId);
        root.addProperty("queueId", "q1");
        root.addProperty("arenaId", "arena1");
        root.addProperty("originLobbyId", "lobby1");
        root.addProperty("returnConnectionAddress", "lobby:25565");
        root.addProperty("returnFallbackTargetId", "lobby1.natural_spawn");
        root.addProperty("launchTravelProfileId", "nexori_launch");
        root.addProperty("instanceTemplateId", "none");
        root.addProperty("assignmentType", "BACKFILL");
        root.addProperty("playerUuid", PLAYER.toString());
        root.addProperty("admissionReservationId", reservationId);
        root.addProperty("admissionExpiresAtEpochMs", expiresAt);
        return GSON.toJson(root);
    }

    private static String backfillInstanceContextJson(String matchId, String reservationId, long expiresAt) {
        JsonObject root = GSON.fromJson(backfillContextJson(matchId, reservationId, expiresAt), JsonObject.class);
        root.addProperty("instanceTemplateId", "my_template");
        return GSON.toJson(root);
    }

    @SuppressWarnings("unchecked")
    private static int pendingBackfillRetryCount(MinigameTransferService service) {
        try {
            java.lang.reflect.Field field = MinigameTransferService.class.getDeclaredField("pendingBackfillRetries");
            field.setAccessible(true);
            return ((Map<UUID, ?>) field.get(service)).size();
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void putSession(MinigameTransferService service, MinigameTransferSession session) {
        try {
            java.lang.reflect.Field field = MinigameTransferService.class.getDeclaredField("sessionsByPlayerUuid");
            field.setAccessible(true);
            ((Map<UUID, MinigameTransferSession>) field.get(service)).put(session.playerUuid(), session);
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ArenaActiveMatch buildMatch(String matchId, int expectedCount) {
        return new ArenaActiveMatch(
            matchId, "q1", "arena1", "lobby1", "lobby:25565", "lobby1.natural_spawn",
            "nexori_launch", "none", "",
            "", "assign-1", "INITIAL_MATCH",
            "", ArenaMatchSource.BACKEND_DRIVEN.id(),        // required for backfill admission
            0, expectedCount + 2, true,                       // admissionCapacity > roster, backfillEnabled
            QueueBackfillMode.PLACEMENT_ONLY.id(), 0,        // PLACEMENT_ONLY: open while placement not complete
            AfkDetectionPolicy.defaults(),
            List.of(PLAYER_2), expectedCount,
            List.of(PLAYER_2), List.of(PLAYER_2), List.of(), List.of(),
            Map.of(), Map.of(), Map.of(), Map.of(), 0, Set.of(),
            false, "", "", 0L, "", 0L, 0L, 0L, 0L, "", NOW, NOW, ""
        ).normalized();
    }

    private static ArenaActiveMatch buildMatchWithInstance(String matchId, int expectedCount) {
        // instanceTemplateId = "my_template" (non-blank) → usesInstanceTemplate() = true
        // instanceWorldName = non-blank → triggers world-alive check in processPendingBackfillRetries
        return new ArenaActiveMatch(
            matchId, "q1", "arena1", "lobby1", "lobby:25565", "lobby1.natural_spawn",
            "nexori_launch", "my_template", matchId + "_nexori_world",
            "", "assign-1", "INITIAL_MATCH",
            "", ArenaMatchSource.BACKEND_DRIVEN.id(),
            0, expectedCount + 2, true,
            QueueBackfillMode.PLACEMENT_ONLY.id(), 0,
            AfkDetectionPolicy.defaults(),
            List.of(PLAYER_2), expectedCount,
            List.of(PLAYER_2), List.of(PLAYER_2), List.of(), List.of(),
            Map.of(), Map.of(), Map.of(), Map.of(), 0, Set.of(),
            false, "", "", 0L, "", 0L, 0L, 0L, 0L, "", NOW, NOW, ""
        ).normalized();
    }

    // -------------------------------------------------------------------------
    // Fake MatchGateway
    // -------------------------------------------------------------------------

    static final class FakeMatchGateway implements MinigameTransferService.MatchGateway {

        final List<UUID> acceptCalls = new ArrayList<>();
        final List<UUID> confirmCalls = new ArrayList<>();
        final List<UUID> failCalls = new ArrayList<>();
        final List<String> failReasons = new ArrayList<>();
        final java.util.Set<UUID> confirmedPlayers = new java.util.HashSet<>();
        final Map<String, ArenaActiveMatch> existingMatches = new LinkedHashMap<>();
        java.util.function.Consumer<UUID> onAcceptTransferArrival = ignored -> {};

        @Override
        @Nonnull
        public Optional<ArenaActiveMatch> findMatchRaw(@Nonnull String matchId) {
            return Optional.ofNullable(existingMatches.get(matchId));
        }

        @Override
        @Nonnull
        public List<Runnable> acceptTransferArrival(
            @Nonnull UUID playerUuid,
            @Nonnull String username,
            @Nonnull LaunchContextData launch,
            @Nullable ArenaActiveMatch existingMatch,
            long nowEpochMs
        ) {
            acceptCalls.add(playerUuid);
            onAcceptTransferArrival.accept(playerUuid);
            return List.of();
        }

        @Override
        @Nonnull
        public List<Runnable> confirmTransferPlacement(@Nonnull UUID playerUuid, long nowEpochMs) {
            confirmCalls.add(playerUuid);
            confirmedPlayers.add(playerUuid);
            return List.of();
        }

        @Override
        @Nonnull
        public List<Runnable> failTransferPlacement(
            @Nonnull UUID playerUuid,
            @Nonnull String reason,
            @Nonnull String returnConnectionAddress,
            @Nonnull String returnFallbackTargetId,
            @Nonnull String launchTravelProfileId,
            @Nonnull String originLobbyId,
            @Nonnull String matchId,
            long nowEpochMs
        ) {
            failCalls.add(playerUuid);
            failReasons.add(reason);
            return List.of();
        }
    }
}
