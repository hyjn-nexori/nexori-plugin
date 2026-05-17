package io.github.hyjn.nexori.plugin.backend;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.logic.BackendAssignmentProcessingPlanner;
import io.github.hyjn.nexori.plugin.backend.payload.BackendAssignmentAckPayload;
import io.github.hyjn.nexori.plugin.backend.testsupport.BackendTestFixtures;
import io.github.hyjn.nexori.plugin.backend.testsupport.FakeBackendHttpTransport;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.ArenaService;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;
import io.github.hyjn.nexori.plugin.minigame.QueueService;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-based integration tests for {@link BackendSyncService}.
 *
 * <p>Uses {@link FakeBackendHttpTransport} to control HTTP responses without
 * opening real sockets, and real {@link BackendAssignmentStore} / {@link LocalConnectionAddressService}
 * with {@link TempDir} for file-system isolation.</p>
 *
 * <p>Each test follows a two-tick pattern:
 * <ol>
 *   <li>Tick T1 — starts the async sync request (captured by the fake transport).</li>
 *   <li>Tick T2 — drains the completed future added to {@code queuedResults} by the
 *       {@code whenComplete} callback (which ran synchronously for the already-completed
 *       future in the fake transport).</li>
 * </ol>
 * For stale-in-flight tests a pending future is injected and T2 is advanced past the
 * timeout + safety window instead.</p>
 */
@ExtendWith(MockitoExtension.class)
final class BackendSyncServiceMockitoTest {

    private static final long T1 = 1_000_000L;
    private static final long T2 = T1 + 1L;
    private static final long T3 = T1 + 1_001L; // past syncIntervalMs=1000
    private static final long T4 = T3 + 1L;

    // request timeout = 3000 ms, safety window = 1000 ms → stale after 4001 ms
    private static final long T_STALE = T1 + 4_100L;

    private static final String PLAYER_UUID = "00000000-0000-0000-0000-000000000099";
    private static final String ASSIGN_ID = "assign-test-1";

    @TempDir
    Path tempDir;

    // HytaleLogger has a private constructor and Flogger's AbstractLogger cannot be
    // instrumented by ByteBuddy's InlineMockMaker; use the real logger instead.
    private HytaleLogger logger;
    @Mock QueueService queueService;
    @Mock QueueCoordinatorService queueCoordinatorService;
    @Mock ArenaService arenaService;
    @Mock ArenaMatchService arenaMatchService;

    private FakeBackendHttpTransport transport;
    private BackendAssignmentStore assignmentStore;
    private LocalConnectionAddressService localConnectionAddressService;
    private ServerIdentity serverIdentity;
    private BackendSyncService service;

    @BeforeEach
    void setUp() throws IOException {
        logger = HytaleLogger.getLogger();
        transport = new FakeBackendHttpTransport();
        assignmentStore = new BackendAssignmentStore(tempDir.resolve("assignments.json"));
        localConnectionAddressService = new LocalConnectionAddressService(tempDir.resolve("connection.txt"));
        serverIdentity = BackendTestFixtures.testServerIdentity();

        // Default stubs for service startup (payload building always calls these)
        when(queueService.list()).thenReturn(List.of());
        when(queueCoordinatorService.listQueueStates()).thenReturn(List.of());
        when(arenaService.list()).thenReturn(List.of());
        when(arenaMatchService.listMatches()).thenReturn(List.of());

        service = new BackendSyncService(
            logger,
            BackendTestFixtures.enabledSyncConfig(),
            assignmentStore,
            serverIdentity,
            localConnectionAddressService,
            queueService,
            queueCoordinatorService,
            arenaService,
            arenaMatchService,
            transport
        );
    }

    // ── Test 1: successful sync with no assignments ───────────────────────────

    @Test
    void successfulSyncWithNoAssignmentsProducesHealthyState() {
        transport.enqueueResponse(200, BackendTestFixtures.emptySyncResponse(1L));

        service.handleTick(T1); // starts sync, whenComplete fires immediately → result queued
        service.handleTick(T2); // drains result → PROCESS_RESPONSE → healthState = HEALTHY

        assertEquals("HEALTHY", service.healthState().status());
        assertEquals(1, transport.capturedRequestCount());
    }

    // ── Test 2: valid INITIAL_MATCH assignment triggers launch ────────────────

    @Test
    void validInitialMatchAssignmentCallsLaunchBackendAssignment() {
        when(arenaMatchService.findActiveMatchId(any(UUID.class))).thenReturn(Optional.empty());
        when(queueCoordinatorService.launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        )).thenReturn(new QueueCoordinatorService.AssignmentLaunchResult(
            QueueCoordinatorService.AssignmentLaunchOutcome.LAUNCHED, "local-match-1", ""
        ));

        transport.enqueueResponse(200,
            BackendTestFixtures.syncResponseWithAssignment(1L, ASSIGN_ID, PLAYER_UUID, "ext-match-1"));

        service.handleTick(T1);
        service.handleTick(T2);

