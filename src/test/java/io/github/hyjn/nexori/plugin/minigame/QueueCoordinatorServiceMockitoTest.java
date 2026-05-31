package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.backend.testsupport.BackendTestFixtures;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;
import io.github.hyjn.nexori.plugin.inventory.InventorySnapshotService;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferPolicyStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;
import io.github.hyjn.nexori.plugin.inventory.PlayerSaveRepository;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetStore;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Mockito-based tests for {@link QueueCoordinatorService}.
 *
 * <p>Tests exercise join, leave, isQueued, findQueueHudState, and advanceCountdowns —
 * none of which invoke {@link SecureTravelService} directly. Only {@link QueueService}
 * and {@link ArenaService} are mocked (they are not final). All other dependencies are
 * built as real instances using @TempDir.</p>
 *
 * <p>BLOCKED: advanceWorldTick tests that call launchReadyBatches() — those use
 * Universe.get().getPlayer() which cannot be exercised outside the Hytale server runtime.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
final class QueueCoordinatorServiceMockitoTest {

    private static final long NOW = 5_000_000L;

    private static final String QUEUE_ID = "queue-test-alpha";
    private static final String QUEUE_ID_B = "queue-test-beta";
    private static final String LOBBY_ID = "lobby-main";
    private static final String PORTAL_ID = "portal-main";

    private static final UUID PLAYER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // HytaleLogger cannot be mocked (private constructor / Flogger internals)
    private HytaleLogger logger;

    // QueueService and ArenaService are NOT final — can be mocked
    @Mock QueueService queueService;
    @Mock ArenaService arenaService;
    // ServerIdentityManager is NOT final — can be mocked
    @Mock ServerIdentityManager identityManager;

    @TempDir Path tempDir;

    private QueueCoordinatorService service;

    // A LOCAL_FIFO queue with a high minPlayers so countdown does NOT trigger after join
    private QueueDefinition enabledQueue;
    // A LOCAL_FIFO queue with minPlayers=2 and countdownSeconds=0 for countdown tests
    private QueueDefinition countdownQueue;

    @BeforeEach
    void setUp() throws Exception {
        logger = HytaleLogger.getLogger();

        // ── Build real dependency chain for SecureTravelService ───────────────
        ServerIdentity serverIdentity = BackendTestFixtures.testServerIdentity();
        TrustBundleStore trustBundleStore = new TrustBundleStore(tempDir.resolve("trust.json"));
        DiagnosticsService diagnosticsService = new DiagnosticsService(
            logger,
            tempDir,
            serverIdentity,
            () -> "",
            "test-1.0"
        );
        DestinationTargetService destinationTargetService = new DestinationTargetService(
            new DestinationTargetStore(tempDir.resolve("targets.json")),
            diagnosticsService
        );
        SecureReferralService secureReferralService = new SecureReferralService(
            logger,
            identityManager,
            serverIdentity,
            trustBundleStore,
            diagnosticsService
        );
        InventoryTransferService inventoryTransferService = new InventoryTransferService(
            logger,
            new InventoryTransferBackupStore(tempDir.resolve("inv-backups.json")),
            new InventoryTransferReceiptStore(tempDir.resolve("inv-receipts.json")),
            new InventoryTransferPolicyStore(tempDir.resolve("inv-policy.json")),
            new PlayerSaveRepository(logger),
            new InventorySnapshotService(),
            secureReferralService,
            diagnosticsService
        );
        InstanceSpawnSlotService instanceSpawnSlotService = new InstanceSpawnSlotService(
            new InstanceSpawnSlotStore(tempDir.resolve("spawn-slots.json"))
        );
        SecureTravelService secureTravelService = new SecureTravelService(
            logger,
            tempDir,
            serverIdentity,
            trustBundleStore,
            destinationTargetService,
            secureReferralService,
            inventoryTransferService,
            diagnosticsService
        );

        MatchSessionService matchSessionService = new MatchSessionService(
            new MatchSessionStore(tempDir.resolve("sessions.json"))
        );
        LocalConnectionAddressService localConnectionAddressService = new LocalConnectionAddressService(
            tempDir.resolve("connection-address.txt")
        );

        // ── Queue definitions ──────────────────────────────────────────────────

        // minPlayers=100 ensures a single join cannot start countdown
        enabledQueue = new QueueDefinition(
            QUEUE_ID,
            "Test Queue Alpha",
            List.of("arena-test"),
            100,    // minPlayers — high so no countdown after single join
            200,
            10,
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );

        // minPlayers=2, countdownSeconds=0 so countdown expires immediately
        countdownQueue = new QueueDefinition(
            QUEUE_ID,
            "Test Queue Alpha",
            List.of("arena-test"),
            2,
            4,
            0,    // countdownSeconds=0 → expires immediately at join time
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );

        service = new QueueCoordinatorService(
            queueService,
            arenaService,
            matchSessionService,
            localConnectionAddressService,
            secureTravelService,
            logger
        );
    }

