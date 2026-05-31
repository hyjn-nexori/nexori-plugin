package io.github.hyjn.nexori.plugin.backend;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.testsupport.BackendTestFixtures;
import io.github.hyjn.nexori.plugin.backend.testsupport.FakeBackendHttpTransport;
import io.github.hyjn.nexori.plugin.backend.testsupport.FakeHttpResponse;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaActiveMatch;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Mockito-based integration tests for {@link BackendMatchAdmissionStateReportingService}.
 *
 * <p>Uses {@link FakeBackendHttpTransport} and Mockito to isolate HTTP I/O and
 * {@link ArenaMatchService} interactions. A real {@link ArenaActiveMatch} is constructed
 * with all required fields for a reportable BACKEND_DRIVEN match.</p>
 *
 * <p>Pattern:
 * <ol>
 *   <li>Dirty the match via {@code markMatchDirtyImmediate} (debounceMs=0 → flushes immediately).</li>
 *   <li>{@code handleTick(T1)} — drains nothing, evaluates open windows, processes publication
 *       states → starts HTTP request (captured by fake transport).</li>
 *   <li>Complete the pending future / pre-enqueue response, then {@code handleTick(T2)} — drains.</li>
 * </ol>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
final class BackendMatchAdmissionStateReportingServiceMockitoTest {

    private static final long T1 = 3_000_000L;
    private static final long T2 = T1 + 1L;
    // matchStateRetryIntervalMs = 3000 from enabledMatchStateConfig
    private static final long T_RETRY = T2 + 3_001L;

    private static final String MATCH_ID = "match-admission-test-1";
    private static final UUID PLAYER_UUID = UUID.fromString("00000000-0000-0000-0000-000000000077");

    // HytaleLogger has a private constructor and Flogger's AbstractLogger cannot be
    // instrumented by ByteBuddy's InlineMockMaker; use the real logger instead.
    private HytaleLogger logger;
    @Mock ArenaMatchService arenaMatchService;

    private FakeBackendHttpTransport transport;
    private ServerIdentity serverIdentity;
    private BackendMatchAdmissionStateReportingService service;
    private ArenaActiveMatch testMatch;

    @BeforeEach
    void setUp() {
        logger = HytaleLogger.getLogger();
        transport = new FakeBackendHttpTransport();
        serverIdentity = BackendTestFixtures.testServerIdentity();

        // A minimal but fully reportable BACKEND_DRIVEN match
        testMatch = new ArenaActiveMatch(
            MATCH_ID,                        // matchId
            "queue-1",                       // queueId
            "arena-1",                       // arenaId
            "",                              // originLobbyId
            "",                              // returnConnectionAddress
            "",                              // returnFallbackTargetId
            "",                              // launchTravelProfileId
            "",                              // instanceTemplateId
            "",                              // instanceWorldName
            "assign-1",                      // assignmentId
            "ext-match-1",                   // externalMatchId (non-blank — required for reporting)
            "BACKEND_DRIVEN",                // matchSource (required: effectiveMatchSource == BACKEND_DRIVEN)
            1,                               // admissionPolicySchemaVersion (> 0)
            4,                               // admissionCapacity (> 0)
            false,                           // backfillEnabled
            "OPPORTUNISTIC",                 // backfillMode (non-blank — required for reporting)
            0,                               // backfillWindowSeconds
            List.of(PLAYER_UUID),            // expectedPlayerUuids (non-empty — required for reporting)
            1,                               // expectedPlayerCount
            List.of(),                       // arrivedPlayerUuids
            List.of(),                       // activePlayerUuids
            List.of(),                       // eliminatedPlayerUuids
            Map.of(),                        // pendingReturnAtEpochMsByPlayerUuid
            "",                              // winnerPlayerUuid
            0L,                              // placementCompletedAtEpochMs
            0L,                              // matchStartedAtEpochMs
            0L,                              // completedAtEpochMs
            0L,                              // resultSubmittedAtEpochMs
            "",                              // resultPayloadHash
            T1,                              // createdAtEpochMs
            T1,                              // lastUpdatedAtEpochMs
            ""                               // lastError
        );

        // Stub arenaMatchService for all tests
        when(arenaMatchService.find(MATCH_ID)).thenReturn(Optional.of(testMatch));
        when(arenaMatchService.findMatchPlacementState(anyString())).thenReturn(Optional.empty());

        service = new BackendMatchAdmissionStateReportingService(
            logger,
            BackendTestFixtures.enabledMatchStateConfig(),
            serverIdentity,
            arenaMatchService,
            transport
        );
    }

    // ── Test 1: marking dirty and handleTick sends one HTTP request ───────────

    @Test
    void markDirtyImmediateAndHandleTickSendsOneAdmissionRequest() {
        transport.enqueuePendingFuture(new CompletableFuture<>());

        service.markMatchDirtyImmediate(MATCH_ID, "MATCH_CREATED", T1);
        service.handleTick(T1); // processPublicationStates → startHttpRequest

        assertEquals(1, transport.capturedRequestCount());
    }

    // ── Test 2: 200 ACKNOWLEDGED response clears the publication state ────────