        verify(queueCoordinatorService, times(1)).launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        );
        verify(queueCoordinatorService, never()).launchBackendBackfillAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyString(), anyString()
        );
        assertEquals("HEALTHY", service.healthState().status());
    }

    // ── Test 3: duplicate assignment with same hash is ignored (no second launch) ──

    @Test
    void duplicateAssignmentWithSameHashIsIgnoredOnSecondCycle() throws Exception {
        when(arenaMatchService.findActiveMatchId(any(UUID.class))).thenReturn(Optional.empty());
        when(queueCoordinatorService.launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        )).thenReturn(new QueueCoordinatorService.AssignmentLaunchResult(
            QueueCoordinatorService.AssignmentLaunchOutcome.LAUNCHED, "local-match-1", ""
        ));

        String body = BackendTestFixtures.syncResponseWithAssignment(1L, ASSIGN_ID, PLAYER_UUID, "ext-match-1");

        // Cycle 1: process the assignment
        transport.enqueueResponse(200, body);
        service.handleTick(T1);
        service.handleTick(T2);

        // Cycle 2: same assignment (same payload → same hash) → IGNORE
        transport.enqueueResponse(200, body);
        service.handleTick(T3);
        service.handleTick(T4);

        // Launch should have been called exactly once (first cycle only)
        verify(queueCoordinatorService, times(1)).launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        );
        verify(queueCoordinatorService, never()).launchBackendBackfillAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyString(), anyString()
        );
        assertEquals(2, transport.capturedRequestCount());
    }

    // ── Test 4: assignment with blank id is ignored ───────────────────────────

    @Test
    void assignmentWithBlankIdIsIgnoredAndLaunchIsNeverCalled() {
        // Blank assignmentId → planBeforeLaunch returns IGNORE immediately
        String body = "{\"schemaVersion\":1,\"receivedSequence\":1,"
            + "\"acknowledgedAssignmentAckIds\":[],\"assignments\":[{"
            + "\"assignmentType\":\"INITIAL_MATCH\","
            + "\"assignmentId\":\"\","
            + "\"matchId\":\"nexori-match-1\","
            + "\"externalMatchId\":\"ext-1\","
            + "\"type\":\"CREATE_MATCH\","
            + "\"queueId\":\"queue-1\","
            + "\"playerUuids\":[\"" + PLAYER_UUID + "\"],"
            + "\"expectedPlayerUuids\":[],"
            + "\"arenaId\":\"arena-1\","
            + "\"players\":[],"
            + "\"reportingServerId\":\"\","
            + "\"targetConnectionAddress\":\"\","
            + "\"modeId\":\"\",\"kitId\":\"\",\"ranked\":false,\"metadata\":{}"
            + "}]}";

        transport.enqueueResponse(200, body);
        service.handleTick(T1);
        service.handleTick(T2);

        verify(queueCoordinatorService, never()).launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        );
        assertEquals("HEALTHY", service.healthState().status());
    }

    // ── Test 5: 401 produces AUTH_FAILED health ───────────────────────────────

    @Test
    void auth401ProducesAuthFailedHealth() {
        transport.enqueueResponse(401, "");

        service.handleTick(T1);
        service.handleTick(T2);

        assertEquals("AUTH_FAILED", service.healthState().status());
    }

    // ── Test 6: 403 produces AUTH_FORBIDDEN health ────────────────────────────

    @Test
    void forbidden403ProducesAuthForbiddenHealth() {
        transport.enqueueResponse(403, "");

        service.handleTick(T1);
        service.handleTick(T2);

        assertEquals("AUTH_FORBIDDEN", service.healthState().status());
    }

    // ── Test 7: malformed 200 body produces SYNC_FAILED health ───────────────

    @Test
    void malformed200BodyProducesSyncFailedHealth() {
        transport.enqueueResponse(200, "NOT_VALID_JSON{{{{");

        service.handleTick(T1);
        service.handleTick(T2);

        assertEquals("SYNC_FAILED", service.healthState().status());
    }

    // ── Test 8: stale in-flight produces IN_FLIGHT_STALE health ──────────────

    @Test
    void staleInFlightProducesInFlightStaleHealth() {
        // Pending future that never completes → request stays in-flight indefinitely
        transport.enqueuePendingFuture(new CompletableFuture<>());

        service.handleTick(T1); // starts sync, future is pending

        // Tick well past requestTimeoutMs(3000) + STALE_SAFETY_WINDOW_MS(1000)
        service.handleTick(T_STALE);

        assertEquals("IN_FLIGHT_STALE", service.healthState().status());
    }

    // ── Test 9: BACKFILL assignment routes to launchBackendBackfillAssignment ─

    @Test
    void backfillAssignmentRoutesToLaunchBackendBackfillAssignment() {
        when(arenaMatchService.findActiveMatchId(any(UUID.class))).thenReturn(Optional.empty());
        when(queueCoordinatorService.launchBackendBackfillAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyString(), anyString()
        )).thenReturn(new QueueCoordinatorService.AssignmentLaunchResult(
            QueueCoordinatorService.AssignmentLaunchOutcome.LAUNCHED, "local-match-backfill", ""
        ));

        transport.enqueueResponse(200,
            BackendTestFixtures.syncResponseWithBackfillAssignment(
                1L, "assign-backfill-1", PLAYER_UUID,
                "reservation-abc", T1 + 60_000L, "remote.game.server:25565"
            ));

        service.handleTick(T1);
        service.handleTick(T2);

        verify(queueCoordinatorService, times(1)).launchBackendBackfillAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyString(), anyString()
        );
        verify(queueCoordinatorService, never()).launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        );
    }

    // ── Test 10: REJECT_DUPLICATE — same assignmentId, different payload hash ──

    @Test
    void differentHashDuplicateAssignmentRecordsRejectedDuplicate() {
        when(arenaMatchService.findActiveMatchId(any(UUID.class))).thenReturn(Optional.empty());
        when(queueCoordinatorService.launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        )).thenReturn(new QueueCoordinatorService.AssignmentLaunchResult(
            QueueCoordinatorService.AssignmentLaunchOutcome.LAUNCHED, "local-match-1", ""
        ));

        // Cycle 1: externalMatchId="ext-match-v1" → LAUNCHED, hash is stored
        transport.enqueueResponse(200,
            BackendTestFixtures.syncResponseWithAssignment(1L, ASSIGN_ID, PLAYER_UUID, "ext-match-v1"));
        service.handleTick(T1);
        service.handleTick(T2);

        // Cycle 2: same assignmentId, different externalMatchId → different hash → REJECT_DUPLICATE
        transport.enqueueResponse(200,
            BackendTestFixtures.syncResponseWithAssignment(2L, ASSIGN_ID, PLAYER_UUID, "ext-match-v2"));
        service.handleTick(T3);
        service.handleTick(T4);

        // Launch was called exactly once (cycle 1 only); cycle 2 was rejected before launch
        verify(queueCoordinatorService, times(1)).launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        );
        verify(queueCoordinatorService, never()).launchBackendBackfillAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyString(), anyString()
        );

        // A REJECTED pending ack carrying the duplicate reason must exist for this assignmentId
        List<BackendAssignmentAckPayload> pendingAcks = assignmentStore.listPendingAcks();
        assertTrue(
            pendingAcks.stream().anyMatch(ack ->
                ASSIGN_ID.equals(ack.assignmentId())
                    && "REJECTED".equals(ack.status())
                    && BackendAssignmentProcessingPlanner.DUPLICATE_PAYLOAD_REUSE_REASON.equals(ack.reason())
            ),
            "Expected a REJECTED pending ack with reason '" + BackendAssignmentProcessingPlanner.DUPLICATE_PAYLOAD_REUSE_REASON + "'"
        );
        assertEquals(2, transport.capturedRequestCount());
    }

    // ── Test 11: REJECT_VALIDATION — valid id, empty playerUuids → no launch ──

    @Test
    void invalidAssignmentWithValidIdRecordsRejectedAckAndDoesNotLaunch() {
        // Valid assignmentId but empty playerUuids → BackendAssignmentValidator rejects with
        // "Assignment must include at least one player." → REJECT_VALIDATION → recordProcessed(REJECTED)
        String body = "{\"schemaVersion\":1,\"receivedSequence\":1,"
            + "\"acknowledgedAssignmentAckIds\":[],\"assignments\":[{"
            + "\"assignmentType\":\"INITIAL_MATCH\","
            + "\"assignmentId\":\"" + ASSIGN_ID + "\","
            + "\"matchId\":\"nexori-match-test-1\","
            + "\"externalMatchId\":\"ext-invalid-1\","
            + "\"type\":\"CREATE_MATCH\","
            + "\"queueId\":\"queue-test\","
            + "\"playerUuids\":[],"
            + "\"expectedPlayerUuids\":[],"
            + "\"arenaId\":\"arena-test\","
            + "\"players\":[],"
            + "\"reportingServerId\":\"srv-1\","
            + "\"targetConnectionAddress\":\"\","
            + "\"modeId\":\"\",\"kitId\":\"\",\"ranked\":false,\"metadata\":{}"
            + "}]}";

        transport.enqueueResponse(200, body);
        service.handleTick(T1);
        service.handleTick(T2);

        // No launch attempted — validation failed before any launch planning
        verify(queueCoordinatorService, never()).launchBackendAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyList()
        );
        verify(queueCoordinatorService, never()).launchBackendBackfillAssignment(
            anyString(), anyString(), anyString(), anyString(), anyString(),
            anyList(), anyString(), anyString()
        );

        // A REJECTED pending ack must be present for the assignment
        Optional<BackendAssignmentAckPayload> ack = assignmentStore.findPendingAckForAssignment(ASSIGN_ID);
        assertTrue(ack.isPresent(), "Expected a pending REJECTED ack for assignmentId=" + ASSIGN_ID);
        assertEquals("REJECTED", ack.get().status());
    }
}