    // ── Test 1: joinQueue with missing queue returns QUEUE_MISSING ───────────

    @Test
    void joinQueueMissingDoesNotTrackPlayer() {
        when(queueService.find("missing")).thenReturn(Optional.empty());

        QueueCoordinatorService.JoinResult result = service.joinQueue(PLAYER_1, "Alice", "missing", LOBBY_ID, PORTAL_ID);

        assertEquals(QueueCoordinatorService.JoinOutcome.QUEUE_MISSING, result.outcome());
        assertFalse(service.isQueued(PLAYER_1));
    }

    // ── Test 2: joinQueue with disabled queue returns QUEUE_DISABLED ─────────

    @Test
    void joinQueueDisabledDoesNotTrackPlayer() {
        QueueDefinition disabledQueue = new QueueDefinition(
            QUEUE_ID,
            "Disabled Queue",
            List.of("arena-test"),
            2,
            4,
            10,
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            false  // enabled = false
        );
        when(queueService.find(QUEUE_ID)).thenReturn(Optional.of(disabledQueue));

        QueueCoordinatorService.JoinResult result = service.joinQueue(PLAYER_1, "Alice", QUEUE_ID, LOBBY_ID, PORTAL_ID);

        assertEquals(QueueCoordinatorService.JoinOutcome.QUEUE_DISABLED, result.outcome());
        assertFalse(service.isQueued(PLAYER_1));
    }

    // ── Test 3: successful join adds player mapping and waiting state ─────────

    @Test
    void joinQueueSuccessAddsPlayerMappingAndWaitingState() {
        when(queueService.find(QUEUE_ID)).thenReturn(Optional.of(enabledQueue));

        QueueCoordinatorService.JoinResult result = service.joinQueue(PLAYER_1, "Alice", QUEUE_ID, LOBBY_ID, PORTAL_ID);

        assertEquals(QueueCoordinatorService.JoinOutcome.JOINED, result.outcome());
        assertTrue(service.isQueued(PLAYER_1));
        assertEquals(QUEUE_ID, service.findQueuedQueueId(PLAYER_1).orElse(null));

        // getQueueState returns state with player in waiting
        Optional<QueueRuntimeState> state = service.getQueueState(QUEUE_ID);
        assertTrue(state.isPresent());
        boolean foundInWaiting = state.get().waitingMembers().stream()
            .anyMatch(m -> m.playerUuid().equals(PLAYER_1));
        assertTrue(foundInWaiting, "Player should be in waiting members after join");
    }

    // ── Test 4: duplicate join returns ALREADY_QUEUED ────────────────────────

    @Test
    void duplicateJoinReturnsAlreadyQueuedAndDoesNotMovePlayer() {
        QueueDefinition queueB = new QueueDefinition(
            QUEUE_ID_B,
            "Queue B",
            List.of("arena-test"),
            100,
            200,
            10,
            "",
            QueueMatchmakingMode.LOCAL_FIFO.id(),
            true
        );

        // First join to queue A
        when(queueService.find(QUEUE_ID)).thenReturn(Optional.of(enabledQueue));
        QueueCoordinatorService.JoinResult first = service.joinQueue(PLAYER_1, "Alice", QUEUE_ID, LOBBY_ID, PORTAL_ID);
        assertEquals(QueueCoordinatorService.JoinOutcome.JOINED, first.outcome());

        // Second join to queue B — should return ALREADY_QUEUED
        when(queueService.find(QUEUE_ID_B)).thenReturn(Optional.of(queueB));
        QueueCoordinatorService.JoinResult second = service.joinQueue(PLAYER_1, "Alice", QUEUE_ID_B, LOBBY_ID, PORTAL_ID);

        assertEquals(QueueCoordinatorService.JoinOutcome.ALREADY_QUEUED, second.outcome());
        // Player is still in queue A, not moved
        assertEquals(QUEUE_ID, service.findQueuedQueueId(PLAYER_1).orElse(null));
    }