    @Test
    void acknowledgedResponseClearsPublicationStateAndNoSecondRequestIsSent() {
        // acknowledged body — any semantic status will do ("" → OK → ACKNOWLEDGED by policy)
        CompletableFuture<java.net.http.HttpResponse<String>> pending = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending);

        service.markMatchDirtyImmediate(MATCH_ID, "MATCH_CREATED", T1);
        service.handleTick(T1); // sends request, state in-flight

        // Complete with 200 empty body ("" → status "OK" → semantically accepted)
        pending.complete(new FakeHttpResponse(200, ""));
        service.handleTick(T2); // drains result → ACKNOWLEDGED → clears publication state

        // No second request should be sent on T2 (state is now clean / removed)
        assertEquals(1, transport.capturedRequestCount());
    }

    // ── Test 3: retryable 500 schedules a pending retry request ──────────────

    @Test
    void retryable500ResponseSchedulesPendingRetryAndRetriesAfterInterval() {
        CompletableFuture<java.net.http.HttpResponse<String>> pending1 = new CompletableFuture<>();
        CompletableFuture<java.net.http.HttpResponse<String>> pending2 = new CompletableFuture<>();
        transport.enqueuePendingFuture(pending1);
        transport.enqueuePendingFuture(pending2);

        service.markMatchDirtyImmediate(MATCH_ID, "MATCH_CREATED", T1);
        service.handleTick(T1); // sends request #1

        // 500 → RETRYABLE_FAILURE → shouldStorePendingRetrySnapshot = true
        // (state is not dirty after the build, snapshot is not yet expired at T2)
        pending1.complete(new FakeHttpResponse(500, ""));
        service.handleTick(T2); // drains 500 → pendingRetry scheduled; no new request yet (rate-limited)

        // Past retryIntervalMs(3000ms) and nextGlobalAttemptAtEpochMs
        service.handleTick(T_RETRY); // retries with the pending snapshot → sends request #2

        assertEquals(2, transport.capturedRequestCount());
    }

    // ── Test 4: disabled config does not send admission state ─────────────────

    @Test
    void disabledConfigDoesNotSendAdmissionState() {
        // Build a config with matchStateReportingEnabled=false
        BackendMatchmakingConfig disabledConfig = new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            "http://backend.test",
            "test-server-token",
            1_000L,
            "us-east",
            3_000L,
            false,
            5_000L,
            false,  // matchStateReportingEnabled = false
            0L,
            5_000L,
            3_000L,
            30_000L
        );

        service.updateConfig(disabledConfig);

        service.markMatchDirtyImmediate(MATCH_ID, "MATCH_CREATED", T1);
        service.handleTick(T1);

        assertEquals(0, transport.capturedRequestCount());
        assertEquals(false, service.isMatchStateReportingEnabled());
    }

    // ── Test 5: flushMatchImmediately sends before normal debounce ────────────

    @Test
    void flushMatchImmediatelySendsBeforeNormalDelay() {
        transport.enqueuePendingFuture(new CompletableFuture<>());

        service.flushMatchImmediately(MATCH_ID, "MATCH_CREATED", T1);
        service.handleTick(T1);

        assertEquals(1, transport.capturedRequestCount());

        // Second tick without changes should not send another request
        service.handleTick(T1 + 1_000L);

        assertEquals(1, transport.capturedRequestCount());
    }

    // ── Test 6: markAdmissionReservationConsumed triggers state send ──────────

    @Test
    void markAdmissionReservationConsumedSendsConsumedState() {
        transport.enqueuePendingFuture(new CompletableFuture<>());

        service.markAdmissionReservationConsumed(MATCH_ID, "reservation-abc-123", T1);
        service.handleTick(T1);

        // At least 1 request should have been sent because markAdmissionReservationConsumed
        // calls markDirty internally with PLAYER_ARRIVED reason (debounce=0 → schedules immediately)
        assertEquals(1, transport.capturedRequestCount());
    }

    // ── Test 7: markDirty normal waits until due according to debounce ────────

    @Test
    void markDirtyNormalWaitsUntilDueAccordingToCurrentDelay() {
        // enabledMatchStateConfig uses debounce=0ms so we need a config with non-zero debounce
        // to test the "waits" behavior. Use debounceMs=1000ms.
        long debounceMs = 1_000L;
        BackendMatchmakingConfig debouncedConfig = new BackendMatchmakingConfig(
            BackendMatchmakingConfig.CURRENT_SCHEMA_VERSION,
            false,
            "http://backend.test",
            "test-server-token",
            1_000L,
            "us-east",
            3_000L,
            false,
            5_000L,
            true,        // matchStateReportingEnabled = true
            debounceMs,  // matchStateDebounceMs = 1000
            5_000L,
            3_000L,
            30_000L
        );
        service.updateConfig(debouncedConfig);

        transport.enqueuePendingFuture(new CompletableFuture<>());

        // Mark dirty at T1 — using regular markMatchDirty (not Immediate)
        service.markMatchDirty(MATCH_ID, "MATCH_CREATED", T1);

        // handleTick at T1: should NOT send yet (scheduledFlushAt = T1 + 1000)
        service.handleTick(T1);
        assertEquals(0, transport.capturedRequestCount());

        // handleTick at T1 + debounceMs: now it is due → should send
        service.handleTick(T1 + debounceMs);
        assertEquals(1, transport.capturedRequestCount());
    }
}
