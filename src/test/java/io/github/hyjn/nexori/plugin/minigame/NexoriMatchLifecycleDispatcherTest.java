package io.github.hyjn.nexori.plugin.minigame;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriListenerRegistration;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchLifecycleListener;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriMatchPlacementState;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerMatchLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementLifecycleEvent;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerPlacementOutcome;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class NexoriMatchLifecycleDispatcherTest {

    private static final UUID PLAYER_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PLAYER_TWO = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void dispatchesRegisteredListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        AtomicInteger calls = new AtomicInteger();

        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        dispatcher.dispatchMatchCreated(matchEvent("capture_the_zone"));

        assertEquals(1, calls.get());
    }

    @Test
    void filtersByRulesEngineId() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        AtomicInteger matchingCalls = new AtomicInteger();
        AtomicInteger otherCalls = new AtomicInteger();

        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                matchingCalls.incrementAndGet();
            }
        });
        dispatcher.register("other_rules", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                otherCalls.incrementAndGet();
            }
        });

        dispatcher.dispatchMatchCreated(matchEvent("capture_the_zone"));

        assertEquals(1, matchingCalls.get());
        assertEquals(0, otherCalls.get());
    }

    @Test
    void registrationCloseUnregistersListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        AtomicInteger calls = new AtomicInteger();
        NexoriListenerRegistration registration = dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        registration.close();
        registration.close();
        dispatcher.dispatchMatchCreated(matchEvent("capture_the_zone"));

        assertEquals(0, calls.get());
    }

    @Test
    void listenerThrowingExceptionDoesNotBreakDispatch() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        AtomicInteger calls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                throw new IllegalStateException("boom");
            }
        });
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onMatchCreated(NexoriMatchLifecycleEvent event) {
                calls.incrementAndGet();
            }
        });

        dispatcher.dispatchMatchCreated(matchEvent("capture_the_zone"));

        assertEquals(1, calls.get());
    }

    @Test
    void dispatchesPlayerArrivedWithMatchContext() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        List<NexoriPlayerMatchLifecycleEvent> received = new ArrayList<>();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerArrived(NexoriPlayerMatchLifecycleEvent event) {
                received.add(event);
            }
        });

        dispatcher.dispatchPlayerArrived(playerEvent("capture_the_zone"));

        assertEquals(1, received.size());
        assertEquals(PLAYER_ONE, received.get(0).playerUuid());
        assertEquals("match-1", received.get(0).match().matchId());
        assertEquals("queue-1", received.get(0).match().queueId());
        assertEquals("arena-1", received.get(0).match().arenaId());
        assertEquals("capture_the_zone", received.get(0).match().rulesEngineId());
    }

    @Test
    void dispatchesRemainingMvpCallbacks() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();
        AtomicInteger placementConfirmedCalls = new AtomicInteger();
        AtomicInteger placementCompletedCalls = new AtomicInteger();
        AtomicInteger startAllowedCalls = new AtomicInteger();
        AtomicInteger completedCalls = new AtomicInteger();
        AtomicInteger runtimeClosedCalls = new AtomicInteger();
        dispatcher.register("capture_the_zone", new NexoriMatchLifecycleListener() {
            @Override
            public void onPlayerPlacementConfirmed(NexoriPlayerPlacementLifecycleEvent event) {
                placementConfirmedCalls.incrementAndGet();
            }

            @Override
            public void onMatchPlacementCompleted(NexoriMatchLifecycleEvent event) {
                placementCompletedCalls.incrementAndGet();
            }

            @Override
            public void onMatchStartAllowed(NexoriMatchLifecycleEvent event) {
                startAllowedCalls.incrementAndGet();
            }

            @Override
            public void onMatchCompleted(NexoriMatchLifecycleEvent event) {
                completedCalls.incrementAndGet();
            }

            @Override
            public void onMatchRuntimeClosed(NexoriMatchLifecycleEvent event) {
                runtimeClosedCalls.incrementAndGet();
            }
        });

        dispatcher.dispatchPlayerPlacementConfirmed(new NexoriPlayerPlacementLifecycleEvent(
            playerEvent("capture_the_zone"),
            NexoriPlayerPlacementOutcome.CONFIRMED,
            new NexoriMatchPlacementState(2, 2, 1, false),
            "world_match_1",
            "template-1",
            2_500L
        ));
        dispatcher.dispatchMatchPlacementCompleted(matchEvent("capture_the_zone"));
        dispatcher.dispatchMatchStartAllowed(matchEvent("capture_the_zone"));
        dispatcher.dispatchMatchCompleted(matchEvent("capture_the_zone"));
        dispatcher.dispatchMatchRuntimeClosed(matchEvent("capture_the_zone"));

        assertEquals(1, placementConfirmedCalls.get());
        assertEquals(1, placementCompletedCalls.get());
        assertEquals(1, startAllowedCalls.get());
        assertEquals(1, completedCalls.get());
        assertEquals(1, runtimeClosedCalls.get());
    }

    @Test
    void registerRejectsBlankRulesEngineId() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();

        assertThrows(IllegalArgumentException.class, () -> dispatcher.register(" ", new NexoriMatchLifecycleListener() {
        }));
    }

    @Test
    void registerRejectsNullListener() {
        NexoriMatchLifecycleDispatcher dispatcher = new NexoriMatchLifecycleDispatcher();

        assertThrows(IllegalArgumentException.class, () -> dispatcher.register("capture_the_zone", null));
    }

    private static NexoriPlayerMatchLifecycleEvent playerEvent(String rulesEngineId) {
        return new NexoriPlayerMatchLifecycleEvent(
            matchEvent(rulesEngineId),
            PLAYER_ONE,
            "PlayerOne",
            "assignment-player-1",
            "PLAYER_ARRIVED",
            2_000L
        );
    }

    private static NexoriMatchLifecycleEvent matchEvent(String rulesEngineId) {
        return new NexoriMatchLifecycleEvent(
            "match-1",
            "queue-1",
            "arena-1",
            "assignment-1",
            "external-1",
            rulesEngineId,
            "none",
            List.of(PLAYER_ONE, PLAYER_TWO),
            List.of(PLAYER_ONE),
            List.of(PLAYER_ONE),
            List.of(),
            List.of(PLAYER_ONE, PLAYER_TWO),
            new NexoriMatchPlacementState(2, 1, 1, false),
            "test",
            1_000L,
            2_000L
        );
    }
}