    // ── Test 5: leaveCurrentQueue removes player mapping ─────────────────────

    @Test
    void leaveCurrentQueueRemovesPlayerMappingAndState() {
        when(queueService.find(QUEUE_ID)).thenReturn(Optional.of(enabledQueue));
        service.joinQueue(PLAYER_1, "Alice", QUEUE_ID, LOBBY_ID, PORTAL_ID);

        // leaveCurrentQueue calls queueService.find internally via removePlayerFromQueue
        QueueCoordinatorService.LeaveResult result = service.leaveCurrentQueue(PLAYER_1);

        assertEquals(QueueCoordinatorService.LeaveOutcome.LEFT, result.outcome());
        assertFalse(service.isQueued(PLAYER_1));
    }

    // ── Test 6: leaveCurrentQueue when not queued returns NOT_QUEUED ─────────

    @Test
    void leaveCurrentQueueWhenNotQueuedReturnsNotQueued() {
        QueueCoordinatorService.LeaveResult result = service.leaveCurrentQueue(PLAYER_2);

        assertEquals(QueueCoordinatorService.LeaveOutcome.NOT_QUEUED, result.outcome());
    }

    // ── Test 7: advanceCountdowns promotes expired countdown to READY ─────────
    //
    // Setup: 2 players join a queue with minPlayers=2 and countdownSeconds=0.
    // After join, the QueueMembershipPlanner triggers countdown (enough players).
    // advanceCountdowns(NOW + 1) fires with nowEpochMs >= countdownEndsAtEpochMs → READY.
    // We do NOT call launchReadyBatches to avoid Universe.get().

    @Test
    void advanceCountdownsPromotesExpiredCountdownToReady() {
        when(queueService.find(QUEUE_ID)).thenReturn(Optional.of(countdownQueue));
        when(queueService.list()).thenReturn(List.of(countdownQueue));

        // Join two players — this should trigger countdown since minPlayers=2 is met
        service.joinQueue(PLAYER_1, "Alice", QUEUE_ID, LOBBY_ID, PORTAL_ID);
        service.joinQueue(PLAYER_2, "Bob", QUEUE_ID, LOBBY_ID, PORTAL_ID);

        // advanceCountdowns: countdownSeconds=0 means countdownEndsAt = joinTime + 0 = joinTime
        // Use System.currentTimeMillis() + 1 to ensure we are past the countdown end
        // (join internally uses System.currentTimeMillis() which is much greater than NOW)
        service.advanceCountdowns(System.currentTimeMillis() + 1_000L);

        // getQueueState also calls state(queueId, System.currentTimeMillis()) internally
        Optional<QueueRuntimeState> stateOpt = service.getQueueState(QUEUE_ID);
        assertTrue(stateOpt.isPresent(), "Queue state should be present");
        QueueRuntimeState state = stateOpt.get();
        // After advanceCountdowns, the state stored in stateByQueueId should be READY
        assertEquals(QueuePhase.READY, state.phase(),
            "Queue should be in READY phase after countdown expires");
    }

    // ── Test 8: findQueueHudState reflects queued player state ───────────────

    @Test
    void findQueueHudStateReflectsQueuedPlayerState() {
        when(queueService.find(QUEUE_ID)).thenReturn(Optional.of(enabledQueue));
        service.joinQueue(PLAYER_1, "Alice", QUEUE_ID, LOBBY_ID, PORTAL_ID);

        Optional<QueueCoordinatorService.QueueHudState> hud = service.findQueueHudState(PLAYER_1, NOW);

        assertTrue(hud.isPresent(), "HUD state should be present for queued player");
        QueueCoordinatorService.QueueHudState hudState = hud.get();
        assertEquals(QUEUE_ID, hudState.queueId());
        assertEquals(QueuePhase.WAITING, hudState.phase());
        assertEquals(1, hudState.queuedPlayers(), "One player should be counted in HUD state");
    }
}
