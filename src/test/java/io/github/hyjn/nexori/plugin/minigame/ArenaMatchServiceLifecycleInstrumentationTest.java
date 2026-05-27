package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.math.vector.Transform;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementOutcome;
import io.github.hyjn.nexori.plugin.minigame.spectator.NoopSpectatorRuntimeController;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class ArenaMatchServiceLifecycleInstrumentationTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

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
            pending("VALIDATING_SLOT"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.forEach(Runnable::run);

        assertEquals(1, received.size());
        assertEquals(PLAYER_ONE, received.get(0).player().playerUuid());
        assertEquals(NexoriPlayerPlacementOutcome.CONFIRMED, received.get(0).placementOutcome());
        assertEquals("match-1", received.get(0).player().match().matchId());
        assertEquals("world-match-1", received.get(0).worldName());
        assertEquals("template-1", received.get(0).instanceTemplateId());
    }

    @Test
    void fallbackPlacementCollectsPlayerPlacementConfirmedOnce() {
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

        assertEquals(1, received.size());
        assertEquals(NexoriPlayerPlacementOutcome.FALLBACK, received.get(0).placementOutcome());
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
        ArenaMatchService service = serviceWithMatch(dispatcher);
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
        ArenaMatchService service = serviceWithMatch(dispatcher);
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

        List<Runnable> dispatches = collectPlayerPlacementTerminalTransition(
            service,
            playerRef(PLAYER_ONE, "PlayerOne"),
            pending("VALIDATING_SLOT"),
            pending("CONFIRMED"),
            NexoriPlayerPlacementOutcome.CONFIRMED
        );
        dispatches.addAll(collectMatchPlacementCompletedTransition(
            service,
            match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)),
            matchWithPlacementCompleted(2_000L)
        ));
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

    private static ArenaMatchService serviceWithMatch(NexoriMatchLifecycleDispatcher dispatcher) {
        ArenaMatchService service = service(dispatcher);
        addActiveMatch(service, match(List.of(PLAYER_ONE), List.of(PLAYER_ONE), List.of(PLAYER_ONE)));
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

    private static List<Runnable> collectPlayerPlacementTerminalTransition(
        ArenaMatchService service,
        PlayerRef playerRef,
        Object previous,
        Object updated,
        NexoriPlayerPlacementOutcome outcome
    ) {
        try {
            Class<?> pendingClass = Class.forName("io.github.hyjn.nexori.plugin.minigame.ArenaMatchService$PendingInstanceSpawnTeleport");
            Method method = ArenaMatchService.class.getDeclaredMethod(
                "collectPlayerPlacementTerminalTransition",
                PlayerRef.class,
                pendingClass,
                pendingClass,
                NexoriPlayerPlacementOutcome.class,
                String.class,
                long.class,
                List.class
            );
            method.setAccessible(true);
            List<Runnable> dispatches = new ArrayList<>();
            method.invoke(service, playerRef, previous, updated, outcome, "PLACEMENT_TEST", 2_000L, dispatches);
            return dispatches;
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException exception) {
            throw new AssertionError(exception);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(cause);
        }
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

    @SuppressWarnings("unchecked")
    private static Object pending(String phaseName) {
        try {
            Class<?> pendingClass = Class.forName("io.github.hyjn.nexori.plugin.minigame.ArenaMatchService$PendingInstanceSpawnTeleport");
            Class<?> phaseClass = Class.forName("io.github.hyjn.nexori.plugin.minigame.ArenaMatchService$PlacementPhase");
            Constructor<?> constructor = pendingClass.getDeclaredConstructor(
                String.class,
                String.class,
                Transform.class,
                phaseClass,
                boolean.class,
                long.class,
                long.class,
                int.class
            );
            constructor.setAccessible(true);
            Object phase = Enum.valueOf((Class<Enum>) phaseClass.asSubclass(Enum.class), phaseName);
            return constructor.newInstance(
                "world-match-1",
                "template-1",
                new Transform(0.0F, 64.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                phase,
                true,
                1_000L,
                1_000L,
                "CONFIRMED".equals(phaseName) ? 2 : 0
            );
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
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
