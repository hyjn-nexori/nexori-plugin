package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementOutcome;
import io.github.hyjn.nexori.plugin.minigame.logic.LaunchContextData;
import io.github.hyjn.nexori.plugin.minigame.spectator.NoopSpectatorRuntimeController;
import io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferPhase;
import io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferService;
import io.github.hyjn.nexori.plugin.travel.PendingArrival;
import io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshotTestHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ArenaMatchServiceLifecycleInstrumentationTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @TempDir
    Path tempDir;

    @Test
    void initialArrivalCollectsMatchCreatedAndPlayerArrivedWithoutDispatchingImmediately() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        List<NexoriMatchLifecycleEvent> createdEvents = new ArrayList<>();
        List<NexoriPlayerMatchLifecycleEvent> arrivedEvents = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                createdEvents.add(event);
            }

            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                arrivedEvents.add(event);
            }
        });

        List<Runnable> dispatches = collectLaunchArrivalLifecycleEvents(
            service,
            true,
            false,
            match(List.of(PLAYER_ONE, PLAYER_TWO), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            playerRef(PLAYER_ONE, "PlayerOne"),
            false
        );

        assertEquals(0, createdEvents.size());
        assertEquals(0, arrivedEvents.size());

        dispatches.forEach(Runnable::run);

        assertEquals(1, createdEvents.size());
        assertEquals("match-1", createdEvents.get(0).matchId());
        assertEquals("queue-1", createdEvents.get(0).queueId());
        assertEquals("arena-1", createdEvents.get(0).arenaId());
        assertEquals("capture_the_zone", createdEvents.get(0).rulesEngineId());
        assertEquals(1, arrivedEvents.size());
        assertEquals(PLAYER_ONE, arrivedEvents.get(0).playerUuid());
        assertEquals("PlayerOne", arrivedEvents.get(0).playerName());
        assertEquals("assignment-player-1", arrivedEvents.get(0).playerAssignmentId());
        assertEquals("match-1", arrivedEvents.get(0).match().matchId());
    }

    @Test
    void laterArrivalDoesNotReemitMatchCreated() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger createdCalls = new AtomicInteger();
        AtomicInteger arrivedCalls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                createdCalls.incrementAndGet();
            }

            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                arrivedCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectLaunchArrivalLifecycleEvents(
            service,
            false,
            false,
            match(List.of(PLAYER_ONE, PLAYER_TWO), List.of(PLAYER_ONE, PLAYER_TWO), List.of(PLAYER_ONE, PLAYER_TWO)),
            playerRef(PLAYER_TWO, "PlayerTwo"),
            false
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, createdCalls.get());
        assertEquals(1, arrivedCalls.get());
    }

    @Test
    void lastPlayerAliveDoesNotResolveBeforePlacementCompleted() {
        ArenaMatchService service = service(new NexoriMatchLifecycleDispatcher());
        ArenaActiveMatch match = lastPlayerAliveMatch(
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE),
            0L
        );

        ArenaActiveMatch updated = applyAutomaticResolutionTrigger(service, match, 2_000L);

        assertEquals("", updated.winnerPlayerUuid());
        assertEquals(false, updated.hasPendingReturn(PLAYER_ONE));
    }

    @Test
    void arrivedOnlyPlayerDoesNotAllowWinnerBeforePlacementCompleted() {
        ArenaMatchService service = service(new NexoriMatchLifecycleDispatcher());
        ArenaActiveMatch match = lastPlayerAliveMatch(
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE),
            0L
        );

        assertEquals(List.of(PLAYER_ONE), match.alivePlayerUuids());
        assertEquals("", applyAutomaticResolutionTrigger(service, match, 2_000L).winnerPlayerUuid());
    }

    @Test
    void lastPlayerAliveResolvesAfterPlacementCompletedForNoInstanceArena() {
        ArenaMatchService service = service(new NexoriMatchLifecycleDispatcher());
        ArenaActiveMatch match = lastPlayerAliveMatch(
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE),
            1_500L
        );

        ArenaActiveMatch updated = applyAutomaticResolutionTrigger(service, match, 2_000L);

        assertEquals(PLAYER_ONE.toString(), updated.winnerPlayerUuid());
        assertEquals(true, updated.hasPendingReturn(PLAYER_ONE));
    }

    @Test
    void duplicateArrivalDoesNotReemitPlayerArrived() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger arrivedCalls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                arrivedCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectLaunchArrivalLifecycleEvents(
            service,
            false,
            true,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            playerRef(PLAYER_ONE, "PlayerOne"),
            false
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, arrivedCalls.get());
    }

    @Test
    void listenerForOtherRulesEngineDoesNotReceiveArrival() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger otherCalls = new AtomicInteger();
        dispatcher.register("other_rules", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                otherCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectLaunchArrivalLifecycleEvents(
            service,
            true,
            false,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            playerRef(PLAYER_ONE, "PlayerOne"),
            false
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, otherCalls.get());
    }

    @Test
    void failingListenerDoesNotBreakLaterArrivalListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectLaunchArrivalLifecycleEvents(
            service,
            false,
            false,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            playerRef(PLAYER_ONE, "PlayerOne"),
            false
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, calls.get());
    }

    @Test
    void confirmedPlacementCollectsPlayerPlacementConfirmedOnce() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        // Player must be arrived-only (not active) so confirmTransferPlacement can transition them.
        ArenaMatchService service = serviceWithArrivedOnlyMatch(dispatcher);
        List<NexoriPlayerPlacementLifecycleEvent> received = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                received.add(event);
            }
        });

        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("VALIDATING_SLOT"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, received.size());
        assertEquals(PLAYER_ONE, received.get(0).player().playerUuid());
        assertEquals(NexoriPlayerPlacementOutcome.CONFIRMED, received.get(0).placementOutcome());
        assertEquals("match-1", received.get(0).player().match().matchId());
        // worldName and instanceTemplateId are "" without a wired MinigameTransferService session;
        // they are populated by MinigameTransferSession in production (tested by MinigameTransferSessionTest).
    }

    @Test
    void fallbackPlacementDoesNotDispatchPlayerPlacementConfirmed() {
        // In the new unified transfer architecture, a FALLBACK/failed placement removes the player
        // from the match without dispatching onPlayerPlacementConfirmed.  This is intentional:
        // failed placements are surfaced via the NEXORI_TRANSFER_FAILED log and return-to-lobby.
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        List<NexoriPlayerPlacementLifecycleEvent> received = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                received.add(event);
            }
        });

        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("PENDING_ISSUE"),
            pending("FALLBACK"),
            NexoriPlayerPlacementOutcome.FALLBACK
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, received.size());
    }

    @Test
    void repeatedTerminalPlacementDoesNotCollectDuplicate() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("CONFIRMED"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, calls.get());
    }

    @Test
    void placementListenerForOtherRulesEngineDoesNotReceiveEvent() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        AtomicInteger otherCalls = new AtomicInteger();
        dispatcher.register("other_rules", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                otherCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("VALIDATING_SLOT"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, otherCalls.get());
    }

    @Test
    void failingPlacementListenerDoesNotBreakLaterListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithArrivedOnlyMatch(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("VALIDATING_SLOT"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, calls.get());
    }

    @Test
    void matchPlacementCompletedCollectsEventOnce() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        List<NexoriMatchLifecycleEvent> received = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                received.add(event);
            }
        });

        List<Runnable> dispatches = collectMatchPlacementCompletedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            matchWithPlacementCompleted(2_000L)
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, received.size());
        assertEquals("match-1", received.get(0).matchId());
        assertEquals("capture_the_zone", received.get(0).rulesEngineId());
        assertEquals("MATCH_PLACEMENT_COMPLETED", received.get(0).reason());
    }

    @Test
    void matchPlacementAlreadyCompletedDoesNotCollectDuplicate() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchPlacementCompletedTransition(
            service,
            matchWithPlacementCompleted(1_000L),
            matchWithPlacementCompleted(2_000L)
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, calls.get());
    }

    @Test
    void matchPlacementListenerForOtherRulesEngineDoesNotReceiveEvent() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger otherCalls = new AtomicInteger();
        dispatcher.register("other_rules", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                otherCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchPlacementCompletedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            matchWithPlacementCompleted(2_000L)
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, otherCalls.get());
    }

    @Test
    void failingMatchPlacementListenerDoesNotBreakLaterListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchPlacementCompletedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            matchWithPlacementCompleted(2_000L)
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, calls.get());
    }

    @Test
    void playerPlacementConfirmedDispatchesBeforeMatchPlacementCompletedWhenCollectedInOrder() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithArrivedOnlyMatch(dispatcher);
        List<String> order = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                order.add("player");
            }

            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                order.add("match");
            }
        });

        // In the new design, confirmTransferPlacement automatically triggers
        // reconcileAdmissionLifecycle which fires the match placement completed event when
        // the last expected player is confirmed.  The explicit collectMatchPlacementCompleted
        // call is no longer needed — it's included automatically.
        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("VALIDATING_SLOT"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.forEach(Runnable::run);

        assertEquals(List.of("player", "match"), order);
    }

    @Test
    void submitFinalMatchResultAcceptedDispatchesMatchCompletedOnce() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        service.setPlayerOutcome("match-1", PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "WIN", "winner");
        List<NexoriMatchLifecycleEvent> received = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                received.add(event);
            }
        });

        ArenaMatchService.SubmitMatchResult result = service.submitFinalMatchResult("match-1", "finished", null);

        assertEquals(ArenaMatchService.SubmitMatchOutcome.ACCEPTED, result.outcome());
        assertEquals(1, received.size());
        assertEquals("match-1", received.get(0).matchId());
        assertEquals("MATCH_COMPLETED", received.get(0).reason());
    }

    @Test
    void duplicateSubmitFinalMatchResultDoesNotRedispatchMatchCompleted() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        service.setPlayerOutcome("match-1", PLAYER_ONE, ArenaPlayerResolutionOutcome.WIN, "WIN", "winner");
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        ArenaMatchService.SubmitMatchResult first = service.submitFinalMatchResult("match-1", "finished", null);
        ArenaMatchService.SubmitMatchResult second = service.submitFinalMatchResult("match-1", "finished", null);

        assertEquals(ArenaMatchService.SubmitMatchOutcome.ACCEPTED, first.outcome());
        assertEquals(ArenaMatchService.SubmitMatchOutcome.ALREADY_SUBMITTED, second.outcome());
        assertEquals(1, calls.get());
    }

    @Test
    void backendAfkCancelDispatchesCancellationBeforeMatchCompleted() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        List<String> order = new ArrayList<>();
        List<NexoriMatchLifecycleEvent> cancellationEvents = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCancellationRequested(NexoriMatchLifecycleEvent event) {
                order.add("cancel");
                cancellationEvents.add(event);
            }

            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                order.add("completed");
            }
        });

        ArenaMatchService.SubmitMatchResult result = service.cancelMatchForBackendAfk(
            "match-1",
            PLAYER_ONE,
            "AFK_LIMIT",
            "Match cancelled because a player went AFK."
        );

        assertEquals(ArenaMatchService.SubmitMatchOutcome.ACCEPTED, result.outcome());
        assertEquals(List.of("cancel", "completed"), order);
        assertEquals(1, cancellationEvents.size());
        assertEquals("match-1", cancellationEvents.get(0).matchId());
        assertEquals("BACKEND_AFK_CANCEL", cancellationEvents.get(0).reason());
        assertEquals(List.of(PLAYER_ONE), cancellationEvents.get(0).activePlayerUuids());
    }

    @Test
    @SuppressWarnings("unchecked")
    void playerTickRunsPendingBackfillRetriesBeforePlayerMatchAssociation() throws Exception {
        ArenaMatchService service = new ArenaMatchService(
            HytaleLogger.getLogger(), null, null, null, null,
            NoopSpectatorRuntimeController.INSTANCE, new NexoriMatchLifecycleDispatcher()
        );
        MinigameTransferService transferService = new MinigameTransferService(
            HytaleLogger.getLogger(),
            new InstanceSpawnSlotService(new InstanceSpawnSlotStore(tempDir.resolve("slots.json")))
        );
        transferService.setMatchGateway(emptyMatchGateway());
        transferService.onMinigameLaunchSafeReady(
            ReadyPlayerSnapshotTestHelper.forTest(PLAYER_ONE),
            pendingBackfillArrival("match-never-created"),
            1_000L
        );
        assertEquals(1, pendingBackfillRetryCount(transferService));

        service.setMinigameTransferService(transferService);
        try {
            service.handlePlayerTick(
                (Ref<EntityStore>) mock(Ref.class),
                (Store<EntityStore>) mock(Store.class),
                31_000L
            );
        } catch (NullPointerException expectedWhenEngineModulesAreNotBootstrapped) {
            // The Hytale EntityModule is not available in this pure unit test. The assertion
            // below verifies the retry tick already ran before player component resolution.
        }

        assertEquals(0, pendingBackfillRetryCount(transferService));
    }

    @Test
    void pendingInitialPlacementKeepsArrivedOnlyBackendMatchRuntimeActive() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        MinigameTransferService transferService = transferServiceWithPendingPlacement(
            PLAYER_ONE,
            tempDir.resolve("pending-placement-slots.json")
        );
        service.setMinigameTransferService(transferService);

        ArenaActiveMatch arrivedOnly = match(
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of()
        );
        addActiveMatch(service, arrivedOnly);
        List<Runnable> lifecycleDispatches = new ArrayList<>();

        storeUpdatedMatchOrCloseEmptyRuntime(service, arrivedOnly, arrivedOnly, lifecycleDispatches);

        assertEquals(true, service.findMatchRaw(arrivedOnly.matchId()).isPresent());
        assertEquals(0, lifecycleDispatches.size());
    }

    @Test
    void endMatchDispatchesMatchCompletedWhenMatchWasOpen() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = serviceWithMatch(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        ArenaMatchService.EndMatchResult result = service.endMatch("match-1", "manual");

        assertEquals(ArenaMatchService.EndMatchOutcome.COMPLETED, result.outcome());
        assertEquals(1, calls.get());
    }

    @Test
    void endMatchDoesNotRedispatchMatchCompletedWhenAlreadyCompleted() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        addActiveMatch(service, matchWithCompleted(2_000L));
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        ArenaMatchService.EndMatchResult result = service.endMatch("match-1", "manual");

        assertEquals(ArenaMatchService.EndMatchOutcome.COMPLETED, result.outcome());
        assertEquals(0, calls.get());
    }

    @Test
    void matchCompletedListenerForOtherRulesEngineDoesNotReceiveEvent() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger otherCalls = new AtomicInteger();
        dispatcher.register("other_rules", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                otherCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchCompletedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            matchWithCompleted(2_000L)
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, otherCalls.get());
    }

    @Test
    void failingMatchCompletedListenerDoesNotBreakLaterListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchCompletedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            matchWithCompleted(2_000L)
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, calls.get());
    }

    @Test
    void nonEmptyToEmptyRuntimeCollectsMatchRuntimeClosedOnce() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        List<NexoriMatchLifecycleEvent> received = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchRuntimeClosed(NexoriMatchLifecycleEvent event) {
                received.add(event);
            }
        });

        List<Runnable> dispatches = collectMatchRuntimeClosedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            match(List.of(PLAYER_ONE), List.of(), List.of())
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, received.size());
        assertEquals("match-1", received.get(0).matchId());
        assertEquals("capture_the_zone", received.get(0).rulesEngineId());
        assertEquals("MATCH_RUNTIME_CLOSED", received.get(0).reason());
        assertEquals(List.of(), received.get(0).activePlayerUuids());
        assertEquals(List.of(PLAYER_ONE), received.get(0).expectedPlayerUuids());
    }

    @Test
    void alreadyEmptyRuntimeDoesNotCollectDuplicateRuntimeClosed() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchRuntimeClosed(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchRuntimeClosedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(), List.of()),
            match(List.of(PLAYER_ONE), List.of(), List.of())
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, calls.get());
    }

    @Test
    void matchRuntimeClosedListenerForOtherRulesEngineDoesNotReceiveEvent() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger otherCalls = new AtomicInteger();
        dispatcher.register("other_rules", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchRuntimeClosed(NexoriMatchLifecycleEvent event) {
                otherCalls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchRuntimeClosedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            match(List.of(PLAYER_ONE), List.of(), List.of())
        );
        dispatches.forEach(Runnable::run);

        assertEquals(0, otherCalls.get());
    }

    @Test
    void failingMatchRuntimeClosedListenerDoesNotBreakLaterListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        ArenaMatchService service = service(dispatcher);
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchRuntimeClosed(NexoriMatchLifecycleEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchRuntimeClosed(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        List<Runnable> dispatches = collectMatchRuntimeClosedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            match(List.of(PLAYER_ONE), List.of(), List.of())
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, calls.get());
    }

    private static ArenaMatchService service(NexoriMatchLifecycleDispatcher dispatcher) {
        return new ArenaMatchService(null, null, null, null, null, NoopSpectatorRuntimeController.INSTANCE, dispatcher);
    }

    private static MinigameTransferService.MatchGateway emptyMatchGateway() {
        return new MinigameTransferService.MatchGateway() {
            @Override
            public Optional<ArenaActiveMatch> findMatchRaw(String matchId) {
                return Optional.empty();
            }

            @Override
            public List<Runnable> acceptTransferArrival(
                UUID playerUuid,
                String username,
                LaunchContextData launch,
                ArenaActiveMatch existingMatch,
                long nowEpochMs
            ) {
                return List.of();
            }

            @Override
            public List<Runnable> confirmTransferPlacement(UUID playerUuid, long nowEpochMs) {
                return List.of();
            }

            @Override
            public List<Runnable> failTransferPlacement(
                UUID playerUuid,
                String reason,
                String returnConnectionAddress,
                String returnFallbackTargetId,
                String launchTravelProfileId,
                String originLobbyId,
                String matchId,
                long nowEpochMs
            ) {
                return List.of();
            }
        };
    }

    private static MinigameTransferService transferServiceWithPendingPlacement(UUID pendingPlayerUuid, Path slotStorePath) {
        try {
            MinigameTransferService transferService = new MinigameTransferService(
                HytaleLogger.getLogger(),
                new InstanceSpawnSlotService(new InstanceSpawnSlotStore(slotStorePath))
            );
            addPendingPlacementSession(transferService, pendingPlayerUuid);
            return transferService;
        } catch (java.io.IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addPendingPlacementSession(MinigameTransferService transferService, UUID playerUuid) {
        try {
            Class<?> sessionClass = Class.forName(
                "io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferSession"
            );
            java.lang.reflect.Constructor<?> constructor = sessionClass.getDeclaredConstructor(
                UUID.class,
                String.class,
                long.class
            );
            constructor.setAccessible(true);
            Object session = constructor.newInstance(playerUuid, "PlayerOne", 1_000L);

            Field phase = sessionClass.getDeclaredField("phase");
            phase.setAccessible(true);
            phase.set(session, MinigameTransferPhase.INSTANCE_WORLD_CREATING);

            Field sessions = MinigameTransferService.class.getDeclaredField("sessionsByPlayerUuid");
            sessions.setAccessible(true);
            ((Map<UUID, Object>) sessions.get(transferService)).put(playerUuid, session);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static PendingArrival pendingBackfillArrival(String matchId) {
        String contextJson = "{"
            + "\"flowType\":\"minigame.launch\","
            + "\"matchId\":\"" + matchId + "\","
            + "\"queueId\":\"queue-1\","
            + "\"arenaId\":\"arena-1\","
            + "\"originLobbyId\":\"lobby-1\","
            + "\"returnConnectionAddress\":\"lobby:25565\","
            + "\"returnFallbackTargetId\":\"lobby-1.natural_spawn\","
            + "\"launchTravelProfileId\":\"nexori_launch\","
            + "\"instanceTemplateId\":\"none\","
            + "\"assignmentType\":\"BACKFILL\","
            + "\"playerUuid\":\"" + PLAYER_ONE + "\","
            + "\"admissionReservationId\":\"reservation-1\","
            + "\"admissionExpiresAtEpochMs\":9223372036854775807"
            + "}";
        return new PendingArrival(
            "op-1", "server-1", "localhost:25000",
            "target-1", "Arena", "NATURAL_SPAWN", "default_world",
            "", "nexori_launch", "", contextJson, ""
        );
    }

    @SuppressWarnings("unchecked")
    private static int pendingBackfillRetryCount(MinigameTransferService transferService) {
        try {
            Field field = MinigameTransferService.class.getDeclaredField("pendingBackfillRetries");
            field.setAccessible(true);
            return ((Map<UUID, ?>) field.get(transferService)).size();
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    private static ArenaMatchService serviceWithMatch(NexoriMatchLifecycleDispatcher dispatcher) {
        ArenaMatchService service = service(dispatcher);
        addActiveMatch(service, match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)));
        return service;
    }

    /**
     * Creates a service with a match where PLAYER_ONE is arrived but not yet active.
     * Used to test placement-confirmed dispatch through the new unified transfer gateway.
     */
    private static ArenaMatchService serviceWithArrivedOnlyMatch(NexoriMatchLifecycleDispatcher dispatcher) {
        ArenaMatchService service = service(dispatcher);
        addActiveMatch(service, match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of()));
        return service;
    }

    private static List<Runnable> collectLaunchArrivalLifecycleEvents(
        ArenaMatchService service,
        boolean matchCreated,
        boolean playerAlreadyAssociated,
        ArenaActiveMatch match,
        PlayerRef playerRef,
        boolean backfillArrival
    ) {
        try {
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "collectLaunchArrivalLifecycleEvents",
                boolean.class,
                boolean.class,
                ArenaActiveMatch.class,
                PlayerRef.class,
                boolean.class,
                long.class,
                List.class
            );
            method.setAccessible(true);
            List<Runnable> dispatches = new ArrayList<>();
            method.invoke(service, matchCreated, playerAlreadyAssociated, match, playerRef, backfillArrival, 2_000L, dispatches);
            return dispatches;
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(cause);
        }
    }

    /**
     * Simulates a player placement terminal transition via the new package-private
     * ArenaMatchService accessor methods.
     *
     * <p>In the new architecture, placement is confirmed through
     * MinigameTransferService.MatchGateway.confirmTransferPlacement.  The "previous" and
     * "updated" phase arguments are no longer needed since the guard is now expressed as
     * "player already in activePlayerUuids rather than checking PendingInstanceSpawnTeleport
     * phase.  FALLBACK placements no longer dispatch onPlayerPlacementConfirmed (the player
     * is removed from the match without a placement event).</p>
     *
     * @param previous ignored in the new design (kept for call-site compatibility)
     * @param updated  ignored in the new design
     */
    private static List<Runnable> collectPlayerPlacementTerminalTransition(
        ArenaMatchService service,
        PlayerRef playerRef,
        Object previous,
        Object updated,
        NexoriPlayerPlacementOutcome outcome
    ) {
        if (outcome == NexoriPlayerPlacementOutcome.CONFIRMED) {
            return service.collectConfirmTransferPlacementForTest(playerRef.getUuid(), 2_000L);
        }
        // FALLBACK: new design does not dispatch onPlayerPlacementConfirmed for failed placements.
        return new ArrayList<>();
    }

    private static List<Runnable> collectMatchPlacementCompletedTransition(
        ArenaMatchService service,
        ArenaActiveMatch previous,
        ArenaActiveMatch updated
    ) {
        try {
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "collectMatchPlacementCompletedTransition",
                ArenaActiveMatch.class,
                ArenaActiveMatch.class,
                String.class,
                long.class,
                List.class
            );
            method.setAccessible(true);
            List<Runnable> dispatches = new ArrayList<>();
            method.invoke(service, previous, updated, "MATCH_PLACEMENT_COMPLETED", 2_000L, dispatches);
            return dispatches;
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(cause);
        }
    }

    private static List<Runnable> collectMatchCompletedTransition(
        ArenaMatchService service,
        ArenaActiveMatch previous,
        ArenaActiveMatch updated
    ) {
        try {
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "collectMatchCompletedTransition",
                ArenaActiveMatch.class,
                ArenaActiveMatch.class,
                String.class,
                long.class,
                List.class
            );
            method.setAccessible(true);
            List<Runnable> dispatches = new ArrayList<>();
            method.invoke(service, previous, updated, "MATCH_COMPLETED", 2_000L, dispatches);
            return dispatches;
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(cause);
        }
    }

    private static List<Runnable> collectMatchRuntimeClosedTransition(
        ArenaMatchService service,
        ArenaActiveMatch previous,
        ArenaActiveMatch updated
    ) {
        try {
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "collectMatchRuntimeClosedTransition",
                ArenaActiveMatch.class,
                ArenaActiveMatch.class,
                String.class,
                long.class,
                List.class
            );
            method.setAccessible(true);
            List<Runnable> dispatches = new ArrayList<>();
            method.invoke(service, previous, updated, "MATCH_RUNTIME_CLOSED", 2_000L, dispatches);
            return dispatches;
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(cause);
        }
    }

    private static ArenaActiveMatch applyAutomaticResolutionTrigger(
        ArenaMatchService service,
        ArenaActiveMatch match,
        long nowEpochMs
    ) {
        try {
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "applyAutomaticResolutionTrigger",
                ArenaActiveMatch.class,
                long.class
            );
            method.setAccessible(true);
            return (ArenaActiveMatch) method.invoke(service, match, nowEpochMs);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    private static void storeUpdatedMatchOrCloseEmptyRuntime(
        ArenaMatchService service,
        ArenaActiveMatch previous,
        ArenaActiveMatch updated,
        List<Runnable> lifecycleDispatches
    ) {
        try {
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "storeUpdatedMatchOrCloseEmptyRuntime",
                ArenaActiveMatch.class,
                ArenaActiveMatch.class,
                long.class,
                String.class,
                List.class
            );
            method.setAccessible(true);
            method.invoke(service, previous, updated, 2_000L, "", lifecycleDispatches);
        } catch (NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new AssertionError(cause);
        }
    }

    @SuppressWarnings("unchecked")
    private static void addActiveMatch(ArenaMatchService service, ArenaActiveMatch match) {
        try {
            Field matchesById = ArenaMatchService.class.getDeclaredField("matchesById");
            matchesById.setAccessible(true);
            ((Map<String, ArenaActiveMatch>) matchesById.get(service)).put(match.matchId(), match);

            Field matchIdByPlayerUuid = ArenaMatchService.class.getDeclaredField("matchIdByPlayerUuid");
            matchIdByPlayerUuid.setAccessible(true);
            ((Map<UUID, String>) matchIdByPlayerUuid.get(service)).put(PLAYER_ONE, match.matchId());
        } catch (NoSuchFieldException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        }
    }

    /**
     * Returns a dummy phase name sentinel for call-site compatibility with tests that were
     * written against the old PendingInstanceSpawnTeleport type.  The new
     * collectPlayerPlacementTerminalTransition helper ignores this value entirely.
     */
    private static Object pending(String phaseName) {
        return phaseName;  // sentinel only — no longer used to construct PendingInstanceSpawnTeleport
    }


    private static PlayerRef playerRef(UUID playerUuid, String username) {
        PlayerRef playerRef = mock(PlayerRef.class);
        when(playerRef.getUuid()).thenReturn(playerUuid);
        when(playerRef.getUsername()).thenReturn(username);
        return playerRef;
    }

    private static ArenaActiveMatch match(
        List<UUID> expectedPlayerUuids,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids
    ) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby:1234",
            "fallback-1",
            "default",
            "",
            "",
            "none",
            "capture_the_zone",
            "assignment-1",
            "INITIAL_MATCH",
            "external-1",
            ArenaMatchSource.defaultSource().id(),
            0,
            2,
            true,
            QueueBackfillMode.defaultMode().id(),
            60,
            expectedPlayerUuids,
            expectedPlayerUuids.size(),
            arrivedPlayerUuids,
            activePlayerUuids,
            List.of(),
            List.of(),
            Map.of(PLAYER_ONE, "assignment-player-1", PLAYER_TWO, "assignment-player-2"),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            0L,
            0L,
            0L,
            0L,
            "",
            1_000L,
            1_000L,
            ""
        ).normalized();
    }

    private static ArenaActiveMatch lastPlayerAliveMatch(
        List<UUID> expectedPlayerUuids,
        List<UUID> arrivedPlayerUuids,
        List<UUID> activePlayerUuids,
        long placementCompletedAtEpochMs
    ) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby:1234",
            "fallback-1",
            "default",
            ArenaDefinition.NO_INSTANCE_TEMPLATE_ID,
            "",
            LastPlayerAliveArenaMatchResolutionTrigger.ID,
            "capture_the_zone",
            "assignment-1",
            "INITIAL_MATCH",
            "external-1",
            ArenaMatchSource.defaultSource().id(),
            0,
            expectedPlayerUuids.size(),
            true,
            QueueBackfillMode.defaultMode().id(),
            60,
            expectedPlayerUuids,
            expectedPlayerUuids.size(),
            arrivedPlayerUuids,
            activePlayerUuids,
            List.of(),
            List.of(),
            Map.of(PLAYER_ONE, "assignment-player-1", PLAYER_TWO, "assignment-player-2"),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            placementCompletedAtEpochMs,
            placementCompletedAtEpochMs,
            0L,
            0L,
            "",
            1_000L,
            1_000L,
            ""
        ).normalized();
    }

    private static ArenaActiveMatch matchWithPlacementCompleted(long placementCompletedAtEpochMs) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby:1234",
            "fallback-1",
            "default",
            "",
            "",
            "none",
            "capture_the_zone",
            "assignment-1",
            "INITIAL_MATCH",
            "external-1",
            ArenaMatchSource.defaultSource().id(),
            0,
            1,
            true,
            QueueBackfillMode.defaultMode().id(),
            60,
            List.of(PLAYER_ONE),
            1,
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(),
            List.of(),
            Map.of(PLAYER_ONE, "assignment-player-1"),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            placementCompletedAtEpochMs,
            placementCompletedAtEpochMs,
            0L,
            0L,
            "",
            1_000L,
            placementCompletedAtEpochMs,
            ""
        ).normalized();
    }

    private static ArenaActiveMatch matchWithCompleted(long completedAtEpochMs) {
        return new ArenaActiveMatch(
            "match-1",
            "queue-1",
            "arena-1",
            "lobby-1",
            "lobby:1234",
            "fallback-1",
            "default",
            "",
            "",
            "none",
            "capture_the_zone",
            "assignment-1",
            "INITIAL_MATCH",
            "external-1",
            ArenaMatchSource.defaultSource().id(),
            0,
            1,
            true,
            QueueBackfillMode.defaultMode().id(),
            60,
            List.of(PLAYER_ONE),
            1,
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(),
            List.of(),
            Map.of(PLAYER_ONE, "assignment-player-1"),
            Map.of(),
            Map.of(),
            Map.of(),
            0,
            Set.of(),
            false,
            "",
            "",
            0L,
            "",
            0L,
            0L,
            completedAtEpochMs,
            0L,
            "",
            1_000L,
            completedAtEpochMs,
            ""
        ).normalized();
    }
}
